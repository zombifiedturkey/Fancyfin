package com.fancyfin.tv.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import com.fancyfin.tv.data.airDateText
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import com.fancyfin.tv.data.MediaVersion
import com.fancyfin.tv.data.Person
import androidx.compose.ui.text.style.TextAlign
import com.fancyfin.tv.data.TmdbTitle
import com.fancyfin.tv.data.trailerKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Remembers where you were on a title page while another page (an actor, say) is on top of it. */
class TitleMem {
    var seasonId: String? = null
    var episodeId: String? = null
    /** Set when the page was opened from an episode: that is the one Play starts. */
    var openEpisodeId: String? = null
    var rowKey: String? = null
}

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
        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
    )
}

/** A poster you can focus; accent border for the one you're looking at, a dot when it's on your server. */
@Composable
internal fun PosterTile(
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
        FocusBox(onClick = onClick, modifier = modifier.width(width).height(height), edge = UiSettings.posterBorder, focusedScale = 1.06f) { _ ->
            Box(Modifier.fillMaxSize().background(Palette.GlassFill)) {
                if (url != null) AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (current) Box(Modifier.fillMaxSize().border(2.dp, Palette.Accent, RoundedCornerShape(UiSettings.corner.card)))
                if (!onServer) NotOnServerPill(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
            }
        }
        // Room for the focused poster to grow into without its border crossing the title.
        Spacer(Modifier.height(5.dp))
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
    onEdit: (Item) -> Unit = {},
    mem: TitleMem = remember { TitleMem() },
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
    // Radarr / Sonarr: null = not added, "adding", "added", or "in" (it was already there).
    var arrState by remember { mutableStateOf<String?>(null) }
    var arrNote by remember { mutableStateOf<String?>(null) }
    var arrDialog by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    // The episode you're on in the episodes row; its description replaces the show's while you're there.
    var focusedEp by remember { mutableStateOf<Item?>(null) }
    var tmdbId by remember { mutableStateOf(ref.tmdbId) }
    val kind = ref.kind

    fun startAdd(id: Int, profile: Int?, folder: String?, season: Int?) {
        arrState = "adding"
        arrNote = null
        scope.launch {
            try {
                val message = client.addToArr(kind, id, profile, folder, season)
                arrNote = message
                arrState = if (message.startsWith("Already")) "in" else "added"
            } catch (e: Exception) {
                arrNote = e.message ?: "Couldn't add it."
                arrState = null
            }
        }
    }

    LaunchedEffect(ref) {
        val start = ref.server
        if (start != null) {
            try {
                var full = client.item(start.id, start.duplicateIds)
                // Landing on an episode (from Continue watching) shows its show.
                val seriesId = full.seriesId
                if (full.isEpisode && seriesId != null) {
                    if (mem.episodeId == null) {
                        mem.episodeId = full.id
                        mem.seasonId = full.seasonId
                        mem.openEpisodeId = full.id
                    }
                    full = client.item(seriesId)
                }
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
            if (start == null) launch { if (client.inArr(kind, id) == true) arrState = "in" }
            if (start == null && kind == "movie") launch { status = try { client.releaseStatus(id) } catch (e: Exception) { null } }
        }
    }

    LaunchedEffect(item?.id) {
        val current = item ?: return@LaunchedEffect
        if (current.isSeries) {
            try {
                seasons = client.seasons(current.id)
                season = seasons.firstOrNull { it.id == mem.seasonId }
                    ?: seasons.firstOrNull { (it.unplayedCount ?: 1) > 0 } ?: seasons.firstOrNull()
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

    // Land on the remembered episode: scroll the episodes row so it is the first one in view.
    val episodesState = rememberLazyListState()
    LaunchedEffect(episodes) {
        val at = episodes.indexOfFirst { it.id == mem.episodeId }
        if (episodes.isNotEmpty()) try { episodesState.scrollToItem(at.coerceAtLeast(0)) } catch (e: Exception) { /* not laid out yet */ }
    }

    // What Play starts: the movie, or for a show the episode you're up to.
    val shown = item
    val isShow = shown != null && shown.isSeries
    val target: Item? = when {
        shown == null -> null
        shown.isSeries -> episodes.firstOrNull { it.id == mem.openEpisodeId }
            ?: episodes.firstOrNull { it.positionTicks > 0 && !it.played }
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
    val ep = focusedEp
    val overview = ep?.overview?.takeIf { it.isNotBlank() } ?: shown?.overview ?: t?.overview
    val tagline = if (ep != null) listOfNotNull(ep.episodeLabel, ep.name).joinToString(" · ") else shown?.tagline ?: t?.tagline
    val genres = shown?.genres?.takeIf { it.isNotEmpty() } ?: t?.genres ?: emptyList()
    val year = shown?.year ?: t?.year
    val rating = shown?.rating ?: t?.rating
    val minutes = ep?.runtimeMinutes?.takeIf { it > 0 } ?: shown?.runtimeMinutes?.takeIf { it > 0 } ?: t?.runtimeMinutes ?: 0
    val hero = shown?.let { client.backdrop(it) } ?: (t?.backdrop ?: ref.backdrop)?.replace("/w1280/", "/original/")
    val posterUrl = shown?.let { client.poster(it, 342) } ?: t?.poster ?: ref.poster
    val trailers = t?.let { trailerKeys(it.videos) } ?: emptyList()

    // One screen, laid out like the browse pages: the title, facts and description sit exactly where they do
    // there, the buttons slot in under them, and the cast, episodes and "more like this" rows take the place
    // of the poster row. Only one of those rows is on show at a time; moving between them fades in place.
    val defaultSpec = LocalBringIntoViewSpec.current
    val hasSeasons = shown != null && shown.isSeries && seasons.isNotEmpty()
    val hasCast = t != null && t.cast.isNotEmpty()
    val moreTmdb = t?.more ?: emptyList()
    val extras = t?.videos ?: emptyList()
    val keys = buildList {
        if (hasSeasons) add("eps")
        if (hasCast) add("cast")
        if (franchise.size > 1) add("franchise")
        if (moreTmdb.isNotEmpty() || serverSimilar.isNotEmpty()) add("more")
        if (extras.isNotEmpty()) add("extras")
    }
    // -1 is the buttons; 0.. is a row in keys.
    var row by remember { mutableStateOf(-1) }
    var rowRestored by remember { mutableStateOf(mem.rowKey == null) }
    var pillFocused by remember { mutableStateOf(false) }
    val sectionReq = remember { List(6) { FocusRequester() } }
    val episodesReq = remember { FocusRequester() }
    val rowShown = row.coerceIn(0, (keys.size - 1).coerceAtLeast(0))
    LaunchedEffect(row, keys) { if (keys.getOrNull(row) != "eps") focusedEp = null }
    fun goSection(n: Int) {
        row = n
        scope.launch {
            delay(40)
            try { (if (keys.getOrNull(n) == "eps") episodesReq else sectionReq[n]).requestFocus() } catch (e: Exception) { /* not laid out yet */ }
        }
    }
    // Coming back to this page (from an actor, say): go back to the row you were on.
    LaunchedEffect(keys) {
        val want = mem.rowKey
        if (!rowRestored && want != null && keys.contains(want)) {
            rowRestored = true
            delay(350)
            goSection(keys.indexOf(want))
        }
    }
    LaunchedEffect(row) { if (rowRestored) mem.rowKey = keys.getOrNull(row) }
    fun goButtons() {
        scope.launch { try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ } }
    }
    val best = versions.maxWithOrNull(compareBy<MediaVersion>({ resolutionHeight(it) }, { rangeRank(it) }, { it.sizeBytes }))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The movie's picture fills the screen, washed with the background colour.
        HeroBackdropUrl(hero)
        ClockPill(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 20.dp))

        CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
            // Same spot and sizes as the browse pages' info block.
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().height((maxHeight - ROW_VIEW_H).coerceAtLeast(120.dp)).padding(start = 20.dp, end = 60.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val logo = shown?.let { client.logo(it) }
                if (logo != null) {
                    AsyncImage(
                        model = logo, contentDescription = title, contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart, modifier = Modifier.height(64.dp).width(280.dp),
                    )
                } else {
                    Text(title, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(520.dp))
                }
                tagline?.let { Text(it, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val facts = listOfNotNull(ep?.let { airDateText(it.premiere) } ?: year?.toString(), runtimeText(minutes), genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(", "))
                    if (facts.isNotEmpty()) Text(facts.joinToString("   "), color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    shown?.officialRating?.let {
                        Text(
                            it, color = Palette.Text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    rating?.let { Text("★ " + "%.1f".format(it), color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                }
                overview?.let { Text(it, color = Palette.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).width(520.dp)) }
            }

            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(ROW_VIEW_H)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val last = keys.size - 1
                        when (ev.key) {
                            Key.DirectionDown -> when {
                                row < 0 -> { if (last >= 0) goSection(0); true }
                                keys.getOrNull(row) == "eps" && pillFocused -> false
                                row < last -> { goSection(row + 1); true }
                                else -> true
                            }
                            Key.DirectionUp -> when {
                                row < 0 -> true
                                keys.getOrNull(row) == "eps" && !pillFocused -> false
                                row == 0 -> { row = -1; goButtons(); true }
                                else -> { goSection(row - 1); true }
                            }
                            else -> false
                        }
                    },
            ) {
                // Buttons on the left; on the right, how it will look and sound (or when it's out).
                Box(Modifier.padding(top = 12.dp, bottom = 8.dp).onFocusChanged { if (it.hasFocus) row = -1 }) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            var firstAssigned = false
                            fun lead(): Modifier = if (!firstAssigned) { firstAssigned = true; Modifier.focusRequester(first) } else Modifier
                            if (target != null) {
                                PillButton(
                                    label = (if (resuming) "Resume" else "Play") + (target.episodeLabel?.let { " $it" } ?: ""),
                                    icon = Icons.Filled.PlayArrow,
                                    solid = true,
                                    small = true,
                                    modifier = lead(),
                                    onClick = { onPlay(target) },
                                )
                            }
                            val addId = tmdbId
                            if (ref.server == null && addId != null) {
                                val app = if (kind == "movie") "Radarr" else "Sonarr"
                                PillButton(
                                    label = when (arrState) {
                                        "in" -> "In $app ✓"
                                        "adding" -> "Adding…"
                                        "added" -> "Added to $app ✓"
                                        else -> "Add to $app"
                                    },
                                    solid = arrState == null,
                                    small = true,
                                    modifier = lead(),
                                    onClick = {
                                        if (arrState == null) {
                                            val saved = UiSettings.savedArr(kind)
                                            if (UiSettings.askArr || saved == null) arrDialog = true
                                            else startAdd(addId, saved.first, saved.second, null)
                                        }
                                    },
                                )
                            }
                            if (shown != null) {
                                IconCircleButton(
                                    small = true,
                                    icon = Icons.Filled.Star,
                                    on = shown.favorite,
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
                                IconCircleButton(
                                    small = true,
                                    icon = Icons.Filled.Check,
                                    on = shown.played,
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
                                if (UiSettings.advanced) {
                                    IconCircleButton(small = true, icon = Icons.Filled.Edit, on = false, onClick = { onEdit(shown) })
                                }
                            }
                        }
                        Box(Modifier.weight(1f))
                        // On its own dark glass so it stays readable over a bright picture.
                        val info = ref.server == null || best != null
                        if (info) {
                            Column(
                                Modifier.padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (ref.server != null && best != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        InfoPill(best.resolution + if (best.codec.isNotEmpty()) " (${best.codec})" else "")
                                        if (best.range != "SDR") InfoPill(best.range, accent = true)
                                        if (best.audio.isNotEmpty()) InfoPill(best.audio)
                                    }
                                    if (versions.size > 1) {
                                        Text("⧉ ${versions.size} copies · you choose when you play", color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else if (ref.server == null) {
                                    Text("NOT ON YOUR SERVER", color = Palette.TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                                    status?.let { Text(it, color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                    arrNote?.let { Text(it, color = Palette.Text, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(260.dp)) }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    keys.forEachIndexed { si, key ->
                        val current = si == rowShown
                        val fade by animateFloatAsState(if (current) 1f else 0f, tween(260), label = "titleRowFade")
                        Box(
                            Modifier.fillMaxSize()
                                .offset(y = if (current || fade > 0.01f) 0.dp else (-4000).dp)
                                .graphicsLayer { alpha = fade }
                                .onFocusChanged { if (it.hasFocus) row = si },
                        ) {
                            when (key) {
                                "eps" -> Column {
                                    HRow(defaultSpec, spacing = 8.dp, vertical = 0.dp, modifier = Modifier.onFocusChanged { pillFocused = it.hasFocus }) {
                                        items(seasons) { s -> GlassButton(s.name, onClick = { season = s; mem.seasonId = s.id; mem.episodeId = null; mem.openEpisodeId = null }, primary = s.id == season?.id, small = true) }
                                    }
                                    HRow(defaultSpec, vertical = 8.dp, modifier = Modifier.focusRequester(episodesReq), state = episodesState) {
                                        items(episodes) { ep ->
                                            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                ItemCard(ep, client, 200.dp, 112.dp, true, onClick = { onPlay(ep) }, onFocused = { focusedEp = ep; mem.episodeId = ep.id })
                                                Text(
                                                    (ep.indexNumber?.let { "$it. " } ?: "") + ep.name,
                                                    color = Palette.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                                "cast" -> Column(Modifier.focusRequester(sectionReq[si])) {
                                    SectionTitle("Cast & Crew")
                            HRow(defaultSpec, spacing = 14.dp) {
                                items(t?.cast ?: emptyList()) { p ->
                                    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        FocusBox(
                                            onClick = { onPerson(p) },
                                            modifier = Modifier.size(84.dp),
                                            shape = RoundedCornerShape(50),
                                            edge = false,
                                        ) { _ ->
                                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f))) {
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
                                        Spacer(Modifier.height(3.dp))
                                        Text(p.name, color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                        p.role?.let { Text(it, color = Palette.TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                                    }
                                }
                            }
                                }
                                "franchise" -> {
                                    val position = franchise.indexOfFirst { it.tmdbId == tmdbId }
                                    Column(Modifier.focusRequester(sectionReq[si])) {
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
                                "more" -> Column(Modifier.focusRequester(sectionReq[si])) {
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
                                "extras" -> Column(Modifier.focusRequester(sectionReq[si])) {
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
        val dialogId = tmdbId
        if (arrDialog && dialogId != null) {
            ArrDialog(
                app = if (kind == "movie") "Radarr" else "Sonarr",
                kind = kind,
                tmdbId = dialogId,
                client = client,
                onAdd = { profile, folder, season, remember ->
                    if (remember) UiSettings.rememberArr(kind, profile, folder)
                    arrDialog = false
                    startAdd(dialogId, profile, folder, season)
                },
                onClose = { arrDialog = false },
            )
        }
    }
}

@Composable
private fun InfoPill(text: String, accent: Boolean = false) {
    Text(
        text, color = Palette.Text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier
            .background(Color(0xB3000000), RoundedCornerShape(6.dp))
            .border(1.dp, if (accent) Palette.Accent else Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun Chip(text: String) {
    val pill = RoundedCornerShape(50)
    Text(
        text, color = Palette.Text, fontSize = 10.sp, maxLines = 1,
        modifier = Modifier.background(Palette.GlassFill, pill).border(1.dp, Palette.GlassEdge, pill).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

internal fun resolutionHeight(v: MediaVersion): Int = Regex("\\d+").find(v.resolution)?.value?.toIntOrNull() ?: 0

/** The copy that looks best: highest resolution, then HDR type, then size. */
internal fun bestVersion(versions: List<MediaVersion>): MediaVersion? =
    versions.maxWithOrNull(compareBy<MediaVersion>({ resolutionHeight(it) }, { rangeRank(it) }, { it.sizeBytes }))

internal fun rangeRank(v: MediaVersion): Int = when {
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
    state: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyRow(
            state = state,
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = vertical),
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
    // Everything fits on the one screen: no scrolling, posters kept at 2:3.
    Box(Modifier.fillMaxSize()) {
        Backdrop(photo)
        Column(Modifier.fillMaxSize().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(
                    Modifier.width(150.dp).height(225.dp)
                        .clip(RoundedCornerShape(UiSettings.corner.tile))
                        .background(Palette.GlassFill),
                ) {
                    if (photo != null) AsyncImage(model = photo, contentDescription = ref.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(info?.name ?: ref.name, color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    info?.born?.let { Text(it, color = Palette.Accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    val bio = info?.biography
                    when {
                        bio != null -> Text(bio, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 11, overflow = TextOverflow.Ellipsis)
                        error != null -> Text(error!!, color = Palette.Accent, fontSize = 13.sp)
                        loaded -> Text("No biography available.", color = Palette.TextMuted, fontSize = 13.sp)
                        else -> Text("Loading…", color = Palette.TextMuted, fontSize = 13.sp)
                    }
                }
            }
            val credits = info?.credits ?: emptyList()
            if (credits.isNotEmpty()) {
                Column {
                    SectionTitle("Known for")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                        items(credits.size) { i ->
                            val d = credits[i]
                            val here = index["${d.kind}:${d.tmdbId}"]
                            PosterTile(
                                url = d.poster, title = d.title, onServer = here != null, current = false,
                                width = 90.dp, height = 135.dp,
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
