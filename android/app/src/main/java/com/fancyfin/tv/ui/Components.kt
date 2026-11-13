package com.fancyfin.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient

/**
 * Anything you can move to with the remote. It grows a little and gets a bright edge when focused.
 * The click handler is what the centre button of the remote triggers.
 */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    focusedScale: Float = 1.08f,
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .border(
                BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Palette.Text else Palette.GlassEdge),
                shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
    ) {
        content(focused)
    }
}

/** A frosted button. The primary one is filled with the accent colour. */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.06f,
    ) { focused ->
        val fill = when {
            primary && focused -> Palette.Accent
            primary -> Palette.Accent.copy(alpha = 0.85f)
            focused -> Palette.GlassFillStrong
            else -> Palette.GlassFill
        }
        Box(Modifier.fillMaxHeight().background(fill).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (primary) Color.Black else Palette.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color(0x66000000))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Palette.Accent))
    }
}

/** A card showing an item's picture, with an optional line of text beneath. */
@Composable
fun ItemCard(
    item: Item,
    client: JellyfinClient,
    width: Dp,
    height: Dp,
    landscape: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val url = if (landscape) client.landscape(item) else client.poster(item) ?: client.landscape(item)
    FocusBox(
        onClick = onClick,
        modifier = modifier.size(width, height),
        onFocused = onFocused,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight().background(Palette.GlassFill))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        } else {
            Text(
                item.name,
                color = Palette.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
        }
        item.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomStart)) }
    }
}

/** Small facts about an item in one line: year, rating, length. */
@Composable
fun MetaLine(item: Item, modifier: Modifier = Modifier) {
    val parts = ArrayList<String>()
    item.episodeLabel?.let { parts.add(it) }
    item.year?.let { parts.add(it.toString()) }
    item.officialRating?.let { parts.add(it) }
    if (item.runtimeMinutes > 0) {
        val h = item.runtimeMinutes / 60
        val m = item.runtimeMinutes % 60
        parts.add(if (h > 0) "${h}h ${m}m" else "${m}m")
    }
    item.rating?.let { parts.add("★ " + "%.1f".format(it)) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(parts.joinToString("   ·   "), color = Palette.TextMuted, fontSize = 12.sp)
    }
}
