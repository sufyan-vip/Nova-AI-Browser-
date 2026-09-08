package com.nova.browser.core.utils

import android.text.Html

object HtmlUtils {

    /** Strips tags, scripts and styles and collapses whitespace. */
    fun toPlainText(html: String): String {
        if (html.isBlank()) return ""
        val cleaned = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<noscript[^>]*>.*?</noscript>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</(div|li|h[1-6]|tr)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
        val decoded = try {
            @Suppress("DEPRECATION")
            Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: Exception) {
            decodeEntities(cleaned)
        }
        return decoded
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /** Escapes a Kotlin string for safe embedding inside a JS string literal. */
    fun escapeJsString(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                '<' -> append("\\u003C")
                else -> append(c)
            }
        }
    }

    fun title(html: String): String? =
        Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.getOrNull(1)?.let { toPlainText(it) }

    fun metaDescription(html: String): String? =
        Regex("(?is)<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']*)[\"']")
            .find(html)?.groupValues?.getOrNull(1)?.let { decodeEntities(it).trim() }

    fun approximateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val words = text.trim().split(Regex("\\s+")).size
        return (words * 1.3).toInt().coerceAtLeast(1)
    }

    /** Truncates text to an approximate token budget, keeping whole words. */
    fun truncateToTokens(text: String, maxTokens: Int): String {
        if (maxTokens <= 0) return ""
        if (approximateTokens(text) <= maxTokens) return text
        val maxWords = (maxTokens / 1.3).toInt().coerceAtLeast(1)
        val words = text.trim().split(Regex("\\s+"))
        return words.take(maxWords).joinToString(" ") + "\n…[content truncated]"
    }
}
