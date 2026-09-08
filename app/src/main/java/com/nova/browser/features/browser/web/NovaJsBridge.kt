package com.nova.browser.features.browser.web

import android.webkit.JavascriptInterface

/**
 * The single @JavascriptInterface exposed to pages. It only accepts data
 * *from* the page (console lines, agent callbacks) and never exposes any
 * privileged app capability, so a hostile page gains nothing.
 */
class NovaJsBridge(
    private val onConsoleLine: (level: String, message: String) -> Unit,
    private val onAgentEvent: (event: String, payload: String) -> Unit = { _, _ -> }
) {
    @JavascriptInterface
    fun onConsole(level: String?, message: String?) {
        if (message.isNullOrBlank()) return
        onConsoleLine(level.orEmpty().ifBlank { "log" }, message.take(4000))
    }

    @JavascriptInterface
    fun onAgent(event: String?, payload: String?) {
        if (event.isNullOrBlank()) return
        onAgentEvent(event, payload.orEmpty().take(8000))
    }

    companion object {
        const val NAME = "NovaBridge"
    }
}
