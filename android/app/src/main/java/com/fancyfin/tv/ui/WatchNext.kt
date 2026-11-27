package com.fancyfin.tv.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.MainActivity
import com.fancyfin.tv.data.JellyfinClient
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A title asked for from outside the app (a Watch Next card). The app starts playing it. */
object DeepLink {
    var itemId by mutableStateOf<String?>(null)
}

/**
 * Puts what you're part way through into the Android TV launcher's Watch Next row, so it shows up
 * on the Shield's home screen. Our own cards are replaced each time, so the row matches Continue watching.
 */
object WatchNext {
    private const val PREFIX = "fancyfin:"

    suspend fun publish(context: Context, client: JellyfinClient, resume: List<Item>) = withContext(Dispatchers.IO) {
        publishLauncherRows(context, client, resume)
        try {
            val resolver = context.contentResolver
            val stale = ArrayList<Long>()
            resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val p = WatchNextProgram.fromCursor(c)
                    if (p.internalProviderId?.startsWith(PREFIX) == true) stale.add(p.id)
                }
            }
            stale.forEach { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }

            resume.take(6).forEach { item ->
                val builder = WatchNextProgram.Builder()
                builder.setType(if (item.isEpisode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                builder.setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                builder.setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                builder.setTitle(item.seriesName ?: item.name)
                val line = if (item.isEpisode) listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else item.overview
                if (line != null) builder.setDescription(line)
                val poster = if (item.isEpisode && item.seriesId != null) client.image(item.seriesId, "Primary", null, 342) else client.poster(item, 342)
                if (poster != null) {
                    builder.setPosterArtUri(Uri.parse(poster))
                    builder.setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3)
                }
                if (item.runtimeTicks > 0) {
                    builder.setDurationMillis((item.runtimeTicks / 10_000L).toInt())
                    builder.setLastPlaybackPositionMillis((item.positionTicks / 10_000L).toInt())
                }
                builder.setInternalProviderId(PREFIX + item.id)
                builder.setIntentUri(Uri.parse("fancyfin://play/" + item.id))
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, builder.build().toContentValues())
            }
        } catch (e: Exception) {
            // The launcher row is a bonus; never let it break the app.
        }
    }
}

/**
 * What the custom launcher reads through [com.fancyfin.tv.LauncherProvider]: Continue watching and Up next,
 * kept in preferences so the launcher gets an answer even when the app isn't running.
 */
object LauncherRows {
    const val AUTHORITY = "com.fancyfin.tv.launcher"
    private const val KEY = "launcher_rows"

    class Row(
        val kind: String,
        val sort: Int,
        val itemId: String,
        val title: String,
        val subtitle: String,
        val posterUrl: String,
        val progress: Double,
    )

    fun save(context: Context, rows: List<Row>) {
        val array = JSONArray()
        rows.forEach {
            array.put(
                JSONObject().put("kind", it.kind).put("sort", it.sort).put("id", it.itemId).put("title", it.title)
                    .put("subtitle", it.subtitle).put("poster", it.posterUrl).put("progress", it.progress),
            )
        }
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
        context.contentResolver.notifyChange(Uri.parse("content://$AUTHORITY/rows"), null)
    }

    fun load(context: Context): List<Row> {
        val text = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Row(
                    o.optString("kind"), o.optInt("sort"), o.optString("id"), o.optString("title"),
                    o.optString("subtitle"), o.optString("poster"), o.optDouble("progress", 0.0),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private fun launcherRow(client: JellyfinClient, kind: String, sort: Int, item: Item): LauncherRows.Row {
    val poster = if (item.isEpisode && item.seriesId != null) client.image(item.seriesId, "Primary", null, 300) else client.poster(item, 300)
    val subtitle = if (item.isEpisode) {
        val label = if (item.parentIndexNumber != null && item.indexNumber != null) "S${item.parentIndexNumber}·E${item.indexNumber} " else ""
        label + item.name
    } else {
        ""
    }
    val progress = if (kind == "continue" && item.runtimeTicks > 0) (item.positionTicks.toDouble() / item.runtimeTicks).coerceIn(0.0, 1.0) else 0.0
    return LauncherRows.Row(kind, sort, item.id, item.seriesName ?: item.name, subtitle, poster ?: "", progress)
}

private suspend fun publishLauncherRows(context: Context, client: JellyfinClient, resume: List<Item>) {
    try {
        val resumeIds = resume.map { it.id }.toSet()
        val next = try { client.nextUp().filter { it.id !in resumeIds }.take(10) } catch (e: Exception) { emptyList() }
        val rows = resume.take(12).mapIndexed { i, it -> launcherRow(client, "continue", i, it) } +
            next.mapIndexed { i, it -> launcherRow(client, "nextup", i, it) }
        LauncherRows.save(context, rows)
    } catch (e: Exception) {
        // The launcher's rows are a bonus; never let them break the app.
    }
}
