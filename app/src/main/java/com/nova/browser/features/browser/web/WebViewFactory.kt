package com.nova.browser.features.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.nova.browser.core.utils.Constants

/** Creates and configures WebViews to the spec's engine requirements (05). */
object WebViewFactory {

    data class Options(
        val isPrivate: Boolean = false,
        val javaScriptEnabled: Boolean = true,
        val desktopMode: Boolean = false,
        val textZoom: Int = 100,
        val blockThirdPartyCookies: Boolean = true,
        val safeBrowsing: Boolean = true,
        val darkMode: Boolean = true
    )

    private const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36 ${Constants.DEFAULT_USER_AGENT_SUFFIX}"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, options: Options): WebView {
        val webView = WebView(context)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        apply(webView, options)
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, options: Options) {
        val settings = webView.settings
        settings.javaScriptEnabled = options.javaScriptEnabled
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.textZoom = options.textZoom.coerceIn(50, 200)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(true)
        settings.setSafeBrowsingEnabled(options.safeBrowsing)
        settings.userAgentString = if (options.desktopMode) {
            DESKTOP_UA
        } else {
            defaultUserAgent(settings.userAgentString)
        }

        // Non-deprecated dark theming via the AndroidX WebKit compat layer.
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, options.darkMode)
            }
        } catch (e: Exception) {
            // Feature detection failed — the page just renders in its own theme.
        }

        webView.isScrollbarFadingEnabled = true
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.setBackgroundColor(android.graphics.Color.parseColor("#0A0E1A"))

        configureCookies(webView, options)
    }

    fun configureCookies(webView: WebView, options: Options) {
        try {
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(!options.isPrivate)
            manager.setAcceptThirdPartyCookies(webView, !options.blockThirdPartyCookies && !options.isPrivate)
        } catch (e: Exception) {
            // Cookie manager may be unavailable during early startup.
        }
    }

    private fun defaultUserAgent(current: String?): String {
        val base = current?.takeIf { it.isNotBlank() }
            ?: "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        return if (base.contains(Constants.DEFAULT_USER_AGENT_SUFFIX)) base
        else "$base ${Constants.DEFAULT_USER_AGENT_SUFFIX}"
    }

    /** Frees a WebView completely so a hibernated tab stops using memory. */
    fun destroy(webView: WebView?) {
        if (webView == null) return
        try {
            webView.stopLoading()
            webView.onPause()
            webView.clearHistory()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (e: Exception) {
            // Already destroyed.
        }
    }

    fun clearAllCookies() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun clearCache(context: Context) {
        try {
            WebView(context).apply {
                clearCache(true)
                clearFormData()
                destroy()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
