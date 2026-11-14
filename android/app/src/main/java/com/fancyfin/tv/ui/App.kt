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
    data class Play(val item: Item, val sourceId: String?) : Route()
}

/** The whole app. */
@Composable
fun FancyfinApp() {
    val context = LocalContext.current
    remember { UiSettings.load(context) }
    val prefs = remember { Prefs(context) }
    val client = remember { JellyfinClient(prefs) }
    var signedIn by remember { mutableStateOf(client.isSignedIn) }
    val stack = remember { mutableStateListOf<Route>(Route.Main(Tab.Home)) }
    val scope = rememberCoroutineScope()
    var trailer by remember { mutableStateOf<List<String>?>(null) }
    var choice by remember { mutableStateOf<Pair<Item, List<MediaVersion>>?>(null) }

    TvScale {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            if (!signedIn) {
                SignInScreen(client) {
                    stack.clear()
                    stack.add(Route.Main(Tab.Home))
                    signedIn = true
                }
                return@Box
            }

            BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

            val signOut = {
                prefs.signOut()
                signedIn = false
            }
            val goTab: (Tab) -> Unit = { tab ->
                stack.clear()
                stack.add(Route.Main(tab))
            }
            val open: (Item) -> Unit = { item -> stack.add(Route.Title(TitleRef.of(item))) }
            val openTitle: (TitleRef) -> Unit = { ref -> stack.add(Route.Title(ref)) }
            val openGenre: (String, String) -> Unit = { genre, types -> stack.add(Route.Genre(genre, types)) }
            // Playing a title that has more than one version asks which one first.
            val play: (Item) -> Unit = { item ->
                scope.launch {
                    val versions = try { client.versions(item) } catch (e: Exception) { emptyList() }
                    if (versions.size > 1) {
                        choice = item to versions
                    } else {
                        stack.add(Route.Play(item, versions.firstOrNull()?.sourceId))
                    }
                }
            }
            val showTrailer: (List<String>) -> Unit = { keys -> trailer = keys }
            val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

            when (val route = stack.last()) {
                is Route.Main -> when (route.tab) {
                    Tab.Home, Tab.Movies, Tab.Shows -> BrowseScreen(route.tab, client, open, play, openGenre, openTitle, goTab, signOut)
                    Tab.Search -> SearchScreen(client, open, goTab, signOut)
                    Tab.Settings -> SettingsScreen(goTab, signOut)
                    Tab.AllMovies, Tab.AllShows -> LibraryScreen(route.tab, client, open, goTab, signOut)
                }
                is Route.Genre -> GenreScreen(route.genre, route.types, client, open, play, openTitle, goTab, signOut)
                is Route.Title -> TitleScreen(route.ref, client, play, openTitle, showTrailer) { p -> stack.add(Route.Person(PersonRef(p.id, p.name, p.photo))) }
                is Route.Person -> PersonScreen(route.ref, client, openTitle)
                is Route.Play -> PlayerScreen(route.item, client, route.sourceId, back)
            }

            trailer?.let { keys -> TrailerPlayer(keys) { trailer = null } }

            choice?.let { (item, versions) ->
                VersionChooser(
                    item = item,
                    versions = versions,
                    onPick = { v ->
                        choice = null
                        stack.add(Route.Play(v.item, v.sourceId))
                    },
                    onCancel = { choice = null },
                )
            }
        }
    }
}
