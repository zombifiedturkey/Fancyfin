package com.fancyfin.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient

private class SortOption(val label: String, val by: String, val descending: Boolean)

private class ShowOption(val label: String, val extra: String)

private val SORTS = listOf(
    SortOption("A–Z", "SortName", false),
    SortOption("Recently added", "DateCreated", true),
    SortOption("Release year", "PremiereDate", true),
    SortOption("Rating", "CommunityRating", true),
)

private val MOVIE_SHOWS = listOf(
    ShowOption("All", ""),
    ShowOption("4K only", "&Is4K=true"),
    ShowOption("Unwatched", "&Filters=IsUnplayed"),
    ShowOption("Favourites", "&Filters=IsFavorite"),
)

private val SHOW_SHOWS = listOf(
    ShowOption("All", ""),
    ShowOption("Unwatched", "&Filters=IsUnplayed"),
    ShowOption("Favourites", "&Filters=IsFavorite"),
)

/** A poster grid of every movie or every show, which you can sort and narrow down. */
@Composable
fun LibraryScreen(
    tab: Tab,
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    val shows = if (tab == Tab.AllMovies) MOVIE_SHOWS else SHOW_SHOWS
    var sortIndex by remember(tab) { mutableStateOf(0) }
    var showIndex by remember(tab) { mutableStateOf(0) }
    var items by remember(tab) { mutableStateOf<List<Item>>(emptyList()) }
    var status by remember(tab) { mutableStateOf("Loading…") }

    LaunchedEffect(tab, sortIndex, showIndex) {
        status = "Loading…"
        try {
            val sort = SORTS[sortIndex]
            items = client.library(
                if (tab == Tab.AllMovies) "Movie" else "Series",
                sort.by,
                sort.descending,
                shows[showIndex].extra,
            )
            status = if (items.isEmpty()) "Nothing matches." else ""
        } catch (e: Exception) {
            status = "Couldn't reach your server."
        }
    }

    Shell(tab, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(contentModifier.fillMaxSize()) {
                Row(
                    Modifier.padding(start = 48.dp, top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassButton("Sort: " + SORTS[sortIndex].label, { sortIndex = (sortIndex + 1) % SORTS.size })
                    GlassButton("Show: " + shows[showIndex].label, { showIndex = (showIndex + 1) % shows.size })
                    val count = if (status.isEmpty()) "${items.size} titles" else status
                    Text(count, color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items) { item ->
                        ItemCard(
                            item = item,
                            client = client,
                            width = 96.dp,
                            height = 144.dp,
                            landscape = false,
                            onClick = { onOpen(item) },
                        )
                    }
                }
            }
        }
    }
}
