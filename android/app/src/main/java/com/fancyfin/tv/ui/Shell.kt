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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
}

/**
 * The wallpaper. It is given a small picture and stretched to fill the screen, which blurs it without
 * any blur effect (the Shield's Android version has none), then darkened so text stays readable.
 */
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
        Box(Modifier.fillMaxSize().background(Color(0xB307080B)))
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
            Column(
                Modifier
                    .width(210.dp)
                    .fillMaxHeight()
                    .background(Color(0xE60B0D12))
                    .border(1.dp, Palette.GlassEdge)
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
                    .padding(start = 18.dp, end = 14.dp, top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Fancyfin", color = Palette.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                var section = ""
                Tab.values().forEach { tab ->
                    if (tab.section != section) {
                        section = tab.section
                        Text(
                            section.uppercase(), color = Palette.TextMuted, fontSize = 10.sp,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
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
                Box(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(32.dp),
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.03f,
    ) { focused ->
        Box(
            Modifier.fillMaxSize()
                .background(
                    when {
                        focused -> Palette.GlassFillStrong
                        selected -> Palette.GlassFill
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                label,
                color = if (selected) Palette.Accent else Palette.Text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
