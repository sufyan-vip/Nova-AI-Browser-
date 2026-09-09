package com.nova.browser.features.browser.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.privacy.engine.TrackerBlocker
import java.io.ByteArrayInputStream

/** Callbacks the browser layer reacts to. */
interface BrowserCallbacks {
    fun onPageStarted(url: String, favicon: Bitmap?)
    fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgress(progress: Int)
    fun onTitle(title: String)
    fun onFavicon(icon: Bitmap)
    fun onError(url: String, code: Int, description: String)
    fun onSslError(url: String, error: String, proceed: (Boolean) -> Unit)
    fun onHttpAuth(host: String, realm: String, submit: (String?, String?) -> Unit)
    fun onExternalScheme(url: String): Boolean
    fun onTrackerBlocked(domain: String, category: String)
    fun onNetworkRequest(url: String, method: String, resourceType: String)
    fun onDownloadRequested(url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long)
    fun onFormResubmission(): Boolean
}

/**
 * WebViewClient with request interception for tracker/ad blocking, HTTPS-only
 * upgrades, tracking-parameter stripping, custom error pages and SSL warnings.
 */
class NovaWebViewClient(
    private val callbacks: BrowserCallbacks,
    private val trackerBlocker: TrackerBlocker,
    private val configProvider: () -> WebConfig
) : WebViewClient() {

    data class WebConfig(
        val blockTrackers: Boolean = true,
        val blockAds: Boolean = true,
        val httpsOnly: Boolean = true,
        val stripTrackingParams: Boolean = true,
        val fingerprintProtection: Boolean = true,
        val doNotTrack: Boolean = true,
        val globalPrivacyControl: Boolean = true,
        val isPrivate: Boolean = false,
        val devToolsEnabled: Boolean = false
    )

    private val blockedResponse: WebResourceResponse
        get() = WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString().orEmpty()
        if (url.isBlank()) return false

        val scheme = request.url?.scheme?.lowercase().orEmpty()
        if (scheme !in setOf("http", "https", "about", "data", "file", "javascript", "blob")) {
            // intent://, mailto:, tel:, market:, whatsapp:, …
            return callbacks.onExternalScheme(url)
        }

        val config = configProvider()
        var target = url
        if (config.stripTrackingParams) target = UrlUtils.stripTrackingParams(target)
        if (config.httpsOnly && UrlUtils.isHttp(target) && !isLocalHost(target)) {
            target = UrlUtils.upgradeToHttps(target)
        }
        if (target != url) {
            view.loadUrl(target, extraHeaders(config))
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        val config = configProvider()
        val pageUrl = try { view.url.orEmpty() } catch (e: Exception) { "" }

        if (config.devToolsEnabled) {
            callbacks.onNetworkRequest(url, request.method ?: "GET", resourceTypeOf(url, request))
        }

        val blockingEnabled = config.blockTrackers || config.blockAds
        val match = trackerBlocker.shouldBlock(url, pageUrl, blockingEnabled)
        if (match != null) {
            val isAd = match.category == TrackerBlocker.Category.ADVERTISING
            val allowed = (isAd && !config.blockAds) || (!isAd && !config.blockTrackers)
            if (!allowed) {
                callbacks.onTrackerBlocked(match.domain, match.category.name)
                return blockedResponse
            }
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        trackerBlocker.resetPage(url)
        callbacks.onPageStarted(url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        val config = configProvider()
        try {
            if (config.fingerprintProtection) {
                view.evaluateJavascript(JsScripts.FINGERPRINT_PROTECTION, null)
            }
            if (config.blockAds) {
                view.evaluateJavascript(JsScripts.COSMETIC_AD_BLOCK, null)
            }
            if (config.devToolsEnabled) {
                view.evaluateJavascript(JsScripts.CONSOLE_HOOK, null)
            }
        } catch (e: Exception) {
            // Injection failures must not break navigation.
        }
        callbacks.onPageFinished(url, view.title, view.canGoBack(), view.canGoForward())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        callbacks.onPageFinished(url, view.title, view.canGoBack(), view.canGoForward())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        // Only surface main-frame failures; subresource errors are noise.
        if (!request.isForMainFrame) return
        val description = try { error.description?.toString().orEmpty() } catch (e: Exception) { "" }
        val code = try { error.errorCode } catch (e: Exception) { -1 }
        val url = request.url?.toString().orEmpty()
        callbacks.onError(url, code, description.ifBlank { "The page could not be loaded" })
        try {
            view.loadDataWithBaseURL(
                null,
                ErrorPages.network(url, description.ifBlank { "The page could not be loaded" }),
                "text/html",
                "utf-8",
                null
            )
        } catch (e: Exception) {
            // Ignore; the callback already informed the UI.
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (!request.isForMainFrame) return
        val status = try { errorResponse.statusCode } catch (e: Exception) { 0 }
        if (status < 400) return
        callbacks.onError(request.url?.toString().orEmpty(), status, "HTTP $status")
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val url = try { error.url.orEmpty() } catch (e: Exception) { view.url.orEmpty() }
        val description = when (error.primaryError) {
            SslError.SSL_EXPIRED -> "The site's security certificate has expired."
            SslError.SSL_IDMISMATCH -> "The certificate does not match this site's name."
            SslError.SSL_NOTYETVALID -> "The certificate is not valid yet."
            SslError.SSL_UNTRUSTED -> "The certificate is not from a trusted authority."
            SslError.SSL_DATE_INVALID -> "The certificate has an invalid date."
            SslError.SSL_INVALID -> "The certificate is invalid."
            else -> "There is a problem with this site's security certificate."
        }
        callbacks.onSslError(url, description) { proceed ->
            try {
                if (proceed) handler.proceed() else handler.cancel()
            } catch (e: Exception) {
                // Handler already consumed.
            }
        }
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String
    ) {
        callbacks.onHttpAuth(host, realm) { username, password ->
            try {
                if (username != null && password != null) {
                    handler.proceed(username, password)
                } else {
                    handler.cancel()
                }
            } catch (e: Exception) {
                // Handler already consumed.
            }
        }
    }

    override fun onFormResubmission(view: WebView, dontResend: android.os.Message, resend: android.os.Message) {
        try {
            if (callbacks.onFormResubmission()) resend.sendToTarget() else dontResend.sendToTarget()
        } catch (e: Exception) {
            try { dontResend.sendToTarget() } catch (e2: Exception) { /* ignore */ }
        }
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: android.webkit.RenderProcessGoneDetail
    ): Boolean {
        // Returning true prevents the app from being killed; the tab is recovered above.
        callbacks.onError(view.url.orEmpty(), -100, "The page crashed and was reloaded")
        return true
    }

    private fun isLocalHost(url: String): Boolean {
        val host = UrlUtils.host(url)
        return host == "localhost" || host == "127.0.0.1" || host.endsWith(".local") || host.startsWith("192.168.")
    }

    private fun resourceTypeOf(url: String, request: WebResourceRequest): String {
        val accept = try { request.requestHeaders?.get("Accept").orEmpty() } catch (e: Exception) { "" }
        val path = url.substringBefore('?').lowercase()
        return when {
            request.isForMainFrame -> "Document"
            path.endsWith(".js") || path.endsWith(".mjs") -> "Script"
            path.endsWith(".css") -> "Stylesheet"
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") -> "Font"
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") -> "Image"
            accept.contains("application/json") -> "XHR"
            accept.contains("text/html") -> "Document"
            else -> "Other"
        }
    }

    companion object {
        fun extraHeaders(config: WebConfig): Map<String, String> = buildMap {
            if (config.doNotTrack) put("DNT", "1")
            if (config.globalPrivacyControl) put("Sec-GPC", "1")
        }
    }
}
