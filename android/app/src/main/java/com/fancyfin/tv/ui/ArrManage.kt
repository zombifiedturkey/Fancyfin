package com.fancyfin.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun gb(bytes: Long): String = if (bytes >= 1L shl 30) "%.1f GB".format(bytes / (1L shl 30).toDouble()) else "${bytes shr 20} MB"

/** Manage a title in Radarr (movies) or Sonarr (shows): monitoring, quality, searching, picking a release, removing. */
@Composable
fun ArrPanel(item: Item, client: JellyfinClient, onClose: () -> Unit) {
    BackHandler { onClose() }
    val kind = if (item.isMovie) "movie" else "series"
    val app = if (item.isMovie) "Radarr" else "Sonarr"
    var entry by remember { mutableStateOf<JSONObject?>(null) }
    var profiles by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var status by remember { mutableStateOf("Asking $app…") }
    var note by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<Int?>(null) }   // season number (0 = whole title) whose releases are being chosen
    var confirmRemove by remember { mutableStateOf(false) }
    var deleteFiles by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }

    fun load() {
        scope.launch {
            try {
                val tmdb = item.tmdbId?.toIntOrNull() ?: throw IllegalStateException("This title has no TMDB id, so $app can't be asked about it.")
                profiles = client.arrProfiles(kind)
                entry = client.arrFind(kind, tmdb)
                status = if (entry == null) "$app doesn't have this title." else ""
            } catch (e: Exception) {
                status = "Couldn't reach $app. " + (e.message ?: "") + " Check it's connected in Settings, and that the Fancyfin container is up to date."
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(entry) {
        delay(80)
        try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }

    fun save(change: (JSONObject) -> Unit) {
        val e = entry ?: return
        val copy = JSONObject(e.toString())
        change(copy)
        note = "Saving…"
        scope.launch {
            try { client.arrSave(kind, copy); entry = copy; note = "Saved." }
            catch (ex: Exception) { note = "Couldn't save. " + (ex.message ?: "") }
        }
    }

    val e = entry
    val id = e?.optInt("id") ?: 0
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${app.uppercase()}  ·  ${item.name}", color = Palette.TextMuted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
        if (status.isNotEmpty()) {
            Text(status, color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.width(620.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Back", onClose, Modifier.focusRequester(first))
                val tmdb = item.tmdbId?.toIntOrNull()
                if (e == null && tmdb != null && status.endsWith("title.")) {
                    GlassButton("Add to $app", {
                        status = "Adding…"
                        scope.launch {
                            try { note = client.addToArr(kind, tmdb); load() } catch (ex: Exception) { status = ex.message ?: "Couldn't add it." }
                        }
                    }, primary = true)
                }
            }
        }
        if (e != null) {
            val profileName = profiles.firstOrNull { it.first == e.optInt("qualityProfileId") }?.second ?: "?"
            val monitored = e.optBoolean("monitored")
            val line = if (item.isMovie) {
                (if (e.optBoolean("hasFile")) "On disk" else "No file yet") + "  ·  " + profileName + "  ·  " + (if (monitored) "Monitored" else "Not monitored")
            } else {
                val st = e.optJSONObject("statistics")
                "${st?.optInt("episodeFileCount") ?: 0} of ${st?.optInt("episodeCount") ?: 0} episodes on disk  ·  $profileName  ·  " + (if (monitored) "Monitored" else "Not monitored")
            }
            Text(line, color = Palette.Text, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                GlassButton(
                    "Search now",
                    {
                        note = "Searching…"
                        scope.launch {
                            try {
                                if (item.isMovie) client.arrCommand(kind, "MoviesSearch", JSONObject().put("movieIds", org.json.JSONArray().put(id)))
                                else client.arrCommand(kind, "SeriesSearch", JSONObject().put("seriesId", id))
                                note = "$app is searching. Check Downloads in a minute."
                            } catch (ex: Exception) { note = "Couldn't start a search. " + (ex.message ?: "") }
                        }
                    },
                    Modifier.focusRequester(first), compact = true,
                )
                if (item.isMovie) GlassButton("Pick a release…", { picker = 0 }, compact = true)
                GlassButton("Monitored: " + if (monitored) "on" else "off", { save { it.put("monitored", !monitored) } }, compact = true)
                GlassButton(
                    "Quality: $profileName  ▸",
                    {
                        if (profiles.isNotEmpty()) {
                            val at = profiles.indexOfFirst { it.first == e.optInt("qualityProfileId") }
                            val next = profiles[(at + 1) % profiles.size].first
                            save { it.put("qualityProfileId", next) }
                        }
                    },
                    compact = true,
                )
                GlassButton("Remove from $app…", { confirmRemove = true }, compact = true)
            }
            if (!item.isMovie) {
                Text("Seasons", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                val seasons = e.optJSONArray("seasons")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(190.dp)) {
                    val list = (0 until (seasons?.length() ?: 0)).map { seasons!!.getJSONObject(it) }.filter { it.optInt("seasonNumber") > 0 }
                    items(list) { sObj ->
                        val n = sObj.optInt("seasonNumber")
                        val st = sObj.optJSONObject("statistics")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Season $n", color = Palette.Text, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                            Text("${st?.optInt("episodeFileCount") ?: 0}/${st?.optInt("episodeCount") ?: 0}", color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.width(50.dp))
                            GlassButton(
                                if (sObj.optBoolean("monitored")) "Monitored" else "Not monitored",
                                {
                                    save { copy ->
                                        val arr = copy.getJSONArray("seasons")
                                        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optInt("seasonNumber") == n) arr.getJSONObject(i).put("monitored", !sObj.optBoolean("monitored"))
                                    }
                                },
                                primary = sObj.optBoolean("monitored"), compact = true,
                            )
                            GlassButton(
                                "Search",
                                {
                                    note = "Searching season $n…"
                                    scope.launch {
                                        try { client.arrCommand(kind, "SeasonSearch", JSONObject().put("seriesId", id).put("seasonNumber", n)); note = "$app is searching season $n." }
                                        catch (ex: Exception) { note = "Couldn't start a search. " + (ex.message ?: "") }
                                    }
                                },
                                compact = true,
                            )
                            GlassButton("Pick a release…", { picker = n }, compact = true)
                        }
                    }
                }
            }
            note?.let { Text(it, color = Palette.TextMuted, fontSize = 12.sp) }
        }
    }

    picker?.let { season ->
        ReleasePicker(kind, id, if (item.isMovie) null else season, client, onGrabbed = { picker = null; note = it }, onClose = { picker = null })
    }
    if (confirmRemove) {
        val panel = RoundedCornerShape(UiSettings.corner.tile)
        BackHandler { confirmRemove = false }
        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(380.dp).clip(panel).background(Palette.Panel).border(1.dp, Palette.GlassEdge, panel)
                    .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Remove ${item.name} from $app?", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("It stops being monitored and downloaded. It stays in Jellyfin unless you delete it there too.", color = Palette.TextMuted, fontSize = 11.sp)
                GlassButton("Also delete the files: " + if (deleteFiles) "yes" else "no", { deleteFiles = !deleteFiles }, Modifier.fillMaxWidth(), compact = true)
                GlassButton(
                    "Remove it",
                    {
                        confirmRemove = false
                        scope.launch {
                            try { client.arrRemove(kind, id, deleteFiles); entry = null; status = "Removed from $app."; note = null }
                            catch (ex: Exception) { note = "Couldn't remove it. " + (ex.message ?: "") }
                        }
                    },
                    Modifier.fillMaxWidth(), compact = true,
                )
                GlassButton("Cancel", { confirmRemove = false }, Modifier.fillMaxWidth().focusRequester(first), primary = true, compact = true)
            }
        }
    }
}

