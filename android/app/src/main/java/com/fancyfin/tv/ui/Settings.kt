package com.fancyfin.tv.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.BuildConfig
import com.fancyfin.tv.MainActivity
import com.fancyfin.tv.Updater

/** How rounded the windows, posters and buttons are. */
enum class Corner(val label: String, val card: Dp, val tile: Dp, val button: Dp) {
    Square("Square", 0.dp, 0.dp, 0.dp),
    Light("Lightly rounded", 6.dp, 10.dp, 6.dp),
    Round("Rounded", 12.dp, 20.dp, 18.dp),
}

/** Look-and-feel choices, remembered between launches. Reading them in a screen updates it when they change. */
object UiSettings {
    var corner by mutableStateOf(Corner.Light)
        private set
    var posterBorder by mutableStateOf(true)
        private set

    private var prefs: SharedPreferences? = null

    fun load(context: Context) {
        val p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        prefs = p
        corner = Corner.values().firstOrNull { it.name == p.getString("ui_corner", null) } ?: Corner.Light
        posterBorder = p.getBoolean("ui_poster_border", true)
    }

    fun setCorner(value: Corner) {
        corner = value
        prefs?.edit()?.putString("ui_corner", value.name)?.apply()
    }

    fun setPosterBorder(value: Boolean) {
        posterBorder = value
        prefs?.edit()?.putBoolean("ui_poster_border", value)?.apply()
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Palette.TextMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
fun SettingsScreen(onTab: (Tab) -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    Shell(Tab.Settings, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(
                contentModifier.fillMaxSize().padding(start = 48.dp, top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Settings", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)

                SettingRow("Window corners") {
                    Corner.values().forEach { c ->
                        GlassButton(c.label, { UiSettings.setCorner(c) }, primary = c == UiSettings.corner)
                    }
                }

                SettingRow("Border around posters") {
                    GlassButton("On", { UiSettings.setPosterBorder(true) }, primary = UiSettings.posterBorder)
                    GlassButton("Off", { UiSettings.setPosterBorder(false) }, primary = !UiSettings.posterBorder)
                }

                SettingRow("Preview") {
                    repeat(3) {
                        val shape = RoundedCornerShape(UiSettings.corner.card)
                        Box(
                            Modifier.size(72.dp, 108.dp)
                                .background(Palette.GlassFillStrong, shape)
                                .then(if (UiSettings.posterBorder) Modifier.border(1.dp, Palette.GlassEdge, shape) else Modifier),
                        )
                    }
                }

                SettingRow("Fancyfin TV " + BuildConfig.VERSION_NAME) {
                    GlassButton("Check for updates", { (context as? Activity)?.let { Updater.check(it, manual = true) } })
                }
            }
        }
    }
}
