package com.fancyfin.tv.data

import org.json.JSONArray
import org.json.JSONObject

/** A movie, series, season or episode, with the parts of it the screens need. */
data class Item(
    val id: String,
    val name: String,
    val type: String,
    val overview: String?,
    val tagline: String?,
    val year: Int?,
    val rating: Double?,
    val officialRating: String?,
    val runtimeTicks: Long,
    val genres: List<String>,
    val primaryTag: String?,
    val thumbTag: String?,
    val logoTag: String?,
    val backdropTag: String?,
    val parentBackdropId: String?,
    val parentBackdropTag: String?,
    val parentLogoId: String?,
    val parentLogoTag: String?,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val positionTicks: Long,
    val played: Boolean,
    val favorite: Boolean,
    val unplayedCount: Int?,
    val childCount: Int?,
    val tmdbId: String?,
    /** Other copies of the same movie or show on the server (other folders), folded into this one. */
    val duplicateIds: List<String> = emptyList(),
) {
    val isEpisode: Boolean get() = type == "Episode"
    val isSeries: Boolean get() = type == "Series"
    val isMovie: Boolean get() = type == "Movie"

    /** How far through it you are, 0 to 1, or null when you haven't started. */
    val progress: Float?
        get() = if (runtimeTicks > 0 && positionTicks > 0) {
            (positionTicks.toDouble() / runtimeTicks).coerceIn(0.0, 1.0).toFloat()
        } else {
            null
        }

    /** "S2:E5" for an episode. */
    val episodeLabel: String?
        get() = if (isEpisode && parentIndexNumber != null && indexNumber != null) {
            "S$parentIndexNumber:E$indexNumber"
        } else {
            null
        }

    val runtimeMinutes: Int get() = (runtimeTicks / 600_000_000L).toInt()
}

private fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotEmpty() } else null

private fun JSONObject.intOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONArray?.firstString(): String? =
    if (this != null && length() > 0) optString(0).takeIf { it.isNotEmpty() } else null

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) out.add(optString(i))
    return out
}

fun parseItem(o: JSONObject): Item {
    val tags = o.optJSONObject("ImageTags")
    val user = o.optJSONObject("UserData")
    return Item(
        id = o.getString("Id"),
        name = o.optString("Name"),
        type = o.optString("Type"),
        overview = o.str("Overview"),
        tagline = o.optJSONArray("Taglines").firstString(),
        year = o.intOrNull("ProductionYear"),
        rating = if (o.has("CommunityRating") && !o.isNull("CommunityRating")) o.getDouble("CommunityRating") else null,
        officialRating = o.str("OfficialRating"),
        runtimeTicks = o.optLong("RunTimeTicks"),
        genres = o.optJSONArray("Genres").strings(),
        primaryTag = tags?.str("Primary"),
        thumbTag = tags?.str("Thumb"),
        logoTag = tags?.str("Logo"),
        backdropTag = o.optJSONArray("BackdropImageTags").firstString(),
        parentBackdropId = o.str("ParentBackdropItemId"),
        parentBackdropTag = o.optJSONArray("ParentBackdropImageTags").firstString(),
        parentLogoId = o.str("ParentLogoItemId"),
        parentLogoTag = o.str("ParentLogoImageTag"),
        seriesId = o.str("SeriesId"),
        seriesName = o.str("SeriesName"),
        seasonId = o.str("SeasonId"),
        indexNumber = o.intOrNull("IndexNumber"),
        parentIndexNumber = o.intOrNull("ParentIndexNumber"),
        positionTicks = user?.optLong("PlaybackPositionTicks") ?: 0L,
        played = user?.optBoolean("Played") ?: false,
        favorite = user?.optBoolean("IsFavorite") ?: false,
        unplayedCount = user?.intOrNull("UnplayedItemCount"),
        childCount = o.intOrNull("ChildCount"),
        tmdbId = o.optJSONObject("ProviderIds")?.str("Tmdb"),
    )
}

fun parseItems(array: JSONArray?): List<Item> {
    if (array == null) return emptyList()
    val out = ArrayList<Item>(array.length())
    for (i in 0 until array.length()) {
        val entry = array.optJSONObject(i) ?: continue
        if (entry.has("Id")) out.add(parseItem(entry))
    }
    return out
}

/** One playable copy of a movie: a file with its own picture, sound and size. */
data class MediaVersion(
    val item: Item,
    val sourceId: String,
    val resolution: String,
    val range: String,
    val audio: String,
    val codec: String,
    val container: String,
    val sizeBytes: Long,
    val fileName: String,
)

private fun resolutionLabel(width: Int, height: Int): String = when {
    width >= 3200 || height >= 2000 -> "2160p (4K)"
    width >= 1800 || height >= 1000 -> "1080p"
    width >= 1200 || height >= 700 -> "720p"
    height > 0 -> "${height}p"
    else -> "Unknown"
}

