package com.fancyfin.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient

private class HomeRow(val title: String, val items: List<Item>, val landscape: Boolean)

enum class Tab(val label: String) { Home("Home"), Movies("Movies"), Shows("TV Shows"), Search("Search") }

/** The big picture behind everything, faded into black so text on top stays readable. */
@Composable
fun Backdrop(url: String?) {
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Crossfade(targetState = url, label = "backdrop") { target ->
            if (target != null) {
                AsyncImage(
                    model = target,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Dark on the left where the text sits, and dark at the bottom where the rows sit.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF207080B), 0.55f to Color(0x9907080B), 1f to Color(0x2207080B)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xF207080B)),
            ),
        )
    }
}

@Composable
fun TopBar(current: Tab, onTab: (Tab) -> Unit, onSignOut: () -> Unit) {
    Row(
        Modifier.padding(start = 48.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Fancyfin", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Tab.values().forEach { tab ->
            GlassButton(tab.label, { onTab(tab) }, primary = tab == current)
        }
        Spacer(Modifier.width(16.dp))
        GlassButton("Sign out", onSignOut)
    }
}

@Composable
fun HomeScreen(
    client: JellyfinClient,
    onOpen: (Item) -> Unit,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
) {
    var rows by remember { mutableStateOf<List<HomeRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(Unit) {
        try {
            val out = ArrayList<HomeRow>()
            val resume = client.resume()
            if (resume.isNotEmpty()) out.add(HomeRow("Continue watching", resume, true))
            val next = client.nextUp().filter { n -> resume.none { it.id == n.id } }
            if (next.isNotEmpty()) out.add(HomeRow("Next up", next, true))
            val movies = client.latest(type = "Movie")
            if (movies.isNotEmpty()) out.add(HomeRow("Recently added movies", movies, false))
            val shows = client.latest(type = "Series")
            if (shows.isNotEmpty()) out.add(HomeRow("Recently added shows", shows, false))
            rows = out
            focused = out.firstOrNull()?.items?.firstOrNull()
        } catch (e: Exception) {
            failed = true
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(focused?.let { client.backdrop(it) })

        Column(Modifier.fillMaxSize()) {
            TopBar(Tab.Home, onTab, onSignOut)
            InfoBlock(focused, client, Modifier.padding(start = 48.dp, top = 20.dp).width(420.dp).height(150.dp))

            when {
                loading -> Text("Loading…", color = Palette.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
                failed -> Text("Couldn't reach your server.", color = Palette.Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
                else -> RowsList(rows, client, onOpen) { focused = it }
            }
        }
    }
}

@Composable
private fun InfoBlock(item: Item?, client: JellyfinClient, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        if (item == null) return@Column
        val title = item.seriesName ?: item.name
        val logo = client.logo(item)
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.height(54.dp).width(260.dp),
            )
        } else {
            Text(title, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (item.isEpisode) {
            Text(item.name, color = Palette.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        MetaLine(item, Modifier.padding(top = 4.dp))
        item.overview?.let {
            Text(it, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RowsList(rows: List<HomeRow>, client: JellyfinClient, onOpen: (Item) -> Unit, onFocus: (Item) -> Unit) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp),
    ) {
        items(rows) { row ->
            Column {
                Text(
                    row.title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                ) {
                    items(row.items) { item ->
                        ItemCard(
                            item = item,
                            client = client,
                            width = if (row.landscape) 160.dp else 100.dp,
                            height = if (row.landscape) 90.dp else 150.dp,
                            landscape = row.landscape,
                            onClick = { onOpen(item) },
                            onFocused = { onFocus(item) },
                        )
                    }
                }
            }
        }
    }
}
