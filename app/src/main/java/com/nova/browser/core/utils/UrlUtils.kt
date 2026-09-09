package com.nova.browser.core.utils

import android.net.Uri
import android.util.Patterns
import java.net.URLEncoder
import java.util.Locale

/** URL parsing / normalisation helpers. All functions are exception-safe. */
object UrlUtils {

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
    private val IP_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")
    private val LOCALHOST_REGEX = Regex("^localhost(:\\d+)?(/.*)?$", RegexOption.IGNORE_CASE)

    val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "mc_eid", "mc_cid",
        "igshid", "twclid", "yclid", "_openstat", "vero_id", "wickedid", "oly_enc_id",
        "ref_src", "ref_url", "spm", "scm", "s_cid", "trk", "trkCampaign"
    )

    /** True when [input] looks like a navigable URL rather than a search phrase. */
    fun isUrl(input: String): Boolean {
        val text = input.trim()
        if (text.isEmpty() || text.contains(' ')) return false
        if (SCHEME_REGEX.matches(text)) return true
        if (LOCALHOST_REGEX.matches(text)) return true
        if (IP_REGEX.matches(text)) return true
        if (text.startsWith("about:") || text.startsWith("nova://") || text.startsWith("file://")) return true
        if (!text.contains('.')) return false
        val host = text.substringBefore('/').substringBefore('?')
        if (host.endsWith('.') || host.startsWith('.')) return false
        val tld = host.substringAfterLast('.', "")
        return tld.length in 2..24 && tld.all { it.isLetter() } &&
            (Patterns.WEB_URL.matcher(text).matches() || host.count { it == '.' } >= 1)
    }

    /**
     * Turns raw omnibox input into a loadable URL (search query fallback).
     * Callers that have access to user settings should pass the selected engine;
     * background/agent callers safely fall back to Google.
     */
    fun toUrlOrSearch(
        input: String,
        searchEngine: SearchEngines.Engine = SearchEngines.byName("Google")
    ): String {
        val text = input.trim()
        if (text.isEmpty()) return Constants.HOME_URL
        if (text.startsWith("nova://") || text.startsWith("about:") || text.startsWith("file://")) return text
        return if (isUrl(text)) normalize(text) else searchUrl(text, searchEngine)
    }

    fun searchUrl(query: String, engine: SearchEngines.Engine): String =
        engine.queryUrl + encode(query)

    fun encode(value: String): String = try {
        URLEncoder.encode(value, "UTF-8")
    } catch (e: Exception) {
        value.replace(" ", "%20")
    }

    /** Adds a scheme when missing; defaults to https. */
    fun normalize(url: String): String {
        val text = url.trim()
        if (text.isEmpty()) return Constants.HOME_URL
        return if (SCHEME_REGEX.matches(text)) text else "https://$text"
    }

    fun host(url: String): String = try {
        Uri.parse(url).host.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** Registrable-ish domain: strips a leading "www." only. */
    fun domain(url: String): String = host(url).removePrefix("www.")

    fun scheme(url: String): String = try {
        Uri.parse(url).scheme.orEmpty().lowercase(Locale.US)
    } catch (e: Exception) {
        ""
    }

    fun isSecure(url: String): Boolean = scheme(url) == "https"

    fun isHttp(url: String): Boolean = scheme(url) == "http"

    fun isInternal(url: String): Boolean = url.startsWith("nova://") || url.startsWith("about:")

    fun isDownloadable(url: String): Boolean {
        val path = try { Uri.parse(url).lastPathSegment.orEmpty() } catch (e: Exception) { "" }
        val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in setOf(
            "pdf", "zip", "rar", "7z", "tar", "gz", "apk", "exe", "dmg", "iso", "mp3",
            "mp4", "mkv", "avi", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "epub"
        )
    }

    /** Removes known tracking query params. Returns original URL on failure. */
    fun stripTrackingParams(url: String): String = try {
        val uri = Uri.parse(url)
        val names = uri.queryParameterNames
        if (names.isEmpty() || names.none { it.lowercase(Locale.US) in TRACKING_PARAMS }) {
            url
        } else {
            val builder = uri.buildUpon().clearQuery()
            names.filter { it.lowercase(Locale.US) !in TRACKING_PARAMS }.forEach { name ->
                uri.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }
            builder.build().toString()
        }
    } catch (e: Exception) {
        url
    }

    fun upgradeToHttps(url: String): String =
        if (isHttp(url)) url.replaceFirst("http://", "https://") else url

    /** Short display form for the address bar. */
    fun displayUrl(url: String): String {
        if (isInternal(url)) return url
        val host = host(url)
        return if (host.isBlank()) url else host.removePrefix("www.")
    }

    fun faviconUrl(url: String): String? {
        val host = host(url)
        return if (host.isBlank()) null else "https://$host/favicon.ico"
    }

    fun fileNameFromUrl(url: String): String {
        val segment = try { Uri.parse(url).lastPathSegment } catch (e: Exception) { null }
        val name = segment?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "download"
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    fun sameOrigin(a: String, b: String): Boolean =
        scheme(a) == scheme(b) && host(a).equals(host(b), ignoreCase = true)
}
