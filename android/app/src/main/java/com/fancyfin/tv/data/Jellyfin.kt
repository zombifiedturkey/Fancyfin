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
            if (code !in 200..299) throw ApiException("HTTP $code", code)
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
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
