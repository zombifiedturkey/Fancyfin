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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import com.fancyfin.tv.data.Prefs

/** Where you are in the app. Back goes to the one before. */
private sealed class Route {
    data class Main(val tab: Tab) : Route()
    data class Detail(val item: Item) : Route()
    data class Play(val item: Item) : Route()
}

/** The whole app. */
@Composable
fun FancyfinApp() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val client = remember { JellyfinClient(prefs) }
    var signedIn by remember { mutableStateOf(client.isSignedIn) }
    val stack = remember { mutableStateListOf<Route>(Route.Main(Tab.Home)) }

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
            val open: (Item) -> Unit = { item -> stack.add(Route.Detail(item)) }
            val play: (Item) -> Unit = { item -> stack.add(Route.Play(item)) }
            val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

            when (val route = stack.last()) {
                is Route.Main -> if (route.tab == Tab.Home) {
                    HomeScreen(client, open, goTab, signOut)
                } else {
                    LibraryScreen(route.tab, client, open, goTab, signOut)
                }
                is Route.Detail -> DetailScreen(route.item, client, play, open)
                is Route.Play -> PlayerScreen(route.item, client, back)
            }
        }
    }
}
