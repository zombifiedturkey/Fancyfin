package com.fancyfin.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import kotlinx.coroutines.delay

/** Type a title (centre button opens the keyboard) and the matching movies and shows appear. */
@Composable
fun SearchScreen(
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    var status by remember { mutableStateOf("Press the centre button on the box to type.") }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            status = "Press the centre button on the box to type."
            return@LaunchedEffect
        }
        delay(400)
        status = "Searching…"
        try {
            results = client.search(q)
            status = if (results.isEmpty()) "Nothing found for \"$q\"." else ""
        } catch (e: Exception) {
            status = "Couldn't reach your server."
        }
    }

    Shell(Tab.Search, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(contentModifier.fillMaxSize()) {
                Column(Modifier.padding(start = 48.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Search movies and shows", query, { query = it }, wide = true)
                    if (status.isNotEmpty()) Text(status, color = Palette.TextMuted, fontSize = 12.sp)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results) { item ->
                        ItemCard(item, client, 96.dp, 144.dp, false, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}
