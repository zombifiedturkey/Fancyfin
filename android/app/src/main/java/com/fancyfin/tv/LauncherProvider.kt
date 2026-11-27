package com.fancyfin.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.fancyfin.tv.ui.LauncherRows

/**
 * Read-only list of Continue watching and Up next for the custom launcher:
 * content://com.fancyfin.tv.launcher/rows, one row per title, written whenever Home opens.
 * Poster addresses carry no login: Jellyfin serves pictures without one.
 */
class LauncherProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.pathSegments.firstOrNull() != "rows") return null
        val cursor = MatrixCursor(arrayOf("kind", "sort", "item_id", "title", "subtitle", "poster_url", "progress", "play_uri"))
        val context = context ?: return cursor
        LauncherRows.load(context).forEach {
            cursor.addRow(arrayOf<Any>(it.kind, it.sort, it.itemId, it.title, it.subtitle, it.posterUrl, it.progress, "fancyfin://play/" + it.itemId))
        }
        cursor.setNotificationUri(context.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.com.fancyfin.tv.launcher.row"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
