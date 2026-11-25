package com.fancyfin.tv.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fancyfin.tv.data.JellyfinClient

/**
 * The main colour of a picture (averaged from a tiny copy of it), nudged so it works as a tint:
 * richer than a plain average, never too dark or too bright. Fades between pictures.
 */
@Composable
fun rememberAmbient(url: String?): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(Color(0xFF3A2A22)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        try {
            val request = ImageRequest.Builder(context).data(url).size(24).allowHardware(false).build()
            val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                ?: return@LaunchedEffect
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val p = bitmap.getPixel(x, y)
                    r += (p shr 16) and 255
                    g += (p shr 8) and 255
                    b += p and 255
                    n++
                }
            }
            if (n == 0) return@LaunchedEffect
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            hsv[1] = (hsv[1] * 1.35f).coerceIn(0f, 0.9f)
            hsv[2] = hsv[2].coerceIn(0.38f, 0.7f)
            target = Color(android.graphics.Color.HSVToColor(hsv))
        } catch (e: Exception) {
            // Keep the last colour.
        }
    }
    return animateColorAsState(target, tween(700), label = "ambient").value
}

/** The full-screen picture of whatever you're on, washed with its own colour from the left. */
@Composable
fun HeroBackdrop(entry: HeroEntry?) = HeroBackdropUrl(entry?.backdrop ?: entry?.poster)

@Composable
fun HeroBackdropUrl(url: String?) {
    // This page is all about the big picture, so it shows whatever the Settings background choice is.
    if (url == null) {
        Backdrop(null)
        return
    }
    val deep = Palette.Background
    Box(Modifier.fillMaxSize().background(deep)) {
        Crossfade(targetState = url, label = "hero") { target ->
            AsyncImage(model = target, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Solid picture in the top right corner, fading diagonally into the accent colour toward the bottom left.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.3f to Color.Transparent,
                    0.62f to deep.copy(alpha = 0.78f),
                    1f to deep.copy(alpha = 0.97f),
                    start = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                ),
            ),
        )
        // Darker toward the bottom so the rows of posters stay easy to see.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.5f),
                ),
            ),
        )
    }
}

private fun runtimeLabel(minutes: Int): String? =
    if (minutes <= 0) null else if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/** The title block above the rows: logo or name, a line of facts, and the description. */
@Composable
fun InfoBlock(entry: HeroEntry?, client: JellyfinClient, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 60.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (entry == null || entry.isMore) return@Column
        val server = entry.onServer
        val logo = server?.let { client.logo(it) }
        if (logo != null) {
            AsyncImage(
                model = logo, contentDescription = entry.title, contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart, modifier = Modifier.height(64.dp).width(280.dp),
            )
        } else {
            Text(entry.title, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(520.dp))
        }
        entry.subtitle?.let {
            Text(it, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val facts = listOfNotNull(
                entry.year?.toString(),
                server?.runtimeMinutes?.let { runtimeLabel(it) },
                entry.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
            )
            if (facts.isNotEmpty()) Text(facts.joinToString("   "), color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            server?.officialRating?.let {
                Text(
                    it, color = Palette.Text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            entry.rating?.let { Text("★ " + "%.1f".format(it), color = Palette.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
            if (server == null) {
                Text(
                    entry.status?.let { "Not on your server · $it" } ?: "Not on your server",
                    color = Palette.Text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.border(1.dp, Palette.GlassEdge, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        entry.overview?.let {
            Text(it, color = Palette.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).width(520.dp))
        }
    }
}
