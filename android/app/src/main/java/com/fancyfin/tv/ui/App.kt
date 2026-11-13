package com.fancyfin.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The whole app. */
@Composable
fun FancyfinApp() {
    TvScale {
        Box(Modifier.fillMaxSize().background(Palette.Background).padding(48.dp)) {
            Column {
                Text("Fancyfin", color = Palette.Text, fontSize = 40.sp)
                Text(
                    "Screen: " + LocalConfiguration.current.screenWidthDp + " x " +
                        LocalConfiguration.current.screenHeightDp + " dp",
                    color = Palette.TextMuted,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
