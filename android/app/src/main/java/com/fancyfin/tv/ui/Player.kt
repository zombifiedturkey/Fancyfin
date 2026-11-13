package com.fancyfin.tv.ui

import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TICKS_PER_MS = 10_000L

private fun describeVideo(f: Format?): String? {
    if (f == null || f.height <= 0) return null
    val size = when {
        f.height >= 2000 -> "4K"
        f.height >= 1000 -> "1080p"
        else -> "${f.height}p"
    }
    val codecs = f.codecs.orEmpty()
    val range = when {
        f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION || codecs.startsWith("dvh") || codecs.startsWith("dav") ->
            "Dolby Vision"
        f.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
        f.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
        else -> "SDR"
    }
    val codec = when {
        f.sampleMimeType == MimeTypes.VIDEO_H265 -> "HEVC"
        f.sampleMimeType == MimeTypes.VIDEO_H264 -> "H.264"
        f.sampleMimeType == MimeTypes.VIDEO_AV1 -> "AV1"
        f.sampleMimeType == MimeTypes.VIDEO_VP9 -> "VP9"
        f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> "HEVC"
        else -> f.sampleMimeType.orEmpty().substringAfter('/')
    }
    val fps = if (f.frameRate > 0) " · " + "%.3f".format(f.frameRate).trimEnd('0').trimEnd('.') + " fps" else ""
    return "$size · $range · $codec$fps"
}

private fun describeAudio(f: Format?): String? {
    if (f == null) return null
    val name = when (f.sampleMimeType) {
        MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
        MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        else -> f.sampleMimeType.orEmpty().substringAfter('/')
    }
    val channels = when (f.channelCount) {
        8 -> " 7.1"
        6 -> " 5.1"
        2 -> " stereo"
        1 -> " mono"
        else -> ""
    }
    return name + channels
}

/**
 * Plays the original file untouched (direct play) so HDR, Dolby Vision and Atmos reach the TV as they
 * are. If the TV can't play the file, it falls back to a transcoded stream. Progress is reported to
 * Jellyfin so "Continue watching" stays right on every device.
 */
@Composable
fun PlayerScreen(item: Item, client: JellyfinClient, onDone: () -> Unit) {
    val context = LocalContext.current
    val startMs = if (item.played) 0L else item.positionTicks / TICKS_PER_MS
    var info by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }
    val lastMs = remember(item.id) { longArrayOf(startMs) }

    val player = remember(item.id) {
        // Audio passthrough to the TV or receiver is automatic; the decoder fallback lets a second-choice
        // decoder take over when the first one refuses a stream.
        val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, factory).build().apply {
            setMediaItem(MediaItem.fromUri(client.directStreamUrl(item.id)), startMs)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var fellBack = false

        fun refreshInfo() {
            val v = describeVideo(player.videoFormat)
            val a = describeAudio(player.audioFormat)
            info = listOfNotNull(v, a).joinToString("\n")
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onDone()
                if (state == Player.STATE_READY) refreshInfo()
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = refreshInfo()

            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) = refreshInfo()

            override fun onPlayerError(error: PlaybackException) {
                if (fellBack) {
                    note = "Playback failed (${error.errorCodeName})."
                    return
                }
                // The TV can't play the original file; ask Jellyfin for a version it can.
                fellBack = true
                note = "Your TV couldn't play the original, so Jellyfin is converting it (HDR and Atmos may be lost)."
                val at = player.currentPosition
                player.setMediaItem(MediaItem.fromUri(client.transcodeUrl(item.id)), at)
                player.prepare()
                player.playWhenReady = true
            }
        }
        player.addListener(listener)
        onDispose {
            lastMs[0] = player.currentPosition
            player.removeListener(listener)
            player.release()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(player) {
        try {
            client.reportStart(item.id, startMs * TICKS_PER_MS)
        } catch (e: Exception) {
            // Reporting is best effort.
        }
        try {
            while (true) {
                delay(10_000)
                lastMs[0] = player.currentPosition
                client.reportProgress(item.id, lastMs[0] * TICKS_PER_MS, !player.isPlaying)
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    client.reportStopped(item.id, lastMs[0] * TICKS_PER_MS)
                } catch (e: Exception) {
                    // Best effort.
                }
            }
        }
    }

    BackHandler { onDone() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        controlsVisible = v == View.VISIBLE
                    })
                    keepScreenOn = true
                    systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    isFocusable = true
                    post { requestFocus() }
                }
            },
        )

        // What the TV is actually being sent, shown with the controls so you can check HDR and Atmos.
        if (controlsVisible && info.isNotEmpty()) {
            Column(
                Modifier.padding(start = 48.dp, top = 32.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                info.split("\n").forEach { Text(it, color = Palette.Text, fontSize = 12.sp) }
            }
        }
        note?.let {
            Text(
                it, color = Palette.Accent, fontSize = 12.sp,
                modifier = Modifier.padding(start = 48.dp, top = 96.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
