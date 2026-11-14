package com.fancyfin.tv.data

import com.fancyfin.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder

/** A popular title from TMDB, which may or may not be on your server. */
/** A stretch of a video with a meaning: an intro, recap, credits and so on. */
class Segment(val type: String, val startMs: Long, val endMs: Long)

class Chapter(val name: String, val startMs: Long)

/** A subtitle file that sits beside the video (not inside it). */
class ExternalSub(val url: String, val mime: String, val language: String?, val title: String)

/** Preview pictures for the seek bar: sheets of small frames, one every intervalMs. */
class Trickplay(val width: Int, val height: Int, val tileW: Int, val tileH: Int, val intervalMs: Long, val count: Int)

/** What the player needs to know about a video beyond the file itself. */
class MediaInfo(
    val chapters: List<Chapter> = emptyList(),
    val segments: List<Segment> = emptyList(),
    val subtitles: List<ExternalSub> = emptyList(),
    val trickplay: Trickplay? = null,
)

/** A trailer, teaser, clip or behind-the-scenes video on YouTube. */
data class Video(val key: String, val name: String, val type: String, val official: Boolean)

/** Trailers first (official before the rest), then teasers; the keys to try in order. */
fun trailerKeys(videos: List<Video>): List<String> =
    videos.filter { it.type == "Trailer" || it.type == "Teaser" }
        .sortedByDescending { (if (it.type == "Trailer") 2 else 0) + (if (it.official) 1 else 0) }
        .map { it.key }

class Person(val id: Int, val name: String, val role: String?, val photo: String?)

/** An actor's page: photo, bio and the films and shows they're known for, most popular first. */
class PersonInfo(
    val id: Int,
    val name: String,
    val biography: String?,
    val photo: String?,
    val born: String?,
    val credits: List<Discover>,
)

class TmdbTitle(
    val kind: String,
    val id: Int,
    val title: String,
    val tagline: String?,
    val overview: String?,
    val year: Int?,
    val rating: Double?,
    val runtimeMinutes: Int,
    val genres: List<String>,
    val backdrop: String?,
    val poster: String?,
    val cast: List<Person>,
    val videos: List<Video>,
    val more: List<Discover>,
    val collectionId: Int?,
    val collectionName: String?,
)

data class Discover(
    val tmdbId: Int,
    val kind: String,
    val title: String,
    val overview: String?,
    val year: Int?,
    val rating: Double?,
    val genres: List<String>,
    val backdrop: String?,
    val poster: String?,
)

class ApiException(message: String, val code: Int = 0) : IOException(message)

/** Talks to a Jellyfin server over its REST API. All calls run off the main thread. */
class JellyfinClient(private val prefs: Prefs) {
    val fancyfin: String get() = prefs.fancyfin.orEmpty().trimEnd('/')

    /** Jellyfin as the Fancyfin container proxies it. Images and streams come from here too. */
    val server: String get() = if (fancyfin.isEmpty()) "" else "$fancyfin/api"
    val userId: String get() = prefs.userId.orEmpty()
    val token: String get() = prefs.token.orEmpty()
    val isSignedIn: Boolean get() = server.isNotEmpty() && token.isNotEmpty() && userId.isNotEmpty()

    private fun authHeader(withToken: Boolean): String {
        val base = "MediaBrowser Client=\"Fancyfin TV\", Device=\"Android TV\", DeviceId=\"${prefs.deviceId}\", " +
            "Version=\"${BuildConfig.VERSION_NAME}\""
        return if (withToken && token.isNotEmpty()) "$base, Token=\"$token\"" else base
    }

