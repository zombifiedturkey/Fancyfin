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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.MediaVersion
import kotlinx.coroutines.delay

private fun sizeText(bytes: Long): String =
    if (bytes <= 0) "" else "%.1f GB".format(bytes / 1_073_741_824.0)

/**
 * A glass pop-up for titles that exist in more than one version. Each row says what you'd get:
 * resolution, HDR or Dolby Vision, the sound, and the size of the file.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VersionChooser(item: Item, versions: List<MediaVersion>, onPick: (MediaVersion) -> Unit, onCancel: () -> Unit) {
    val first = remember { FocusRequester() }
    val ordered = remember(versions) { versions.sortedByDescending { it.sizeBytes } }
    BackHandler { onCancel() }
    LaunchedEffect(Unit) {
        delay(80)
        try { first.requestFocus() } catch (e: Exception) { /* not laid out yet */ }
    }

    val panel = RoundedCornerShape(UiSettings.corner.tile)
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .background(Color(0xE0151922), panel)
                .border(1.dp, Palette.GlassEdge, panel)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose a version", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(item.seriesName ?: item.name, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            ordered.forEachIndexed { index, v ->
                FocusBox(
                    onClick = { onPick(v) },
                    modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                    shape = RoundedCornerShape(UiSettings.corner.card),
                    focusedScale = 1.02f,
                ) { focused ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(if (focused) Palette.GlassFillStrong else Palette.GlassFill)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(v.resolution, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            val special = v.range != "SDR"
                            Text(
                                v.range,
                                color = if (special) Palette.Accent else Palette.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (special) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        Text(
                            listOf(v.audio, v.codec, v.container, sizeText(v.sizeBytes)).filter { it.isNotEmpty() }.joinToString("   ·   "),
                            color = Palette.Text, fontSize = 12.sp,
                        )
                        if (v.fileName.isNotEmpty()) {
                            Text(v.fileName, color = Palette.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
