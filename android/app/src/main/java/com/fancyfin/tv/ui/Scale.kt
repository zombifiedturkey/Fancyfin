package com.fancyfin.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Every screen is designed on a canvas that is this many dp wide (and 540 tall at 16:9). */
const val REFERENCE_WIDTH_DP = 960f

/**
 * Makes the layout the same on any TV, whatever size the system reports. The screen is always treated
 * as 960 dp wide, so a design that looks right at 1080p looks the same at 4K, just sharper.
 */
@Composable
fun TvScale(content: @Composable () -> Unit) {
    val metrics = LocalContext.current.resources.displayMetrics
    val density = metrics.widthPixels / REFERENCE_WIDTH_DP
    CompositionLocalProvider(
        LocalDensity provides Density(density = density, fontScale = 1f),
        content = content,
    )
}
