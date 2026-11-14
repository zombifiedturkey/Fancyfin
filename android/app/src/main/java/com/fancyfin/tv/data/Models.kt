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
