package com.fancyfin.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun problems(i: Item): List<String> {
    val out = ArrayList<String>()
    if (i.tmdbId == null) out.add("No match")
    if (i.primaryTag == null) out.add("No poster")
    if (i.overview == null) out.add("No description")
    if (i.backdropTag == null) out.add("No background")
    return out
}

private val FILTERS = listOf("All issues", "No match", "No poster", "No description", "No background")

/** Titles that are missing a match, poster, description or background. OK on one opens its Edit page. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AttentionScreen(client: JellyfinClient, onEdit: (Item) -> Unit, onTab: (Tab) -> Unit, onSignOut: () -> Unit) {
    var all by remember { mutableStateOf<List<Item>?>(null) }
    var status by remember { mutableStateOf("Checking your library…") }
    var filter by remember { mutableStateOf(0) }
    var tasksOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            all = client.library("Movie,Series").filter { problems(it).isNotEmpty() }
            status = ""
        } catch (e: Exception) {
            status = "Couldn't reach your server."
        }
    }
    val list = all
    val shown = remember(list, filter) {
        if (list == null) emptyList() else if (filter == 0) list else list.filter { FILTERS[filter] in problems(it) }
    }

    Shell(Tab.Attention, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(contentModifier.fillMaxSize().padding(start = 48.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Needs attention", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTERS.forEachIndexed { i, name ->
                        val count = if (list == null) null else if (i == 0) list.size else list.count { name in problems(it) }
                        GlassButton(name + (count?.let { "  $it" } ?: ""), { filter = i }, primary = i == filter, compact = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        "Scan all libraries",
                        {
                            note = "Asking the server…"
                            scope.launch { note = try { client.scanLibraries(); "Scan started." } catch (e: Exception) { e.message ?: "Couldn't start the scan." } }
                        },
                        compact = true,
                    )
                    GlassButton("Server tasks", { tasksOpen = true }, compact = true)
                }
                note?.let { Text(it, color = Palette.TextMuted, fontSize = 11.sp) }
                if (status.isNotEmpty()) Text(status, color = Palette.TextMuted, fontSize = 12.sp)
                else if (shown.isEmpty()) Text("Nothing here. Everything looks good.", color = Palette.TextMuted, fontSize = 12.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    contentPadding = PaddingValues(top = 12.dp, end = 48.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown) { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            ItemCard(item, client, 96.dp, 144.dp, false, onClick = { onEdit(item) })
                            Text(item.name, color = Palette.Text, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(problems(item).joinToString(", "), color = Palette.TextMuted, fontSize = 8.sp, maxLines = 2)
                        }
                    }
                }
            }
            if (tasksOpen) ServerTasks(client) { tasksOpen = false }
        }
    }
}

/** What the Jellyfin server is doing in the background, with a button to run a task now. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ServerTasks(client: JellyfinClient, onClose: () -> Unit) {
    BackHandler { onClose() }
    var tasks by remember { mutableStateOf<List<JellyfinClient.ServerTask>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            try { tasks = client.tasks(); error = null } catch (e: Exception) { error = "Couldn't read the server's tasks. Your account needs to be an administrator." }
            delay(3000)
        }
    }
    val panel = RoundedCornerShape(UiSettings.corner.tile)
    Box(Modifier.fillMaxSize().background(Color0), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).height(400.dp).clip(panel).background(Palette.Panel).border(1.dp, Palette.GlassEdge, panel)
                .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Server tasks", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val list = tasks
            when {
                error != null -> Text(error!!, color = Palette.TextMuted, fontSize = 12.sp)
                list == null -> Text("Loading…", color = Palette.TextMuted, fontSize = 12.sp)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    items(list) { t ->
                        val running = t.state == "Running"
                        FocusBox(
                            onClick = { if (!running) scope.launch { try { client.runTask(t.id) } catch (e: Exception) { /* shown on the next refresh */ } } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.02f,
                            edge = false,
                        ) { focused ->
                            Row(
                                Modifier.fillMaxWidth().background(if (focused) Palette.GlassFillStrong else Palette.GlassFill).padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(t.name, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(t.category + (t.lastResult?.let { "  ·  last: $it" } ?: ""), color = Palette.TextMuted, fontSize = 9.sp, maxLines = 1)
                                }
                                Text(
                                    if (running) "Running " + (t.progress?.let { "${it.toInt()}%" } ?: "") else "Run now",
                                    color = if (running) Palette.Accent else Palette.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0x99000000)
