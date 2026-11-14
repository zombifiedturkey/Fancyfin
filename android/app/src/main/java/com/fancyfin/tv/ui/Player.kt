package com.fancyfin.tv.ui

import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import com.fancyfin.tv.data.MediaInfo
import com.fancyfin.tv.data.Segment
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

private fun languageName(code: String?): String? {
    if (code.isNullOrEmpty() || code == "und") return null
    return try {
        java.util.Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotEmpty() } ?: code
    } catch (e: Exception) { code }
}

private fun audioLabel(f: Format): String = listOfNotNull(
    languageName(f.language),
    f.label?.takeIf { it.isNotEmpty() && !it.equals(languageName(f.language), ignoreCase = true) },
    describeAudio(f),
    if (f.roleFlags and C.ROLE_FLAG_COMMENTARY != 0) "Commentary" else null,
).joinToString(" · ").ifEmpty { "Audio" }

private fun subtitleLabel(f: Format): String = listOfNotNull(
    languageName(f.language),
    f.label?.takeIf { it.isNotEmpty() && !it.equals(languageName(f.language), ignoreCase = true) },
    if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) "Forced" else null,
    if (f.roleFlags and C.ROLE_FLAG_CAPTION != 0) "SDH" else null,
).joinToString(" · ").ifEmpty { "Subtitles" }

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** A video frame from the seek-bar preview sheets, cropped out of the sheet it sits in. */
@Composable
private fun Thumbnail(url: String, tileW: Int, tileH: Int, col: Int, row: Int, width: Dp, height: Dp) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111))) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed((width * tileW).roundToPx(), (height * tileH).roundToPx()))
                layout(width.roundToPx(), height.roundToPx()) {
                    placeable.place(-(width * col).roundToPx(), -(height * row).roundToPx())
                }
            },
        )
    }
}

/**
 * Audio and subtitle choices from whatever the video actually contains. Pick a row with OK; Back closes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TrackMenu(player: ExoPlayer, tick: Int, onClose: () -> Unit) {
    BackHandler { onClose() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }

    // Re-read the tracks whenever they change.
    val groups = remember(tick) { player.currentTracks.groups }
    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    class Entry(val label: String, val selected: Boolean, val pick: () -> Unit)

    fun pick(group: Tracks.Group, index: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
    }

    val audio = ArrayList<Entry>()
    val text = ArrayList<Entry>()
    text.add(
        Entry("Off", textOff || groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected }) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        },
    )
    for (g in groups) {
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            when (g.type) {
                C.TRACK_TYPE_AUDIO -> audio.add(Entry(audioLabel(f), g.isTrackSelected(i)) { pick(g, i) })
                C.TRACK_TYPE_TEXT -> text.add(Entry(subtitleLabel(f), g.isTrackSelected(i) && !textOff) { pick(g, i) })
            }
        }
    }

    val panel = RoundedCornerShape(UiSettings.corner.tile)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.CenterEnd) {
        Row(
            Modifier
                .padding(end = 40.dp)
                .background(Color(0xF2121520), panel)
                .border(1.dp, Palette.GlassEdge, panel)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            @Composable
            fun column(title: String, entries: List<Entry>, lead: Boolean) {
                Column(Modifier.width(260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = Palette.TextMuted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                    if (entries.isEmpty()) Text("None in this video", color = Palette.TextMuted, fontSize = 12.sp)
                    val leadIndex = entries.indexOfFirst { it.selected }.coerceAtLeast(0)
                    entries.forEachIndexed { i, e ->
                        FocusBox(
                            onClick = { e.pick(); onClose() },
                            modifier = (if (lead && i == leadIndex) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                            shape = RoundedCornerShape(UiSettings.corner.button.coerceAtLeast(6.dp)),
                            focusedScale = 1.03f,
                            edge = false,
                        ) { focused ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (focused) Palette.GlassFillStrong else Palette.GlassFill)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(if (e.selected) "✓" else " ", color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(e.label, color = Palette.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            column("AUDIO", audio, lead = true)
            column("SUBTITLES", text, lead = audio.isEmpty())
        }
    }
}

/**
 * Plays the original file untouched (direct play) so HDR, Dolby Vision and Atmos reach the TV as they
 * are. If the TV can't play the file, it falls back to a transcoded stream. Progress is reported to
 * Jellyfin so "Continue watching" stays right on every device.
 */
