package com.nova.browser.features.browser.web

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Chrome-layer callbacks: progress, dialogs, permissions, fullscreen, file picking. */
interface ChromeCallbacks {
    fun onProgressChanged(progress: Int)
    fun onTitleChanged(title: String)
    fun onIconChanged(icon: Bitmap)
    fun onConsoleMessage(level: String, message: String, source: String, line: Int)
    fun onJsDialog(type: String, message: String, defaultValue: String?, respond: (Boolean, String?) -> Unit)
    fun onGeolocationRequest(origin: String, grant: (Boolean) -> Unit)
    fun onPermissionRequest(resources: List<String>, grant: (Boolean) -> Unit)
    fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onHideCustomView()
    fun onFileChooser(callback: ValueCallback<Array<Uri>>, acceptTypes: Array<String>, allowMultiple: Boolean): Boolean
    fun onCreateWindow(isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean
    fun onCloseWindow()
}

class NovaWebChromeClient(
    private val callbacks: ChromeCallbacks
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        callbacks.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) callbacks.onTitleChanged(title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        if (icon != null) callbacks.onIconChanged(icon)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val level = when (consoleMessage.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> "error"
            ConsoleMessage.MessageLevel.WARNING -> "warn"
            ConsoleMessage.MessageLevel.DEBUG -> "debug"
            ConsoleMessage.MessageLevel.TIP -> "info"
            else -> "log"
        }
        callbacks.onConsoleMessage(
            level,
            consoleMessage.message().orEmpty(),
            consoleMessage.sourceId().orEmpty(),
            consoleMessage.lineNumber()
        )
        return true
    }

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        callbacks.onJsDialog("alert", message.orEmpty(), null) { confirmed, _ ->
            try {
                if (confirmed) result.confirm() else result.cancel()
            } catch (e: Exception) { /* already handled */ }
        }
        return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        callbacks.onJsDialog("confirm", message.orEmpty(), null) { confirmed, _ ->
            try {
                if (confirmed) result.confirm() else result.cancel()
            } catch (e: Exception) { /* already handled */ }
        }
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult
    ): Boolean {
        callbacks.onJsDialog("prompt", message.orEmpty(), defaultValue) { confirmed, value ->
            try {
                if (confirmed) result.confirm(value.orEmpty()) else result.cancel()
            } catch (e: Exception) { /* already handled */ }
        }
        return true
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callbacks.onGeolocationRequest(origin) { granted ->
            try {
                callback.invoke(origin, granted, false)
            } catch (e: Exception) { /* already handled */ }
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val resources = try { request.resources.toList() } catch (e: Exception) { emptyList() }
        callbacks.onPermissionRequest(resources) { granted ->
            try {
                if (granted) request.grant(request.resources) else request.deny()
            } catch (e: Exception) { /* already handled */ }
        }
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        callbacks.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        callbacks.onHideCustomView()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        val acceptTypes = try {
            fileChooserParams.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray() ?: arrayOf("*/*")
        } catch (e: Exception) {
            arrayOf("*/*")
        }
        val multiple = try {
            fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
        } catch (e: Exception) {
            false
        }
        return callbacks.onFileChooser(
            filePathCallback,
            if (acceptTypes.isEmpty()) arrayOf("*/*") else acceptTypes,
            multiple
        )
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean = callbacks.onCreateWindow(isUserGesture, resultMsg)

    override fun onCloseWindow(window: WebView) {
        callbacks.onCloseWindow()
    }
}
