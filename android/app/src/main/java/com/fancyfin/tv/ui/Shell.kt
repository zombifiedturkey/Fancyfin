package com.fancyfin.tv.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.SideEffect
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

/** Who is signed in, for the little avatar on the rail and menu. */
object UserState {
    var name by mutableStateOf<String?>(null)
}

/** The wallpaper currently showing, so the menu can frost it. */
object WallpaperState {
    var url by mutableStateOf<String?>(null)
}

enum class Tab(val label: String, val section: String, val inMenu: Boolean = true) {
    Search("Search", "Browse"),
    Home("Home", "Browse"),
    Movies("Movies", "Browse"),
    Shows("TV Shows", "Browse"),
    Collections("Collections", "Browse"),
    // Still reachable in code, no longer in the menu.
    AllMovies("All movies", "Library", false),
    AllShows("All shows", "Library", false),
    Settings("Settings", "System"),
}

/**
 * The wallpaper: a small picture stretched to fill the screen, which blurs it without any blur effect
 * (the Shield's Android version has none), then darkened so text stays readable. Or, if you've chosen
 * it in Settings, a gradient built from your accent colour.
 */
@Composable
fun Backdrop(url: String?) {
    SideEffect { WallpaperState.url = url }
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
private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Search -> Icons.Filled.Search
    Tab.Home -> Icons.Filled.Home
    Tab.Movies -> Icons.Filled.PlayArrow
    Tab.Collections -> Icons.Filled.Star
    Tab.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Menu
}

@Composable
private fun Avatar(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(Palette.Accent, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            (UserState.name?.firstOrNull() ?: 'F').uppercase(),
            color = Color.Black, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** The slim strip of icons that is always at the left edge: you, search, home and the menu. */
@Composable
private fun Rail(current: Tab, modifier: Modifier) {
    Column(
        modifier.background(Color(0x66000000), RoundedCornerShape(50)).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(18.dp)
        listOf(Tab.Search, Tab.Home).forEach { tab ->
            Box(
                Modifier.size(20.dp).background(if (tab == current) Color.White.copy(alpha = 0.22f) else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(tabIcon(tab), null, tint = Palette.Text, modifier = Modifier.size(12.dp)) }
        }
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Menu, null, tint = Palette.Text, modifier = Modifier.size(12.dp))
        }
    }
}

/** The time, in a small frosted pill at the top right. */
@Composable
fun ClockPill(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val text = remember(now / 60_000L) {
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))
    }
    Text(
        text, color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.background(Color(0x59000000), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Wraps a screen with the side menu. A slim icon rail sits at the left edge; press left at the far left and
 * it opens into a dark menu panel; press right (or Back) and it closes again.
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
        content(Modifier.padding(start = 40.dp).focusRequester(contentFocus).focusGroup())

        Rail(current, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        ClockPill(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 20.dp))

        // Dim what's behind the menu so the text in it stays easy to read.
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x99000000), 0.5f to Color(0x44000000), 1f to Color(0x00000000))))
        }

        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            val panel = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                    .width(190.dp)
                    .fillMaxHeight()
                    .background(Color(0xF50B0B0D), panel)
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
                    .padding(start = 10.dp, end = 10.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(20.dp)
                    Text(UserState.name ?: "Fancyfin", color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    var section = ""
                    Tab.values().filter { it.inMenu }.forEach { tab ->
                        if (tab.section != section) {
                            // Your pinned genres and collections sit just before System, with headings only when pinned.
                            if (tab.section == "System") {
                                if (Pins.genres.isNotEmpty()) {
                                    MenuHeading("GENRES")
                                    Pins.genres.toList().forEach { g -> MenuItem(g.label, false, Icons.Filled.Star, Modifier, { MenuActions.genre(g.genre, g.types) }) }
                                }
                                if (Pins.collections.isNotEmpty()) {
                                    MenuHeading("COLLECTIONS")
                                    Pins.collections.toList().forEach { c -> MenuItem(c.name, false, Icons.Filled.Star, Modifier, { MenuActions.collection(c.key) }) }
                                }
                            }
                            section = tab.section
                            if (section != "Browse") MenuHeading(section.uppercase())
                        }
                        MenuItem(
                            label = tab.label,
                            selected = tab == current,
                            icon = tabIcon(tab),
                            modifier = if (tab == Tab.Search) Modifier.focusRequester(sidebarFocus) else Modifier,
                            onClick = { onTab(tab) },
                        )
                    }
                }
                MenuItem("Sign out", false, null, Modifier, onSignOut)
                Box(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(text, color = Palette.TextMuted, fontSize = 9.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun MenuItem(label: String, selected: Boolean, icon: ImageVector?, modifier: Modifier, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(30.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.02f,
        edge = false,
    ) { focused ->
        val ink = if (selected) Color.Black else Palette.Text
        Box(
            Modifier.fillMaxSize()
                .background(
                    when {
                        selected -> Color.White
                        focused -> Color.White.copy(alpha = 0.22f)
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(13.dp))
                Text(
                    label, color = ink, fontSize = 12.sp, maxLines = 1,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
