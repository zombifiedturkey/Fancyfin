package com.fancyfin.tv.data

import com.fancyfin.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

class ApiException(message: String, val code: Int = 0) : IOException(message)

/** Talks to a Jellyfin server over its REST API. All calls run off the main thread. */
class JellyfinClient(private val prefs: Prefs) {
    val server: String get() = prefs.server.orEmpty().trimEnd('/')
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
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
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

    // ---- Sign in -----------------------------------------------------------------------------

    /** Checks the address is a Jellyfin server and returns its name. */
    suspend fun probe(address: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject(request(address, "/System/Info/Public", withToken = false))
        json.optString("ServerName", "Jellyfin")
    }

    suspend fun signIn(address: String, user: String, password: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("Username", user).put("Pw", password)
        val json = JSONObject(request(address, "/Users/AuthenticateByName", "POST", body, withToken = false))
        prefs.server = address.trimEnd('/')
        prefs.token = json.getString("AccessToken")
        prefs.userId = json.getJSONObject("User").getString("Id")
        prefs.userName = json.getJSONObject("User").optString("Name")
    }

    /** Servers on the local network answer this UDP broadcast. Returns addresses like http://192.168.1.5:8096. */
    suspend fun discover(): List<String> = withContext(Dispatchers.IO) {
        val found = LinkedHashSet<String>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1_500
                val message = "who is JellyfinServer?".toByteArray()
                socket.send(DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), 7359))
                val buffer = ByteArray(1024)
                val deadline = System.currentTimeMillis() + 2_500
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val reply = JSONObject(String(packet.data, 0, packet.length))
                        reply.optString("Address").takeIf { it.isNotEmpty() }?.let { found.add(it) }
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // No network or broadcast blocked: the person can type the address instead.
        }
        found.toList()
    }

    // ---- Browsing ----------------------------------------------------------------------------

    private val fields = "Overview,Taglines,Genres,ParentBackdropItemId,ParentBackdropImageTags,ParentLogoItemId,ParentLogoImageTag"

    private fun items(json: String): List<Item> {
        val trimmed = json.trim()
        return if (trimmed.startsWith("[")) {
            parseItems(JSONArray(trimmed))
        } else {
            parseItems(JSONObject(trimmed).optJSONArray("Items"))
        }
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

    suspend fun library(type: String, sort: String = "SortName", descending: Boolean = false, limit: Int = 300): List<Item> {
        val order = if (descending) "Descending" else "Ascending"
        return items(
            get(
                "/Users/$userId/Items?IncludeItemTypes=$type&Recursive=true&SortBy=$sort&SortOrder=$order" +
                    "&Limit=$limit&Fields=$fields&EnableTotalRecordCount=false",
            ),
        )
    }

    suspend fun favorites(): List<Item> =
        items(get("/Users/$userId/Items?Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series&Limit=40&Fields=$fields"))

    suspend fun search(query: String): List<Item> {
        val q = URLEncoder.encode(query, "UTF-8")
        return items(
            get("/Users/$userId/Items?SearchTerm=$q&Recursive=true&IncludeItemTypes=Movie,Series,Episode&Limit=40&Fields=$fields"),
        )
    }

    suspend fun item(id: String): Item = parseItem(JSONObject(get("/Users/$userId/Items/$id")))

    suspend fun seasons(seriesId: String): List<Item> =
        items(get("/Shows/$seriesId/Seasons?UserId=$userId&Fields=$fields"))

    suspend fun episodes(seriesId: String, seasonId: String): List<Item> =
        items(get("/Shows/$seriesId/Episodes?UserId=$userId&SeasonId=$seasonId&Fields=$fields"))

    suspend fun similar(id: String): List<Item> =
        items(get("/Items/$id/Similar?UserId=$userId&Limit=16&Fields=$fields"))

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

    fun directStreamUrl(id: String): String = "$server/Videos/$id/stream?static=true&api_key=$token"
}
