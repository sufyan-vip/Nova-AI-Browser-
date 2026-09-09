package com.nova.browser.features.privacy.engine

import com.nova.browser.core.database.dao.TrackerStatDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Curated blocklist + per-session counters (spec 17_PRIVACY_ENGINE). */
@Singleton
class TrackerBlocker @Inject constructor(
    private val trackerStatDao: TrackerStatDao,
    @Named("applicationScope") private val scope: CoroutineScope
) {
    enum class Category { ANALYTICS, ADVERTISING, SOCIAL, FINGERPRINTING, CRYPTOMINING }

    data class Match(val domain: String, val category: Category)

    private val analytics = setOf(
        "google-analytics.com", "googletagmanager.com", "analytics.google.com", "segment.io",
        "segment.com", "mixpanel.com", "amplitude.com", "heap.io", "hotjar.com", "fullstory.com",
        "mouseflow.com", "crazyegg.com", "quantserve.com", "quantcast.com", "scorecardresearch.com",
        "chartbeat.com", "parsely.com", "newrelic.com", "nr-data.net", "sentry.io", "bugsnag.com",
        "statcounter.com", "matomo.cloud", "clarity.ms", "yandex.ru", "mc.yandex.ru", "kissmetrics.com",
        "optimizely.com", "vwo.com", "loggly.com", "adobedtm.com", "omtrdc.net", "demdex.net"
    )

    private val advertising = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "2mdn.net", "adnxs.com", "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "revcontent.com", "mgid.com",
        "media.net", "amazon-adsystem.com", "adcolony.com", "applovin.com", "unityads.unity3d.com",
        "inmobi.com", "smaato.net", "smartadserver.com", "casalemedia.com", "sharethrough.com",
        "teads.tv", "spotxchange.com", "yieldmo.com", "bidswitch.net", "3lift.com", "indexww.com",
        "adform.net", "zedo.com", "advertising.com", "adtechus.com", "moatads.com", "serving-sys.com"
    )

    private val social = setOf(
        "connect.facebook.net", "facebook.com/tr", "graph.facebook.com", "platform.twitter.com",
        "syndication.twitter.com", "ads-twitter.com", "analytics.tiktok.com", "ads.tiktok.com",
        "business-api.tiktok.com", "snap.licdn.com", "px.ads.linkedin.com", "pinterest.com/ct",
        "ct.pinterest.com", "sc-static.net", "reddit.com/api/v2/gen_204", "redditstatic.com/ads",
        "addthis.com", "sharethis.com", "disqus.com/embed", "bat.bing.com"
    )

    private val fingerprinting = setOf(
        "fingerprintjs.com", "fpjs.io", "fingerprint.com", "iovation.com", "threatmetrix.com",
        "distilnetworks.com", "perimeterx.net", "px-cloud.net", "datadome.co", "seon.io",
        "maxmind.com", "deviceatlas.com", "51degrees.com"
    )

    private val cryptomining = setOf(
        "coinhive.com", "coin-hive.com", "jsecoin.com", "cryptoloot.pro", "webminepool.com",
        "minero.cc", "coinimp.com", "crypto-loot.org"
    )

    private val allowlist = ConcurrentHashMap.newKeySet<String>()
    private val perPageCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val sessionCount = AtomicInteger(0)
    private val perPageDomains = ConcurrentHashMap<String, MutableSet<String>>()

    val totalBlockedFlow = trackerStatDao.observeTotalBlocked()
    val topTrackersFlow = trackerStatDao.observeTop(50)

    fun sessionBlocked(): Int = sessionCount.get()

    fun blockedOnPage(pageUrl: String): Int = perPageCounts[pageKey(pageUrl)]?.get() ?: 0

    fun trackersOnPage(pageUrl: String): Set<String> =
        perPageDomains[pageKey(pageUrl)]?.toSet() ?: emptySet()

    fun resetPage(pageUrl: String) {
        val key = pageKey(pageUrl)
        perPageCounts[key] = AtomicInteger(0)
        perPageDomains[key] = ConcurrentHashMap.newKeySet()
    }

    fun allowSite(domain: String) {
        allowlist += domain.lowercase(Locale.US).removePrefix("www.")
    }

    fun disallowSite(domain: String) {
        allowlist -= domain.lowercase(Locale.US).removePrefix("www.")
    }

    fun isSiteAllowlisted(domain: String): Boolean =
        allowlist.contains(domain.lowercase(Locale.US).removePrefix("www."))

    /** Classifies a request URL; null when it is not a known tracker. */
    fun classify(requestUrl: String): Match? {
        val lower = requestUrl.lowercase(Locale.US)
        val host = hostOf(lower) ?: return null
        analytics.firstOrNull { matches(host, lower, it) }?.let { return Match(it, Category.ANALYTICS) }
        advertising.firstOrNull { matches(host, lower, it) }?.let { return Match(it, Category.ADVERTISING) }
        social.firstOrNull { matches(host, lower, it) }?.let { return Match(it, Category.SOCIAL) }
        fingerprinting.firstOrNull { matches(host, lower, it) }?.let { return Match(it, Category.FINGERPRINTING) }
        cryptomining.firstOrNull { matches(host, lower, it) }?.let { return Match(it, Category.CRYPTOMINING) }
        return null
    }

    /**
     * Decides whether to block [requestUrl] loaded from [pageUrl] and records
     * the hit. Called on the WebView's IO thread — must stay cheap.
     */
    fun shouldBlock(requestUrl: String, pageUrl: String, enabled: Boolean): Match? {
        if (!enabled) return null
        val pageDomain = hostOf(pageUrl.lowercase(Locale.US))?.removePrefix("www.")
        if (pageDomain != null && isSiteAllowlisted(pageDomain)) return null

        val match = classify(requestUrl) ?: return null

        // Never block first-party requests.
        val requestHost = hostOf(requestUrl.lowercase(Locale.US))?.removePrefix("www.")
        if (pageDomain != null && requestHost != null && requestHost.endsWith(pageDomain)) return null

        val key = pageKey(pageUrl)
        perPageCounts.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        perPageDomains.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(match.domain)
        sessionCount.incrementAndGet()

        scope.launch {
            runCatching { trackerStatDao.increment(match.domain, match.category.name) }
        }
        return match
    }

    suspend fun clearStats() {
        runCatching { trackerStatDao.deleteAll() }
        perPageCounts.clear()
        perPageDomains.clear()
        sessionCount.set(0)
    }

    private fun matches(host: String, fullUrl: String, pattern: String): Boolean =
        if (pattern.contains('/')) fullUrl.contains(pattern) else host == pattern || host.endsWith(".$pattern")

    private fun hostOf(url: String): String? = try {
        java.net.URI(url).host
    } catch (e: Exception) {
        Regex("^[a-z]+://([^/:?#]+)").find(url)?.groupValues?.getOrNull(1)
    }

    private fun pageKey(pageUrl: String): String = hostOf(pageUrl.lowercase(Locale.US)) ?: pageUrl
}
