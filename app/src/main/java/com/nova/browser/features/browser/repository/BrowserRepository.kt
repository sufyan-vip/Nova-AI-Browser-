package com.nova.browser.features.browser.repository

import com.nova.browser.core.database.dao.BookmarkDao
import com.nova.browser.core.database.dao.HistoryDao
import com.nova.browser.core.database.dao.SiteSettingsDao
import com.nova.browser.core.database.entities.BookmarkEntity
import com.nova.browser.core.database.entities.HistoryEntity
import com.nova.browser.core.database.entities.SiteSettingsEntity
import com.nova.browser.core.utils.SearchEngines
import com.nova.browser.core.utils.UrlUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** An omnibox suggestion row. */
data class Suggestion(
    val title: String,
    val subtitle: String,
    val url: String,
    val type: Type
) {
    enum class Type { SEARCH, HISTORY, BOOKMARK, URL, AI }
}

@Singleton
class BrowserRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val siteSettingsDao: SiteSettingsDao
) {
    /* ------------------------------ history ------------------------------ */

    val history: Flow<List<HistoryEntity>> = historyDao.observeRecent()
    val mostVisited: Flow<List<HistoryEntity>> = historyDao.observeMostVisited(8)
    val historyCount: Flow<Int> = historyDao.observeCount()

    fun searchHistory(query: String): Flow<List<HistoryEntity>> = historyDao.observeSearch(query)

    suspend fun recordVisit(title: String, url: String, favicon: String?, isPrivate: Boolean) {
        if (isPrivate || UrlUtils.isInternal(url) || url.isBlank()) return
        runCatching { historyDao.record(title.ifBlank { UrlUtils.displayUrl(url) }, url, favicon) }
    }

    suspend fun deleteHistoryEntry(id: Long) {
        runCatching { historyDao.deleteById(id) }
    }

    suspend fun clearHistory() {
        runCatching { historyDao.deleteAll() }
    }

    suspend fun clearHistorySince(since: Long) {
        runCatching { historyDao.deleteSince(since) }
    }

    /* ----------------------------- bookmarks ----------------------------- */

    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()
    val folders: Flow<List<BookmarkEntity>> = bookmarkDao.observeFolders()
    val bookmarkCount: Flow<Int> = bookmarkDao.observeCount()

    fun isBookmarked(url: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(url)

    suspend fun addBookmark(title: String, url: String, favicon: String?, folderId: Long? = null): Long =
        runCatching {
            bookmarkDao.insert(
                BookmarkEntity(
                    title = title.ifBlank { UrlUtils.displayUrl(url) },
                    url = url,
                    favicon = favicon,
                    folderId = folderId
                )
            )
        }.getOrDefault(-1L)

    suspend fun createFolder(name: String): Long = runCatching {
        bookmarkDao.insert(BookmarkEntity(title = name, url = "", isFolder = true))
    }.getOrDefault(-1L)

    suspend fun updateBookmark(bookmark: BookmarkEntity) {
        runCatching { bookmarkDao.update(bookmark) }
    }

    suspend fun removeBookmark(url: String) {
        runCatching { bookmarkDao.deleteByUrl(url) }
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        runCatching {
            if (bookmark.isFolder) bookmarkDao.deleteFolderContents(bookmark.id)
            bookmarkDao.delete(bookmark)
        }
    }

    suspend fun toggleBookmark(title: String, url: String, favicon: String?): Boolean {
        val existing = runCatching { bookmarkDao.findByUrl(url) }.getOrNull()
        return if (existing != null) {
            runCatching { bookmarkDao.delete(existing) }
            false
        } else {
            addBookmark(title, url, favicon)
            true
        }
    }

    suspend fun clearBookmarks() {
        runCatching { bookmarkDao.deleteAll() }
    }

    /* ---------------------------- suggestions ---------------------------- */

    /** Builds omnibox suggestions from history, bookmarks and the search engine. */
    suspend fun suggestions(query: String, engine: SearchEngines.Engine, limit: Int = 8): List<Suggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val out = mutableListOf<Suggestion>()

        if (UrlUtils.isUrl(trimmed)) {
            out += Suggestion(
                title = trimmed,
                subtitle = "Open site",
                url = UrlUtils.normalize(trimmed),
                type = Suggestion.Type.URL
            )
        } else {
            out += Suggestion(
                title = trimmed,
                subtitle = "Search with ${engine.name}",
                url = UrlUtils.searchUrl(trimmed, engine),
                type = Suggestion.Type.SEARCH
            )
            out += Suggestion(
                title = "Ask NOVA AI: \"$trimmed\"",
                subtitle = "Get a direct AI answer",
                url = "nova://ai?q=${UrlUtils.encode(trimmed)}",
                type = Suggestion.Type.AI
            )
        }

        val bookmarkHits = runCatching { bookmarkDao.search(trimmed, 4) }.getOrDefault(emptyList())
        bookmarkHits.filterNot { it.isFolder }.forEach { bookmark ->
            out += Suggestion(bookmark.title, UrlUtils.displayUrl(bookmark.url), bookmark.url, Suggestion.Type.BOOKMARK)
        }

        val historyHits = runCatching { historyDao.search(trimmed, 6) }.getOrDefault(emptyList())
        historyHits.forEach { entry ->
            if (out.none { it.url == entry.url }) {
                out += Suggestion(entry.title, UrlUtils.displayUrl(entry.url), entry.url, Suggestion.Type.HISTORY)
            }
        }

        return out.distinctBy { it.url }.take(limit)
    }

    /* --------------------------- site settings --------------------------- */

    val allSiteSettings: Flow<List<SiteSettingsEntity>> = siteSettingsDao.observeAll()

    suspend fun siteSettings(domain: String): SiteSettingsEntity =
        runCatching { siteSettingsDao.getForDomain(domain) }.getOrNull()
            ?: SiteSettingsEntity(domain = domain)

    suspend fun saveSiteSettings(settings: SiteSettingsEntity) {
        runCatching { siteSettingsDao.upsert(settings.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun resetSiteSettings(domain: String) {
        runCatching { siteSettingsDao.deleteForDomain(domain) }
    }

    suspend fun clearAllSiteSettings() {
        runCatching { siteSettingsDao.deleteAll() }
    }
}