/** The releases the indexers found. Pick one and the download client gets it. Ones Radarr/Sonarr would reject are marked. */
@Composable
private fun ReleasePicker(kind: String, id: Int, season: Int?, client: JellyfinClient, onGrabbed: (String) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    var releases by remember { mutableStateOf<List<JellyfinClient.ArrRelease>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { releases = client.arrReleases(kind, id, season) } catch (e: Exception) { error = "Couldn't search the indexers. " + (e.message ?: "") }
    }
    val panel = RoundedCornerShape(UiSettings.corner.tile)
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(760.dp).height(420.dp).clip(panel).background(Palette.Panel).border(1.dp, Palette.GlassEdge, panel)
                .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Choose a release" + (season?.let { " for season $it" } ?: ""), color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val list = releases
            when {
                busy -> Text("Sending it to your download client…", color = Palette.Text, fontSize = 12.sp)
                error != null -> Text(error!!, color = Palette.TextMuted, fontSize = 12.sp)
                list == null -> Text("Searching your indexers. This can take half a minute…", color = Palette.TextMuted, fontSize = 12.sp)
                list.isEmpty() -> Text("No releases found.", color = Palette.TextMuted, fontSize = 12.sp)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    items(list) { r ->
                        FocusBox(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try { client.arrGrab(kind, r); onGrabbed("Sent to your download client: ${r.title.take(60)}") }
                                    catch (e: Exception) { busy = false; error = "Couldn't grab that one. " + (e.message ?: "") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.02f,
                            edge = false,
                        ) { focused ->
                            Column(Modifier.fillMaxWidth().background(if (focused) Palette.GlassFillStrong else Palette.GlassFill).padding(horizontal = 12.dp, vertical = 7.dp)) {
                                Text(r.title, color = if (r.rejected) Palette.TextMuted else Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        r.quality.ifEmpty { null }, gb(r.sizeBytes), r.seeders?.let { "$it seeders" }, r.indexer.ifEmpty { null },
                                        if (r.ageDays > 0) "${r.ageDays}d old" else null, r.reason?.let { "Rejected: $it" },
                                    ).joinToString("  ·  "),
                                    color = Palette.TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Everything Radarr and Sonarr are downloading right now. OK on one offers to remove it. */
@Composable
fun DownloadsScreen(client: JellyfinClient, onTab: (Tab) -> Unit, onSignOut: () -> Unit) {
    var queue by remember { mutableStateOf<List<JellyfinClient.QueueItem>?>(null) }
    var picked by remember { mutableStateOf<JellyfinClient.QueueItem?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(picked == null) {
        while (picked == null) {
            queue = client.arrQueue()
            delay(5000)
        }
    }
    Shell(Tab.Downloads, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(contentModifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Downloads", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                note?.let { Text(it, color = Palette.TextMuted, fontSize = 11.sp) }
                val list = queue
                when {
                    list == null -> Text("Asking Radarr and Sonarr…", color = Palette.TextMuted, fontSize = 12.sp)
                    list.isEmpty() -> Text("Nothing is downloading.", color = Palette.TextMuted, fontSize = 12.sp)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(list) { q ->
                            FocusBox(onClick = { picked = q }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), focusedScale = 1.02f, edge = false) { focused ->
                                Column(Modifier.fillMaxWidth().background(if (focused) Palette.GlassFillStrong else Palette.GlassFill).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(q.title, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(listOfNotNull(q.status, q.timeLeft).joinToString("  ·  "), color = Palette.TextMuted, fontSize = 10.sp)
                                    }
                                    ProgressLine(q.progress)
                                    q.error?.let { Text(it, color = Palette.TextMuted, fontSize = 9.sp, maxLines = 1) }
                                }
                            }
                        }
                    }
                }
            }
            picked?.let { q ->
                BackHandler { picked = null }
                val panel = RoundedCornerShape(UiSettings.corner.tile)
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ } }
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.width(360.dp).clip(panel).background(Palette.Panel).border(1.dp, Palette.GlassEdge, panel)
                            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(q.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        GlassButton("Remove from the queue", { remove(scope, client, q, false) { note = it; picked = null } }, Modifier.fillMaxWidth().focusRequester(first), compact = true)
                        GlassButton("Remove and don't pick it again", { remove(scope, client, q, true) { note = it; picked = null } }, Modifier.fillMaxWidth(), compact = true)
                        GlassButton("Cancel", { picked = null }, Modifier.fillMaxWidth(), primary = true, compact = true)
                    }
                }
            }
        }
    }
}

private fun remove(scope: kotlinx.coroutines.CoroutineScope, client: JellyfinClient, q: JellyfinClient.QueueItem, blocklist: Boolean, done: (String) -> Unit) {
    scope.launch {
        done(try { client.arrRemoveFromQueue(q.kind, q.id, blocklist); "Removed ${q.title}." } catch (e: Exception) { "Couldn't remove it. " + (e.message ?: "") })
    }
}
