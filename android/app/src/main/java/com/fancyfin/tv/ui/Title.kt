package com.fancyfin.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fancyfin.tv.data.Discover
import com.fancyfin.tv.data.Item
import com.fancyfin.tv.data.JellyfinClient
import com.fancyfin.tv.data.MediaVersion
import com.fancyfin.tv.data.Person
import androidx.compose.ui.text.style.TextAlign
import com.fancyfin.tv.data.TmdbTitle
import com.fancyfin.tv.data.trailerKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which title to show: a movie or show from TMDB, the copy on your server if there is one, or both. */
data class TitleRef(
    val kind: String,
    val tmdbId: Int?,
    val server: Item?,
    val title: String,
    val backdrop: String? = null,
    val poster: String? = null,
) {
    companion object {
        fun of(i: Item) = TitleRef(if (i.isMovie) "movie" else "tv", i.tmdbId?.toIntOrNull(), i, i.seriesName ?: i.name)
    }
}

private fun sizeText(bytes: Long): String = if (bytes <= 0) "" else "%.1f GB".format(bytes / 1_073_741_824.0)

private fun runtimeText(minutes: Int): String? =
    if (minutes <= 0) null else if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
    )
}

/** A poster you can focus; accent border for the one you're looking at, a dot when it's on your server. */
@Composable
private fun PosterTile(
    url: String?,
    title: String,
    onServer: Boolean,
    current: Boolean,
    onClick: () -> Unit,
    width: Dp = 100.dp,
    height: Dp = 150.dp,
    modifier: Modifier = Modifier,
) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FocusBox(onClick = onClick, modifier = modifier.width(width).height(height), edge = UiSettings.posterBorder) { _ ->
            Box(Modifier.fillMaxSize().background(Palette.GlassFill)) {
                if (url != null) AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (current) Box(Modifier.fillMaxSize().border(2.dp, Palette.Accent, RoundedCornerShape(UiSettings.corner.card)))
                if (onServer) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(5.dp).size(9.dp)
                            .background(Palette.Accent, CircleShape).border(1.dp, Color.Black, CircleShape),
                    )
                }
            }
        }
        Text(title, color = if (current) Palette.Accent else Palette.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The full page for one movie or show: what it is, how it will look and sound on your server, the cast,
 * the rest of its franchise, more like it, and trailers and extras. Works for titles that aren't on your
 * server too, using TMDB.
 */
@Composable
fun TitleScreen(
    ref: TitleRef,
    client: JellyfinClient,
    onPlay: (Item) -> Unit,
    onOpen: (TitleRef) -> Unit,
    onTrailer: (List<String>) -> Unit,
    onPerson: (Person) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf(ref.server) }
    var versions by remember { mutableStateOf<List<MediaVersion>>(emptyList()) }
    var seasons by remember { mutableStateOf<List<Item>>(emptyList()) }
    var season by remember { mutableStateOf<Item?>(null) }
    var episodes by remember { mutableStateOf<List<Item>>(emptyList()) }
    var serverSimilar by remember { mutableStateOf<List<Item>>(emptyList()) }
    var tmdb by remember { mutableStateOf<TmdbTitle?>(null) }
    var franchise by remember { mutableStateOf<List<Discover>>(emptyList()) }
    var index by remember { mutableStateOf<Map<String, Item>>(emptyMap()) }
    var status by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var tmdbId by remember { mutableStateOf(ref.tmdbId) }
    val kind = ref.kind

    LaunchedEffect(ref) {
        val start = ref.server
        if (start != null) {
            try {
                var full = client.item(start.id, start.duplicateIds)
                // Landing on an episode (from Continue watching) shows its show.
                val seriesId = full.seriesId
                if (full.isEpisode && seriesId != null) full = client.item(seriesId)
                item = full
                if (tmdbId == null) tmdbId = full.tmdbId?.toIntOrNull()
            } catch (e: Exception) {
                // Keep what we were given.
            }
            launch {
                serverSimilar = try { client.similar(item?.id ?: start.id) } catch (e: Exception) { emptyList() }
            }
        }
        launch { index = try { client.serverIndex() } catch (e: Exception) { emptyMap() } }
        val id = tmdbId
        if (id != null) {
            launch {
                val t = try { client.tmdbTitle(kind, id) } catch (e: Exception) { null }
                tmdb = t
                val cid = t?.collectionId
                if (cid != null) franchise = try { client.franchise(cid) } catch (e: Exception) { emptyList() }
            }
            if (start == null && kind == "movie") launch { status = try { client.releaseStatus(id) } catch (e: Exception) { null } }
        }
    }

    LaunchedEffect(item?.id) {
        val current = item ?: return@LaunchedEffect
        if (current.isSeries) {
            try {
                seasons = client.seasons(current.id)
                season = seasons.firstOrNull { (it.unplayedCount ?: 1) > 0 } ?: seasons.firstOrNull()
            } catch (e: Exception) {
                seasons = emptyList()
            }
        }
    }
    LaunchedEffect(season?.id) {
        val s = season ?: return@LaunchedEffect
        val current = item ?: return@LaunchedEffect
        episodes = try { client.episodes(current.id, s.id) } catch (e: Exception) { emptyList() }
    }

    // What Play starts: the movie, or for a show the episode you're up to.
    val shown = item
    val target: Item? = when {
        shown == null -> null
        shown.isSeries -> episodes.firstOrNull { it.positionTicks > 0 && !it.played }
            ?: episodes.firstOrNull { !it.played }
            ?: episodes.firstOrNull()
        else -> shown
    }
    val resuming = target != null && target.positionTicks > 0 && !target.played

    LaunchedEffect(target?.id) {
        val t = target ?: return@LaunchedEffect
        versions = try { client.versions(t) } catch (e: Exception) { emptyList() }
    }

    val first = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(target?.id, ref) {
        if (!focused && (target != null || ref.server == null)) {
            delay(200)
            try { first.requestFocus(); focused = true } catch (e: Exception) { /* not laid out yet */ }
        }
    }

    val t = tmdb
    val title = shown?.seriesName ?: shown?.name ?: t?.title ?: ref.title
    val overview = shown?.overview ?: t?.overview
    val tagline = shown?.tagline ?: t?.tagline
    val genres = shown?.genres?.takeIf { it.isNotEmpty() } ?: t?.genres ?: emptyList()
    val year = shown?.year ?: t?.year
    val rating = shown?.rating ?: t?.rating
    val minutes = shown?.runtimeMinutes?.takeIf { it > 0 } ?: t?.runtimeMinutes ?: 0
    val backdrop = shown?.let { client.backdrop(it) } ?: t?.backdrop ?: ref.backdrop
    val trailers = t?.let { trailerKeys(it.videos) } ?: emptyList()

    Box(Modifier.fillMaxSize()) {
        Backdrop(backdrop)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(Modifier.padding(start = 24.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val logo = shown?.let { client.logo(it) }
                    if (logo != null) {
                        AsyncImage(
                            model = logo, contentDescription = title, contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart, modifier = Modifier.height(70.dp).width(300.dp),
                        )
                    } else {
                        Text(title, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val meta = listOfNotNull(
                        year?.toString(), shown?.officialRating, runtimeText(minutes), rating?.let { "★ " + "%.1f".format(it) },
                    )
                    if (meta.isNotEmpty()) Text(meta.joinToString("   ·   "), color = Palette.TextMuted, fontSize = 12.sp)
                    if (genres.isNotEmpty()) Text(genres.take(4).joinToString(", "), color = Palette.TextMuted, fontSize = 12.sp)
                    tagline?.let { Text(it, color = Palette.Accent, fontSize = 12.sp) }
                    overview?.let { Text(it, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                }
            }

            // Quality for what's on the server; release status for what isn't.
            item {
                Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (ref.server != null) {
                        if (versions.isNotEmpty()) {
                            Text("QUALITY", color = Palette.TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            versions.sortedByDescending { it.sizeBytes }.take(3).forEach { v ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(v.resolution, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        v.range, fontSize = 12.sp,
                                        color = if (v.range != "SDR") Palette.Accent else Palette.TextMuted,
                                        fontWeight = if (v.range != "SDR") FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    Text(
                                        listOf(v.audio, v.codec, v.container, sizeText(v.sizeBytes)).filter { it.isNotEmpty() }.joinToString("   ·   "),
                                        color = Palette.Text, fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    } else {
                        Text("NOT ON YOUR SERVER", color = Palette.TextMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                        status?.let { Text(it, color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            item {
                Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        var firstAssigned = false
                        fun lead(): Modifier = if (!firstAssigned) { firstAssigned = true; Modifier.focusRequester(first) } else Modifier
                        if (target != null) {
                            GlassButton(
                                label = (if (resuming) "Resume" else "Play") + (target.episodeLabel?.let { " $it" } ?: ""),
                                primary = true,
                                modifier = lead(),
                                onClick = { onPlay(target) },
                            )
                        }
                        if (ref.server == null) {
                            // Becomes "Add to Radarr / Sonarr" once the arr flow is in.
                            GlassButton("Not on your server", {}, modifier = lead(), clear = true)
                        }
                        if (trailers.isNotEmpty()) {
                            GlassButton("Trailer", { onTrailer(trailers) }, modifier = lead())
                        }
                        if (shown != null) {
                            GlassButton(
                                label = if (shown.played) "Watched ✓" else "Mark watched",
                                modifier = lead(),
                                onClick = {
                                    val next = !shown.played
                                    scope.launch {
                                        try {
                                            client.setPlayed(shown.id, next)
                                            item = client.item(shown.id, shown.duplicateIds)
                                        } catch (e: Exception) { /* leave the button as it was */ }
                                    }
                                },
                            )
                            GlassButton(
                                label = if (shown.favorite) "★ Favourite" else "☆ Favourite",
                                onClick = {
                                    val next = !shown.favorite
                                    scope.launch {
                                        try {
                                            client.setFavorite(shown.id, next)
                                            item = client.item(shown.id, shown.duplicateIds)
                                        } catch (e: Exception) { /* leave the button as it was */ }
                                    }
                                },
                            )
                        }
                    }
                    note?.let { Text(it, color = Palette.TextMuted, fontSize = 11.sp) }
                }
            }

            if (shown != null && shown.isSeries && seasons.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                        items(seasons) { s -> GlassButton(s.name, onClick = { season = s }, primary = s.id == season?.id) }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                        items(episodes) { ep ->
                            Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ItemCard(ep, client, 160.dp, 90.dp, true, onClick = { onPlay(ep) })
                                Text(
                                    (ep.indexNumber?.let { "$it. " } ?: "") + ep.name,
                                    color = Palette.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (t != null && t.cast.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Cast")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                            items(t.cast) { p ->
                                FocusBox(
                                    onClick = { onPerson(p) },
                                    modifier = Modifier.width(108.dp).height(156.dp),
                                    edge = UiSettings.posterBorder,
                                ) { _ ->
                                    Column(
                                        Modifier.fillMaxSize()
                                            .background(Palette.GlassFill)
                                            .background(Palette.Accent.copy(alpha = 0.10f))
                                            .padding(horizontal = 6.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(Modifier.size(62.dp).clip(CircleShape).background(Palette.GlassFillStrong)) {
                                            if (p.photo != null) AsyncImage(model = p.photo, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                        Text(
                                            p.name, color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                        p.role?.let {
                                            Text(it, color = Palette.TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (franchise.size > 1) {
                item {
                    val position = franchise.indexOfFirst { it.tmdbId == tmdbId }
                    Column {
                        SectionTitle(
                            (t?.collectionName ?: "Franchise") + if (position >= 0) "   ·   This is part ${position + 1} of ${franchise.size}" else "",
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                            items(franchise) { part ->
                                val here = index["movie:${part.tmdbId}"]
                                PosterTile(
                                    url = part.poster, title = part.title + (part.year?.let { " ($it)" } ?: ""),
                                    onServer = here != null, current = part.tmdbId == tmdbId,
                                    onClick = {
                                        if (part.tmdbId != tmdbId) onOpen(TitleRef("movie", part.tmdbId, here, part.title, part.backdrop, part.poster))
                                    },
                                )
                            }
                        }
                    }
                }
            }

            val moreTmdb = t?.more ?: emptyList()
            if (moreTmdb.isNotEmpty() || serverSimilar.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("More like this")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                            if (moreTmdb.isNotEmpty()) {
                                items(moreTmdb) { d ->
                                    val here = index["${d.kind}:${d.tmdbId}"]
                                    PosterTile(
                                        url = d.poster, title = d.title, onServer = here != null, current = false,
                                        onClick = { onOpen(TitleRef(d.kind, d.tmdbId, here, d.title, d.backdrop, d.poster)) },
                                    )
                                }
                            } else {
                                items(serverSimilar) { s -> ItemCard(s, client, 100.dp, 150.dp, false, onClick = { onOpen(TitleRef.of(s)) }) }
                            }
                        }
                    }
                }
            }

            val extras = t?.videos ?: emptyList()
            if (extras.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Trailers and extras")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                            items(extras) { v ->
                                Column(Modifier.width(176.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FocusBox(onClick = { onTrailer(listOf(v.key)) }, modifier = Modifier.width(176.dp).height(99.dp), edge = UiSettings.posterBorder) { _ ->
                                        Box(Modifier.fillMaxSize().background(Palette.GlassFill)) {
                                            AsyncImage(
                                                model = "https://i.ytimg.com/vi/${v.key}/mqdefault.jpg", contentDescription = v.name,
                                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                            )
                                            Text("▶", color = Palette.Text, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center))
                                        }
                                    }
                                    Text(v.type.uppercase(), color = Palette.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(v.name, color = Palette.Text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Who to show a page for. */
data class PersonRef(val id: Int, val name: String, val photo: String?)

/** An actor's page: big photo on the left, bio on the right, and their films and shows underneath. */
@Composable
fun PersonScreen(
    ref: PersonRef,
    client: JellyfinClient,
    onOpen: (TitleRef) -> Unit,
) {
    var info by remember { mutableStateOf<com.fancyfin.tv.data.PersonInfo?>(null) }
    var index by remember { mutableStateOf<Map<String, Item>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(ref.id) {
        index = try { client.serverIndex() } catch (e: Exception) { emptyMap() }
        info = try { client.person(ref.id) } catch (e: Exception) { null }
        loaded = true
    }
    LaunchedEffect(loaded) {
        if (loaded) {
            delay(250)
            try { first.requestFocus() } catch (e: Exception) { /* nothing to focus */ }
        }
    }

    val photo = info?.photo ?: ref.photo
    Box(Modifier.fillMaxSize()) {
        Backdrop(photo)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(Modifier.padding(start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(
                        Modifier.width(190.dp).height(285.dp)
                            .clip(RoundedCornerShape(UiSettings.corner.tile))
                            .background(Palette.GlassFill),
                    ) {
                        if (photo != null) AsyncImage(model = photo, contentDescription = ref.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(info?.name ?: ref.name, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        info?.born?.let { Text(it, color = Palette.Accent, fontSize = 12.sp) }
                        val bio = info?.biography
                        when {
                            bio != null -> Text(bio, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 12, overflow = TextOverflow.Ellipsis)
                            loaded -> Text("No biography available.", color = Palette.TextMuted, fontSize = 13.sp)
                            else -> Text("Loading…", color = Palette.TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
            val credits = info?.credits ?: emptyList()
            if (credits.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle("Known for")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                            items(credits.size) { i ->
                                val d = credits[i]
                                val here = index["${d.kind}:${d.tmdbId}"]
                                PosterTile(
                                    url = d.poster, title = d.title, onServer = here != null, current = false,
                                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                                    onClick = { onOpen(TitleRef(d.kind, d.tmdbId, here, d.title, d.backdrop, d.poster)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
