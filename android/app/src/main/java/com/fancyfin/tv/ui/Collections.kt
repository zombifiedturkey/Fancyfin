package com.fancyfin.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.CollectionTile
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.delay

/** What the Collections page remembers while you look inside one and come back. */
class CollectionsState {
    var tiles by mutableStateOf<List<CollectionTile>>(emptyList())
    var status by mutableStateOf<String?>("Loading…")
    var scanned = false
    var filter by mutableStateOf(0)
    var query by mutableStateOf("")
    var lastKey: String? = null
    /** A collection to open as soon as it's found (from a pin in the menu). */
    var pendingOpen by mutableStateOf<String?>(null)
    val grid = LazyGridState()
}

private val FILTERS = listOf("All", "Franchises", "Studios")

/** Franchises and studios as a grid of poster collages, with how much of each you own. */
@Composable
fun CollectionsScreen(
    client: JellyfinClient,
    state: CollectionsState,
    onOpen: (CollectionTile) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (state.scanned) return@LaunchedEffect
        try {
            client.collectionTiles { tiles, status ->
                state.tiles = tiles
                state.status = status
            }
            state.scanned = true
        } catch (e: Exception) {
            if (state.tiles.isEmpty()) state.status = "Couldn't reach your server."
        }
    }

    LaunchedEffect(state.tiles.size, state.pendingOpen, state.scanned) {
        val key = state.pendingOpen ?: return@LaunchedEffect
        val tile = state.tiles.firstOrNull { it.key == key }
        if (tile != null) {
            state.pendingOpen = null
            onOpen(tile)
        } else if (state.scanned) {
            state.pendingOpen = null
        }
    }

    val requesters = remember { HashMap<String, FocusRequester>() }
    LaunchedEffect(Unit) {
        delay(300)
        try { state.lastKey?.let { requesters[it]?.requestFocus() } } catch (e: Exception) { /* not on screen */ }
    }

    val shown = state.tiles.filter {
        (state.filter == 0 || (state.filter == 1 && it.kind == "franchise") || (state.filter == 2 && it.kind == "studio")) &&
            (state.query.isBlank() || it.name.contains(state.query.trim(), ignoreCase = true))
    }
    val franchises = state.tiles.count { it.kind == "franchise" }
    val studios = state.tiles.count { it.kind == "studio" }

    Shell(Tab.Collections, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize()) {
            Backdrop(null)
            Column(contentModifier.fillMaxSize()) {
                Column(Modifier.padding(start = 40.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Collections", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.tiles.size} collections" + (state.status?.let { "  ·  $it" } ?: ""),
                            color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FILTERS.forEachIndexed { i, label ->
                            val n = when (i) { 0 -> state.tiles.size; 1 -> franchises; else -> studios }
                            GlassButton("$label  $n", { state.filter = i }, primary = state.filter == i, compact = true)
                        }
                        Box(Modifier.padding(start = 10.dp)) {
                            Field("Filter collections", state.query, { state.query = it }, wide = false)
                        }
                    }
                }
                LazyVerticalGrid(
                    state = state.grid,
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 16.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.key }) { tile ->
                        CollectionCard(
                            tile,
                            Modifier.focusRequester(requesters.getOrPut(tile.key) { FocusRequester() }),
                            onClick = { state.lastKey = tile.key; onOpen(tile) },
                            onFocused = { state.lastKey = tile.key },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(tile: CollectionTile, modifier: Modifier, onClick: () -> Unit, onFocused: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FocusBox(
            onClick = onClick,
            onFocused = onFocused,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f),
            edge = UiSettings.posterBorder,
        ) { _ ->
            Box(Modifier.fillMaxSize().background(Palette.GlassFill)) {
                Column(Modifier.fillMaxSize()) {
                    for (row in 0..1) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (col in 0..1) {
                                val url = tile.posters.getOrNull(row * 2 + col)
                                Box(Modifier.weight(1f).fillMaxHeight().background(Color(0x22FFFFFF))) {
                                    if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                Text(
                    tile.kind.uppercase(), color = Palette.Text, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(Color(0x99000000), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Text(tile.name, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val total = tile.total
        Text(
            if (total != null && total > tile.owned) "${tile.owned} of $total films" else "${tile.owned} films",
            color = Palette.TextMuted, fontSize = 11.sp,
        )
        if (total != null && total > tile.owned) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth((tile.owned.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(Palette.Accent, RoundedCornerShape(50)))
            }
        }
    }
}

private class Entry(val title: String, val poster: String?, val item: Item?, val ref: TitleRef)

/** What's inside one franchise or studio: what you own, and for a franchise the films you don't have yet. */
@Composable
fun CollectionScreen(
    tile: CollectionTile,
    client: JellyfinClient,
    onOpenItem: (Item) -> Unit,
    onOpenTitle: (TitleRef) -> Unit,
) {
    var entries by remember(tile.key) {
        mutableStateOf(tile.items.map { Entry(it.name, client.poster(it, 300), it, TitleRef.of(it)) })
    }
    LaunchedEffect(tile.key) {
        val id = tile.collectionId ?: return@LaunchedEffect
        try {
            val index = client.serverIndex()
            val parts = client.franchise(id)
            if (parts.isNotEmpty()) {
                entries = parts.map { d ->
                    val owned = index["movie:${d.tmdbId}"]
                    Entry(d.title, owned?.let { client.poster(it, 300) } ?: d.poster, owned, TitleRef("movie", d.tmdbId, owned, d.title, d.backdrop, d.poster))
                }
            }
        } catch (e: Exception) {
            // Keep the films you own.
        }
    }
    Box(Modifier.fillMaxSize()) {
        Backdrop(null)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 22.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tile.kind.uppercase(), color = Palette.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(tile.name, color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    val total = tile.total
                    Text(
                        if (total != null && total > tile.owned) "${tile.owned} of $total films on your server" else "${tile.owned} films",
                        color = Palette.TextMuted, fontSize = 12.sp,
                    )
                }
                GlassButton(
                    if (Pins.hasCollection(tile.key)) "Unpin from menu" else "Pin to menu",
                    { Pins.toggleCollection(tile.key, tile.name) },
                    compact = true,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 16.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.ref.tmdbId?.toString() ?: it.item?.id ?: it.title }) { e ->
                    PosterTile(
                        url = e.poster,
                        title = e.title,
                        onServer = e.item != null,
                        current = false,
                        onClick = { if (e.item != null) onOpenItem(e.item) else onOpenTitle(e.ref) },
                        width = 104.dp,
                        height = 156.dp,
                        modifier = if (e.item == null) Modifier.alpha(0.6f) else Modifier,
                    )
                }
            }
        }
    }
}
