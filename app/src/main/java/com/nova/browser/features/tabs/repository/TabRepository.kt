package com.nova.browser.features.tabs.repository

import com.nova.browser.core.database.dao.TabDao
import com.nova.browser.core.database.entities.TabEntity
import com.nova.browser.core.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing tab model. */
data class Tab(
    val id: String,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val isActive: Boolean = false,
    val isPinned: Boolean = false,
    val isPrivate: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val position: Int = 0,
    val parentTabId: String? = null,
    val lastAccessed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val screenshot: String? = null,
    val isSleeping: Boolean = false
) {
    val isHome: Boolean get() = url.isBlank() || url == Constants.HOME_URL
    val displayTitle: String get() = title.ifBlank { if (isHome) "New Tab" else url }

    fun toEntity() = TabEntity(
        id = id, title = title, url = url, favicon = favicon, isActive = isActive,
        isPinned = isPinned, isPrivate = isPrivate, groupId = groupId, groupName = groupName,
        position = position, parentTabId = parentTabId, lastAccessed = lastAccessed,
        createdAt = createdAt, screenshot = screenshot, isSleeping = isSleeping
    )

    companion object {
        fun from(entity: TabEntity) = Tab(
            id = entity.id, title = entity.title, url = entity.url, favicon = entity.favicon,
            isActive = entity.isActive, isPinned = entity.isPinned, isPrivate = entity.isPrivate,
            groupId = entity.groupId, groupName = entity.groupName, position = entity.position,
            parentTabId = entity.parentTabId, lastAccessed = entity.lastAccessed,
            createdAt = entity.createdAt, screenshot = entity.screenshot, isSleeping = entity.isSleeping
        )

        fun create(
            url: String = Constants.HOME_URL,
            title: String = "New Tab",
            isPrivate: Boolean = false,
            position: Int = 0,
            parentTabId: String? = null
        ) = Tab(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            isPrivate = isPrivate,
            position = position,
            parentTabId = parentTabId,
            isActive = true
        )
    }
}

@Singleton
class TabRepository @Inject constructor(
    private val tabDao: TabDao
) {
    val tabs: Flow<List<Tab>> = tabDao.observeAll().map { list -> list.map(Tab::from) }
    val tabCount: Flow<Int> = tabDao.observeCount()

    suspend fun getAll(): List<Tab> = runCatching { tabDao.getAll().map(Tab::from) }.getOrDefault(emptyList())

    suspend fun get(id: String): Tab? = runCatching { tabDao.getById(id)?.let(Tab::from) }.getOrNull()

    suspend fun getActive(): Tab? = runCatching { tabDao.getActive()?.let(Tab::from) }.getOrNull()

    suspend fun save(tab: Tab) {
        runCatching { tabDao.upsert(tab.toEntity()) }
    }

    suspend fun saveAll(tabs: List<Tab>) {
        runCatching { tabDao.upsertAll(tabs.map { it.toEntity() }) }
    }

    suspend fun create(
        url: String = Constants.HOME_URL,
        title: String = "New Tab",
        isPrivate: Boolean = false,
        parentTabId: String? = null
    ): Tab {
        val existing = getAll()
        val tab = Tab.create(
            url = url,
            title = title,
            isPrivate = isPrivate,
            position = existing.size,
            parentTabId = parentTabId
        )
        runCatching {
            tabDao.clearActive()
            tabDao.upsert(tab.toEntity())
        }
        return tab
    }

    suspend fun setActive(id: String) {
        runCatching { tabDao.setActive(id) }
    }

    suspend fun setSleeping(id: String, sleeping: Boolean) {
        runCatching { tabDao.setSleeping(id, sleeping) }
    }

    suspend fun setGroup(id: String, groupId: String?, groupName: String?) {
        runCatching { tabDao.setGroup(id, groupId, groupName) }
    }

    /** Creates a group from the given tabs and returns its id. */
    suspend fun groupTabs(ids: List<String>, name: String): String {
        val groupId = UUID.randomUUID().toString()
        ids.forEach { runCatching { tabDao.setGroup(it, groupId, name) } }
        return groupId
    }

    suspend fun ungroup(ids: List<String>) {
        ids.forEach { runCatching { tabDao.setGroup(it, null, null) } }
    }

    /** Closes a tab and returns the id that should become active, if any. */
    suspend fun close(id: String): String? {
        val all = getAll()
        val index = all.indexOfFirst { it.id == id }
        val wasActive = all.getOrNull(index)?.isActive == true
        runCatching { tabDao.deleteById(id) }

        val remaining = all.filterNot { it.id == id }
        remaining.forEachIndexed { position, tab ->
            if (tab.position != position) runCatching { tabDao.updatePosition(tab.id, position) }
        }
        if (!wasActive) return null
        val next = remaining.getOrNull(index) ?: remaining.lastOrNull() ?: return null
        setActive(next.id)
        return next.id
    }

    suspend fun closeOthers(keepId: String) {
        getAll().filter { it.id != keepId && !it.isPinned }.forEach {
            runCatching { tabDao.deleteById(it.id) }
        }
        setActive(keepId)
    }

    suspend fun closeAll(includePinned: Boolean = false) {
        runCatching {
            if (includePinned) tabDao.deleteAll() else tabDao.deleteUnpinned()
        }
    }

    suspend fun closePrivate() {
        runCatching { tabDao.deletePrivate() }
    }

    suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { position, id ->
            runCatching { tabDao.updatePosition(id, position) }
        }
    }

    suspend fun togglePin(id: String) {
        val tab = get(id) ?: return
        save(tab.copy(isPinned = !tab.isPinned))
    }

    suspend fun duplicate(id: String): Tab? {
        val source = get(id) ?: return null
        return create(url = source.url, title = source.title, isPrivate = source.isPrivate, parentTabId = source.id)
    }

    /** Ensures at least one tab exists (used on cold start). */
    suspend fun ensureAtLeastOne(): Tab {
        val all = getAll()
        val active = all.firstOrNull { it.isActive }
        if (active != null) return active
        all.firstOrNull()?.let {
            setActive(it.id)
            return it.copy(isActive = true)
        }
        return create()
    }
}
