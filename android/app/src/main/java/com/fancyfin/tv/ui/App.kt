package com.fancyfin.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import com.fancyfin.tv.data.MediaVersion
import com.fancyfin.tv.data.Prefs
import kotlinx.coroutines.launch

/** Where you are in the app. Back goes to the one before. */
private sealed class Route {
    data class Main(val tab: Tab) : Route()
    data class Title(val ref: TitleRef) : Route()
    data class Person(val ref: PersonRef) : Route()
    data class Genre(val genre: String, val types: String) : Route()
    data class Play(val item: Item, val sourceId: String?, val maxHeight: Int? = null) : Route()
    data class Collection(val tile: com.fancyfin.tv.data.CollectionTile) : Route()
}

/** The whole app. */
@Composable
fun FancyfinApp() {
    val context = LocalContext.current
    remember { UiSettings.load(context); Pins.load(context) }
    val prefs = remember { Prefs(context) }
    val client = remember { JellyfinClient(prefs) }
    var signedIn by remember { mutableStateOf(client.isSignedIn) }
    val stack = remember { mutableStateListOf<Route>(Route.Main(Tab.Home)) }
    val scope = rememberCoroutineScope()
    var lastBack by remember { mutableStateOf(0L) }
    var askExit by remember { mutableStateOf(false) }
    var trailer by remember { mutableStateOf<List<String>?>(null) }
    // Each browse page's rows, focus and scroll, so Back lands where you were.
    val screens = remember { HashMap<String, FeatureState>() }
    val collections = remember { CollectionsState() }
    val searchState = remember { SearchState() }
    var choice by remember { mutableStateOf<Pair<Item, List<MediaVersion>>?>(null) }

    TvScale {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            if (!signedIn) {
                BackHandler { askExit = true }
                SignInScreen(client) {
                    stack.clear()
                    stack.add(Route.Main(Tab.Home))
                    signedIn = true
                }
                if (askExit) ExitPrompt(onStay = { askExit = false }, onClose = { (context as? android.app.Activity)?.finish() })
                return@Box
            }

            val signOut = {
                prefs.signOut()
                signedIn = false
            }
            val goTab: (Tab) -> Unit = { tab ->
                screens.clear()
                stack.clear()
                stack.add(Route.Main(tab))
            }
            val back: () -> Unit = {
                if (stack.size > 1) {
                    // Coming back from the player: watch progress changed, so the rows refresh (keeping your place).
                    if (stack.last() is Route.Play) screens.values.forEach { it.loaded = false }
                    stack.removeAt(stack.lastIndex)
                }
            }

            // The last stop for Back: step back through the pages, then to Home, then ask before closing.
            // Screens with their own Back handling (menus, dialogs, the Home rows) take it first.
            BackHandler {
                val now = System.currentTimeMillis()
                val quick = now - lastBack < 700
                lastBack = now
                val here = stack.last()
                when {
                    // Two quick presses: all the way back to Home.
                    quick && (stack.size > 1 || (here is Route.Main && here.tab != Tab.Home)) -> {
                        stack.clear()
                        stack.add(Route.Main(Tab.Home))
                    }
                    stack.size > 1 -> back()
                    here is Route.Main && here.tab != Tab.Home -> goTab(Tab.Home)
                    !quick -> askExit = true
                }
            }
            val open: (Item) -> Unit = { item -> stack.add(Route.Title(TitleRef.of(item))) }
            val openTitle: (TitleRef) -> Unit = { ref -> stack.add(Route.Title(ref)) }
            val openGenre: (String, String) -> Unit = { genre, types -> stack.add(Route.Genre(genre, types)) }
            // Playing a title that has more than one version asks which one first.
            // Your quality limit from Settings turns a bigger copy into a converted stream.
            fun playRoute(item: Item, v: MediaVersion?, useCopy: Boolean = false): Route.Play {
                val cap = UiSettings.maxHeight
                val height = v?.let { resolutionHeight(it) } ?: 0
                return Route.Play(if (useCopy && v != null) v.item else item, v?.sourceId, if (cap > 0 && height > cap) cap else null)
            }
            val play: (Item) -> Unit = { item ->
                scope.launch {
                    val versions = try { client.versions(item) } catch (e: Exception) { emptyList() }
                    if (versions.size > 1 && UiSettings.bestCopy) {
                        stack.add(playRoute(item, bestVersion(versions), useCopy = true))
                    } else if (versions.size > 1) {
                        choice = item to versions
                    } else {
                        stack.add(playRoute(item, versions.firstOrNull()))
                    }
                }
            }
            MenuActions.genre = { genre, types ->
                stack.clear()
                stack.add(Route.Main(Tab.Home))
                stack.add(Route.Genre(genre, types))
            }
            MenuActions.collection = { key ->
                stack.clear()
                stack.add(Route.Main(Tab.Collections))
                val tile = collections.tiles.firstOrNull { it.key == key }
                if (tile != null) stack.add(Route.Collection(tile)) else collections.pendingOpen = key
            }
            val showTrailer: (List<String>) -> Unit = { keys -> trailer = keys }
            when (val route = stack.last()) {
                is Route.Main -> when (route.tab) {
                    Tab.Home, Tab.Movies, Tab.Shows -> BrowseScreen(route.tab, screens.getOrPut("tab:${route.tab}") { FeatureState() }, client, open, play, openGenre, openTitle, goTab, signOut)
                    Tab.Search -> SearchScreen(searchState, client, open, goTab, signOut)
                    Tab.Collections -> CollectionsScreen(client, collections, { stack.add(Route.Collection(it)) }, goTab, signOut)
                    Tab.Settings -> SettingsScreen(client, goTab, signOut)
                    Tab.AllMovies, Tab.AllShows -> LibraryScreen(route.tab, client, open, goTab, signOut)
                }
                is Route.Genre -> GenreScreen(route.genre, route.types, screens.getOrPut("genre:${route.genre}:${route.types}") { FeatureState() }, client, open, play, openTitle, goTab, signOut)
                is Route.Collection -> androidx.compose.runtime.key(route.tile.key) {
                    CollectionScreen(route.tile, screens.getOrPut("coll:${route.tile.key}") { FeatureState() }, client, open, play, openTitle, goTab, signOut)
                }
                // Keyed so opening another title from this one (More like this, franchise) starts a fresh page
                // instead of reusing the old page's state.
                is Route.Title -> androidx.compose.runtime.key(route.ref) {
                    TitleScreen(route.ref, client, play, openTitle, showTrailer) { p -> stack.add(Route.Person(PersonRef(p.id, p.name, p.photo))) }
                }
                is Route.Person -> androidx.compose.runtime.key(route.ref) { PersonScreen(route.ref, client, openTitle) }
                is Route.Play -> androidx.compose.runtime.key(route.item.id) {
                    PlayerScreen(route.item, client, route.sourceId, route.maxHeight, back) { next ->
                        // Autoplay: swap this player for the next episode's.
                        scope.launch {
                            val versions = try { client.versions(next) } catch (e: Exception) { emptyList() }
                            if (stack.lastOrNull() == route) {
                                stack.removeAt(stack.lastIndex)
                                stack.add(playRoute(next, versions.firstOrNull()))
                            }
                        }
                    }
                }
            }

            if (askExit) ExitPrompt(onStay = { askExit = false }, onClose = { (context as? android.app.Activity)?.finish() })

            trailer?.let { keys -> TrailerPlayer(keys) { trailer = null } }

            choice?.let { (item, versions) ->
                VersionChooser(
                    item = item,
                    versions = versions,
                    onPick = { v ->
                        choice = null
                        stack.add(playRoute(v.item, v, useCopy = true))
                    },
                    onCancel = { choice = null },
                )
            }
        }
    }
}
