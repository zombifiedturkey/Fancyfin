package com.fancyfin.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fancyfin.tv.ui.DeepLink
import com.fancyfin.tv.ui.FancyfinApp

/** The app's one screen: everything is drawn by Compose, see ui/. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLink(intent)
        setContent { FancyfinApp() }
        Updater.checkQuietly(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** A Watch Next card opens fancyfin://play/<id>: hand that title to the app to start playing. */
    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "fancyfin" && data.host == "play") DeepLink.itemId = data.lastPathSegment
    }

    companion object {
        const val PREFS = "fancyfin"
    }
}
