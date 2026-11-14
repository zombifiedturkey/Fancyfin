package com.fancyfin.tv.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.fancyfin.tv.MainActivity
import org.json.JSONArray
import org.json.JSONObject

/** Genres and collections you've pinned to the side menu. Nothing shows in the menu until you pin something. */
object Pins {
    class GenrePin(val genre: String, val types: String) {
        val label: String get() = genre + when (types) { "Movie" -> "  ·  Movies"; "Series" -> "  ·  Shows"; else -> "" }
    }

    class CollectionPin(val key: String, val name: String)

    val genres = mutableStateListOf<GenrePin>()
    val collections = mutableStateListOf<CollectionPin>()
    private var prefs: SharedPreferences? = null

    fun load(context: Context) {
        val p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        prefs = p
        genres.clear()
        collections.clear()
        try {
            JSONArray(p.getString("pins_genres", "[]")).let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { genres.add(GenrePin(it.optString("g"), it.optString("t"))) }
            }
            JSONArray(p.getString("pins_collections", "[]")).let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { collections.add(CollectionPin(it.optString("k"), it.optString("n"))) }
            }
        } catch (e: Exception) {
            // Start with nothing pinned.
        }
    }

    private fun save() {
        val g = JSONArray().also { a -> genres.forEach { a.put(JSONObject().put("g", it.genre).put("t", it.types)) } }
        val c = JSONArray().also { a -> collections.forEach { a.put(JSONObject().put("k", it.key).put("n", it.name)) } }
        prefs?.edit()?.putString("pins_genres", g.toString())?.putString("pins_collections", c.toString())?.apply()
    }

    fun hasGenre(genre: String, types: String) = genres.any { it.genre == genre && it.types == types }

    fun toggleGenre(genre: String, types: String) {
        val existing = genres.firstOrNull { it.genre == genre && it.types == types }
        if (existing != null) genres.remove(existing) else genres.add(GenrePin(genre, types))
        save()
    }

    fun hasCollection(key: String) = collections.any { it.key == key }

    fun toggleCollection(key: String, name: String) {
        val existing = collections.firstOrNull { it.key == key }
        if (existing != null) collections.remove(existing) else collections.add(CollectionPin(key, name))
        save()
    }
}

/** How the menu asks the app to open a pinned genre or collection. Set by the app. */
object MenuActions {
    var genre: (String, String) -> Unit = { _, _ -> }
    var collection: (String) -> Unit = {}
}
