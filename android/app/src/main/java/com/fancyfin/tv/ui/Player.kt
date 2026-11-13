package com.fancyfin.tv.ui

import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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

/**
 * Plays the original file untouched (direct play) so HDR, Dolby Vision and Atmos reach the TV as they
 * are. Progress is reported to Jellyfin so "Continue watching" stays right on every device.
 */
@Composable
fun PlayerScreen(item: Item, client: JellyfinClient, onDone: () -> Unit) {
    val context = LocalContext.current
    val startMs = if (item.played) 0L else item.positionTicks / TICKS_PER_MS

    val player = remember(item.id) {
        val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, factory).build().apply {
            setMediaItem(MediaItem.fromUri(client.directStreamUrl(item.id)), startMs)
            playWhenReady = true
            prepare()
        }
    }

    // Keep the screen on and report where we are.
    DisposableEffect(player) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onDone()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
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
                client.reportProgress(item.id, player.currentPosition * TICKS_PER_MS, !player.isPlaying)
            }
        } finally {
            val last = player.currentPosition * TICKS_PER_MS
            withContext(NonCancellable) {
                try {
                    client.reportStopped(item.id, last)
                } catch (e: Exception) {
                    // Best effort.
                }
            }
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

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
                    keepScreenOn = true
                    systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    isFocusable = true
                    post { requestFocus() }
                }
            },
        )
    }
}
