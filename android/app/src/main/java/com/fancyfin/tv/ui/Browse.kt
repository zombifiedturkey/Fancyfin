package com.fancyfin.tv.ui

import androidx.compose.animation.core.animateDpAsState
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

/** A feature row: one big tile plus narrow ones. `mini` is the smaller version used for genres. */
class HeroSection(
    val title: String,
    val entries: List<HeroEntry>,
    val mini: Boolean = false,
    val genre: String? = null,
    /** OK plays the title (resuming) instead of opening its page. */
    val playDirect: Boolean = false,
)

/** The grid of everything at the bottom of a genre page, with its sort buttons. */
class GridSpec(
    val items: List<Item>,
    val labels: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

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

private fun itemEntry(i: Item, client: JellyfinClient) = HeroEntry(
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

private fun discoverEntry(d: Discover, index: Map<String, Item>) = HeroEntry(
    key = "${d.kind}:${d.tmdbId}", title = d.title, overview = d.overview, year = d.year,
    rating = d.rating, genres = d.genres, backdrop = d.backdrop, poster = d.poster,
    kind = d.kind, tmdbId = d.tmdbId, onServer = index["${d.kind}:${d.tmdbId}"],
)

private fun moreEntry(genre: String) = HeroEntry(
    key = "more:$genre", title = genre, overview = null, year = null, rating = null, genres = emptyList(),
    backdrop = null, poster = null, kind = "more", tmdbId = null, onServer = null, isMore = true,
)

/** Play a title that's on the server, open it if it's a show, or say it isn't here. */
private fun playEntry(
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

private fun titleRef(e: HeroEntry) = TitleRef(e.kind, e.tmdbId, e.onServer, e.title, e.backdrop, e.poster)

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
    client: JellyfinClient,
    sections: List<HeroSection>,
    grid: GridSpec?,
    onWallpaper: (String?) -> Unit,
    onPlay: (HeroEntry) -> Unit,
    onDetails: (HeroEntry) -> Unit,
    onMore: (String) -> Unit,
    onOpenItem: (Item) -> Unit,
    onExit: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var listFocused by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<Int, Int>() }
    var focusRow by remember { mutableStateOf(0) }
    val columnState = rememberLazyListState()
    val rowStates = remember { HashMap<Int, LazyListState>() }
    val requesters = remember { HashMap<Int, HashMap<Int, FocusRequester>>() }

    LaunchedEffect(focusRow) { columnState.animateScrollToItem(focusRow) }

    // Land on the first tile of the first row when the page opens.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        try { requesters[0]?.get(0)?.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }

    // Back: first jump to the start of the row you're on; from there, ask whether to close the app.
    val atStart = focusRow < sections.size && (selected[focusRow] ?: 0) == 0
    BackHandler(enabled = listFocused && (!atStart || onExit != null)) {
        if (focusRow >= sections.size || (selected[focusRow] ?: 0) != 0) {
            val row = if (focusRow >= sections.size) 0 else focusRow
            try { requesters[row]?.get(0)?.requestFocus() } catch (e: Exception) { /* row not on screen */ }
        } else {
            onExit?.invoke()
        }
    }

    fun wallpaperFor(e: HeroEntry): String? =
        if (e.backdrop?.contains("image.tmdb.org") == true) smallTmdb(e.backdrop)
        else e.onServer?.let { client.backdrop(it, 96) } ?: smallTmdb(e.backdrop)

    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        LazyColumn(
            modifier.fillMaxSize().onFocusChanged { listFocused = it.hasFocus },
            state = columnState,
            contentPadding = PaddingValues(top = 24.dp, bottom = 300.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(sections) { si, section ->
                Column {
                    Text(
                        section.title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                    )
                    HeroRow(
                        entries = section.entries,
                        mini = section.mini,
                        expandedIndex = if (si == focusRow) selected[si] ?: 0 else null,
                        listState = rowStates.getOrPut(si) { LazyListState() },
                        requesters = requesters.getOrPut(si) { HashMap() },
                        target = { selected[si] ?: 0 },
                        onSelect = { i ->
                            selected[si] = i
                            focusRow = si
                            val e = section.entries[i]
                            if (!e.isMore) onWallpaper(wallpaperFor(e))
                            if (e.onServer == null && e.kind == "movie" && e.tmdbId != null && e.status == null) {
                                scope.launch { e.status = client.releaseStatus(e.tmdbId) }
                            }
                        },
                        onClick = { e -> if (e.isMore) onMore(section.genre ?: "") else if (section.playDirect && e.onServer != null) onDetails(e) else onPlay(e) },
                        onLongClick = { e -> if (!e.isMore) onDetails(e) },
                    )
                }
            }

            if (grid != null) {
                val base = sections.size
                item {
                    Column {
                        Text(
                            "All titles", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
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
                                },
                            )
                        }
                    }
                }
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
    var sections by remember(tab) { mutableStateOf<List<HeroSection>>(emptyList()) }
    var loading by remember(tab) { mutableStateOf(true) }
    var failed by remember(tab) { mutableStateOf(false) }
    var wallpaper by remember(tab) { mutableStateOf<String?>(null) }
    var message by remember(tab) { mutableStateOf<String?>(null) }

    val types = when (tab) {
        Tab.Movies -> "Movie"
        Tab.Shows -> "Series"
        else -> "Movie,Series"
    }

    LaunchedEffect(tab) {
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
                sections = sections + HeroSection(popularTitle, popular)
                val first = popular.first()
                wallpaper = if (first.backdrop?.contains("image.tmdb.org") == true) smallTmdb(first.backdrop) else first.onServer?.let { client.backdrop(it, 96) }
            }
            loading = false

            val resume = client.resume().filter { if (tab == Tab.Movies) it.isMovie else if (tab == Tab.Shows) it.isEpisode else true }
            if (resume.isNotEmpty()) sections = sections + HeroSection("Continue watching", resume.map { itemEntry(it, client) }, playDirect = true)

            if (tab != Tab.Movies && UiSettings.showUpNext) {
                val resumeIds = resume.map { it.id }.toSet()
                val next = client.nextUp().filter { it.id !in resumeIds }
                if (next.isNotEmpty()) sections = sections + HeroSection("Up next", next.map { itemEntry(it, client) })
            }

            val recent = client.library(types, "DateCreated", true, limit = 15)
            if (recent.isNotEmpty()) sections = sections + HeroSection("Recently added", recent.map { itemEntry(it, client) })

            val onServerGenres = client.genres(types).associateBy { it.lowercase() }
            val chosen = POPULAR_GENRES.mapNotNull { onServerGenres[it.lowercase()] }.take(8)
            for (genre in chosen) {
                val found = client.byGenre(genre, types, 10)
                if (found.size >= 4) {
                    sections = sections + HeroSection(genre, found.map { itemEntry(it, client) } + moreEntry(genre), mini = true, genre = genre)
                }
            }
        } catch (e: Exception) {
            if (sections.isEmpty()) failed = true
        }
        loading = false
    }

    Shell(tab, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize()) {
            Backdrop(wallpaper)
            when {
                failed -> Text("Couldn't reach your server.", color = Palette.Accent, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                loading -> Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                else -> key(tab) {
                    FeatureList(
                        modifier = contentModifier,
                        client = client,
                        sections = sections,
                        grid = null,
                        onWallpaper = { wallpaper = it },
                        onPlay = { e -> onTitle(titleRef(e)) },
                        onDetails = { e -> playEntry(e, scope, client, onPlay, onOpen) { message = it } },
                        onMore = { genre -> onGenre(genre, types) },
                        onOpenItem = onOpen,
                        onExit = { askExit = true },
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
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onPlay: (Item) -> Unit,
    onTitle: (TitleRef) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sections by remember(genre) { mutableStateOf<List<HeroSection>>(emptyList()) }
    var allItems by remember(genre) { mutableStateOf<List<Item>>(emptyList()) }
    var sortIndex by remember(genre) { mutableStateOf(1) }
    var loading by remember(genre) { mutableStateOf(true) }
    var failed by remember(genre) { mutableStateOf(false) }
    var wallpaper by remember(genre) { mutableStateOf<String?>(null) }
    var message by remember(genre) { mutableStateOf<String?>(null) }

    val sorts = listOf(Triple("Date released", "PremiereDate", true), Triple("Date added", "DateCreated", true), Triple("Alphabetical", "SortName", false))
    val tab = when (types) {
        "Movie" -> Tab.Movies
        "Series" -> Tab.Shows
        else -> Tab.Home
    }

    LaunchedEffect(genre) {
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
                sections = sections + HeroSection("Popular in $genre", popularEntries)
                val first = popularEntries.first()
                wallpaper = if (first.backdrop?.contains("image.tmdb.org") == true) smallTmdb(first.backdrop) else first.onServer?.let { client.backdrop(it, 96) }
            }
            loading = false

            val ids = everything.map { it.id }.toSet()
            val resume = client.resume().filter { it.id in ids || (it.seriesId != null && it.seriesId in ids) }
            if (resume.isNotEmpty()) sections = sections + HeroSection("Continue watching", resume.map { itemEntry(it, client) }, playDirect = true)

            val recent = client.genreLibrary(genre, types, "DateCreated", true, 15)
            if (recent.isNotEmpty()) sections = sections + HeroSection("Recently added", recent.map { itemEntry(it, client) })
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
            Backdrop(wallpaper)
            when {
                failed -> Text("Couldn't reach your server.", color = Palette.Accent, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                loading -> Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                else -> key(genre) {
                    FeatureList(
                        modifier = contentModifier,
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
 * One row of tiles. The tile you are on is wide and shows everything; the rest are narrow posters that
 * open as you reach them. The row scrolls so the tile you are on has exactly one narrow tile before it.
 * Coming into the row from above or below lands on `target()`: the first tile, or where you left off.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun HeroRow(
    entries: List<HeroEntry>,
    mini: Boolean,
    expandedIndex: Int?,
    listState: LazyListState,
    requesters: HashMap<Int, FocusRequester>,
    target: () -> Int,
    onSelect: (Int) -> Unit,
    onClick: (HeroEntry) -> Unit,
    onLongClick: (HeroEntry) -> Unit,
) {
    LaunchedEffect(expandedIndex) {
        if (expandedIndex != null) listState.animateScrollToItem((expandedIndex - 1).coerceAtLeast(0))
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        LazyRow(
            modifier = Modifier
                .focusProperties { enter = { requesters[target()] ?: FocusRequester.Default } }
                .focusGroup(),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(if (mini) 8.dp else 10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            itemsIndexed(entries) { index, entry ->
                val requester = remember { FocusRequester() }
                DisposableEffect(index) {
                    requesters[index] = requester
                    onDispose { if (requesters[index] === requester) requesters.remove(index) }
                }
                HeroTile(
                    entry = entry,
                    mini = mini,
                    expanded = index == expandedIndex && !entry.isMore,
                    focusRequester = requester,
                    onFocused = { onSelect(index) },
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroTile(
    entry: HeroEntry,
    mini: Boolean,
    expanded: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val narrow = if (mini) 100.dp else 118.dp
    val wide = if (mini) 312.dp else 520.dp
    val tall = if (mini) 150.dp else 250.dp
    val width by animateDpAsState(if (expanded) wide else narrow, label = "heroWidth")
    val shape = RoundedCornerShape(UiSettings.corner.tile.let { if (mini) it * 0.7f else it })

    Box(
        Modifier
            .width(width)
            .height(tall)
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

        val picture = if (expanded) entry.backdrop ?: entry.poster else entry.poster ?: entry.backdrop
        if (picture != null) {
            AsyncImage(model = picture, contentDescription = entry.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (expanded) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Color(0xF0000000), 0.6f to Color(0x99000000), 1f to Color(0x10000000)),
                ),
            )
            val titleSize = if (mini) 16.sp else 26.sp
            val small = if (mini) 10.sp else 12.sp
            Column(
                Modifier.fillMaxHeight().width(if (mini) 200.dp else 330.dp)
                    .padding(start = if (mini) 12.dp else 20.dp, top = if (mini) 10.dp else 18.dp, bottom = if (mini) 10.dp else 18.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val kindLabel = if (entry.kind == "movie") "MOVIE" else "SERIES"
                val top = if (entry.onServer == null && entry.status != null) (if (mini) entry.status!!.uppercase() else kindLabel + "  ·  " + entry.status!!.uppercase()) else if (mini) null else kindLabel
                if (top != null) Text(top, color = Palette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.title, color = Palette.Text, fontSize = titleSize, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.subtitle?.let { Text(it, color = Palette.Text, fontSize = if (mini) 10.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val facts = listOfNotNull(
                    entry.year?.toString(),
                    entry.rating?.let { "★ " + "%.1f".format(it) },
                    entry.genres.takeIf { it.isNotEmpty() && !mini }?.joinToString(", "),
                )
                if (facts.isNotEmpty()) Text(facts.joinToString("   ·   "), color = Palette.TextMuted, fontSize = small, modifier = Modifier.padding(top = 3.dp))
                entry.overview?.let {
                    Text(it, color = Palette.TextMuted, fontSize = small, maxLines = if (mini) 2 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                val here = entry.onServer != null
                val round = RoundedCornerShape(UiSettings.corner.button.coerceAtMost(if (mini) 11.dp else 15.dp))
                val h = if (mini) 22.dp else 30.dp
                val pad = if (mini) 10.dp else 16.dp
                Box(
                    Modifier.padding(top = if (mini) 6.dp else 10.dp).height(h)
                        .background(if (here) Palette.Accent else Palette.GlassFillStrong, round)
                        .padding(horizontal = pad),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (here) "▶  ${entry.action}" else "Not on your server",
                        color = if (here) Color.Black else Palette.Text, fontSize = small, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        entry.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomStart)) }
    }
}
