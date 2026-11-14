package com.fancyfin.tv.data

import android.content.Context
import com.fancyfin.tv.MainActivity
import java.util.UUID

/** What the app remembers between launches: which Fancyfin, who is signed in, and this device's id. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

    /** The Fancyfin container's address. Jellyfin, Radarr, Sonarr and TMDB are all reached through it. */
    var fancyfin: String?
        get() = prefs.getString(KEY_FANCYFIN, null)
        set(value) = prefs.edit().putString(KEY_FANCYFIN, value).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    /** Jellyfin lists each signed-in device by this id, so it is made once and kept. */
    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            val made = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, made).apply()
            return made
        }

    private val cache = context.getSharedPreferences("collections_cache", Context.MODE_PRIVATE)

    /** Small lookups worth keeping between launches (which TMDB collection a movie belongs to, and so on). */
    fun cacheGet(key: String): String? = cache.getString(key, null)

    fun cachePut(values: Map<String, String>) {
        val edit = cache.edit()
        values.forEach { (k, v) -> edit.putString(k, v) }
        edit.apply()
    }

    fun signOut() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).remove(KEY_USER_NAME).apply()
    }

    private companion object {
        // Not "server": the old web-view version of the app kept the Fancyfin address under that name.
        const val KEY_FANCYFIN = "fancyfin_url"
        const val KEY_TOKEN = "jellyfin_token"
        const val KEY_USER_ID = "jellyfin_user_id"
        const val KEY_USER_NAME = "jellyfin_user_name"
        const val KEY_DEVICE_ID = "device_id"
    }
}
