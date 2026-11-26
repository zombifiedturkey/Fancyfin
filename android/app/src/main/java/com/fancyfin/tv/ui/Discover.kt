@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.fancyfin.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the Discover page was showing, kept so Back from a title lands exactly where you were. */
class DiscoverState {
    var kind by mutableStateOf("movie")
    var genre by mutableStateOf<String?>(null)
    var decade by mutableStateOf<Int?>(null)
    var results by mutableStateOf<List<HeroEntry>>(emptyList())
    var loading by mutableStateOf(true)
    var note by mutableStateOf<String?>(null)
    var focused by mutableStateOf<HeroEntry?>(null)
    var loadedKey: String? = null
    var genres by mutableStateOf<List<String>>(emptyList())
    var genresFor: String? = null
    var owned: Map<String, Item> = emptyMap()
    val listState = LazyListState()
    var focusRow by mutableStateOf(0)
}

private val DECADES = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960, 1950, 1940, 1930, 1920)

/**
 * The best rated 100 movies or shows, narrowed by genre and decade. The details of the title you're on
 * sit above, the pills below them, then the grid. OK opens a title (where Radarr / Sonarr can add it);
 * holding OK on one that isn't on your server adds it straight away.
 */
@Composable
fun DiscoverScreen(
    state: DiscoverState,
    client: JellyfinClient,
    onTitle: (TitleRef) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<String?>(null) }
    var arrFor by remember { mutableStateOf<HeroEntry?>(null) }
    val genrePill = remember { FocusRequester() }
    val decadePill = remember { FocusRequester() }

    fun startAdd(e: HeroEntry, profile: Int?, folder: String?, season: Int?) {
        val id = e.tmdbId ?: return
        state.note = "Adding “${e.title}”…"
        scope.launch {
            state.note = try {
                client.addToArr(e.kind, id, profile, folder, season)
            } catch (x: Exception) {
                x.message ?: "Couldn't add it."
            }
        }
    }

    LaunchedEffect(state.note) {
        val shown = state.note
        if (shown != null && !shown.endsWith("…")) {
            delay(4500)
            if (state.note == shown) state.note = null
        }
    }

    // Genre names follow the movie / show switch (TMDB names them a little differently).
    LaunchedEffect(state.kind) {
        if (state.genresFor == state.kind) return@LaunchedEffect
        state.genres = try { client.discoverGenres(state.kind) } catch (e: Exception) { emptyList() }
        state.genresFor = state.kind
        if (state.genre != null && state.genre !in state.genres) state.genre = null
    }

    LaunchedEffect(state.kind, state.genre, state.decade, state.genresFor) {
        if (state.genresFor != state.kind) return@LaunchedEffect
        val key = "${state.kind}|${state.genre}|${state.decade}"
        if (state.loadedKey == key) return@LaunchedEffect
        state.loading = true
        state.note = null
        if (state.owned.isEmpty()) state.owned = try { client.serverIndex() } catch (e: Exception) { emptyMap() }
        val found = try { client.topRated(state.kind, state.genre, state.decade) } catch (e: Exception) { emptyList() }
        if (found == null) {
            state.results = emptyList()
            state.note = "TMDB isn't connected yet. Add its key in Fancyfin's settings on the web."
        } else {
            state.results = found.map { discoverEntry(it, state.owned) }
            state.loadedKey = key
        }
        state.focused = state.results.firstOrNull()
        state.focusRow = 0
        state.listState.scrollToItem(0)
        state.loading = false
    }
    LaunchedEffect(state.focusRow) { state.listState.animateScrollToItem(state.focusRow) }

    Shell(Tab.Discover, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize()) {
            HeroBackdrop(state.focused)
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
                BoxWithConstraints(contentModifier.fillMaxSize()) {
                    InfoBlock(state.focused, client, Modifier.align(Alignment.TopStart).height((maxHeight - ROW_VIEW_H).coerceAtLeast(120.dp)))
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(ROW_VIEW_H).clipToBounds()) {
                        Column(Modifier.padding(top = VIEW_HEADROOM, bottom = 4.dp)) {
                            Row(Modifier.padding(start = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Top 100", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Hold OK on a title that isn't on your server to add it", color = Palette.TextMuted, fontSize = 10.sp)
                            }
                            Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SortPill("Movies", state.kind == "movie", { state.kind = "movie" })
                                SortPill("TV Shows", state.kind == "tv", { state.kind = "tv" })
                                SortPill(if (state.genre == null) "Genre ▾" else "${state.genre} ▾", state.genre != null, { picker = "genre" }, Modifier.focusRequester(genrePill))
                                SortPill(if (state.decade == null) "Decade ▾" else "${state.decade}s ▾", state.decade != null, { picker = "decade" }, Modifier.focusRequester(decadePill))
                            }
                        }
                        when {
                            state.loading -> Text("Loading…", color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
                            state.results.isEmpty() -> Text(
                                state.note ?: "Nothing matched. Try a wider decade or genre.",
                                color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                            )
                            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), state = state.listState) {
                                itemsIndexed(state.results.chunked(9)) { r, chunk ->
                                    Row(Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        chunk.forEachIndexed { c, e ->
                                            DiscoverTile(
                                                e, rank = r * 9 + c + 1,
                                                onClick = { onTitle(titleRef(e)) },
                                                onLongClick = {
                                                    if (e.onServer != null) {
                                                        state.note = "“${e.title}” is already on your server."
                                                    } else if (e.tmdbId != null) {
                                                        val saved = UiSettings.savedArr(e.kind)
                                                        if (UiSettings.askArr || saved == null) arrFor = e else startAdd(e, saved.first, saved.second, null)
                                                    }
                                                },
                                                onFocused = {
                                                    state.focusRow = r
                                                    state.focused = e
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.loading.not()) state.note?.takeIf { state.results.isNotEmpty() || state.loadedKey != null }?.let {
                Text(
                    it, color = Palette.Text, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 20.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(UiSettings.corner.button))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            when (picker) {
                "genre" -> ChoicePicker(
                    title = "Genre",
                    options = listOf<String?>(null) + state.genres,
                    label = { it ?: "All genres" },
                    current = state.genre,
                    onPick = { state.genre = it; picker = null; scope.launch { delay(60); try { genrePill.requestFocus() } catch (e: Exception) { /* gone */ } } },
                    onClose = { picker = null; scope.launch { delay(60); try { genrePill.requestFocus() } catch (e: Exception) { /* gone */ } } },
                )
                "decade" -> ChoicePicker(
                    title = "Decade",
                    options = listOf<Int?>(null) + DECADES,
                    label = { if (it == null) "All time" else "${it}s" },
                    current = state.decade,
                    onPick = { state.decade = it; picker = null; scope.launch { delay(60); try { decadePill.requestFocus() } catch (e: Exception) { /* gone */ } } },
                    onClose = { picker = null; scope.launch { delay(60); try { decadePill.requestFocus() } catch (e: Exception) { /* gone */ } } },
                )
            }
            arrFor?.let { e ->
                val id = e.tmdbId
                if (id != null) {
                    ArrDialog(
                        app = if (e.kind == "movie") "Radarr" else "Sonarr",
                        kind = e.kind,
                        tmdbId = id,
                        client = client,
                        onAdd = { profile, folder, season, remember ->
                            if (remember) UiSettings.rememberArr(e.kind, profile, folder)
                            arrFor = null
                            startAdd(e, profile, folder, season)
                        },
                        onClose = { arrFor = null },
                    )
                }
            }
        }
    }
}

/** A poster with its rank in the top 100, and a tick when the title is already on your server. */
@Composable
private fun DiscoverTile(e: HeroEntry, rank: Int, onClick: () -> Unit, onLongClick: () -> Unit, onFocused: () -> Unit) {
    FocusBox(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        modifier = Modifier.width(92.dp).height(138.dp),
        edge = UiSettings.posterBorder,
        focusedScale = 1.06f,
    ) { _ ->
        Box(Modifier.fillMaxSize().background(Palette.GlassFill)) {
            e.poster?.let { AsyncImage(model = smallTmdb(it), contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Text(
                "$rank", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(Color(0xB3000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
            if (e.onServer != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).width(16.dp).height(16.dp).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.width(11.dp).height(11.dp))
                }
            }
        }
    }
}

/** A panel of choices in a grid; OK picks one. The first entry of a list with a null is "everything". */
@Composable
private fun <T> ChoicePicker(title: String, options: List<T?>, label: (T?) -> String, current: T?, onPick: (T?) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }
    val lead = options.indexOf(current).coerceAtLeast(0)
    val panel = RoundedCornerShape(UiSettings.corner.tile)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .background(Palette.Panel, panel)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.height(230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.withIndex().chunked(4).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { (i, o) ->
                            FocusBox(
                                onClick = { onPick(o) },
                                modifier = (if (i == lead) Modifier.focusRequester(first) else Modifier).width(125.dp).height(30.dp),
                                shape = RoundedCornerShape(50),
                                focusedScale = 1.05f,
                                edge = false,
                            ) { focused ->
                                val on = o == current
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(if (on) Palette.Accent else Color.White.copy(alpha = if (focused) 0.32f else 0.14f))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(label(o), color = if (on) Color.Black else Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
