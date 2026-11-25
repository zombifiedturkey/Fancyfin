package com.fancyfin.tv.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which tool is open on the edit page. */
private enum class EditPanel { None, Backdrop, Primary, Logo, Thumb, Match }

/**
 * Advanced mode: change a title's pictures, fix its match, or have the server re-read it.
 * Needs a Jellyfin account that is allowed to change the library.
 */
@Composable
fun EditScreen(item: Item, client: JellyfinClient) {
    var panel by remember { mutableStateOf(EditPanel.None) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(panel) {
        if (panel == EditPanel.None) {
            delay(80)
            try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        HeroBackdropUrl(client.backdrop(item, 960))
        Box(Modifier.fillMaxSize().background(Palette.Background.copy(alpha = 0.78f)))
        when (panel) {
            EditPanel.None -> Column(
                Modifier.padding(start = 48.dp, top = 36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("EDIT", color = Palette.TextMuted, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                Text(item.name, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Pictures", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                val w = Modifier.width(300.dp)
                GlassButton("Change background image", { panel = EditPanel.Backdrop }, w.focusRequester(first))
                GlassButton("Change poster", { panel = EditPanel.Primary }, w)
                GlassButton("Change logo", { panel = EditPanel.Logo }, w)
                GlassButton("Change thumbnail", { panel = EditPanel.Thumb }, w)
                Text("Match and metadata", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                GlassButton("Fix match", { panel = EditPanel.Match }, w)
                GlassButton(
                    "Refresh metadata",
                    {
                        note = "Asking the server…"
                        scope.launch {
                            note = try { client.refreshItem(item.id, false); "Refresh started. Updates appear in a minute or so." }
                            catch (e: Exception) { e.message ?: "Couldn't start the refresh." }
                        }
                    },
                    w,
                )
                GlassButton(
                    "Refresh and replace everything",
                    {
                        note = "Asking the server…"
                        scope.launch {
                            note = try { client.refreshItem(item.id, true); "Refresh started. This replaces all metadata and pictures." }
                            catch (e: Exception) { e.message ?: "Couldn't start the refresh." }
                        }
                    },
                    w,
                )
                note?.let { Text(it, color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
            EditPanel.Match -> MatchPanel(item, client, onDone = { note = it; panel = EditPanel.None }, onClose = { panel = EditPanel.None })
            else -> {
                val (type, label) = when (panel) {
                    EditPanel.Backdrop -> "Backdrop" to "Background image"
                    EditPanel.Primary -> "Primary" to "Poster"
                    EditPanel.Logo -> "Logo" to "Logo"
                    else -> "Thumb" to "Thumbnail"
                }
                ImagePanel(item, client, type, label, onDone = { note = it; panel = EditPanel.None }, onClose = { panel = EditPanel.None })
            }
        }
    }
}

@Composable
private fun ImagePanel(item: Item, client: JellyfinClient, type: String, label: String, onDone: (String) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    var all by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<JellyfinClient.RemoteImg>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(all) {
        images = null
        error = null
        try { images = client.remoteImages(item.id, type, all) } catch (e: Exception) { error = "Couldn't get pictures. " + (e.message ?: "") }
    }
    LaunchedEffect(Unit) {
        delay(80)
        try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }
    val columns = if (type == "Primary") 7 else 4
    val ratio = when (type) { "Primary" -> 2f / 3f; "Logo" -> 2.4f; else -> 16f / 9f }
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CHOOSE A ${label.uppercase()}  ·  ${item.name}", color = Palette.TextMuted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("All languages: " + if (all) "on" else "off", { all = !all }, Modifier.focusRequester(first))
            GlassButton("Back", onClose)
        }
        val list = images
        when {
            busy -> Text("Saving…", color = Palette.Text, fontSize = 13.sp)
            error != null -> Text(error!!, color = Palette.TextMuted, fontSize = 12.sp)
            list == null -> Text("Loading…", color = Palette.TextMuted, fontSize = 12.sp)
            list.isEmpty() -> Text("No pictures on offer. Try All languages.", color = Palette.TextMuted, fontSize = 12.sp)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list) { img ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        FocusBox(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        client.useRemoteImage(item.id, type, img)
                                        onDone("$label changed.")
                                    } catch (e: Exception) {
                                        busy = false
                                        error = "That didn't work: " + (e.message ?: "the server refused it") + ". Your account needs to be allowed to edit the library."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                        ) {
                            Box(Modifier.fillMaxSize().background(Palette.GlassFill))
                            AsyncImage(
                                model = img.thumb ?: img.url, contentDescription = null,
                                contentScale = if (type == "Logo") ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            listOfNotNull(
                                img.language?.uppercase(), if (img.width > 0) "${img.width}×${img.height}" else null,
                                img.provider.ifEmpty { null },
                            ).joinToString(" · "),
                            color = Palette.TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchPanel(item: Item, client: JellyfinClient, onDone: (String) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    var name by remember { mutableStateOf(item.name) }
    var year by remember { mutableStateOf(item.year?.toString() ?: "") }
    var replace by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<JellyfinClient.MatchResult>?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        scope.launch {
            status = "Searching…"
            results = null
            try {
                val found = client.matchSearch(item, name.trim(), year.trim().toIntOrNull())
                results = found
                status = if (found.isEmpty()) "No matches. Try a different title or year." else ""
            } catch (e: Exception) {
                results = emptyList()
                status = "Couldn't search. " + (e.message ?: "") + " Your account needs to be allowed to edit the library."
            }
        }
    }
    LaunchedEffect(Unit) { search() }

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FIX MATCH  ·  ${item.name}", color = Palette.TextMuted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(300.dp)) { Field("Title", name, { name = it }, wide = true) }
            Box(Modifier.width(90.dp)) { Field("Year", year, { year = it }, keyboard = KeyboardType.Number) }
            GlassButton("Search", { search() })
            GlassButton("Replace pictures: " + if (replace) "on" else "off", { replace = !replace })
            GlassButton("Back", onClose)
        }
        if (status.isNotEmpty()) Text(status, color = Palette.TextMuted, fontSize = 12.sp)
        if (busy) Text("Applying…", color = Palette.Text, fontSize = 13.sp)
        val list = results
        if (list != null && !busy) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp, start = 4.dp, end = 4.dp)) {
                items(list) { r ->
                    FocusBox(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    client.applyMatch(item.id, r, replace)
                                    onDone("Matched to ${r.name}${r.year?.let { " ($it)" } ?: ""}. The server is updating it.")
                                } catch (e: Exception) {
                                    busy = false
                                    status = "Couldn't apply that match. " + (e.message ?: "")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.02f,
                        edge = false,
                    ) { focused ->
                        Row(
                            Modifier.fillMaxWidth().background(if (focused) Palette.GlassFillStrong else Palette.GlassFill).padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.width(46.dp).height(69.dp).background(Palette.GlassFill)) {
                                if (r.image != null) AsyncImage(r.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(r.name + (r.year?.let { "  ($it)" } ?: ""), color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(listOf(r.provider, r.ids).filter { it.isNotEmpty() }.joinToString("   "), color = Palette.TextMuted, fontSize = 10.sp, maxLines = 1)
                                r.overview?.let { Text(it, color = Palette.TextMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}
