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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** How see-through the glass is. */
enum class Glass(val label: String, val fill: Float, val strong: Float, val panel: Float) {
    Subtle("Subtle", 0.10f, 0.18f, 0.55f),
    Balanced("Balanced", 0.20f, 0.30f, 0.72f),
    Frosted("Frosted", 0.30f, 0.42f, 0.88f),
}

/** The colour used for buttons, highlights and the gradient background. */
enum class AccentChoice(val label: String, val color: Color) {
    Amber("Amber", Color(0xFFFFC145)),
    Lime("Lime", Color(0xFF9BD64B)),
    Teal("Teal", Color(0xFF3DD6C3)),
    Sky("Sky Blue", Color(0xFF4DA3FF)),
    Violet("Violet", Color(0xFFA78BFA)),
    Pink("Pink", Color(0xFFF472B6)),
    Red("Red", Color(0xFFF0584F)),
    Orange("Orange", Color(0xFFFF9A3D)),
    Silver("Silver", Color(0xFFC4C9D4)),
}

/** Look-and-feel choices, remembered between launches. Reading them in a screen updates it when they change. */
object UiSettings {
    var corner by mutableStateOf(Corner.Light)
        private set
    var posterBorder by mutableStateOf(true)
        private set
    var showUpNext by mutableStateOf(true)
        private set
    var glass by mutableStateOf(Glass.Balanced)
        private set
    var accent by mutableStateOf(AccentChoice.Teal)
        private set
    var backgroundPicture by mutableStateOf(true)
        private set
    /** Ask which quality profile, folder and season to use when adding to Radarr / Sonarr. */
    var askArr by mutableStateOf(true)
        private set

    private var prefs: SharedPreferences? = null

    fun load(context: Context) {
        val p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        prefs = p
        corner = Corner.values().firstOrNull { it.name == p.getString("ui_corner", null) } ?: Corner.Light
        posterBorder = p.getBoolean("ui_poster_border", true)
        showUpNext = p.getBoolean("ui_up_next", true)
        glass = Glass.values().firstOrNull { it.name == p.getString("ui_glass", null) } ?: Glass.Balanced
        accent = AccentChoice.values().firstOrNull { it.name == p.getString("ui_accent", null) } ?: AccentChoice.Teal
        backgroundPicture = p.getBoolean("ui_bg_picture", true)
        askArr = p.getBoolean("ui_ask_arr", true)
    }

    fun chooseAskArr(value: Boolean) {
        askArr = value
        prefs?.edit()?.putBoolean("ui_ask_arr", value)?.apply()
    }

    /** The profile and folder last remembered for "movie" or "series", or null if none. */
    fun savedArr(kind: String): Pair<Int, String>? {
        val p = prefs ?: return null
        val folder = p.getString("arr_${kind}_folder", null) ?: return null
        val profile = p.getInt("arr_${kind}_profile", -1)
        return if (profile >= 0) profile to folder else null
    }

    /** Remember these choices and stop asking, until the Settings toggle is switched back on. */
    fun rememberArr(kind: String, profile: Int, folder: String) {
        prefs?.edit()?.putInt("arr_${kind}_profile", profile)?.putString("arr_${kind}_folder", folder)?.apply()
        chooseAskArr(false)
    }

    fun chooseCorner(value: Corner) {
        corner = value
        prefs?.edit()?.putString("ui_corner", value.name)?.apply()
    }

    fun chooseGlass(value: Glass) {
        glass = value
        prefs?.edit()?.putString("ui_glass", value.name)?.apply()
    }

    fun chooseAccent(value: AccentChoice) {
        accent = value
        prefs?.edit()?.putString("ui_accent", value.name)?.apply()
    }

    fun chooseBackgroundPicture(value: Boolean) {
        backgroundPicture = value
        prefs?.edit()?.putBoolean("ui_bg_picture", value)?.apply()
    }

    fun chooseUpNext(value: Boolean) {
        showUpNext = value
        prefs?.edit()?.putBoolean("ui_up_next", value)?.apply()
    }

    fun choosePosterBorder(value: Boolean) {
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
    // Back from Settings goes to Home (the menu's own Back handler still wins while the menu is open).
    androidx.activity.compose.BackHandler { onTab(Tab.Home) }
    Shell(Tab.Settings, onTab, onSignOut) { contentModifier ->
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(
                contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 48.dp, top = 28.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Settings", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)

                SettingRow("Window corners") {
                    Corner.values().forEach { c ->
                        GlassButton(c.label, { UiSettings.chooseCorner(c) }, primary = c == UiSettings.corner)
                    }
                }

                SettingRow("Glass strength") {
                    Glass.values().forEach { g ->
                        GlassButton(g.label, { UiSettings.chooseGlass(g) }, primary = g == UiSettings.glass)
                    }
                }

                SettingRow("Accent colour  —  " + UiSettings.accent.label) {
                    AccentChoice.values().forEach { a ->
                        FocusBox(
                            onClick = { UiSettings.chooseAccent(a) },
                            modifier = Modifier.size(34.dp),
                            shape = RoundedCornerShape(50),
                            focusedScale = 1.15f,
                        ) {
                            Box(Modifier.fillMaxSize().background(a.color), contentAlignment = Alignment.Center) {
                                if (a == UiSettings.accent) Text("✓", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                SettingRow("Background") {
                    GlassButton("Picture", { UiSettings.chooseBackgroundPicture(true) }, primary = UiSettings.backgroundPicture)
                    GlassButton("Accent gradient", { UiSettings.chooseBackgroundPicture(false) }, primary = !UiSettings.backgroundPicture)
                }

                SettingRow("Border around posters") {
                    GlassButton("On", { UiSettings.choosePosterBorder(true) }, primary = UiSettings.posterBorder)
                    GlassButton("Off", { UiSettings.choosePosterBorder(false) }, primary = !UiSettings.posterBorder)
                }

                SettingRow("Up next row on the home screen") {
                    GlassButton("On", { UiSettings.chooseUpNext(true) }, primary = UiSettings.showUpNext)
                    GlassButton("Off", { UiSettings.chooseUpNext(false) }, primary = !UiSettings.showUpNext)
                }

                SettingRow("Ask for quality profile, folder and season when adding to Radarr / Sonarr") {
                    GlassButton("On", { UiSettings.chooseAskArr(true) }, primary = UiSettings.askArr)
                    GlassButton("Off", { UiSettings.chooseAskArr(false) }, primary = !UiSettings.askArr)
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
