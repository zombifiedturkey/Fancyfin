package com.fancyfin.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Discover
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One tile in a feature row: a title, on the server or not. The last tile of a genre row is "See all". */
class HeroEntry(
    val key: String,
    val title: String,
    val overview: String?,
    val year: Int?,
    val rating: Double?,
    val genres: List<String>,
    val backdrop: String?,
    val poster: String?,
    val kind: String,
    val tmdbId: Int?,
    val onServer: Item?,
    val subtitle: String? = null,
    val progress: Float? = null,
    val action: String = "Play",
    val isMore: Boolean = false,
) {
    /** Release status for movies that aren't on the server, filled in when you reach the tile. */
    var status by mutableStateOf<String?>(null)
}

/**
 * Everything a browse page needs to come back exactly as you left it: its rows, which tile you were on
 * and how far it was scrolled. Kept by the app, so Back from a title lands on the same tile.
 */
class FeatureState {
    var sections by mutableStateOf<List<HeroSection>>(emptyList())
    var allItems by mutableStateOf<List<Item>>(emptyList())
    var sortIndex by mutableStateOf(1)
    var wallpaper by mutableStateOf<String?>(null)
    /** The title you are on, which the backdrop and the details at the top follow. */
    var focused by mutableStateOf<HeroEntry?>(null)
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
    /** False until a full load finishes, and again when something (like playing a title) makes it stale. */
    var loaded = false
    val selected = mutableStateMapOf<Int, Int>()
    /** The genre picked in the genre strip on Home, and the posters already loaded for each genre. */
    var genreIndex by mutableStateOf(0)
    val genreEntries = mutableStateMapOf<String, List<HeroEntry>>()
    var focusRow by mutableStateOf(0)
    var visited = false
    val columnState = LazyListState()
    val rowStates = HashMap<Int, LazyListState>()
}

/** A feature row: one big tile plus narrow ones. `mini` is the smaller version used for genres. */
class HeroSection(
    val title: String,
    val entries: List<HeroEntry>,
    val mini: Boolean = false,
    val genre: String? = null,
    /** OK plays the title (resuming) instead of opening its page. */
    val playDirect: Boolean = false,
    /** When set, this section is the genre picker: one row of posters whose genre you change from a strip below it. */
    val genreList: List<String> = emptyList(),
)

