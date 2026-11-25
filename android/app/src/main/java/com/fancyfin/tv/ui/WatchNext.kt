package com.fancyfin.tv.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
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
