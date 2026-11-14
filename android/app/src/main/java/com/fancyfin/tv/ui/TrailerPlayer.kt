package com.fancyfin.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private fun page(key: String) = """
<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player;
function onYouTubeIframeAPIReady(){
  player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$key',
    host:'https://www.youtube.com',
    playerVars:{autoplay:1,controls:1,rel:0,modestbranding:1,playsinline:1,fs:0,iv_load_policy:3,cc_load_policy:0,origin:'https://com.fancyfin.tv',enablejsapi:1},
    events:{
      onReady:function(e){Android.ready();e.target.playVideo();},
      onError:function(e){Android.failed(e.data);},
      onStateChange:function(e){if(e.data===0)Android.ended();}
    }});
}
function toggle(){if(!player)return;if(player.getPlayerState()===1)player.pauseVideo();else player.playVideo();}
function seek(s){if(!player)return;player.seekTo(player.getCurrentTime()+s,true);}
</script></body></html>
"""

/** What the page calls back into; a named public class so WebView can reach it. */
class TrailerBridge(
    private val main: Handler,
    private val onReady: () -> Unit,
    private val onFailed: (Int) -> Unit,
    private val onEnded: () -> Unit,
) {
    @JavascriptInterface fun ready() { main.post { onReady() } }
    @JavascriptInterface fun failed(code: Int) { main.post { onFailed(code) } }
    @JavascriptInterface fun ended() { main.post { onEnded() } }
}

/** Last resort when YouTube won't embed a video: hand it to the YouTube app. */
fun openInYouTube(context: Context, key: String) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")).addFlags(flags))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")).addFlags(flags))
        } catch (x: Exception) {
            // Nothing can open it.
        }
    }
}

/**
 * Plays a trailer full screen inside Fancyfin using YouTube's embedded player. OK pauses or resumes,
 * Left and Right skip 10 seconds, Back closes. Some videos don't allow embedding; then it tries the
 * next one, and if none work it opens the YouTube app instead.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayer(keys: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var index by remember { mutableStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>("Loading trailer…") }
    val main = remember { Handler(Looper.getMainLooper()) }

    BackHandler { onClose() }

    var skipping by remember { mutableStateOf<String?>(null) }

    fun next() {
        if (index + 1 < keys.size) {
            ready = false
            message = "Trying the next trailer…"
            index += 1
        } else {
            openInYouTube(context, keys[0])
            onClose()
        }
    }

    // Say why a trailer failed for a moment before moving on, so there's something to report.
    LaunchedEffect(skipping) {
        val why = skipping ?: return@LaunchedEffect
        message = why
        delay(2500)
        skipping = null
        next()
    }

    LaunchedEffect(Unit) {
        delay(80)
        try { focus.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
        delay(4000)
        hint = false
    }
    LaunchedEffect(index, web) {
        val view = web ?: return@LaunchedEffect
        message = "Loading trailer…"
        // YouTube only embeds for pages that say who they are; the app's id works as the referrer.
        view.loadDataWithBaseURL("https://com.fancyfin.tv/", page(keys[index]), "text/html", "utf-8", null)
        // If the player never reports in, move on rather than sit on a black screen.
        delay(15_000)
        if (!ready) skipping = "YouTube didn't respond (timed out)."
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        web?.evaluateJavascript("toggle()", null); true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> { web?.evaluateJavascript("seek(10)", null); true }
                    Key.DirectionLeft, Key.MediaRewind -> { web?.evaluateJavascript("seek(-10)", null); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                            if (request.isForMainFrame || request.url.toString().contains("iframe_api")) {
                                main.post { skipping = "Couldn't load the player: " + error.description }
                            }
                        }
                    }
                    addJavascriptInterface(
                        TrailerBridge(main, { ready = true; message = null }, { code -> skipping = "YouTube won't embed this video (code $code)." }, { onClose() }),
                        "Android",
                    )
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
        message?.let {
            Text(
                it, color = Palette.Text, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).background(Color(0xB3000000)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (hint && ready) {
            Text(
                "OK  pause / play        ◀ ▶  skip 10s        Back  close",
                color = Palette.Text, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 24.dp)
                    .background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
