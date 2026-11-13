package com.fancyfin.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fancyfin.tv.ui.FancyfinApp

/** The app's one screen: everything is drawn by Compose, see ui/. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FancyfinApp() }
        Updater.checkQuietly(this)
    }

    companion object {
        const val PREFS = "fancyfin"
    }
}