/** The grid of everything at the bottom of a genre page, with its sort buttons. */
class GridSpec(
    val items: List<Item>,
    val labels: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

/** How tall the details at the top of a browse page are; the rows start below it. */
private val INFO_HEIGHT = 250.dp
private val POSTER_W = 128.dp
private val POSTER_H = 192.dp

private val POPULAR_GENRES = listOf(
    "Action", "Comedy", "Drama", "Science Fiction", "Sci-Fi & Fantasy", "Thriller", "Horror", "Animation",
    "Adventure", "Crime", "Fantasy", "Romance", "Family", "Mystery", "Documentary",
)

internal fun smallTmdb(url: String?): String? = url?.replace("/w1280/", "/w300/")?.replace("/w342/", "/w185/")

/** Stops a list scrolling by itself when something gets focus, for lists that place their own contents. */
@OptIn(ExperimentalFoundationApi::class)
internal val NoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

internal fun itemEntry(i: Item, client: JellyfinClient) = HeroEntry(
    key = i.id,
    title = i.seriesName ?: i.name,
    overview = i.overview,
    year = i.year,
    rating = i.rating,
    genres = i.genres.take(3),
    backdrop = client.backdrop(i, 1280),
    poster = if (i.isEpisode && i.seriesId != null) client.image(i.seriesId, "Primary", null, 342) else client.poster(i, 342),
    kind = if (i.isMovie) "movie" else "tv",
    tmdbId = null,
    onServer = i,
    subtitle = if (i.isEpisode) listOfNotNull(i.episodeLabel, i.name).joinToString(" · ") else null,
    progress = i.progress,
    action = if (i.positionTicks > 0) "Resume" else "Play",
)

internal fun discoverEntry(d: Discover, index: Map<String, Item>) = HeroEntry(
    key = "${d.kind}:${d.tmdbId}", title = d.title, overview = d.overview, year = d.year,
    rating = d.rating, genres = d.genres, backdrop = d.backdrop, poster = d.poster,
    kind = d.kind, tmdbId = d.tmdbId, onServer = index["${d.kind}:${d.tmdbId}"],
)

private fun moreEntry(genre: String) = HeroEntry(
    key = "more:$genre", title = genre, overview = null, year = null, rating = null, genres = emptyList(),
    backdrop = null, poster = null, kind = "more", tmdbId = null, onServer = null, isMore = true,
)

/** Play a title that's on the server, open it if it's a show, or say it isn't here. */
internal fun playEntry(
    e: HeroEntry,
    scope: CoroutineScope,
    client: JellyfinClient,
    onPlay: (Item) -> Unit,
    onOpen: (Item) -> Unit,
    say: (String) -> Unit,
) {
    val server = e.onServer
    if (server == null) {
        say("“${e.title}” isn't on your server yet.")
    } else if (server.isMovie || server.isEpisode) {
        scope.launch {
            try { onPlay(client.item(server.id, server.duplicateIds)) } catch (x: Exception) { onOpen(server) }
        }
    } else {
        onOpen(server)
    }
}

internal fun titleRef(e: HeroEntry) = TitleRef(e.kind, e.tmdbId, e.onServer, e.title, e.backdrop, e.poster)

/** "Close Fancyfin?" in glass. Back or Stay dismisses it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ExitPrompt(onStay: () -> Unit, onClose: () -> Unit) {
    val stay = remember { FocusRequester() }
    BackHandler { onStay() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { stay.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }
    val panel = RoundedCornerShape(UiSettings.corner.tile)
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(380.dp)
                .background(Color(0xF0151922), panel)
                .border(1.dp, Palette.GlassEdge, panel)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Close Fancyfin?", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("You'll go back to the Shield home screen.", color = Palette.TextMuted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Stay", onStay, modifier = Modifier.focusRequester(stay), primary = true)
                GlassButton("Close Fancyfin", onClose, clear = true)
            }
        }
    }
}

/**
 * The feature rows (and, on a genre page, the grid beneath them). Only the row you're on shows its big
 * tile. Moving down pins the next row to the top of the screen and lands on its first tile, unless
 * you've been in that row before, in which case it goes back to the tile you left on.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun FeatureList(
    modifier: Modifier,
    state: FeatureState,
    client: JellyfinClient,
    sections: List<HeroSection>,
    grid: GridSpec?,
    onWallpaper: (String?) -> Unit,
    onPlay: (HeroEntry) -> Unit,
    onDetails: (HeroEntry) -> Unit,
    onMore: (String) -> Unit,
    onOpenItem: (Item) -> Unit,
    onExit: (() -> Unit)?,
    genreLoader: (suspend (String) -> List<HeroEntry>)? = null,
    genreTypes: String = "",
) {
    val scope = rememberCoroutineScope()
    var listFocused by remember { mutableStateOf(false) }
    val selected = state.selected
    var focusRow by state::focusRow
    val columnState = state.columnState
    val rowStates = state.rowStates
    val requesters = remember { HashMap<Int, HashMap<Int, FocusRequester>>() }

    LaunchedEffect(focusRow) { columnState.animateScrollToItem(focusRow) }

    // The genre picker: moving along the strip swaps the posters (loaded once, then kept).
    val genreNames = sections.firstOrNull { it.genreList.isNotEmpty() }?.genreList ?: emptyList()
    val genreSel = state.genreIndex.coerceIn(0, (genreNames.size - 1).coerceAtLeast(0))
    val stripRequesters = remember { HashMap<Int, FocusRequester>() }
    var stripFocused by remember { mutableStateOf(false) }
    var pinGenre by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(genreSel, genreNames) {
        val loader = genreLoader ?: return@LaunchedEffect
        val g = genreNames.getOrNull(genreSel) ?: return@LaunchedEffect
        if (state.genreEntries[g] == null) {
            kotlinx.coroutines.delay(200)
            try { state.genreEntries[g] = loader(g) } catch (e: Exception) { return@LaunchedEffect }
        }
        if (stripFocused) state.focused = state.genreEntries[g]?.firstOrNull { !it.isMore }
    }

    // Land on the first tile of the first row when the page opens, or back on the tile you left.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        val row = if (state.visited) focusRow.coerceIn(0, (sections.size - 1).coerceAtLeast(0)) else 0
        val col = if (state.visited) selected[row] ?: 0 else 0
        try {
            requesters[row]?.get(col)?.requestFocus()
        } catch (e: Exception) {
            try { requesters[0]?.get(0)?.requestFocus() } catch (x: Exception) { /* not laid out yet */ }
        }
    }

    // Back is handled by the app: one press goes back a page, two quick presses go Home.

    fun wallpaperFor(e: HeroEntry): String? =
        if (e.backdrop?.contains("image.tmdb.org") == true) smallTmdb(e.backdrop)
        else e.onServer?.let { client.backdrop(it, 96) } ?: smallTmdb(e.backdrop)

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
      Box(modifier.fillMaxSize().onFocusChanged { listFocused = it.hasFocus }) {
        InfoBlock(state.focused, client, Modifier.align(Alignment.TopStart).height(INFO_HEIGHT))
        LazyColumn(
            Modifier.fillMaxSize().padding(top = INFO_HEIGHT),
            state = columnState,
            contentPadding = PaddingValues(top = 0.dp, bottom = 300.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(sections) { si, section ->
                val isGenre = section.genreList.isNotEmpty()
                val genreName = if (isGenre) section.genreList.getOrNull(genreSel) ?: "" else section.genre
                val entries = if (isGenre) state.genreEntries[genreName] ?: emptyList() else section.entries
                Column {
                    Text(
                        if (isGenre) genreName else section.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                    )
                    if (entries.isEmpty()) Box(Modifier.height(POSTER_H + 16.dp))
                    HeroRow(
                        entries = entries,
                        focusIndex = if (si == focusRow) selected[si] ?: 0 else null,
                        listState = rowStates.getOrPut(si) { LazyListState() },
                        requesters = requesters.getOrPut(si) { HashMap() },
                        target = { selected[si] ?: 0 },
                        onSelect = { i ->
                            selected[si] = i
                            focusRow = si
                            state.visited = true
                            val e = entries[i]
                            if (!e.isMore) {
                                onWallpaper(wallpaperFor(e))
                                state.focused = e
                            }
                            if (e.onServer == null && e.kind == "movie" && e.tmdbId != null && e.status == null) {
                                scope.launch { e.status = client.releaseStatus(e.tmdbId) }
                            }
                        },
                        onClick = { e -> if (e.isMore) onMore(genreName ?: "") else if (section.playDirect && e.onServer != null) onDetails(e) else onPlay(e) },
                        onLongClick = { e -> if (!e.isMore) onDetails(e) },
                    )
                    if (isGenre) {
                        GenreStrip(
                            genres = section.genreList,
                            selected = genreSel,
                            requesters = stripRequesters,
                            onFocus = { i ->
                                state.genreIndex = i
                                selected[si] = 0
                                focusRow = si
                                state.visited = true
                            },
                            onPick = {
                                try { requesters[si]?.get(0)?.requestFocus() } catch (e: Exception) { /* posters not loaded yet */ }
                            },
                            onHold = { i -> pinGenre = section.genreList.getOrNull(i) },
                            onFocusState = { stripFocused = it },
                        )
                    }
                }
            }

            if (grid != null) {
                val base = sections.size
                item {
                    Column {
                        Text(
                            "All titles", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                        )
                        Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            grid.labels.forEachIndexed { i, label ->
                                GlassButton(label, { grid.onSelect(i) }, primary = i == grid.selected, clear = true, onFocused = { focusRow = base })
                            }
                        }
                    }
                }
                itemsIndexed(grid.items.chunked(8)) { r, chunk ->
                    Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        chunk.forEach { item ->
                            ItemCard(
                                item = item, client = client, width = 96.dp, height = 144.dp, landscape = false,
                                onClick = { onOpenItem(item) },
                                onFocused = {
                                    focusRow = base + 1 + r
                                    onWallpaper(client.backdrop(item, 96))
                                    state.focused = itemEntry(item, client)
                                },
                            )
                        }
                    }
                }
            }
        }
        pinGenre?.let { g ->
            PinDialog(
                title = g,
                pinned = Pins.hasGenre(g, genreTypes),
                onToggle = { Pins.toggleGenre(g, genreTypes) },
                onClose = { pinGenre = null },
            )
        }
      }
    }
}