    private fun request(
        base: String,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        withToken: Boolean = true,
    ): String {
        val connection = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", authHeader(withToken))
            if (body != null || method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                // Radarr and Sonarr explain refusals in the body; pass that on when there is one.
                val text = try { connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (e: Exception) { null }
                throw ApiException(readableError(text) ?: "HTTP $code", code)
            }
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readableError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val t = body.trim()
            if (t.startsWith("[")) {
                val a = JSONArray(t)
                (0 until a.length()).firstNotNullOfOrNull { a.optJSONObject(it)?.optString("errorMessage")?.takeIf { m -> m.isNotEmpty() } }
            } else {
                val o = JSONObject(t)
                o.optString("message").ifEmpty { o.optString("error") }.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) { null }
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) { request(server, path) }

    private suspend fun send(path: String, method: String = "POST", body: JSONObject? = null) {
        withContext(Dispatchers.IO) { request(server, path, method, body) }
    }

    // ---- Connecting and signing in -----------------------------------------------------------

    /** Checks the address is a Fancyfin container that is connected to Jellyfin. Returns the clean address. */
    suspend fun checkFancyfin(address: String): String = withContext(Dispatchers.IO) {
        val clean = address.trimEnd('/')
        val json = JSONObject(request(clean, "/config.json", withToken = false))
        if (!json.has("proxy")) throw ApiException("Not Fancyfin")
        if (!json.optBoolean("proxy")) throw ApiException("Fancyfin has no Jellyfin address set")
        clean
    }

    private fun saveSession(fancyfin: String, json: JSONObject) {
        prefs.fancyfin = fancyfin.trimEnd('/')
        prefs.token = json.getString("AccessToken")
        prefs.userId = json.getJSONObject("User").getString("Id")
        prefs.userName = json.getJSONObject("User").optString("Name")
    }

    suspend fun signIn(fancyfin: String, user: String, password: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("Username", user).put("Pw", password)
        val json = JSONObject(request("$fancyfin/api", "/Users/AuthenticateByName", "POST", body, withToken = false))
        saveSession(fancyfin, json)
    }

    class QuickConnect(val code: String, val secret: String)

    /** Starts Quick Connect. Throws with code 401 when it is switched off in Jellyfin. */
    suspend fun quickConnectStart(fancyfin: String): QuickConnect = withContext(Dispatchers.IO) {
        val json = JSONObject(request("$fancyfin/api", "/QuickConnect/Initiate", "POST", withToken = false))
        QuickConnect(json.getString("Code"), json.getString("Secret"))
    }

    /** True once the code has been approved in another Jellyfin app. */
    suspend fun quickConnectApproved(fancyfin: String, secret: String): Boolean = withContext(Dispatchers.IO) {
        JSONObject(request("$fancyfin/api", "/QuickConnect/Connect?secret=$secret", withToken = false))
            .optBoolean("Authenticated")
    }

    suspend fun quickConnectFinish(fancyfin: String, secret: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("Secret", secret)
        val json = JSONObject(request("$fancyfin/api", "/Users/AuthenticateWithQuickConnect", "POST", body, withToken = false))
        saveSession(fancyfin, json)
    }

    /** Looks for Fancyfin on this network: tries port 8282 on every address near this device's. */
    suspend fun discover(): List<String> = withContext(Dispatchers.IO) {
        val prefixes = LinkedHashSet<String>()
        try {
            for (nic in NetworkInterface.getNetworkInterfaces()) {
                if (nic.isLoopback || !nic.isUp) continue
                for (addr in nic.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        prefixes.add(addr.hostAddress!!.substringBeforeLast('.'))
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        val gate = Semaphore(64)
        coroutineScope {
            prefixes.flatMap { prefix -> (1..254).map { "$prefix.$it" } }.map { host ->
                async {
                    gate.withPermit {
                        try {
                            val connection = URL("http://$host:8282/config.json").openConnection() as HttpURLConnection
                            connection.connectTimeout = 600
                            connection.readTimeout = 800
                            try {
                                if (connection.responseCode == 200) {
                                    val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                                    if (JSONObject(text).has("proxy")) "http://$host:8282" else null
                                } else {
                                    null
                                }
                            } finally {
                                connection.disconnect()
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    // ---- Browsing ----------------------------------------------------------------------------

    private val fields = "Overview,Taglines,Genres,ParentBackdropItemId,ParentBackdropImageTags,ParentLogoItemId,ParentLogoImageTag,ProviderIds"

    private fun items(json: String): List<Item> {
        val trimmed = json.trim()
        val parsed = if (trimmed.startsWith("[")) {
            parseItems(JSONArray(trimmed))
        } else {
            parseItems(JSONObject(trimmed).optJSONArray("Items"))
        }
        return consolidate(parsed)
    }

    suspend fun resume(): List<Item> =
        items(get("/Users/$userId/Items/Resume?Limit=16&MediaTypes=Video&Fields=$fields&EnableTotalRecordCount=false"))

    suspend fun nextUp(): List<Item> =
        items(get("/Shows/NextUp?UserId=$userId&Limit=16&Fields=$fields&EnableTotalRecordCount=false"))

    suspend fun latest(parentId: String? = null, type: String? = null): List<Item> {
        val parent = if (parentId != null) "&ParentId=$parentId" else ""
        val kinds = if (type != null) "&IncludeItemTypes=$type" else ""
        return items(get("/Users/$userId/Items/Latest?Limit=20&Fields=$fields$parent$kinds"))
    }

    /** Every movie or show, sorted, with optional extra query such as "&Is4K=true". */
    suspend fun library(
        type: String,
        sort: String = "SortName",
        descending: Boolean = false,
        extra: String = "",
        limit: Int = 3000,
    ): List<Item> {
        val order = if (descending) "Descending" else "Ascending"
        return items(
            get(
                "/Users/$userId/Items?IncludeItemTypes=$type&Recursive=true&SortBy=$sort,SortName&SortOrder=$order" +
                    "&Limit=$limit&Fields=$fields&EnableTotalRecordCount=false$extra",
            ),
        )
    }

    suspend fun favorites(): List<Item> =
        items(get("/Users/$userId/Items?Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series&Limit=40&Fields=$fields"))

    suspend fun search(query: String): List<Item> {
        val q = URLEncoder.encode(query, "UTF-8")
        return items(
            get("/Users/$userId/Items?SearchTerm=$q&Recursive=true&IncludeItemTypes=Movie,Series&Limit=60&Fields=$fields"),
        )
    }

    /** One item in full. Pass the copies folded into it so they aren't lost. */
    suspend fun item(id: String, duplicates: List<String> = emptyList()): Item =
        parseItem(JSONObject(get("/Users/$userId/Items/$id"))).copy(duplicateIds = duplicates)

    /** Every playable copy of this item, across its duplicates and any merged versions. */
    suspend fun versions(item: Item): List<MediaVersion> {
        val out = ArrayList<MediaVersion>()
        for (id in listOf(item.id) + item.duplicateIds) {
            val json = JSONObject(get("/Users/$userId/Items/$id"))
            out.addAll(parseVersions(parseItem(json), json))
        }
        return out
    }

    suspend fun seasons(seriesId: String): List<Item> =
        items(get("/Shows/$seriesId/Seasons?UserId=$userId&Fields=$fields"))

    suspend fun episodes(seriesId: String, seasonId: String): List<Item> =
        items(get("/Shows/$seriesId/Episodes?UserId=$userId&SeasonId=$seasonId&Fields=$fields"))

    suspend fun similar(id: String): List<Item> =
        items(get("/Items/$id/Similar?UserId=$userId&Limit=16&Fields=$fields"))

    // ---- Discover (TMDB through Fancyfin) ----------------------------------------------------

    private var genreNames: Map<String, String>? = null
    private var genreIds: Map<String, Map<String, Int>>? = null

    private fun tmdbImage(path: String?, size: String): String? =
        if (path.isNullOrEmpty()) null else "https://image.tmdb.org/t/p/$size$path"

    /** TMDB's genre lists, so ids can be shown as names and names looked up as ids. */
    private fun loadGenres() {
        if (genreNames != null) return
        val names = HashMap<String, String>()
        val ids = HashMap<String, Map<String, Int>>()
        for (k in listOf("movie", "tv")) {
            val byName = HashMap<String, Int>()
            try {
                val genres = JSONObject(request(fancyfin, "/tmdb/genre/$k/list")).optJSONArray("genres")
                if (genres != null) {
                    for (i in 0 until genres.length()) {
                        val g = genres.getJSONObject(i)
                        names[g.getInt("id").toString()] = g.getString("name")
                        byName[g.getString("name").lowercase()] = g.getInt("id")
                    }
                }
            } catch (e: Exception) {
                // Names are a nicety.
            }
            ids[k] = byName
        }
        genreNames = names
        genreIds = ids
    }

    private fun parseTmdb(results: JSONArray, defaultKind: String): List<Discover> {
        val out = ArrayList<Discover>()
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val type = r.optString("media_type", defaultKind)
            if (type != "movie" && type != "tv") continue
            val date = r.optString(if (type == "movie") "release_date" else "first_air_date")
            val ids = r.optJSONArray("genre_ids")
            val genres = ArrayList<String>()
            if (ids != null) {
                for (j in 0 until ids.length()) genreNames?.get(ids.getInt(j).toString())?.let { genres.add(it) }
            }
            out.add(
                Discover(
                    tmdbId = r.getInt("id"),
                    kind = type,
                    title = r.optString(if (type == "movie") "title" else "name"),
                    overview = r.optString("overview").takeIf { it.isNotEmpty() },
                    year = date.take(4).toIntOrNull(),
                    rating = if (r.optDouble("vote_average", 0.0) > 0) r.getDouble("vote_average") else null,
                    genres = genres.take(3),
                    backdrop = tmdbImage(r.optString("backdrop_path").takeIf { it != "null" }, "w1280"),
                    poster = tmdbImage(r.optString("poster_path").takeIf { it != "null" }, "w342"),
                ),
            )
        }
        return out
    }

    /** What's popular this week on TMDB. kind is "all", "movie" or "tv". Null when TMDB isn't set up in Fancyfin. */
    suspend fun trending(kind: String): List<Discover>? = withContext(Dispatchers.IO) {
        loadGenres()
        val body = try {
            request(fancyfin, "/tmdb/trending/$kind/week")
        } catch (e: ApiException) {
            if (e.code == 404) return@withContext null else throw e
        }
        parseTmdb(JSONObject(body).optJSONArray("results") ?: return@withContext emptyList(), if (kind == "tv") "tv" else "movie")
    }


    /** A video from TMDB (always YouTube here): a trailer, teaser, clip, featurette or behind the scenes. */
    suspend fun videos(kind: String, tmdbId: Int): List<Video> = withContext(Dispatchers.IO) {
        val body = try { request(fancyfin, "/tmdb/$kind/$tmdbId/videos") } catch (e: ApiException) { return@withContext emptyList() }
        parseVideos(JSONObject(body).optJSONArray("results"))
    }

    private fun parseVideos(list: JSONArray?): List<Video> {
        val out = ArrayList<Video>()
        if (list == null) return out
        for (i in 0 until list.length()) {
            val v = list.getJSONObject(i)
            if (v.optString("site") != "YouTube" || v.optString("key").isEmpty()) continue
            out.add(Video(v.getString("key"), v.optString("name"), v.optString("type"), v.optBoolean("official")))
        }
        return out
    }

    /** One title in full from TMDB: cast, videos and what's like it. Null when TMDB isn't set up. */
    suspend fun tmdbTitle(kind: String, tmdbId: Int): TmdbTitle? = withContext(Dispatchers.IO) {
        loadGenres()
        val body = try {
            request(fancyfin, "/tmdb/$kind/$tmdbId?append_to_response=credits,videos,recommendations,similar")
        } catch (e: ApiException) { return@withContext null }
        val r = JSONObject(body)
        val genres = ArrayList<String>()
        r.optJSONArray("genres")?.let { for (i in 0 until it.length()) genres.add(it.getJSONObject(i).getString("name")) }
        val cast = ArrayList<Person>()
        r.optJSONObject("credits")?.optJSONArray("cast")?.let {
            for (i in 0 until minOf(it.length(), 20)) {
                val c = it.getJSONObject(i)
                cast.add(Person(c.optInt("id"), c.optString("name"), c.optString("character").takeIf { s -> s.isNotEmpty() }, tmdbImage(c.optString("profile_path").takeIf { s -> s != "null" }, "w342")))
            }
        }
        val rec = r.optJSONObject("recommendations")?.optJSONArray("results")
        val sim = r.optJSONObject("similar")?.optJSONArray("results")
        val more = (if (rec != null && rec.length() > 0) parseTmdb(rec, kind) else if (sim != null) parseTmdb(sim, kind) else emptyList())
            .filter { it.tmdbId != tmdbId }
        val date = r.optString(if (kind == "movie") "release_date" else "first_air_date")
        val runtime = if (kind == "movie") r.optInt("runtime") else r.optJSONArray("episode_run_time")?.optInt(0) ?: 0
        val collection = r.optJSONObject("belongs_to_collection")
        TmdbTitle(
            kind = kind, id = tmdbId,
            title = r.optString(if (kind == "movie") "title" else "name"),
            tagline = r.optString("tagline").takeIf { it.isNotEmpty() },
            overview = r.optString("overview").takeIf { it.isNotEmpty() },
            year = date.take(4).toIntOrNull(),
            rating = if (r.optDouble("vote_average", 0.0) > 0) r.getDouble("vote_average") else null,
            runtimeMinutes = runtime,
            genres = genres,
            backdrop = tmdbImage(r.optString("backdrop_path").takeIf { it != "null" }, "w1280"),
            poster = tmdbImage(r.optString("poster_path").takeIf { it != "null" }, "w342"),
            cast = cast,
            videos = parseVideos(r.optJSONObject("videos")?.optJSONArray("results")),
            more = more,
            collectionId = collection?.optInt("id")?.takeIf { it > 0 },
            collectionName = collection?.optString("name"),
        )
    }

    /** One person from TMDB with their credits. Null when TMDB isn't set up. */
    suspend fun person(id: Int): PersonInfo? = withContext(Dispatchers.IO) {
        loadGenres()
        // Errors go up so the page can say what went wrong (a Fancyfin container that predates people lookups gives a 404).
        val body = request(fancyfin, "/tmdb/person/$id?append_to_response=combined_credits")
        val r = JSONObject(body)
        val cast = r.optJSONObject("combined_credits")?.optJSONArray("cast")
        val picked = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        if (cast != null) {
            for (i in 0 until cast.length()) {
                val c = cast.getJSONObject(i)
                val type = c.optString("media_type")
                if ((type != "movie" && type != "tv") || c.isNull("poster_path")) continue
                val ids = c.optJSONArray("genre_ids")
                var talk = false
                if (ids != null) for (j in 0 until ids.length()) if (ids.getInt(j) == 10767 || ids.getInt(j) == 10763) talk = true
                if (talk || !seen.add(type + c.optInt("id"))) continue
                picked.add(c)
            }
        }
        picked.sortByDescending { it.optDouble("popularity", 0.0) }
        val arr = JSONArray()
        picked.take(40).forEach { arr.put(it) }
        val born = r.optString("birthday").takeIf { it.isNotEmpty() && it != "null" }?.let {
            try {
                val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)
                java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ENGLISH).format(d!!)
            } catch (e: Exception) { it }
        }
        val place = r.optString("place_of_birth").takeIf { it.isNotEmpty() && it != "null" }
        PersonInfo(
            id = id,
            name = r.optString("name"),
            biography = r.optString("biography").trim().takeIf { it.isNotEmpty() && it != "null" },
            photo = tmdbImage(r.optString("profile_path").takeIf { it != "null" }, "h632"),
            born = listOfNotNull(born?.let { "Born $it" }, place).joinToString(" in ").takeIf { it.isNotEmpty() },
            credits = parseTmdb(arr, "movie"),
        )
    }

    /** The other films in a franchise, in release order. */
    suspend fun franchise(collectionId: Int): List<Discover> = withContext(Dispatchers.IO) {
        loadGenres()
        val body = try { request(fancyfin, "/tmdb/collection/$collectionId") } catch (e: ApiException) { return@withContext emptyList() }
        val parts = JSONObject(body).optJSONArray("parts") ?: return@withContext emptyList()
        val dated = ArrayList<Pair<String, JSONObject>>()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            dated.add((p.optString("release_date").takeIf { it.isNotEmpty() } ?: "9999") to p)
        }
        dated.sortBy { it.first }
        val sorted = JSONArray()
        dated.forEach { sorted.put(it.second) }
        parseTmdb(sorted, "movie")
    }

    // ---- Playback extras ---------------------------------------------------------------------

    /** Chapters, intro/credits markers, sidecar subtitles and seek-bar previews for a video. Never throws. */
    suspend fun mediaInfo(id: String, sourceId: String?): MediaInfo = withContext(Dispatchers.IO) {
        val json = try { JSONObject(request(server, "/Users/$userId/Items/$id")) } catch (e: Exception) { return@withContext MediaInfo() }

        val chapters = ArrayList<Chapter>()
        json.optJSONArray("Chapters")?.let {
            for (i in 0 until it.length()) {
                val c = it.getJSONObject(i)
                chapters.add(Chapter(c.optString("Name"), c.optLong("StartPositionTicks") / 10_000))
            }
        }

        var source: JSONObject? = null
        json.optJSONArray("MediaSources")?.let {
            for (i in 0 until it.length()) {
                val candidate = it.getJSONObject(i)
                if (source == null) source = candidate
                if (candidate.optString("Id") == sourceId) { source = candidate; break }
            }
        }
        val subtitles = ArrayList<ExternalSub>()
        val src = source
        src?.optJSONArray("MediaStreams")?.let {
            for (i in 0 until it.length()) {
                val st = it.getJSONObject(i)
                if (st.optString("Type") != "Subtitle" || !st.optBoolean("IsExternal")) continue
                val codec = st.optString("Codec").lowercase()
                val (ext, mime) = when (codec) {
                    "srt", "subrip" -> "srt" to "application/x-subrip"
                    "ass", "ssa" -> "ass" to "text/x-ssa"
                    "vtt", "webvtt" -> "vtt" to "text/vtt"
                    else -> continue
                }
                val url = "$server/Videos/$id/${src.optString("Id", id)}/Subtitles/${st.optInt("Index")}/Stream.$ext?api_key=$token"
                val title = st.optString("DisplayTitle").ifEmpty { st.optString("Title") }.ifEmpty { "Subtitles" }
                subtitles.add(ExternalSub(url, mime, st.optString("Language").takeIf { l -> l.isNotEmpty() }, title))
            }
        }

        var trickplay: Trickplay? = null
        json.optJSONObject("Trickplay")?.let { bySource ->
            val widths = bySource.optJSONObject(sourceId ?: id)
                ?: bySource.keys().asSequence().firstOrNull()?.let { k -> bySource.optJSONObject(k) }
            if (widths != null) {
                val best = widths.keys().asSequence().mapNotNull { k -> widths.optJSONObject(k) }
                    .minByOrNull { o -> Math.abs(o.optInt("Width") - 320) }
                if (best != null && best.optInt("Width") > 0 && best.optInt("Interval") > 0) {
                    trickplay = Trickplay(
                        best.optInt("Width"), best.optInt("Height"), best.optInt("TileWidth", 10), best.optInt("TileHeight", 10),
                        best.optLong("Interval"), best.optInt("ThumbnailCount"),
                    )
                }
            }
        }

        val segments = ArrayList<Segment>()
        try {
            val list = JSONObject(request(server, "/MediaSegments/$id")).optJSONArray("Items")
            if (list != null) {
                for (i in 0 until list.length()) {
                    val seg = list.getJSONObject(i)
                    segments.add(Segment(seg.optString("Type"), seg.optLong("StartTicks") / 10_000, seg.optLong("EndTicks") / 10_000))
                }
            }
        } catch (e: Exception) {
            // Older servers have no segments; chapter names are the fallback below.
        }
        if (segments.none { it.type == "Intro" || it.type == "Outro" }) {
            val introName = Regex("(?i)^(intro|opening|title sequence|theme)")
            val outroName = Regex("(?i)(credits|ending|outro)")
            for (i in chapters.indices) {
                val end = chapters.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
                if (introName.containsMatchIn(chapters[i].name)) segments.add(Segment("Intro", chapters[i].startMs, end))
                if (outroName.containsMatchIn(chapters[i].name)) segments.add(Segment("Outro", chapters[i].startMs, end))
            }
        }
        MediaInfo(chapters, segments, subtitles, trickplay)
    }

    fun trickplayUrl(id: String, sourceId: String?, width: Int, sheet: Int): String =
        "$server/Videos/$id/Trickplay/$width/$sheet.jpg?MediaSourceId=${sourceId ?: id}&api_key=$token"

    /** The episode after this one, across seasons (specials are skipped), or null. */
    suspend fun nextEpisode(item: Item): Item? {
        val seriesId = item.seriesId ?: return null
        val all = items(get("/Shows/$seriesId/Episodes?UserId=$userId&Fields=$fields"))
            .filter { (it.parentIndexNumber ?: 1) != 0 || item.parentIndexNumber == 0 }
        val at = all.indexOfFirst { it.id == item.id }
        return if (at >= 0 && at + 1 < all.size) all[at + 1] else null
    }

    // ---- Radarr and Sonarr (through the Fancyfin container) -----------------------------------

    private fun arrPath(app: String, sub: String) = "/arr/$app/api/v3/$sub"

    private fun tvdbId(tmdbId: Int): Int? =
        JSONObject(request(fancyfin, "/tmdb/tv/$tmdbId/external_ids")).optInt("tvdb_id").takeIf { it > 0 }

    /** True if Radarr (movies) or Sonarr (shows) already has this title; null if it couldn't be asked. */
    suspend fun inArr(kind: String, tmdbId: Int): Boolean? = withContext(Dispatchers.IO) {
        try {
            if (kind == "movie") {
                JSONArray(request(fancyfin, arrPath("radarr", "movie?tmdbId=$tmdbId"))).length() > 0
            } else {
                val tvdb = tvdbId(tmdbId) ?: return@withContext false
                JSONArray(request(fancyfin, arrPath("sonarr", "series?tvdbId=$tvdb"))).length() > 0
            }
        } catch (e: Exception) { null }
    }

    /** Quality profiles, root folders and (for shows) seasons the Add dialog offers. */
    class ArrOptions(val profiles: List<Pair<Int, String>>, val folders: List<String>, val seasons: List<Int>)

    private fun arrLookup(kind: String, tmdbId: Int): JSONObject {
        val movie = kind == "movie"
        val app = if (movie) "radarr" else "sonarr"
        val label = if (movie) "Radarr" else "Sonarr"
        val what = if (movie) "movie" else "series"
        val term = if (movie) {
            "tmdb:$tmdbId"
        } else {
            "tvdb:" + (tvdbId(tmdbId) ?: throw ApiException("TMDB has no TVDB id for this show, so Sonarr can't find it."))
        }
        val found = JSONArray(request(fancyfin, arrPath(app, "$what/lookup?term=" + URLEncoder.encode(term, "UTF-8"))))
        for (i in 0 until found.length()) {
            val c = found.getJSONObject(i)
            val match = if (movie) c.optInt("tmdbId") == tmdbId else ("tvdb:" + c.optInt("tvdbId")) == term
            if (match) return c
        }
        throw ApiException("$label didn't return an exact match for this title.")
    }

    suspend fun arrOptions(kind: String, tmdbId: Int): ArrOptions = withContext(Dispatchers.IO) {
        val movie = kind == "movie"
        val app = if (movie) "radarr" else "sonarr"
        val label = if (movie) "Radarr" else "Sonarr"
        val profiles = JSONArray(request(fancyfin, arrPath(app, "qualityprofile")))
        val folders = JSONArray(request(fancyfin, arrPath(app, "rootfolder")))
        if (profiles.length() == 0 || folders.length() == 0) {
            throw ApiException("$label has no quality profile or root folder to add to.")
        }
        val seasons = ArrayList<Int>()
        if (!movie) {
            val list = arrLookup(kind, tmdbId).optJSONArray("seasons")
            if (list != null) for (i in 0 until list.length()) {
                val n = list.getJSONObject(i).optInt("seasonNumber")
                if (n > 0) seasons.add(n)
            }
        }
        ArrOptions(
            (0 until profiles.length()).map { profiles.getJSONObject(it).let { o -> o.getInt("id") to o.optString("name") } },
            (0 until folders.length()).map { folders.getJSONObject(it).getString("path") },
            seasons.sorted(),
        )
    }

    /**
     * Adds a movie to Radarr or a show to Sonarr and searches straight away. The profile and folder are
     * the ones given (the first of each when null); `season` adds just that season of a show.
     * Returns what to tell the person; throws ApiException with a readable message when it can't.
     */
    suspend fun addToArr(kind: String, tmdbId: Int, profileId: Int? = null, folder: String? = null, season: Int? = null): String =
        withContext(Dispatchers.IO) {
            val movie = kind == "movie"
            val app = if (movie) "radarr" else "sonarr"
            val label = if (movie) "Radarr" else "Sonarr"
            val what = if (movie) "movie" else "series"
            val candidate = arrLookup(kind, tmdbId)
            if (candidate.optInt("id") > 0) return@withContext "Already in $label."

            val options = if (profileId == null || folder == null) arrOptions(kind, tmdbId) else null
            val body = JSONObject(candidate.toString())
            body.remove("id")
            body.put("qualityProfileId", profileId ?: options!!.profiles[0].first)
            body.put("rootFolderPath", folder ?: options!!.folders[0])
            body.put("monitored", true)
            if (movie) {
                body.put("minimumAvailability", "released")
                body.put("addOptions", JSONObject().put("monitor", "movieOnly").put("searchForMovie", true))
            } else {
                body.put("seasonFolder", true)
                val type = candidate.optString("seriesType")
                body.put("seriesType", if (type == "standard" || type == "daily" || type == "anime") type else "standard")
                val add = JSONObject().put("searchForMissingEpisodes", true).put("searchForCutoffUnmetEpisodes", false)
                if (season == null) {
                    add.put("monitor", "all")
                } else {
                    // Only the chosen season is monitored, so only it gets searched.
                    val seasons = body.optJSONArray("seasons") ?: JSONArray()
                    for (i in 0 until seasons.length()) {
                        val o = seasons.getJSONObject(i)
                        o.put("monitored", o.optInt("seasonNumber") == season)
                    }
                    body.put("seasons", seasons)
                }
                body.put("addOptions", add)
            }
            request(fancyfin, arrPath(app, what), "POST", body)
            "Added to $label" + (if (season != null) " (season $season)" else "") + ". It's searching for a copy now."
        }

    private val statusCache = HashMap<Int, String>()

    /**
     * Where a movie that isn't on your server stands: Streaming, Physical, In theatres or Coming soon,
     * with the date of the next streaming or physical release when one is known. Dates are Australia's
     * where TMDB has them, then the US's.
     */
    suspend fun releaseStatus(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        statusCache[tmdbId]?.let { return@withContext it }
        val body = try { request(fancyfin, "/tmdb/movie/$tmdbId/release_dates") } catch (e: ApiException) { return@withContext null }
        val countries = JSONObject(body).optJSONArray("results") ?: return@withContext null
        // Earliest date for each release type, per country.
        val byCountry = HashMap<String, HashMap<Int, String>>()
        for (i in 0 until countries.length()) {
            val c = countries.getJSONObject(i)
            val map = byCountry.getOrPut(c.optString("iso_3166_1")) { HashMap() }
            val dates = c.optJSONArray("release_dates") ?: continue
            for (j in 0 until dates.length()) {
                val d = dates.getJSONObject(j)
                val type = d.optInt("type")
                val date = d.optString("release_date").take(10)
                if (date.length == 10 && (map[type] == null || date < map[type]!!)) map[type] = date
            }
        }
        fun pick(type: Int): String? =
            byCountry["AU"]?.get(type) ?: byCountry["US"]?.get(type)
                ?: byCountry.values.mapNotNull { it[type] }.minOrNull()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        fun nice(date: String): String = try {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date)
            java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(parsed!!)
        } catch (e: Exception) { date }

        val digital = pick(4)
        val physical = pick(5)
        val theatrical = listOfNotNull(pick(3), pick(2)).minOrNull()
        val streaming = digital != null && digital <= today
        val onDisc = physical != null && physical <= today
        val inCinemas = theatrical != null && theatrical <= today
        val next = listOfNotNull(
            digital?.takeIf { it > today }?.let { it to "Streaming" },
            physical?.takeIf { it > today }?.let { it to "Physical" },
        ).minByOrNull { it.first }

        val label = when {
            streaming && onDisc -> "Streaming · Physical"
            streaming -> "Streaming"
            onDisc -> "Physical"
            inCinemas -> "In theatres" + (next?.let { " · ${it.second} ${nice(it.first)}" } ?: "")
            next != null -> "Coming soon · ${next.second} ${nice(next.first)}"
            theatrical != null -> "Coming soon · In theatres ${nice(theatrical)}"
            else -> "Coming soon"
        }
        statusCache[tmdbId] = label
        label
    }

    /** TMDB uses a few different genre names for TV. */
    private val tvAlias = mapOf(
        "action" to "action & adventure", "adventure" to "action & adventure",
        "science fiction" to "sci-fi & fantasy", "fantasy" to "sci-fi & fantasy", "war" to "war & politics",
    )

    /**
     * The most popular titles in one genre, on your server or not. types is "Movie", "Series" or both;
     * with both, movies and shows are mixed. Null when TMDB isn't set up in Fancyfin.
     */
    suspend fun popularInGenre(genre: String, types: String): List<Discover>? = withContext(Dispatchers.IO) {
        loadGenres()
        val kinds = ArrayList<String>()
        if (types.contains("Movie")) kinds.add("movie")
        if (types.contains("Series")) kinds.add("tv")
        val lists = ArrayList<List<Discover>>()
        for (kind in kinds) {
            val key = genre.lowercase()
            val id: Int? = genreIds?.get(kind)?.get(key)
                ?: (if (kind == "tv") genreIds?.get(kind)?.get(tvAlias[key] ?: "") else null)
            if (id == null) continue
            val body = try {
                request(fancyfin, "/tmdb/discover/$kind?with_genres=$id&sort_by=popularity.desc&vote_count.gte=100")
            } catch (e: ApiException) {
                if (e.code == 404) return@withContext null else throw e
            }
            lists.add(parseTmdb(JSONObject(body).optJSONArray("results") ?: continue, kind))
        }
        // Take turns between movies and shows.
        val out = ArrayList<Discover>()
        var n = 0
        while (out.size < 20 && lists.any { n < it.size }) {
            for (l in lists) if (n < l.size) out.add(l[n])
            n++
        }
        out
    }

    /** Everything on the server, keyed "movie:<tmdb id>" or "tv:<tmdb id>", to tell what is already here. */
    suspend fun serverIndex(): Map<String, Item> {
        val all = items(
            get(
                "/Users/$userId/Items?Recursive=true&IncludeItemTypes=Movie,Series&Fields=ProviderIds" +
                    "&EnableImages=false&EnableUserData=false&EnableTotalRecordCount=false",
            ),
        )
        val out = HashMap<String, Item>()
        for (item in all) {
            val id = item.tmdbId ?: continue
            out[(if (item.isMovie) "movie:" else "tv:") + id] = item
        }
        return out
    }

    /** Genre names on the server for these types ("Movie", "Series" or "Movie,Series"). */
    suspend fun genres(types: String): List<String> {
        val json = JSONObject(get("/Genres?UserId=$userId&IncludeItemTypes=$types&SortBy=SortName"))
        val array = json.optJSONArray("Items") ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it).optString("Name") }
    }

    suspend fun byGenre(genre: String, types: String, limit: Int = 20): List<Item> =
        genreLibrary(genre, types, "CommunityRating", true, limit)

    suspend fun genreLibrary(genre: String, types: String, sort: String, descending: Boolean, limit: Int = 3000): List<Item> =
        library(types, sort, descending, "&Genres=" + URLEncoder.encode(genre, "UTF-8"), limit)

    // ---- Actions -----------------------------------------------------------------------------

    suspend fun setPlayed(id: String, played: Boolean) =
        send("/Users/$userId/PlayedItems/$id", if (played) "POST" else "DELETE")

    suspend fun setFavorite(id: String, favorite: Boolean) =
        send("/Users/$userId/FavoriteItems/$id", if (favorite) "POST" else "DELETE")

    private fun playbackBody(id: String, ticks: Long, paused: Boolean = false) = JSONObject()
        .put("ItemId", id)
        .put("PositionTicks", ticks)
        .put("IsPaused", paused)
        .put("PlayMethod", "DirectPlay")
        .put("CanSeek", true)

    suspend fun reportStart(id: String, ticks: Long) = send("/Sessions/Playing", body = playbackBody(id, ticks))

    suspend fun reportProgress(id: String, ticks: Long, paused: Boolean) =
        send("/Sessions/Playing/Progress", body = playbackBody(id, ticks, paused))

    suspend fun reportStopped(id: String, ticks: Long) = send("/Sessions/Playing/Stopped", body = playbackBody(id, ticks))

    // ---- Addresses ---------------------------------------------------------------------------

    fun image(id: String, type: String, tag: String?, width: Int): String {
        val t = if (tag != null) "&tag=$tag" else ""
        return "$server/Items/$id/Images/$type?maxWidth=$width&quality=90$t"
    }

    /** The widest picture for an item: its own backdrop, else its show's. */
    fun backdrop(item: Item, width: Int = 1920): String? = when {
        item.backdropTag != null -> image(item.id, "Backdrop", item.backdropTag, width)
        item.parentBackdropId != null -> image(item.parentBackdropId, "Backdrop", item.parentBackdropTag, width)
        else -> null
    }

    fun logo(item: Item, width: Int = 600): String? = when {
        item.logoTag != null -> image(item.id, "Logo", item.logoTag, width)
        item.parentLogoId != null -> image(item.parentLogoId, "Logo", item.parentLogoTag, width)
        else -> null
    }

    fun poster(item: Item, width: Int = 400): String? =
        if (item.primaryTag != null) image(item.id, "Primary", item.primaryTag, width) else null

    /** A wide still: thumb, else backdrop, else poster. Episodes use their own screenshot. */
    fun landscape(item: Item, width: Int = 640): String? = when {
        item.isEpisode && item.primaryTag != null -> image(item.id, "Primary", item.primaryTag, width)
        item.thumbTag != null -> image(item.id, "Thumb", item.thumbTag, width)
        else -> backdrop(item, width) ?: poster(item, width)
    }

    /** A transcoded stream, used only when the TV can't play the original file. */
    fun transcodeUrl(id: String, sourceId: String? = null): String =
        "$server/Videos/$id/main.m3u8?MediaSourceId=${sourceId ?: id}&DeviceId=${prefs.deviceId}" +
            "&PlaySessionId=${java.util.UUID.randomUUID()}&VideoCodec=hevc,h264&AudioCodec=eac3,ac3,aac" +
            "&TranscodingMaxAudioChannels=6&MaxStreamingBitrate=120000000&SegmentContainer=ts" +
            "&BreakOnNonKeyFrames=True&api_key=$token"

    fun directStreamUrl(id: String, sourceId: String? = null): String =
        "$server/Videos/$id/stream?static=true&api_key=$token" + if (sourceId != null) "&MediaSourceId=$sourceId" else ""
}
