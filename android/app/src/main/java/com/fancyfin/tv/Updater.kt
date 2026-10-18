package com.fancyfin.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates the TV app from inside the app. It looks at this project's GitHub releases for one tagged
 * tv-v<version> with an .apk attached, offers it when it is newer, downloads it and hands it to
 * Android's installer. Android only accepts the update if it is signed with the same key as the app
 * that is installed, so a release from anywhere else can't replace it.
 */
object Updater {
    private const val TAG_PREFIX = "tv-v"
    private const val KEY_LAST_CHECK = "lastUpdateCheck"
    private const val QUIET_INTERVAL_MS = 6L * 60 * 60 * 1000

    private class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val size: Long,
    )

    /** At start-up: look, but at most every few hours, and say nothing unless there is an update. */
    fun checkQuietly(activity: Activity) {
        val prefs = activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < QUIET_INTERVAL_MS) return
        check(activity, manual = false)
    }

    /** Look for an update. A manual check also says when there isn't one, or when it failed. */
    fun check(activity: Activity, manual: Boolean) {
        if (manual) toast(activity, "Checking for updates…")
        Thread {
            var release: Release? = null
            var failed = false
            try {
                release = latestRelease()
            } catch (e: Exception) {
                failed = true
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (!failed) {
                    activity.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                        .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                }
                val found = release
                when {
                    failed -> if (manual) {
                        toast(activity, "Couldn't check for updates. Is this device online?")
                    }
                    found != null && isNewer(found.version, BuildConfig.VERSION_NAME) -> offer(activity, found)
                    manual -> toast(activity, "Fancyfin TV is up to date (" + BuildConfig.VERSION_NAME + ").")
                }
            }
        }.start()
    }

    /** True when version a is newer than version b, comparing major, minor and patch. */
    fun isNewer(a: String, b: String): Boolean {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until 3) {
            val left = x.getOrElse(i) { 0 }
            val right = y.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    private fun open(address: String): HttpURLConnection {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "FancyfinTV/" + BuildConfig.VERSION_NAME)
        return connection
    }

    /** The newest published tv-v release that has an APK, or null if there is none. */
    private fun latestRelease(): Release? {
        val connection = open("https://api.github.com/repos/" + BuildConfig.UPDATE_REPO + "/releases?per_page=30")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val body = try {
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }

        val releases = JSONArray(body)
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            val tag = release.optString("tag_name")
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            if (!tag.startsWith(TAG_PREFIX)) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (asset.optString("name").endsWith(".apk")) {
                    return Release(
                        version = tag.removePrefix(TAG_PREFIX),
                        notes = release.optString("body"),
                        apkUrl = asset.getString("browser_download_url"),
                        size = asset.optLong("size"),
                    )
                }
            }
        }
        return null
    }

    private fun offer(activity: Activity, release: Release) {
        val notes = release.notes.trim().let { if (it.length > 600) it.take(600) + "…" else it }
        val message = "Fancyfin TV " + release.version + " is ready. You have " + BuildConfig.VERSION_NAME + "." +
            if (notes.isNotEmpty()) "\n\n$notes" else ""
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(message)
            .setPositiveButton("Update now") { _, _ -> start(activity, release) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun canInstall(activity: Activity): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    private fun start(activity: Activity, release: Release) {
        if (!canInstall(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("Allow updates")
                .setMessage(
                    "Android needs your OK before Fancyfin can install updates. On the next screen turn on " +
                        "\"Allow from this source\", go back, then choose Check for updates again.",
                )
                .setPositiveButton("Open settings") { _, _ ->
                    try {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.packageName),
                            ),
                        )
                    } catch (e: Exception) {
                        toast(activity, "Open Settings, Apps, Special access, Install unknown apps, Fancyfin.")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        download(activity, release)
    }

    private fun download(activity: Activity, release: Release) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Downloading Fancyfin TV " + release.version)
            .setMessage("Starting…")
            .setCancelable(false)
            .show()

        Thread {
            try {
                val dir = File(activity.cacheDir, "updates")
                dir.mkdirs()
                val file = File(dir, "fancyfin-tv.apk")
                val connection = open(release.apkUrl)
                val total = if (connection.contentLengthLong > 0) connection.contentLengthLong else release.size
                try {
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var shown = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                val percent = if (total > 0) (done * 100 / total).toInt() else -1
                                if (percent != shown) {
                                    shown = percent
                                    activity.runOnUiThread {
                                        dialog.setMessage(if (percent >= 0) "$percent%" else "${done / 1024} KB")
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (release.size > 0 && file.length() != release.size) throw IOException("Incomplete download")
                activity.runOnUiThread {
                    dialog.dismiss()
                    install(activity, file)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    toast(activity, "The download failed. Try again later.")
                }
            }
        }.start()
    }

    private fun install(activity: Activity, file: File) {
        try {
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(intent)
        } catch (e: Exception) {
            toast(activity, "Couldn't open the installer.")
        }
    }

    private fun toast(activity: Activity, message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