private fun rangeLabel(video: JSONObject?): String {
    if (video == null) return "SDR"
    val type = video.optString("VideoRangeType").uppercase()
    val title = video.optString("DisplayTitle")
    return when {
        type.startsWith("DOVI") || title.contains("Dolby Vision", true) ->
            if (type.contains("HDR10")) "Dolby Vision / HDR10" else "Dolby Vision"
        type == "HDR10PLUS" -> "HDR10+"
        type == "HDR10" -> "HDR10"
        type == "HLG" -> "HLG"
        video.optString("VideoRange").equals("HDR", true) -> "HDR"
        else -> "SDR"
    }
}

private fun audioLabel(audio: JSONObject?): String {
    if (audio == null) return "No audio"
    val codec = audio.optString("Codec").lowercase()
    val profile = audio.optString("Profile")
    val title = audio.optString("DisplayTitle")
    val spatial = audio.optString("AudioSpatialFormat")
    val atmos = spatial.contains("Atmos", true) || profile.contains("Atmos", true) || title.contains("Atmos", true)
    val dtsx = spatial.contains("DTSX", true) || profile.contains("DTS:X", true) || title.contains("DTS:X", true)
    val name = when {
        codec == "truehd" -> if (atmos) "Dolby TrueHD Atmos" else "Dolby TrueHD"
        codec == "eac3" -> if (atmos) "Dolby Atmos" else "Dolby Digital Plus"
        codec == "ac3" -> "Dolby Digital"
        codec == "dts" -> when {
            dtsx -> "DTS:X"
            profile.contains("MA", true) -> "DTS-HD MA"
            profile.contains("HRA", true) -> "DTS-HD HRA"
            else -> "DTS"
        }
        codec == "aac" -> "AAC"
        codec == "flac" -> "FLAC"
        codec == "opus" -> "Opus"
        codec.startsWith("pcm") -> "PCM"
        else -> codec.uppercase()
    }
    val channels = when (audio.optInt("Channels")) {
        8 -> "7.1"
        7 -> "6.1"
        6 -> "5.1"
        2 -> "2.0"
        1 -> "1.0"
        else -> audio.optString("ChannelLayout")
    }
    return if (channels.isNotEmpty()) "$name $channels" else name
}

fun parseVersions(item: Item, json: JSONObject): List<MediaVersion> {
    val sources = json.optJSONArray("MediaSources") ?: return emptyList()
    val out = ArrayList<MediaVersion>()
    for (i in 0 until sources.length()) {
        val source = sources.getJSONObject(i)
        val streams = source.optJSONArray("MediaStreams")
        var video: JSONObject? = null
        val audios = ArrayList<JSONObject>()
        if (streams != null) {
            for (j in 0 until streams.length()) {
                val st = streams.getJSONObject(j)
                when (st.optString("Type")) {
                    "Video" -> if (video == null) video = st
                    "Audio" -> audios.add(st)
                }
            }
        }
        val bestAudio = audios.firstOrNull { it.optBoolean("IsDefault") } ?: audios.maxByOrNull { it.optInt("Channels") }
        val vCodec = when (video?.optString("Codec")?.lowercase()) {
            "hevc", "h265" -> "HEVC"
            "h264", "avc" -> "H.264"
            "av1" -> "AV1"
            "vp9" -> "VP9"
            null -> ""
            else -> video?.optString("Codec")?.uppercase().orEmpty()
        }
        val path = source.optString("Path")
        out.add(
            MediaVersion(
                item = item,
                sourceId = source.getString("Id"),
                resolution = resolutionLabel(video?.optInt("Width") ?: 0, video?.optInt("Height") ?: 0),
                range = rangeLabel(video),
                audio = audioLabel(bestAudio),
                codec = vCodec,
                container = source.optString("Container").uppercase(),
                sizeBytes = source.optLong("Size"),
                fileName = path.substringAfterLast('/').substringAfterLast('\\'),
            ),
        )
    }
    return out
}

/**
 * Folds copies of the same movie or show (the same TMDB id, or failing that the same name and year)
 * into one entry, keeping the order of the first copy. The other copies' ids are kept on it.
 */
fun consolidate(items: List<Item>): List<Item> {
    val groups = LinkedHashMap<String, MutableList<Item>>()
    var loose = 0
    for (item in items) {
        val key = when {
            !(item.isMovie || item.isSeries) -> "x${loose++}"
            item.tmdbId != null -> "${item.type}:t${item.tmdbId}"
            item.year != null -> "${item.type}:n${item.name.lowercase().trim()}:${item.year}"
            else -> "x${loose++}"
        }
        groups.getOrPut(key) { ArrayList() }.add(item)
    }
    return groups.values.map { group ->
        if (group.size == 1) {
            group[0]
        } else {
            val rep = group.firstOrNull { it.primaryTag != null } ?: group[0]
            rep.copy(duplicateIds = group.filter { it.id != rep.id }.map { it.id })
        }
    }
}