/**
 * Home, Movies and TV Shows: popular right now, continue watching, up next, recently added (all
 * feature rows), then a smaller feature row for each popular category.
 */
@Composable
fun BrowseScreen(
    tab: Tab,
    state: FeatureState,
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onPlay: (Item) -> Unit,
    onGenre: (String, String) -> Unit,
    onTitle: (TitleRef) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var askExit by remember { mutableStateOf(false) }
    var sections by state::sections
    var loading by state::loading
    var failed by state::failed
    var wallpaper by state::wallpaper
    var message by remember(tab) { mutableStateOf<String?>(null) }

    val types = when (tab) {
        Tab.Movies -> "Movie"
        Tab.Shows -> "Series"
        else -> "Movie,Series"
    }

    LaunchedEffect(tab) {
        if (state.loaded) return@LaunchedEffect
        val fresh = sections.isEmpty()
        val acc = ArrayList<HeroSection>()
        fun add(section: HeroSection) { acc.add(section); if (fresh) sections = acc.toList() }
        val trendingKind = when (tab) {
            Tab.Movies -> "movie"
            Tab.Shows -> "tv"
            else -> "all"
        }
        try {
            val index = try { client.serverIndex() } catch (e: Exception) { emptyMap() }
            val trending = try { client.trending(trendingKind) } catch (e: Exception) { null }
            val popular = if (!trending.isNullOrEmpty()) {
                trending.map { discoverEntry(it, index) }
            } else {
                // TMDB isn't set up in Fancyfin yet: feature what's newest on the server instead.
                client.library(types, "DateCreated", true, limit = 12).map { itemEntry(it, client) }
            }
            val popularTitle = when (tab) {
                Tab.Movies -> "Popular movies"
                Tab.Shows -> "Popular shows"
                else -> "Popular right now"
            }
            if (popular.isNotEmpty()) {
                add(HeroSection(popularTitle, popular))
                val first = popular.first()
                if (fresh) {
                    wallpaper = if (first.backdrop?.contains("image.tmdb.org") == true) smallTmdb(first.backdrop) else first.onServer?.let { client.backdrop(it, 96) }
                    state.focused = first
                }
            }
            loading = false

            val resume = client.resume().filter { if (tab == Tab.Movies) it.isMovie else if (tab == Tab.Shows) it.isEpisode else true }
            if (tab == Tab.Home) WatchNext.publish(context, client, resume)
            if (resume.isNotEmpty()) add(HeroSection("Continue watching", resume.map { itemEntry(it, client) }, playDirect = true))

            if (tab != Tab.Movies && UiSettings.showUpNext) {
                val resumeIds = resume.map { it.id }.toSet()
                val next = client.nextUp().filter { it.id !in resumeIds }
                if (next.isNotEmpty()) add(HeroSection("Up next", next.map { itemEntry(it, client) }))
            }

            val recent = client.library(types, "DateCreated", true, limit = 15)
            if (recent.isNotEmpty()) add(HeroSection("Recently added", recent.map { itemEntry(it, client) }))

            // Every genre on your server: the popular ones first, the rest A to Z.
            val onServerGenres = client.genres(types)
            val ordered = POPULAR_GENRES.mapNotNull { p -> onServerGenres.firstOrNull { it.equals(p, ignoreCase = true) } } +
                onServerGenres.filter { g -> POPULAR_GENRES.none { it.equals(g, ignoreCase = true) } }.sorted()
            if (ordered.isNotEmpty()) {
                val pick = ordered[state.genreIndex.coerceIn(0, ordered.size - 1)]
                if (state.genreEntries[pick] == null) {
                    state.genreEntries[pick] = client.byGenre(pick, types, 10).map { itemEntry(it, client) } + moreEntry(pick)
                }
                add(HeroSection("", emptyList(), genreList = ordered))
            }
            if (!fresh) sections = acc.toList()
            state.loaded = true
        } catch (e: Exception) {
            if (sections.isEmpty()) failed = true
        }
        loading = false
    }

    Shell(tab, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize()) {
            HeroBackdrop(state.focused)
            when {
                failed -> Text("Couldn't reach your server.", color = Palette.Accent, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                loading -> Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                else -> key(tab) {
                    FeatureList(
                        modifier = contentModifier,
                        state = state,
                        client = client,
                        sections = sections,
                        grid = null,
                        onWallpaper = { wallpaper = it },
                        onPlay = { e -> onTitle(titleRef(e)) },
                        onDetails = { e -> playEntry(e, scope, client, onPlay, onOpen) { message = it } },
                        onMore = { genre -> onGenre(genre, types) },
                        onOpenItem = onOpen,
                        onExit = { askExit = true },
                        genreLoader = { g -> client.byGenre(g, types, 10).map { itemEntry(it, client) } + moreEntry(g) },
                        genreTypes = types,
                    )
                }
            }
            if (askExit) ExitPrompt(onStay = { askExit = false }, onClose = { (context as? android.app.Activity)?.finish() })
            message?.let {
                Text(
                    it, color = Palette.Text, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 24.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(UiSettings.corner.button))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * A page for one genre: popular in it (on your server or not), continue watching, recently added, and
 * then everything in the genre as a grid you can sort.
 */
@Composable
fun GenreScreen(
    genre: String,
    types: String,
    state: FeatureState,
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onPlay: (Item) -> Unit,
    onTitle: (TitleRef) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sections by state::sections
    var allItems by state::allItems
    var sortIndex by state::sortIndex
    var loading by state::loading
    var failed by state::failed
    var wallpaper by state::wallpaper
    var message by remember(genre) { mutableStateOf<String?>(null) }

    val sorts = listOf(Triple("Date released", "PremiereDate", true), Triple("Date added", "DateCreated", true), Triple("Alphabetical", "SortName", false))
    val tab = when (types) {
        "Movie" -> Tab.Movies
        "Series" -> Tab.Shows
        else -> Tab.Home
    }

    LaunchedEffect(genre) {
        if (state.loaded) return@LaunchedEffect
        val fresh = sections.isEmpty()
        val acc = ArrayList<HeroSection>()
        fun add(section: HeroSection) { acc.add(section); if (fresh) sections = acc.toList() }
        try {
            val index = try { client.serverIndex() } catch (e: Exception) { emptyMap() }
            val everything = client.genreLibrary(genre, types, "SortName", false)
            val popular = try { client.popularInGenre(genre, types) } catch (e: Exception) { null }
            val popularEntries = if (!popular.isNullOrEmpty()) {
                popular.map { discoverEntry(it, index) }
            } else {
                everything.sortedByDescending { it.rating ?: 0.0 }.take(15).map { itemEntry(it, client) }
            }
            if (popularEntries.isNotEmpty()) {
                add(HeroSection("Popular in $genre", popularEntries))
                val first = popularEntries.first()
                if (fresh) {
                    wallpaper = if (first.backdrop?.contains("image.tmdb.org") == true) smallTmdb(first.backdrop) else first.onServer?.let { client.backdrop(it, 96) }
                    state.focused = first
                }
            }
            loading = false

            val ids = everything.map { it.id }.toSet()
            val resume = client.resume().filter { it.id in ids || (it.seriesId != null && it.seriesId in ids) }
            if (resume.isNotEmpty()) add(HeroSection("Continue watching", resume.map { itemEntry(it, client) }, playDirect = true))

            val recent = client.genreLibrary(genre, types, "DateCreated", true, 15)
            if (recent.isNotEmpty()) add(HeroSection("Recently added", recent.map { itemEntry(it, client) }))
            if (!fresh) sections = acc.toList()
            state.loaded = true
        } catch (e: Exception) {
            if (sections.isEmpty()) failed = true
        }
        loading = false
    }

    LaunchedEffect(genre, sortIndex) {
        try {
            val (_, by, desc) = sorts[sortIndex]
            allItems = client.genreLibrary(genre, types, by, desc)
        } catch (e: Exception) {
            // The rows above still work.
        }
    }

    Shell(tab, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize()) {
            HeroBackdrop(state.focused)
            when {
                failed -> Text("Couldn't reach your server.", color = Palette.Accent, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                loading -> Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                else -> key(genre) {
                  Column(contentModifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 40.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(genre, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    FeatureList(
                        modifier = Modifier.weight(1f),
                        state = state,
                        client = client,
                        sections = sections,
                        grid = GridSpec(allItems, sorts.map { it.first }, sortIndex) { sortIndex = it },
                        onWallpaper = { wallpaper = it },
                        onPlay = { e -> onTitle(titleRef(e)) },
                        onDetails = { e -> playEntry(e, scope, client, onPlay, onOpen) { message = it } },
                        onMore = {},
                        onOpenItem = onOpen,
                        onExit = null,
                    )
                  }
                }
            }
            message?.let {
                Text(
                    it, color = Palette.Text, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 24.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(UiSettings.corner.button))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * One row of posters. The row scrolls so the poster you are on has exactly one before it.
 * Coming into the row from above or below lands on `target()`: the first poster, or where you left off.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun HeroRow(
    entries: List<HeroEntry>,
    focusIndex: Int?,
    listState: LazyListState,
    requesters: HashMap<Int, FocusRequester>,
    target: () -> Int,
    onSelect: (Int) -> Unit,
    onClick: (HeroEntry) -> Unit,
    onLongClick: (HeroEntry) -> Unit,
) {
    LaunchedEffect(focusIndex) {
        if (focusIndex != null) listState.animateScrollToItem((focusIndex - 1).coerceAtLeast(0))
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        LazyRow(
            modifier = Modifier
                .focusProperties { enter = { requesters[target()] ?: FocusRequester.Default } }
                .focusGroup(),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            itemsIndexed(entries) { index, entry ->
                val requester = remember { FocusRequester() }
                DisposableEffect(index) {
                    requesters[index] = requester
                    onDispose { if (requesters[index] === requester) requesters.remove(index) }
                }
                PosterCard(
                    entry = entry,
                    fade = if (focusIndex == null) 1f else posterAlpha(kotlin.math.abs(index - focusIndex)),
                    focusRequester = requester,
                    onFocused = { onSelect(index) },
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            }
        }
    }
}

/** A poster in a row: white edge and a little growth when you're on it, a tick when you've watched it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    entry: HeroEntry,
    fade: Float,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.07f else 1f, label = "posterGrow")
    val shape = RoundedCornerShape(UiSettings.corner.tile.coerceAtMost(10.dp))
    val seeThrough by animateFloatAsState(fade, label = "posterFade")

    Box(
        Modifier
            .graphicsLayer { alpha = seeThrough }
            .width(POSTER_W)
            .height(POSTER_H)
            .scale(grow)
            .clip(shape)
            .then(
                if (focused) Modifier.border(2.dp, Palette.Text, shape)
                else if (UiSettings.posterBorder) Modifier.border(1.dp, Palette.GlassEdge, shape)
                else Modifier,
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.GlassFill))

        if (entry.isMore) {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text("See all", color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(entry.title, color = Palette.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("›", color = Palette.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            return@Box
        }

        val picture = entry.poster ?: entry.backdrop
        if (picture != null) {
            AsyncImage(model = picture, contentDescription = entry.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(entry.title, color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp))
        }
        if (entry.onServer == null) {
            NotOnServerPill(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        } else if (entry.onServer.played) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(14.dp).background(Color(0xE6FFFFFF), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
        entry.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomStart)) }
    }
}

/** How solid a poster is: the one you're on is fully solid, and each step away (either side) fades more. */
private fun posterAlpha(distance: Int): Float = when (distance) {
    0 -> 1f
    1 -> 0.85f
    2 -> 0.65f
    3 -> 0.45f
    else -> 0.25f
}

/** The strip of genres under the posters. Moving along it swaps the posters; OK jumps up to them; holding OK offers to pin. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun GenreStrip(
    genres: List<String>,
    selected: Int,
    requesters: HashMap<Int, FocusRequester>,
    onFocus: (Int) -> Unit,
    onPick: () -> Unit,
    onHold: (Int) -> Unit,
    onFocusState: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        LazyRow(
            modifier = Modifier
                .focusProperties { enter = { requesters[selected] ?: FocusRequester.Default } }
                .focusGroup()
                .onFocusChanged { onFocusState(it.hasFocus) },
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        ) {
            itemsIndexed(genres) { i, g ->
                val requester = remember { FocusRequester() }
                DisposableEffect(i) {
                    requesters[i] = requester
                    onDispose { if (requesters[i] === requester) requesters.remove(i) }
                }
                FocusBox(
                    onClick = onPick,
                    onLongClick = { onHold(i) },
                    onFocused = { onFocus(i) },
                    modifier = Modifier.height(28.dp).focusRequester(requester),
                    shape = RoundedCornerShape(50),
                    focusedScale = 1.06f,
                    edge = false,
                ) { focused ->
                    val on = i == selected
                    Box(
                        Modifier.fillMaxHeight()
                            .background(if (on) Color.White else Color.White.copy(alpha = if (focused) 0.32f else 0.16f))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(g, color = if (on) Color.Black else Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Hold OK on a genre: pin it to the menu (or unpin it). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PinDialog(title: String, pinned: Boolean, onToggle: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }
    val panel = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(250.dp)
                .background(Color(0xF50B0B0D), panel)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            GlassButton(
                if (pinned) "Unpin from menu" else "Pin to menu",
                { onToggle(); onClose() },
                modifier = Modifier.fillMaxWidth().focusRequester(first), primary = true, compact = true,
            )
            GlassButton("Cancel", onClose, modifier = Modifier.fillMaxWidth(), compact = true)
        }
    }
}

/**
 * Inside one franchise, universe or studio: featured tiles for its most popular films, then the most
 * recently released, then everything you own as a grid. Missing films in a franchise or universe show too,
 * marked "Not on server", so you can add them.
 */
@Composable
fun CollectionScreen(
    tile: com.fancyfin.tv.data.CollectionTile,
    state: FeatureState,
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onPlay: (Item) -> Unit,
    onTitle: (TitleRef) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sections by state::sections
    var allItems by state::allItems
    var sortIndex by state::sortIndex
    var loading by state::loading
    var wallpaper by state::wallpaper
    var message by remember { mutableStateOf<String?>(null) }
    val sorts = listOf("Date released", "Rating", "Alphabetical")

    LaunchedEffect(tile.key) {
        if (state.loaded) return@LaunchedEffect
        val owned = tile.items.map { itemEntry(it, client) }
        // Show what you own straight away, then add the films you don't have.
        fun build(all: List<HeroEntry>) {
            val popular = all.sortedByDescending { it.rating ?: 0.0 }.take(12)
            val recent = all.sortedByDescending { it.year ?: 0 }.take(12)
            sections = listOfNotNull(
                popular.takeIf { it.isNotEmpty() }?.let { HeroSection("Most popular", it) },
                recent.takeIf { it.isNotEmpty() }?.let { HeroSection("Recently released", it) },
            )
            if (wallpaper == null) {
                val first = popular.firstOrNull()
                wallpaper = if (first?.backdrop?.contains("image.tmdb.org") == true) smallTmdb(first.backdrop) else first?.onServer?.let { client.backdrop(it, 96) }
                state.focused = first
            }
        }
        build(owned)
        loading = false
        try {
            val index = client.serverIndex()
            val everything: List<HeroEntry>? = when {
                tile.collectionId != null -> client.franchise(tile.collectionId).map { discoverEntry(it, index) }.takeIf { it.isNotEmpty() }
                tile.wikidataId != null -> {
                    val have = tile.items.mapNotNull { it.tmdbId?.toIntOrNull() }.toSet()
                    val missing = client.universeFilmIds(tile.wikidataId).filter { it !in have && index["movie:$it"] == null }
                    owned + client.tmdbMovies(missing).map { discoverEntry(it, index) }
                }
                else -> null
            }
            if (everything != null) build(everything)
        } catch (e: Exception) {
            // The films you own are already showing.
        }
        state.loaded = true
    }

    allItems = when (sortIndex) {
        0 -> tile.items.sortedByDescending { it.year ?: 0 }
        1 -> tile.items.sortedByDescending { it.rating ?: 0.0 }
        else -> tile.items.sortedBy { it.name }
    }

    Shell(Tab.Collections, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize()) {
            HeroBackdrop(state.focused)
            if (loading) {
                Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            } else {
                Column(contentModifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 40.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tile.kind.uppercase(), color = Palette.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Text(tile.name, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            val total = tile.total
                            Text(
                                if (total != null && total > tile.owned) "${tile.owned} of $total films on your server" else "${tile.owned} films",
                                color = Palette.TextMuted, fontSize = 11.sp,
                            )
                        }
                    }
                    FeatureList(
                        modifier = Modifier.weight(1f),
                        state = state,
                        client = client,
                        sections = sections,
                        grid = GridSpec(allItems, sorts, sortIndex) { sortIndex = it },
                        onWallpaper = { wallpaper = it },
                        onPlay = { e -> onTitle(titleRef(e)) },
                        onDetails = { e -> playEntry(e, scope, client, onPlay, onOpen) { message = it } },
                        onMore = {},
                        onOpenItem = onOpen,
                        onExit = null,
                    )
                }
            }
            message?.let {
                Text(
                    it, color = Palette.Text, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 24.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(UiSettings.corner.button))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
