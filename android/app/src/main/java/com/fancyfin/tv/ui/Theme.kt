package com.fancyfin.tv.ui

import androidx.compose.ui.graphics.Color

/** The colours used everywhere. The accent and the glass strength follow the choices in Settings. */
object Palette {
    val Background: Color get() = UiSettings.background.color
    val Text = Color(0xFFF5F6F8)
    val TextMuted = Color(0xFFA6ACB8)
    val Accent: Color get() = UiSettings.accent.color
    val GlassFill: Color get() = Color.White.copy(alpha = UiSettings.glass.fill)
    val GlassFillStrong: Color get() = Color.White.copy(alpha = UiSettings.glass.strong)
    val GlassEdge = Color(0x40FFFFFF)
    /** The big floating panels (side menu, dialogs): dark glass that follows the glass strength. */
    val Panel: Color get() = Palette.Background.copy(alpha = UiSettings.glass.panel)
    val Scrim = Color(0xE6000000)
}
