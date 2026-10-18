package com.fancyfin.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Fancyfin for Android TV: a full-screen WebView that shows the Fancyfin web app from your own
 * server. The web app updates whenever your container does; only this small shell needs the
 * in-app updater (see Updater.kt).
 */
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private var webView: WebView? = null
    private var errorView: View? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        root.setBackgroundColor(BACKGROUND)
        setContentView(root)

        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SERVER, null)
        if (saved.isNullOrBlank()) showSetup() else showWeb(saved)

        Updater.checkQuietly(this)
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    // ---- The web app ----

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWeb(address: String) {
        root.removeAllViews()
        errorView = null
        customView = null

        val view = WebView(this)
        view.setBackgroundColor(BACKGROUND)
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = false
        // The web app looks for this to switch on its TV layout and the TV app settings.
        settings.userAgentString = settings.userAgentString + " FancyfinTV/" + BuildConfig.VERSION_NAME

        view.addJavascriptInterface(FancyfinBridge(this), "FancyfinTV")

        val host = Uri.parse(address).host
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Stay on the Fancyfin server; a TV has no browser to hand other links to.
                return request.url.host != host
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    showError(address, error.description?.toString().orEmpty())
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                webView?.visibility = View.GONE
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        webView = view
        view.loadUrl(address)
        view.requestFocus()
    }

    /** Leave a full-screen video or page and go back to the app. */
    private fun hideCustomView() {
        customView?.let { root.removeView(it) }
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        webView?.visibility = View.VISIBLE
    }

    // ---- First run: which server? ----

    private fun showSetup(problem: String? = null) {
        webView?.destroy()
        webView = null
        errorView = null
        customView = null
        root.removeAllViews()

        val current = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SERVER, null)

        val column = column()
        column.addView(text("Fancyfin", 40f, Color.WHITE))
        column.addView(
            text(
                "Enter the address of your Fancyfin server, for example http://192.168.1.20:8282",
                18f,
                MUTED,
                top = 12,
            ),
        )

        val input = EditText(this)
        input.setText(current ?: "http://")
        input.setSelection(input.text.length)
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.imeOptions = EditorInfo.IME_ACTION_GO
        input.setTextColor(Color.WHITE)
        input.textSize = 22f
        column.addView(input, matchWidth(top = 28))

        val message = text(problem.orEmpty(), 16f, WARNING, top = 12)
        column.addView(message)

        fun submit() {
            val address = normalizeAddress(input.text.toString())
            if (address == null) {
                message.text = "That doesn't look like an address. Try http://192.168.1.20:8282"
                return
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SERVER, address).apply()
            showWeb(address)
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit()
                true
            } else {
                false
            }
        }

        val connect = Button(this)
        connect.text = "Connect"
        connect.setOnClickListener { submit() }
        column.addView(connect, wrap(top = 20))

        root.addView(column)
        input.requestFocus()
    }

    // ---- When the server can't be reached ----

    private fun showError(address: String, detail: String) {
        if (errorView != null) return
        val column = column()
        column.setBackgroundColor(BACKGROUND)
        column.isClickable = true
        column.addView(text("Can't reach Fancyfin", 32f, Color.WHITE))
        column.addView(
            text("Tried $address" + if (detail.isNotBlank()) " ($detail)" else "", 18f, MUTED, top = 12),
        )
        column.addView(
            text("Check that the server is running and this device is on the same network.", 18f, MUTED, top = 8),
        )

        val retry = Button(this)
        retry.text = "Try again"
        retry.setOnClickListener {
            root.removeView(column)
            errorView = null
            webView?.loadUrl(address)
        }
        column.addView(retry, wrap(top = 24))

        val change = Button(this)
        change.text = "Change address"
        change.setOnClickListener { showSetup() }
        column.addView(change, wrap(top = 8))

        errorView = column
        root.addView(column)
        retry.requestFocus()
    }

    // ---- Remote control ----

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val web = webView
        when {
            customView != null -> hideCustomView()
            errorView == null && web != null && web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** A small menu that works even when the web app doesn't (Menu key on remotes that have one). */
    fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Fancyfin TV " + BuildConfig.VERSION_NAME)
            .setItems(arrayOf("Check for updates", "Change server address", "Reload")) { _, which ->
                when (which) {
                    0 -> Updater.check(this, manual = true)
                    1 -> showSetup()
                    else -> webView?.reload()
                }
            }
            .show()
    }

    fun changeServer() = showSetup()

    // ---- Small layout helpers (no XML layouts needed) ----

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun column(): LinearLayout {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        column.setPadding(dp(96), dp(48), dp(96), dp(48))
        column.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return column
    }

    private fun text(value: String, size: Float, color: Int, top: Int = 0): TextView {
        val view = TextView(this)
        view.text = value
        view.textSize = size
        view.setTextColor(color)
        view.gravity = Gravity.START
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(top) }
        return view
    }

    private fun matchWidth(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun wrap(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    companion object {
        const val PREFS = "fancyfin"
        const val KEY_SERVER = "server"
        private val BACKGROUND = Color.parseColor("#07080B")
        private val MUTED = Color.parseColor("#A7ADBA")
        private val WARNING = Color.parseColor("#FF8A80")

        /** "192.168.1.20:8282" becomes "http://192.168.1.20:8282"; nonsense gives null. */
        fun normalizeAddress(raw: String): String? {
            var text = raw.trim()
            if (text.isEmpty()) return null
            if (!text.contains("://")) text = "http://$text"
            val uri = Uri.parse(text)
            if (uri.scheme != "http" && uri.scheme != "https") return null
            if (uri.host.isNullOrBlank()) return null
            return text.trimEnd('/')
        }
    }
}

/** What the web app can ask of the TV app (window.FancyfinTV in the page). */
class FancyfinBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun version(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun checkForUpdates() {
        activity.runOnUiThread { Updater.check(activity, manual = true) }
    }

    @JavascriptInterface
    fun changeServer() {
        activity.runOnUiThread { activity.changeServer() }
    }
}
