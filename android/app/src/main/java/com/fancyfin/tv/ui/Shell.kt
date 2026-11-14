package com.fancyfin.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

enum class Tab(val label: String, val section: String) {
    Home("Home", "Browse"),
    Movies("Movies", "Browse"),
    Shows("TV Shows", "Browse"),
    AllMovies("All movies", "Library"),
    AllShows("All shows", "Library"),
    Search("Search", "Library"),
    Settings("Settings", "System"),
}

/**
 * The wallpaper: a small picture stretched to fill the screen, which blurs it without any blur effect
 * (the Shield's Android version has none), then darkened so text stays readable. Or, if you've chosen
 * it in Settings, a gradient built from your accent colour.
 */
@Composable
fun Backdrop(url: String?) {
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        if (UiSettings.backgroundPicture && url != null) {
            Crossfade(targetState = url, label = "backdrop") { target ->
                AsyncImage(
                    model = target,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(Color(0xB307080B)))
        } else {
            val accent = Palette.Accent
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(
                            lerp(Palette.Background, accent, 0.42f),
                            lerp(Palette.Background, accent, 0.14f),
                            Palette.Background,
                        ),
                    ),
                ),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x4007080B), 1f to Color(0xF207080B)),
            ),
        )
    }
}

/**
 * Wraps a screen with the side menu. The menu stays out of sight: press left at the far left edge
 * and it slides in; press right (or Back) and it slides away, leaving the whole screen for content.
 * The content gets a modifier that lets the shell put focus back on it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Shell(
    current: Tab,
    onTab: (Tab) -> Unit,
    onSignOut: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val focus = LocalFocusManager.current
    var open by remember { mutableStateOf(false) }
    val sidebarFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }

    LaunchedEffect(open) {
        delay(80)
        try {
            if (open) sidebarFocus.requestFocus() else contentFocus.requestFocus()
        } catch (e: Exception) {
            // Nothing to focus yet.
        }
    }
    BackHandler(enabled = open) { open = false }

    Box(
        Modifier.fillMaxSize().onKeyEvent { event ->
            // Left with nowhere further left to go: bring the menu in.
            if (!open && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                if (!focus.moveFocus(FocusDirection.Left)) open = true
                true
            } else {
                false
            }
        },
    ) {
        content(Modifier.focusRequester(contentFocus).focusGroup())

        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            val panel = RoundedCornerShape(UiSettings.corner.tile.coerceAtLeast(0.dp))
            Column(
                Modifier
                    .padding(14.dp)
                    .width(220.dp)
                    .fillMaxHeight()
                    .clip(panel)
                    // Dark glass with a soft sheen from the top, and an edge that catches the light.
                    .background(Color(0xFF0B0D12).copy(alpha = UiSettings.glass.panel))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.10f))), panel)
                    .focusProperties { exit = { FocusRequester.Cancel } }
                    .focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            open = false
                            true
                        } else {
                            false
                        }
                    }
                    .padding(start = 14.dp, end = 12.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Fancyfin", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                var section = ""
                Tab.values().forEach { tab ->
                    if (tab.section != section) {
                        section = tab.section
                        Text(
                            section.uppercase(), color = Palette.TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 3.dp),
                        )
                    }
                    MenuItem(
                        label = tab.label,
                        selected = tab == current,
                        modifier = if (tab == current) Modifier.focusRequester(sidebarFocus) else Modifier,
                        onClick = { onTab(tab) },
                    )
                }
                Box(Modifier.weight(1f))
                MenuItem("Sign out", false, Modifier, onSignOut)
                Box(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(34.dp),
        shape = RoundedCornerShape(UiSettings.corner.button.coerceAtLeast(8.dp)),
        focusedScale = 1.03f,
        edge = false,
    ) { focused ->
        Box(
            Modifier.fillMaxSize()
                .background(
                    when {
                        focused -> Color.White.copy(alpha = 0.26f)
                        selected -> Color.White.copy(alpha = 0.13f)
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).background(if (selected) Palette.Accent else Color.Transparent, RoundedCornerShape(50)))
                Text(
                    label,
                    color = Palette.Text,
                    fontSize = 13.sp,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
