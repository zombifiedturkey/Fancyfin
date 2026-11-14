package com.fancyfin.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Discover
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.launch

/** One tile in a feature row: a title, on the server or not. */
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
)

/** A band of the page: either a feature row (big tile plus narrow ones) or a plain row of cards. */
private class Section(
    val title: String,
    val heroes: List<HeroEntry>? = null,
    val items: List<Item> = emptyList(),
    val landscape: Boolean = false,
)

private val POPULAR_GENRES = listOf(
    "Action", "Comedy", "Drama", "Science Fiction", "Sci-Fi & Fantasy", "Thriller", "Horror", "Animation",
    "Adventure", "Crime", "Fantasy", "Romance", "Family", "Mystery", "Documentary",
)

private fun smallTmdb(url: String?): String? = url?.replace("/w1280/", "/w300/")?.replace("/w342/", "/w185/")

/** Stops a list scrolling by itself when something gets focus, for lists that place their own contents. */
@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private fun resumeEntry(i: Item, client: JellyfinClient) = HeroEntry(
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

/**
 * Home, Movies and TV Shows. A big "popular right now" row (play it if it's on your server), then
 * Continue watching in the same feature layout, then Up next, Recently added and a row for each
 * popular category. Moving down pins the row you reach to the top of the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    tab: Tab,
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onPlay: (Item) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sections by remember(tab) { mutableStateOf<List<Section>>(emptyList()) }
    val selected = remember(tab) { mutableStateMapOf<Int, Int>() }
    var loading by remember(tab) { mutableStateOf(true) }
    var failed by remember(tab) { mutableStateOf(false) }
    var wallpaper by remember(tab) { mutableStateOf<String?>(null) }
    var message by remember(tab) { mutableStateOf<String?>(null) }
    var focusSection by remember(tab) { mutableStateOf(0) }
    val columnState = rememberLazyListState()

    // Whichever band you move into goes to the top of the screen.
    LaunchedEffect(focusSection) { columnState.animateScrollToItem(focusSection) }

    fun wallpaperFor(e: HeroEntry): String? =
        if (e.backdrop?.contains("image.tmdb.org") == true) smallTmdb(e.backdrop)
        else e.onServer?.let { client.backdrop(it, 96) } ?: smallTmdb(e.backdrop)

    LaunchedEffect(tab) {
        val trendingKind = when (tab) {
            Tab.Movies -> "movie"
            Tab.Shows -> "tv"
            else -> "all"
        }
        val types = when (tab) {
            Tab.Movies -> "Movie"
            Tab.Shows -> "Series"
            else -> "Movie,Series"
        }
        try {
            // Popular right now.
            val index = try { client.serverIndex() } catch (e: Exception) { emptyMap() }
            val trending = try { client.trending(trendingKind) } catch (e: Exception) { null }
            val popular = if (!trending.isNullOrEmpty()) {
                trending.map { d: Discover ->
                    HeroEntry(
                        key = "${d.kind}:${d.tmdbId}", title = d.title, overview = d.overview, year = d.year,
                        rating = d.rating, genres = d.genres, backdrop = d.backdrop, poster = d.poster,
                        kind = d.kind, tmdbId = d.tmdbId, onServer = index["${d.kind}:${d.tmdbId}"],
                    )
                }
            } else {
                // TMDB isn't set up in Fancyfin yet: feature what's newest on the server instead.
                client.library(types, "DateCreated", true, limit = 12).map { i ->
                    HeroEntry(
                        key = i.id, title = i.name, overview = i.overview, year = i.year, rating = i.rating,
                        genres = i.genres.take(3), backdrop = client.backdrop(i, 1280), poster = client.poster(i, 342),
                        kind = if (i.isMovie) "movie" else "tv", tmdbId = null, onServer = i,
                    )
                }
            }
            val popularTitle = when (tab) {
                Tab.Movies -> "Popular movies"
                Tab.Shows -> "Popular shows"
                else -> "Popular right now"
            }
            if (popular.isNotEmpty()) {
                sections = sections + Section(popularTitle, heroes = popular)
                wallpaper = wallpaperFor(popular.first())
            }
            loading = false

            // The rest, added as they arrive.
            val resume = client.resume().filter { if (tab == Tab.Movies) it.isMovie else if (tab == Tab.Shows) it.isEpisode else true }
            if (resume.isNotEmpty()) sections = sections + Section("Continue watching", heroes = resume.map { resumeEntry(it, client) })
            if (tab != Tab.Movies) {
                val resumeIds = resume.map { it.id }.toSet()
                val next = client.nextUp().filter { it.id !in resumeIds }
                if (next.isNotEmpty()) sections = sections + Section("Up next", items = next, landscape = true)
            }
            val recent = client.library(types, "DateCreated", true, limit = 20)
            if (recent.isNotEmpty()) sections = sections + Section("Recently added", items = recent)

            val onServerGenres = client.genres(types).associateBy { it.lowercase() }
            val chosen = POPULAR_GENRES.mapNotNull { onServerGenres[it.lowercase()] }.take(8)
            for (genre in chosen) {
                val found = client.byGenre(genre, types)
                if (found.size >= 4) sections = sections + Section(genre, items = found)
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
                failed -> Text("Couldn't reach your server.", color = Palette.Accent, fontSize = 14.sp, modifier = Modifier.padding(48.dp))
                loading -> Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(48.dp))
                else -> CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
                    LazyColumn(
                        contentModifier.fillMaxSize(),
                        state = columnState,
                        contentPadding = PaddingValues(top = 24.dp, bottom = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(sections) { sectionIndex, section ->
                            Column {
                                Text(
                                    section.title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                                )
                                if (section.heroes != null) {
                                    HeroRow(
                                        entries = section.heroes,
                                        selected = selected[sectionIndex] ?: 0,
                                        onSelect = { i ->
                                            selected[sectionIndex] = i
                                            focusSection = sectionIndex
                                            wallpaper = wallpaperFor(section.heroes[i])
                                        },
                                        onClick = { e ->
                                            val server = e.onServer
                                            if (server == null) {
                                                message = "“${e.title}” isn't on your server yet."
                                            } else if (server.isMovie || server.isEpisode) {
                                                scope.launch {
                                                    try { onPlay(client.item(server.id)) } catch (x: Exception) { onOpen(server) }
                                                }
                                            } else {
                                                onOpen(server)
                                            }
                                        },
                                        onLongClick = { e -> e.onServer?.let(onOpen) },
                                    )
                                    if (sectionIndex == 0) {
                                        message?.let { Text(it, color = Palette.Accent, fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp, top = 6.dp)) }
                                    }
                                } else {
                                    CardRow(section, client, onOpen) { item ->
                                        focusSection = sectionIndex
                                        wallpaper = client.backdrop(item, 96)
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

/** A plain row of cards. Scrolls the usual way, keeping a margin at the edge. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(section: Section, client: JellyfinClient, onOpen: (Item) -> Unit, onFocus: (Item) -> Unit) {
    val margin = with(LocalDensity.current) { 56.dp.toPx() }
    val spec = remember(margin) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val trailing = offset + size
                return when {
                    offset < margin -> offset - margin
                    trailing > containerSize - margin -> trailing - (containerSize - margin)
                    else -> 0f
                }
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        ) {
            items(section.items) { item ->
                ItemCard(
                    item = item,
                    client = client,
                    width = if (section.landscape) 160.dp else 100.dp,
                    height = if (section.landscape) 90.dp else 150.dp,
                    landscape = section.landscape,
                    onClick = { onOpen(item) },
                    onFocused = { onFocus(item) },
                )
            }
        }
    }
}

/**
 * The tile you are on is wide and shows everything; the rest are narrow posters that open as you reach
 * them. The row always scrolls so the tile you are on has exactly one narrow tile before it, in either
 * direction, so it doesn't jump around as it opens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroRow(
    entries: List<HeroEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onClick: (HeroEntry) -> Unit,
    onLongClick: (HeroEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        listState.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            itemsIndexed(entries) { index, entry ->
                HeroTile(entry, expanded = index == selected, onFocused = { onSelect(index) }, onClick = { onClick(entry) }, onLongClick = { onLongClick(entry) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroTile(
    entry: HeroEntry,
    expanded: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 520.dp else 118.dp, label = "heroWidth")
    val shape = RoundedCornerShape(UiSettings.corner.tile)

    Box(
        Modifier
            .width(width)
            .height(250.dp)
            .clip(shape)
            .then(
                if (focused) Modifier.border(2.dp, Palette.Text, shape)
                else if (UiSettings.posterBorder) Modifier.border(1.dp, Palette.GlassEdge, shape)
                else Modifier,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.GlassFill))
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
            Column(
                Modifier.fillMaxHeight().width(330.dp).padding(start = 20.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(if (entry.kind == "movie") "MOVIE" else "SERIES", color = Palette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(entry.title, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.subtitle?.let { Text(it, color = Palette.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val facts = listOfNotNull(entry.year?.toString(), entry.rating?.let { "★ " + "%.1f".format(it) }, entry.genres.takeIf { it.isNotEmpty() }?.joinToString(", "))
                if (facts.isNotEmpty()) Text(facts.joinToString("   ·   "), color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                entry.overview?.let {
                    Text(it, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val here = entry.onServer != null
                    Box(
                        Modifier.height(30.dp)
                            .background(if (here) Palette.Accent else Palette.GlassFillStrong, RoundedCornerShape(UiSettings.corner.button))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (here) "▶  ${entry.action}" else "Not on your server",
                            color = if (here) Color.Black else Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (here) Text("hold OK for details", color = Palette.TextMuted, fontSize = 10.sp)
                }
            }
        }
        entry.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomStart)) }
    }
}
