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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.delay

/**
 * Choose where a title goes in Radarr / Sonarr: quality profile, folder and, for shows, the whole show
 * or one season. "Add and remember" saves the profile and folder and stops asking (Settings turns it back on).
 */
@Composable
fun ArrDialog(
    app: String,
    kind: String,
    tmdbId: Int,
    client: JellyfinClient,
    onAdd: (profile: Int, folder: String, season: Int?, remember: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    var options by remember { mutableStateOf<JellyfinClient.ArrOptions?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<Int?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            val o = client.arrOptions(kind, tmdbId)
            val saved = UiSettings.savedArr(kind)
            profile = saved?.first?.takeIf { id -> o.profiles.any { it.first == id } } ?: o.profiles.first().first
            folder = saved?.second?.takeIf { o.folders.contains(it) } ?: o.folders.first()
            options = o
        } catch (e: Exception) {
            error = e.message ?: "Couldn't reach $app."
        }
    }
    LaunchedEffect(options) {
        if (options != null) {
            delay(80)
            try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
        }
    }

    val panel = RoundedCornerShape(UiSettings.corner.tile)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(640.dp)
                .background(Color(0xF2121520), panel)
                .border(1.dp, Palette.GlassEdge, panel)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add to $app", color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val o = options
            when {
                error != null -> {
                    Text(error!!, color = Palette.Accent, fontSize = 12.sp)
                    GlassButton("Close", onClose, compact = true, modifier = Modifier.focusRequester(first))
                    LaunchedEffect(Unit) { delay(80); try { first.requestFocus() } catch (e: Exception) { } }
                }
                o == null -> Text("Loading…", color = Palette.TextMuted, fontSize = 12.sp)
                else -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        @Composable
                        fun choices(title: String, labels: List<String>, selected: Int, lead: Boolean, pick: (Int) -> Unit) {
                            Column(Modifier.width(190.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title, color = Palette.TextMuted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
                                labels.forEachIndexed { i, label ->
                                    FocusBox(
                                        onClick = { pick(i) },
                                        modifier = (if (lead && i == selected) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                                        shape = RoundedCornerShape(UiSettings.corner.button.coerceAtLeast(6.dp)),
                                        focusedScale = 1.03f,
                                        edge = false,
                                    ) { focused ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .background(if (focused) Palette.GlassFillStrong else Palette.GlassFill)
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(if (i == selected) "✓" else " ", color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(label, color = Palette.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        choices("QUALITY PROFILE", o.profiles.map { it.second }, o.profiles.indexOfFirst { it.first == profile }, true) { profile = o.profiles[it].first }
                        choices("FOLDER", o.folders, o.folders.indexOf(folder), false) { folder = o.folders[it] }
                        if (o.seasons.isNotEmpty()) {
                            val labels = listOf("Whole show") + o.seasons.map { "Season $it" }
                            choices("ADD", labels, if (season == null) 0 else o.seasons.indexOf(season) + 1, false) { season = if (it == 0) null else o.seasons[it - 1] }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Add", { onAdd(profile!!, folder!!, season, false) }, primary = true, compact = true)
                        GlassButton("Add and remember", { onAdd(profile!!, folder!!, season, true) }, compact = true)
                        GlassButton("Cancel", onClose, compact = true)
                    }
                    Text("Remembering skips this next time (whole show for series). Turn it back on in Settings.", color = Palette.TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