@Composable
fun PlayerScreen(item: Item, client: JellyfinClient, sourceId: String?, onDone: () -> Unit, onNext: (Item) -> Unit) {
    var media by remember(item.id) { mutableStateOf<MediaInfo?>(null) }
    var nextEp by remember(item.id) { mutableStateOf<Item?>(null) }
    LaunchedEffect(item.id) {
        media = try { client.mediaInfo(item.id, sourceId) } catch (e: Exception) { MediaInfo() }
    }
    LaunchedEffect(item.id) {
        if (item.isEpisode) nextEp = try { client.nextEpisode(item) } catch (e: Exception) { null }
    }
    val loaded = media
    if (loaded == null) {
        BackHandler { onDone() }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp)
        }
    } else {
        PlayerBody(item, client, sourceId, loaded, nextEp, onDone, onNext)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlayerBody(
    item: Item,
    client: JellyfinClient,
    sourceId: String?,
    media: MediaInfo,
    nextEp: Item?,
    onDone: () -> Unit,
    onNext: (Item) -> Unit,
) {
    val context = LocalContext.current
    val startMs = if (item.played) 0L else item.positionTicks / TICKS_PER_MS
    var info by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(startMs) }
    var duration by remember { mutableStateOf(item.runtimeTicks / TICKS_PER_MS) }
    var buffered by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var scrub by remember { mutableStateOf<Long?>(null) }
    var tracksTick by remember { mutableStateOf(0) }
    var nextDismissed by remember { mutableStateOf(false) }
    val lastMs = remember(item.id) { longArrayOf(startMs) }
    val root = remember { FocusRequester() }
    val nextRef = rememberUpdatedState(nextEp)
    val dismissedRef = rememberUpdatedState(nextDismissed)

    val player = remember(item.id) {
        // Audio passthrough to the TV or receiver is automatic; the decoder fallback lets a second-choice
        // decoder take over when the first one refuses a stream.
        val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, factory).build().apply {
            val builder = MediaItem.Builder().setUri(client.directStreamUrl(item.id, sourceId))
            builder.setSubtitleConfigurations(
                media.subtitles.map { s ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(s.url))
                        .setMimeType(s.mime)
                        .setLanguage(s.language)
                        .setLabel(s.title)
                        .build()
                },
            )
            setMediaItem(builder.build(), startMs)
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
                if (state == Player.STATE_ENDED) {
                    val next = nextRef.value
                    if (next != null && !dismissedRef.value) onNext(next) else onDone()
                }
                if (state == Player.STATE_READY) refreshInfo()
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksTick++
                refreshInfo()
            }

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
                player.setMediaItem(MediaItem.fromUri(client.transcodeUrl(item.id, sourceId)), at)
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

    // What the seek bar and buttons read from the player, a few times a second.
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            val d = player.duration
            if (d != C.TIME_UNSET && d > 0) duration = d
            buffered = player.bufferedPosition
            playing = player.isPlaying
            delay(250)
        }
    }

    // Controls fade after a few seconds of nothing, while playing.
    LaunchedEffect(controls, interaction, playing, menuOpen, scrub) {
        if (controls && playing && !menuOpen && scrub == null) {
            delay(5_000)
            controls = false
        }
    }
    // Left and Right move a preview position; it jumps there once you stop pressing.
    LaunchedEffect(scrub) {
        val target = scrub ?: return@LaunchedEffect
        delay(800)
        player.seekTo(target)
        scrub = null
    }
    // Show the stream info for a few seconds whenever the controls appear (or the stream changes), then fade it.
    LaunchedEffect(info, controls) {
        if (controls && info.isNotEmpty()) {
            showInfo = true
            delay(5_000)
        }
        showInfo = false
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(8_000)
            note = null
        }
    }
    LaunchedEffect(menuOpen) {
        if (!menuOpen) {
            delay(60)
            try { root.requestFocus() } catch (e: Exception) { /* not ready */ }
        }
    }

    val active: Segment? = media.segments.firstOrNull {
        (it.type == "Intro" || it.type == "Recap" || it.type == "Outro" || it.type == "Preview") &&
            position >= it.startMs && position < it.endMs - 1_500
    }
    val remaining = duration - position
    val nextCard = nextEp != null && !nextDismissed && duration > 0 && position > 0 && remaining <= 12_000

    fun skip(segment: Segment) {
        val next = nextEp
        if (segment.type == "Outro" && next != null && !nextDismissed) {
            onNext(next)
        } else {
            player.seekTo(if (segment.endMs >= duration) (duration - 500).coerceAtLeast(0) else segment.endMs)
        }
        controls = false
    }

    fun chapterJump(forward: Boolean) {
        val chapters = media.chapters
        if (chapters.isEmpty()) return
        val target = if (forward) {
            chapters.firstOrNull { it.startMs > position + 500 }?.startMs
        } else {
            chapters.lastOrNull { it.startMs < position - 3_000 }?.startMs ?: 0L
        }
        if (target != null) player.seekTo(target)
        controls = true
    }

    BackHandler {
        when {
            menuOpen -> menuOpen = false
            nextCard -> nextDismissed = true
            controls -> controls = false
            else -> onDone()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (menuOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interaction++
                val repeats = event.nativeKeyEvent.repeatCount
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                        nextCard -> nextEp?.let { onNext(it) }
                        active != null && !controls -> skip(active)
                        scrub != null -> { player.seekTo(scrub!!); scrub = null }
                        else -> {
                            if (player.isPlaying) player.pause() else player.play()
                            controls = true
                        }
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controls = true
                    }
                    Key.DirectionRight, Key.MediaFastForward, Key.DirectionLeft, Key.MediaRewind -> {
                        val step = when {
                            repeats > 10 -> 60_000L
                            repeats > 3 -> 30_000L
                            else -> 10_000L
                        }
                        val forward = event.key == Key.DirectionRight || event.key == Key.MediaFastForward
                        scrub = ((scrub ?: position) + if (forward) step else -step).coerceIn(0L, maxOf(duration, 0L))
                        controls = true
                    }
                    Key.DirectionUp -> if (!controls) controls = true else menuOpen = true
                    Key.DirectionDown -> controls = false
                    Key.MediaNext -> if (media.chapters.isNotEmpty()) chapterJump(true) else nextEp?.let { onNext(it) }
                    Key.MediaPrevious -> chapterJump(false)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    keepScreenOn = true
                    systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    isFocusable = false
                }
            },
        )

        // What the TV is actually being sent, shown with the controls so you can check HDR and Atmos.
        AnimatedVisibility(visible = showInfo, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
            Column(
                Modifier.padding(start = 40.dp, top = 28.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                info.split("\n").forEach { Text(it, color = Palette.Text, fontSize = 12.sp) }
            }
        }
        note?.let {
            Text(
                it, color = Palette.Accent, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 90.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // The control bar: title, times, seek bar with chapter ticks, and a preview frame while scrubbing.
        AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val shownPos = scrub ?: position
            val fraction = if (duration > 0) (shownPos.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val chapter = media.chapters.lastOrNull { it.startMs <= shownPos }
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0xCC000000), 1f to Color(0xF2000000)))
                    .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text(item.seriesName ?: item.name, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.isEpisode) {
                            Text(listOfNotNull(item.episodeLabel, item.name).joinToString(" · "), color = Palette.TextMuted, fontSize = 12.sp)
                        }
                    }
                    Text(
                        clock(shownPos) + "  /  " + clock(duration),
                        color = if (scrub != null) Palette.Accent else Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(if (scrub != null) 120.dp else 14.dp)) {
                    val barWidth = maxWidth
                    val tp = media.trickplay
                    if (scrub != null && tp != null && tp.intervalMs > 0) {
                        val thumbW = 200.dp
                        val thumbH = thumbW * tp.height / tp.width.coerceAtLeast(1)
                        val index = (shownPos / tp.intervalMs).toInt().coerceIn(0, (tp.count - 1).coerceAtLeast(0))
                        val perSheet = tp.tileW * tp.tileH
                        val x = (barWidth * fraction - thumbW / 2).coerceIn(0.dp, (barWidth - thumbW).coerceAtLeast(0.dp))
                        Column(Modifier.offset(x = x).align(Alignment.TopStart)) {
                            Thumbnail(
                                url = client.trickplayUrl(item.id, sourceId, tp.width, index / perSheet),
                                tileW = tp.tileW, tileH = tp.tileH, col = index % tp.tileW, row = (index / tp.tileW) % tp.tileH,
                                width = thumbW, height = thumbH,
                            )
                        }
                    }
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (scrub != null) 10.dp else 6.dp).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF))) {
                        if (duration > 0) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((buffered.toFloat() / duration).coerceIn(0f, 1f)).background(Color(0x40FFFFFF)))
                            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Palette.Accent))
                            media.chapters.drop(1).forEach { c ->
                                Box(Modifier.offset(x = barWidth * (c.startMs.toFloat() / duration).coerceIn(0f, 1f)).width(2.dp).fillMaxHeight().background(Color(0xCC000000)))
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .offset(x = (barWidth * fraction - 7.dp).coerceAtLeast(0.dp), y = if (scrub != null) 2.dp else 4.dp)
                            .size(14.dp).clip(CircleShape).background(Palette.Text),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(chapter?.name ?: "", color = Palette.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(300.dp))
                    Text(
                        "◀ ▶ Seek      ▲ Audio and subtitles      OK Pause" + if (media.chapters.isNotEmpty()) "      ⏮ ⏭ Chapters" else "",
                        color = Palette.TextMuted, fontSize = 11.sp,
                    )
                }
            }
        }

        // Skip intro / recap / credits, or the next episode coming up.
        if (nextCard && nextEp != null) {
            val seconds = ((remaining + 999) / 1000).toInt().coerceAtLeast(0)
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = if (controls) 150.dp else 40.dp)
                    .width(300.dp)
                    .background(Color(0xE6121520), RoundedCornerShape(UiSettings.corner.card))
                    .border(1.dp, Palette.GlassEdge, RoundedCornerShape(UiSettings.corner.card))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("NEXT UP  ·  in ${seconds}s", color = Palette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(listOfNotNull(nextEp.episodeLabel, nextEp.name).joinToString(" · "), color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("OK  play now        Back  stay here", color = Palette.TextMuted, fontSize = 11.sp)
            }
        } else if (active != null) {
            val label = when (active.type) {
                "Intro" -> "Skip intro"
                "Recap" -> "Skip recap"
                "Preview" -> "Skip preview"
                else -> if (nextEp != null) "Next episode" else "Skip credits"
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = if (controls) 150.dp else 40.dp)
                    .background(Palette.Accent, RoundedCornerShape(UiSettings.corner.button.coerceAtLeast(8.dp)))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text("$label  ▶" + if (!controls) "   (OK)" else "", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (menuOpen) TrackMenu(player, tracksTick) { menuOpen = false }
    }
}
