package com.fancyfin.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient

/** A poster grid of every movie or every show. */
@Composable
fun LibraryScreen(
    tab: Tab,
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    var items by remember(tab) { mutableStateOf<List<Item>>(emptyList()) }
    var status by remember(tab) { mutableStateOf("Loading…") }

    LaunchedEffect(tab) {
        try {
            items = client.library(if (tab == Tab.Movies) "Movie" else "Series")
            status = if (items.isEmpty()) "Nothing here yet." else ""
        } catch (e: Exception) {
            status = "Couldn't reach your server."
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(tab, onTab, onSignOut)
            if (status.isNotEmpty()) {
                Text(status, color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 32.dp),
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
