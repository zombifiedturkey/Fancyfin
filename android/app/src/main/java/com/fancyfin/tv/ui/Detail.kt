package com.fancyfin.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.launch

/** Everything about one movie, show or episode, with the buttons to play it. */
@Composable
fun DetailScreen(
    start: Item,
    client: JellyfinClient,
    onPlay: (Item) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf(start) }
    var seasons by remember { mutableStateOf<List<Item>>(emptyList()) }
    var season by remember { mutableStateOf<Item?>(null) }
    var episodes by remember { mutableStateOf<List<Item>>(emptyList()) }
    var similar by remember { mutableStateOf<List<Item>>(emptyList()) }

    LaunchedEffect(start.id) {
        try {
            item = client.item(start.id, start.duplicateIds)
        } catch (e: Exception) {
            // Keep what we were given.
        }
        try {
            similar = client.similar(start.id)
        } catch (e: Exception) {
            similar = emptyList()
        }
        if (item.isSeries) {
            try {
                seasons = client.seasons(item.id)
                season = seasons.firstOrNull { (it.unplayedCount ?: 1) > 0 } ?: seasons.firstOrNull()
            } catch (e: Exception) {
                seasons = emptyList()
            }
        }
    }

    LaunchedEffect(season?.id) {
        val s = season ?: return@LaunchedEffect
        try {
            episodes = client.episodes(item.id, s.id)
        } catch (e: Exception) {
            episodes = emptyList()
        }
    }

    // What the Play button starts: this item, or for a show the episode you're up to.
    val target: Item? = when {
        item.isSeries -> episodes.firstOrNull { it.positionTicks > 0 && !it.played }
            ?: episodes.firstOrNull { !it.played }
            ?: episodes.firstOrNull()
        else -> item
    }
    val resuming = target != null && target.positionTicks > 0 && !target.played

    Box(Modifier.fillMaxSize()) {
        Backdrop(client.backdrop(item))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 36.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(Modifier.padding(start = 48.dp).width(460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val logo = client.logo(item)
                    if (logo != null) {
                        AsyncImage(
                            model = logo,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.height(70.dp).width(300.dp),
                        )
                    } else {
                        Text(
                            item.seriesName ?: item.name,
                            color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.isEpisode) Text(item.name, color = Palette.Text, fontSize = 16.sp)
                    MetaLine(item)
                    if (item.genres.isNotEmpty()) {
                        Text(item.genres.take(4).joinToString(", "), color = Palette.TextMuted, fontSize = 12.sp)
                    }
                    item.tagline?.let { Text(it, color = Palette.Accent, fontSize = 12.sp) }
                    item.overview?.let {
                        Text(it, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (target != null) {
                        GlassButton(
                            label = (if (resuming) "Resume" else "Play") + (target.episodeLabel?.let { " $it" } ?: ""),
                            primary = true,
                            onClick = { onPlay(target) },
                        )
                    }
                    GlassButton(
                        label = if (item.played) "Watched ✓" else "Mark watched",
                        onClick = {
                            val next = !item.played
                            scope.launch {
                                try {
                                    client.setPlayed(item.id, next)
                                    item = client.item(item.id, item.duplicateIds)
                                } catch (e: Exception) {
                                    // Leave the button as it was.
                                }
                            }
                        },
                    )
                    GlassButton(
                        label = if (item.favorite) "★ Favourite" else "☆ Favourite",
                        onClick = {
                            val next = !item.favorite
                            scope.launch {
                                try {
                                    client.setFavorite(item.id, next)
                                    item = client.item(item.id, item.duplicateIds)
                                } catch (e: Exception) {
                                    // Leave the button as it was.
                                }
                            }
                        },
                    )
                }
            }

            if (item.isSeries && seasons.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                    ) {
                        items(seasons) { s ->
                            GlassButton(s.name, onClick = { season = s }, primary = s.id == season?.id)
                        }
                    }
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                    ) {
                        items(episodes) { ep ->
                            Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ItemCard(ep, client, 160.dp, 90.dp, true, onClick = { onPlay(ep) })
                                Text(
                                    (ep.indexNumber?.let { "$it. " } ?: "") + ep.name,
                                    color = Palette.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (similar.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            "More like this", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                        ) {
                            items(similar) { s ->
                                ItemCard(s, client, 100.dp, 150.dp, false, onClick = { onOpen(s) })
                            }
                        }
                    }
                }
            }
        }
    }
}
