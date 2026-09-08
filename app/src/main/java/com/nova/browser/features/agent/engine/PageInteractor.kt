package com.nova.browser.features.agent.engine

import android.webkit.WebView
import com.google.gson.Gson
import com.nova.browser.core.utils.Constants
import com.nova.browser.features.ai.engine.ContextEngine
import com.nova.browser.features.browser.web.JsScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Executes JavaScript against the live WebView and returns parsed results.
 * All calls are main-thread dispatched and timeout-guarded.
 */
@Singleton
class PageInteractor @Inject constructor(
    private val gson: Gson,
    private val contextEngine: ContextEngine
) {
    data class JsResult(
        val ok: Boolean,
        val raw: String,
        val error: String? = null,
        val values: List<String> = emptyList(),
        val text: String = ""
    )

    private var webViewProvider: (() -> WebView?)? = null

    fun attach(provider: () -> WebView?) {
        webViewProvider = provider
    }

    fun detach() {
        webViewProvider = null
    }

    private fun webView(): WebView? = try {
        webViewProvider?.invoke()
    } catch (e: Exception) {
        null
    }

    /** Runs [script] and returns the raw (unwrapped) JSON string. */
    suspend fun evaluate(script: String, timeoutMs: Long = Constants.AGENT_ACTION_TIMEOUT_MS): String? =
        try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.Main) {
                    val view = webView() ?: return@withContext null
                    suspendCancellableCoroutine { continuation ->
                        try {
                            view.evaluateJavascript(script) { value ->
                                if (continuation.isActive) continuation.resume(value)
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            null
        }

    /** Runs [script] and parses the standard `{ok, error, ...}` envelope. */
    suspend fun run(script: String, timeoutMs: Long = Constants.AGENT_ACTION_TIMEOUT_MS): JsResult {
        val raw = evaluate(script, timeoutMs)
            ?: return JsResult(false, "", "The page didn't respond in time")
        val json = contextEngine.unwrapEvaluateResult(raw)
        return try {
            val map = gson.fromJson(json, Map::class.java) ?: return JsResult(false, json, "Empty result")
            val ok = (map["ok"] as? Boolean) ?: false
            val error = map["error"] as? String
            @Suppress("UNCHECKED_CAST")
            val values = (map["values"] as? List<Any?>)?.map { it?.toString().orEmpty() } ?: emptyList()
            val text = listOfNotNull(
                map["text"] as? String,
                map["matched"] as? String,
                map["selected"] as? String
            ).firstOrNull().orEmpty()
            JsResult(ok, json, error, values, text)
        } catch (e: Exception) {
            JsResult(false, json, "Couldn't parse the page response")
        }
    }

    /* ------------------------------ actions ------------------------------ */

    suspend fun click(selector: String) = run(JsScripts.click(selector))

    suspend fun clickText(text: String) = run(JsScripts.clickByText(text))

    suspend fun type(selector: String, value: String) = run(JsScripts.typeText(selector, value))

    suspend fun typeByLabel(label: String, value: String) = run(JsScripts.typeByLabel(label, value))

    suspend fun select(selector: String, option: String) = run(JsScripts.selectOption(selector, option))

    suspend fun setCheckbox(selector: String, checked: Boolean) =
        run(JsScripts.setCheckbox(selector, checked))

    suspend fun scroll(amount: Int) = run(JsScripts.scrollBy(amount))

    suspend fun scrollTo(selector: String) = run(JsScripts.scrollToElement(selector))

    suspend fun extract(selector: String) = run(JsScripts.extractText(selector))

    suspend fun exists(selector: String): Boolean {
        val result = run(JsScripts.elementExists(selector))
        return result.raw.contains("\"exists\":true")
    }

    /** Polls until [selector] appears or the timeout elapses. */
    suspend fun waitFor(selector: String, timeoutMs: Long = Constants.AGENT_ACTION_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (exists(selector)) return true
            delay(400)
        }
        return false
    }

    /** Reads the full page context for AI reasoning. */
    suspend fun readPage(): com.nova.browser.features.ai.engine.PageContext {
        val raw = evaluate(JsScripts.EXTRACT_CONTENT, 15_000)
        return contextEngine.parse(raw)
    }

    /** Interactive elements the planner can target. */
    suspend fun interactiveElements(): String {
        val raw = evaluate(JsScripts.EXTRACT_INTERACTIVE, 12_000) ?: return "{}"
        return contextEngine.unwrapEvaluateResult(raw)
    }

    suspend fun extractLinks(): List<String> {
        val page = readPage()
        return page.links.map { "${it.text} → ${it.url}" }
    }

    suspend fun extractTables(): List<String> {
        val page = readPage()
        return page.tables.mapIndexed { index, rows ->
            buildString {
                appendLine("Table ${index + 1}:")
                rows.forEach { row -> appendLine("| " + row.joinToString(" | ")) }
            }
        }
    }

    /** Waits for the page to settle after a navigation-triggering action. */
    suspend fun awaitLoad(timeoutMs: Long = 12_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        delay(500)
        while (System.currentTimeMillis() < deadline) {
            val progress = withContext(Dispatchers.Main) {
                try { webView()?.progress ?: 100 } catch (e: Exception) { 100 }
            }
            if (progress >= 100) {
                delay(350)
                return
            }
            delay(250)
        }
    }

    suspend fun currentUrl(): String = withContext(Dispatchers.Main) {
        try { webView()?.url.orEmpty() } catch (e: Exception) { "" }
    }

    suspend fun currentTitle(): String = withContext(Dispatchers.Main) {
        try { webView()?.title.orEmpty() } catch (e: Exception) { "" }
    }
}
