package com.fancyfin.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.fancyfin.tv.data.ApiException
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Discover
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import com.fancyfin.tv.data.MediaVersion
import com.fancyfin.tv.data.Person
import androidx.compose.ui.text.style.TextAlign
import com.fancyfin.tv.data.TmdbTitle
import com.fancyfin.tv.data.trailerKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which title to show: a movie or show from TMDB, the copy on your server if there is one, or both. */
data class TitleRef(
    val kind: String,
    val tmdbId: Int?,
    val server: Item?,
    val title: String,
    val backdrop: String? = null,
    val poster: String? = null,
) {
    companion object {
        fun of(i: Item) = TitleRef(if (i.isMovie) "movie" else "tv", i.tmdbId?.toIntOrNull(), i, i.seriesName ?: i.name)
    }
}

private fun sizeText(bytes: Long): String = if (bytes <= 0) "" else "%.1f GB".format(bytes / 1_073_741_824.0)

private fun runtimeText(minutes: Int): String? =
    if (minutes <= 0) null else if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
    )
}

/** A poster you can focus; accent border for the one you're looking at, a dot when it's on your server. */
@Composable
private fun PosterTile(
    url: String?,
    title: String,
    onServer: Boolean,
    current: Boolean,
    onClick: () -> Unit,
    width: Dp = 100.dp,
    height: Dp = 150.dp,
    modifier: Modifier = Modifier,
) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FocusBox(onClick = onClick, modifier = modifier.width(width).height(height), edge = UiSettings.posterBorder) { _ ->
            Box(Modifier.fillMaxSize().background(Palette.GlassFill)) {
                if (url != null) AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (current) Box(Modifier.fillMaxSize().border(2.dp, Palette.Accent, RoundedCornerShape(UiSettings.corner.card)))
                if (onServer) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(5.dp).size(9.dp)
                            .background(Palette.Accent, CircleShape).border(1.dp, Color.Black, CircleShape),
                    )
                }
            }
        }
        Text(title, color = if (current) Palette.Accent else Palette.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The full page for one movie or show: what it is, how it will look and sound on your server, the cast,
 * the rest of its franchise, more like it, and trailers and extras. Works for titles that aren't on your
 * server too, using TMDB.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleScreen(
    ref: TitleRef,
    client: JellyfinClient,
    onPlay: (Item) -> Unit,
    onOpen: (TitleRef) -> Unit,
    onTrailer: (List<String>) -> Unit,
    onPerson: (Person) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf(ref.server) }
    var versions by remember { mutableStateOf<List<MediaVersion>>(emptyList()) }
    var seasons by remember { mutableStateOf<List<Item>>(emptyList()) }
    var season by remember { mutableStateOf<Item?>(null) }
    var episodes by remember { mutableStateOf<List<Item>>(emptyList()) }
    var serverSimilar by remember { mutableStateOf<List<Item>>(emptyList()) }
    var tmdb by remember { mutableStateOf<TmdbTitle?>(null) }
    var franchise by remember { mutableStateOf<List<Discover>>(emptyList()) }
    var index by remember { mutableStateOf<Map<String, Item>>(emptyMap()) }
    var status by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var tmdbId by remember { mutableStateOf(ref.tmdbId) }
    val kind = ref.kind

    LaunchedEffect(ref) {
        val start = ref.server
        if (start != null) {
            try {
                var full = client.item(start.id, start.duplicateIds)
                // Landing on an episode (from Continue watching) shows its show.
                val seriesId = full.seriesId
                if (full.isEpisode && seriesId != null) full = client.item(seriesId)
                item = full
                if (tmdbId == null) tmdbId = full.tmdbId?.toIntOrNull()
            } catch (e: Exception) {
                // Keep what we were given.
            }
            launch {
                serverSimilar = try { client.similar(item?.id ?: start.id) } catch (e: Exception) { emptyList() }
            }
        }
        launch { index = try { client.serverIndex() } catch (e: Exception) { emptyMap() } }
        val id = tmdbId
        if (id != null) {
            launch {
                val t = try { client.tmdbTitle(kind, id) } catch (e: Exception) { null }
                tmdb = t
                val cid = t?.collectionId
                if (cid != null) franchise = try { client.franchise(cid) } catch (e: Exception) { emptyList() }
            }
            if (start == null && kind == "movie") launch { status = try { client.releaseStatus(id) } catch (e: Exception) { null } }
        }
    }

    LaunchedEffect(item?.id) {
        val current = item ?: return@LaunchedEffect
        if (current.isSeries) {
            try {
                seasons = client.seasons(current.id)
                season = seasons.firstOrNull { (it.unplayedCount ?: 1) > 0 } ?: seasons.firstOrNull()
            } catch (e: Exception) {
                seasons = emptyList()
            }
        }
    }
    LaunchedEffect(season?.id) {
        val s = season ?: return@LaunchedEffect
        val current = item ?: return@LaunchedEffect
        episodes = try { client.episodes(current.id, s.id) } catch (e: Exception) { emptyList() }
    }

    // What Play starts: the movie, or for a show the episode you're up to.
    val shown = item
    val target: Item? = when {
        shown == null -> null
        shown.isSeries -> episodes.firstOrNull { it.positionTicks > 0 && !it.played }
            ?: episodes.firstOrNull { !it.played }
            ?: episodes.firstOrNull()
        else -> shown
    }
    val resuming = target != null && target.positionTicks > 0 && !target.played

    LaunchedEffect(target?.id) {
        val t = target ?: return@LaunchedEffect
        versions = try { client.versions(t) } catch (e: Exception) { emptyList() }
    }

    val first = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(target?.id, ref) {
        if (!focused && (target != null || ref.server == null)) {
            delay(200)
            try { first.requestFocus(); focused = true } catch (e: Exception) { /* not laid out yet */ }
        }
    }

    val t = tmdb
    val title = shown?.seriesName ?: shown?.name ?: t?.title ?: ref.title
    val overview = shown?.overview ?: t?.overview
    val tagline = shown?.tagline ?: t?.tagline
    val genres = shown?.genres?.takeIf { it.isNotEmpty() } ?: t?.genres ?: emptyList()
    val year = shown?.year ?: t?.year
    val rating = shown?.rating ?: t?.rating
    val minutes = shown?.runtimeMinutes?.takeIf { it > 0 } ?: t?.runtimeMinutes ?: 0
    val hero = shown?.let { client.backdrop(it) } ?: (t?.backdrop ?: ref.backdrop)?.replace("/w1280/", "/original/")
    val posterUrl = shown?.let { client.poster(it, 342) } ?: t?.poster ?: ref.poster
    val trailers = t?.let { trailerKeys(it.videos) } ?: emptyList()

    // Pages open at the top, with the cast row at the bottom of the screen. Going down from the buttons to
    // the cast doesn't scroll; going further down scrolls the next row to the top; coming back up to the
    // cast (or above) goes back to the very top.
    val defaultSpec = LocalBringIntoViewSpec.current
    val listState = rememberLazyListState()
    var focusItem by remember { mutableStateOf(0) }
    val hasSeasons = shown != null && shown.isSeries && seasons.isNotEmpty()
    val hasCast = t != null && t.cast.isNotEmpty()
    val topLimit = 1 + (if (hasSeasons) 2 else 0) + (if (hasCast) 1 else 0)
    LaunchedEffect(focusItem, topLimit) {
        listState.animateScrollToItem(if (focusItem <= topLimit) 0 else focusItem)
    }
    val track: (Int) -> Modifier = { i -> Modifier.onFocusChanged { if (it.hasFocus) focusItem = i } }
    val best = versions.maxWithOrNull(compareBy<MediaVersion>({ resolutionHeight(it) }, { rangeRank(it) }, { it.sizeBytes }))

    Box(Modifier.fillMaxSize()) {
        // The accent gradient, with the movie's big picture across the top fading into it. The picture
        // scrolls away with the page.
        Backdrop(null)
        if (hero != null) {
            val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 100000
            Box(
                Modifier.fillMaxWidth().height(340.dp)
                    .offset { IntOffset(0, -scrolled) }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Black, 0.45f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                AsyncImage(model = hero, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xB0000000), 0.55f to Color(0x30000000), 1f to Color.Transparent)))
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 20.dp, bottom = 300.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                var n = 0

                // Poster, then what it is, then how it will look and sound (or when it's out).
                val headerIndex = n++
                item {
                    Row(Modifier.padding(horizontal = 24.dp).then(track(headerIndex)), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(
                            Modifier.width(100.dp).height(150.dp)
                                .clip(RoundedCornerShape(UiSettings.corner.card))
                                .background(Palette.GlassFill),
                        ) {
                            if (posterUrl != null) AsyncImage(model = posterUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Column(Modifier.width(430.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val logo = shown?.let { client.logo(it) }
                            if (logo != null) {
                                AsyncImage(
                                    model = logo, contentDescription = title, contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart, modifier = Modifier.height(60.dp).width(300.dp),
                                )
                            } else {
                                Text(title, color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            tagline?.let { Text(it, color = Palette.TextMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOfNotNull(
                                    year?.toString(), shown?.officialRating, runtimeText(minutes), rating?.let { "★ " + "%.1f".format(it) },
                                ).plus(genres.take(3)).forEach { Chip(it) }
                            }
                            overview?.let { Text(it, color = Palette.Text, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                        Column(Modifier.width(250.dp).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (ref.server != null) {
                                if (best != null) {
                                    Text("QUALITY", color = Palette.TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(best.resolution, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            best.range, fontSize = 13.sp,
                                            color = if (best.range != "SDR") Palette.Accent else Palette.TextMuted,
                                            fontWeight = if (best.range != "SDR") FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                    val sound = listOf(best.audio, best.codec).filter { it.isNotEmpty() }.joinToString("   ·   ")
                                    if (sound.isNotEmpty()) Text(sound, color = Palette.Text, fontSize = 12.sp)
                                    val file = listOf(best.container, sizeText(best.sizeBytes)).filter { it.isNotEmpty() }.joinToString("   ·   ")
                                    if (file.isNotEmpty()) Text(file, color = Palette.TextMuted, fontSize = 12.sp)
                                    if (versions.size > 1) {
                                        Text(
                                            "⧉  ${versions.size} copies · best shown, you choose when you play",
                                            color = Palette.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            } else {
                                Text("NOT ON YOUR SERVER", color = Palette.TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                                status?.let { Text(it, color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }

                val buttonsIndex = n++
                item {
                    Row(Modifier.padding(start = 24.dp).then(track(buttonsIndex)), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        var firstAssigned = false
                        fun lead(): Modifier = if (!firstAssigned) { firstAssigned = true; Modifier.focusRequester(first) } else Modifier
                        if (target != null) {
                            GlassButton(
                                label = (if (resuming) "Resume" else "Play") + (target.episodeLabel?.let { " $it" } ?: ""),
                                primary = true,
                                modifier = lead(),
                                onClick = { onPlay(target) },
                            )
                        }
                        if (ref.server == null) {
                            // Becomes "Add to Radarr / Sonarr" once the arr flow is in.
                            GlassButton("Not on your server", {}, modifier = lead(), clear = true)
                        }
                        if (shown != null) {
                            GlassButton(
                                label = if (shown.played) "Watched ✓" else "Mark watched",
                                modifier = lead(),
                                onClick = {
                                    val next = !shown.played
                                    scope.launch {
                                        try {
                                            client.setPlayed(shown.id, next)
                                            item = client.item(shown.id, shown.duplicateIds)
                                        } catch (e: Exception) { /* leave the button as it was */ }
                                    }
                                },
                            )
                            GlassButton(
                                label = if (shown.favorite) "★ Favourite" else "☆ Favourite",
                                onClick = {
                                    val next = !shown.favorite
                                    scope.launch {
                                        try {
                                            client.setFavorite(shown.id, next)
                                            item = client.item(shown.id, shown.duplicateIds)
                                        } catch (e: Exception) { /* leave the button as it was */ }
                                    }
                                },
                            )
                        }
                    }
                }

                if (hasSeasons) {
                    val seasonsIndex = n++
                    item {
                        HRow(defaultSpec, spacing = 8.dp, vertical = 0.dp, modifier = track(seasonsIndex)) {
                            items(seasons) { s -> GlassButton(s.name, onClick = { season = s }, primary = s.id == season?.id) }
                        }
                    }
                    val episodesIndex = n++
                    item {
                        HRow(defaultSpec, vertical = 8.dp, modifier = track(episodesIndex)) {
                            items(episodes) { ep ->
                                Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ItemCard(ep, client, 160.dp, 90.dp, true, onClick = { onPlay(ep) })
                                    Text(
                                        (ep.indexNumber?.let { "$it. " } ?: "") + ep.name,
                                        color = Palette.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                if (t != null && hasCast) {
                    val castIndex = n++
                    item {
                        Column(track(castIndex)) {
                            SectionTitle("Cast")
                            HRow(defaultSpec, spacing = 14.dp) {
                                items(t.cast) { p ->
                                    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        FocusBox(
                                            onClick = { onPerson(p) },
                                            modifier = Modifier.width(96.dp).height(138.dp),
                                            edge = UiSettings.posterBorder,
                                        ) { _ ->
                                            Box(Modifier.fillMaxSize().background(Palette.Accent.copy(alpha = 0.25f))) {
                                                if (p.photo != null) {
                                                    AsyncImage(model = p.photo, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Text(
                                                        p.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() },
                                                        color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.align(Alignment.Center),
                                                    )
                                                }
                                            }
                                        }
                                        Text(p.name, color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        p.role?.let { Text(it, color = Palette.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            }
                        }
                    }
                }

                if (franchise.size > 1) {
                    val franchiseIndex = n++
                    item {
                        val position = franchise.indexOfFirst { it.tmdbId == tmdbId }
                        Column(track(franchiseIndex)) {
                            SectionTitle(
                                (t?.collectionName ?: "Franchise") + if (position >= 0) "   ·   This is part ${position + 1} of ${franchise.size}" else "",
                            )
                            HRow(defaultSpec) {
                                items(franchise) { part ->
                                    val here = index["movie:${part.tmdbId}"]
                                    PosterTile(
                                        url = part.poster, title = part.title + (part.year?.let { " ($it)" } ?: ""),
                                        onServer = here != null, current = part.tmdbId == tmdbId,
                                        onClick = {
                                            if (part.tmdbId != tmdbId) onOpen(TitleRef("movie", part.tmdbId, here, part.title, part.backdrop, part.poster))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                val moreTmdb = t?.more ?: emptyList()
                if (moreTmdb.isNotEmpty() || serverSimilar.isNotEmpty()) {
                    val moreIndex = n++
                    item {
                        Column(track(moreIndex)) {
                            SectionTitle("More like this")
                            HRow(defaultSpec) {
                                if (moreTmdb.isNotEmpty()) {
                                    items(moreTmdb) { d ->
                                        val here = index["${d.kind}:${d.tmdbId}"]
                                        PosterTile(
                                            url = d.poster, title = d.title, onServer = here != null, current = false,
                                            onClick = { onOpen(TitleRef(d.kind, d.tmdbId, here, d.title, d.backdrop, d.poster)) },
                                        )
                                    }
                                } else {
                                    items(serverSimilar) { sim -> ItemCard(sim, client, 100.dp, 150.dp, false, onClick = { onOpen(TitleRef.of(sim)) }) }
                                }
                            }
                        }
                    }
                }

                val extras = t?.videos ?: emptyList()
                if (extras.isNotEmpty()) {
                    val extrasIndex = n++
                    item {
                        Column(track(extrasIndex)) {
                            SectionTitle("Trailers and extras")
                            HRow(defaultSpec) {
                                items(extras) { v ->
                                    Column(Modifier.width(176.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FocusBox(onClick = { onTrailer(listOf(v.key)) }, modifier = Modifier.width(176.dp).height(99.dp), edge = UiSettings.posterBorder) { _ ->
                                            Box(Modifier.fillMaxSize().background(Palette.GlassFill)) {
                                                AsyncImage(
                                                    model = "https://i.ytimg.com/vi/${v.key}/mqdefault.jpg", contentDescription = v.name,
                                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                                )
                                                Text("▶", color = Palette.Text, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center))
                                            }
                                        }
                                        Text(v.type.uppercase(), color = Palette.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Text(v.name, color = Palette.Text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    val pill = RoundedCornerShape(50)
    Text(
        text, color = Palette.Text, fontSize = 10.sp, maxLines = 1,
        modifier = Modifier.background(Palette.GlassFill, pill).border(1.dp, Palette.GlassEdge, pill).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun resolutionHeight(v: MediaVersion): Int = Regex("\\d+").find(v.resolution)?.value?.toIntOrNull() ?: 0

private fun rangeRank(v: MediaVersion): Int = when {
    v.range.contains("Dolby Vision") -> 4
    v.range.contains("HDR10") -> 3
    v.range.contains("HDR") || v.range.contains("HLG") -> 2
    else -> 0
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HRow(
    spec: BringIntoViewSpec,
    spacing: Dp = 12.dp,
    vertical: Dp = 4.dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = vertical),
            content = content,
        )
    }
}

/** Who to show a page for. */
data class PersonRef(val id: Int, val name: String, val photo: String?)

/** An actor's page: big photo on the left, bio on the right, and their films and shows underneath. */
@Composable
fun PersonScreen(
    ref: PersonRef,
    client: JellyfinClient,
    onOpen: (TitleRef) -> Unit,
) {
    var info by remember { mutableStateOf<com.fancyfin.tv.data.PersonInfo?>(null) }
    var index by remember { mutableStateOf<Map<String, Item>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    LaunchedEffect(ref.id) {
        index = try { client.serverIndex() } catch (e: Exception) { emptyMap() }
        info = try {
            client.person(ref.id)
        } catch (e: Exception) {
            error = if (e is ApiException && e.code == 404) {
                "Fancyfin can't look up people yet. Update your Fancyfin container to the latest image."
            } else {
                "Couldn't load this bio: " + (e.message ?: "unknown error")
            }
            null
        }
        loaded = true
    }
    LaunchedEffect(loaded) {
        if (loaded) {
            delay(250)
            try { first.requestFocus() } catch (e: Exception) { /* nothing to focus */ }
        }
    }

    val photo = info?.photo ?: ref.photo
    Box(Modifier.fillMaxSize()) {
        Backdrop(photo)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(Modifier.padding(start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(
                        Modifier.width(190.dp).height(285.dp)
                            .clip(RoundedCornerShape(UiSettings.corner.tile))
                            .background(Palette.GlassFill),
                    ) {
                        if (photo != null) AsyncImage(model = photo, contentDescription = ref.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(info?.name ?: ref.name, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        info?.born?.let { Text(it, color = Palette.Accent, fontSize = 12.sp) }
                        val bio = info?.biography
                        when {
                            bio != null -> Text(bio, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 12, overflow = TextOverflow.Ellipsis)
                            error != null -> Text(error!!, color = Palette.Accent, fontSize = 13.sp)
                            loaded -> Text("No biography available.", color = Palette.TextMuted, fontSize = 13.sp)
                            else -> Text("Loading…", color = Palette.TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
            val credits = info?.credits ?: emptyList()
            if (credits.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Known for")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                            items(credits.size) { i ->
                                val d = credits[i]
                                val here = index["${d.kind}:${d.tmdbId}"]
                                PosterTile(
                                    url = d.poster, title = d.title, onServer = here != null, current = false,
                                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                                    onClick = { onOpen(TitleRef(d.kind, d.tmdbId, here, d.title, d.backdrop, d.poster)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
