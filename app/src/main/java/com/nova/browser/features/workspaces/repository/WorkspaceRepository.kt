package com.nova.browser.features.workspaces.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nova.browser.core.database.dao.WorkspaceDao
import com.nova.browser.core.database.entities.WorkspaceEntity
import com.nova.browser.features.tabs.repository.TabRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Workspaces are named sets of tabs. Switching a workspace closes the current
 * unpinned tabs and reopens the saved URLs of the target workspace.
 */
@Singleton
class WorkspaceRepository @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val tabRepository: TabRepository,
    private val gson: Gson
) {
    private val listType = object : TypeToken<List<String>>() {}.type

    val workspaces: Flow<List<WorkspaceEntity>> = workspaceDao.observeAll().catch { emit(emptyList()) }
    val active: Flow<WorkspaceEntity?> = workspaceDao.observeActive().catch { emit(null) }

    /** Decodes the stored tab URL list, tolerating corrupt JSON. */
    fun urlsOf(workspace: WorkspaceEntity): List<String> = try {
        gson.fromJson<List<String>>(workspace.tabIds, listType) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Creates a workspace capturing the currently open tabs. */
    suspend fun createFromCurrentTabs(name: String, icon: String?, color: String?): Long {
        val urls = tabRepository.getAll().filterNot { it.isHome }.map { it.url }
        return create(name, icon, color, urls)
    }

    suspend fun create(name: String, icon: String?, color: String?, urls: List<String>): Long = try {
        workspaceDao.insert(
            WorkspaceEntity(
                name = name.trim(),
                icon = icon,
                color = color,
                tabIds = gson.toJson(urls)
            )
        )
    } catch (e: Exception) {
        -1L
    }

    suspend fun rename(workspace: WorkspaceEntity, name: String, icon: String?, color: String?) {
        runCatching {
            workspaceDao.update(workspace.copy(name = name.trim(), icon = icon, color = color))
        }
    }

    /** Overwrites the workspace's tab list with what is open right now. */
    suspend fun syncToCurrentTabs(workspace: WorkspaceEntity) {
        val urls = tabRepository.getAll().filterNot { it.isHome }.map { it.url }
        runCatching { workspaceDao.update(workspace.copy(tabIds = gson.toJson(urls))) }
    }

    /** Saves the open tabs into the active workspace, then opens [workspace]. */
    suspend fun switchTo(workspace: WorkspaceEntity) {
        runCatching {
            val urls = urlsOf(workspace)
            tabRepository.closeAll(includePinned = false)
            if (urls.isEmpty()) {
                tabRepository.create()
            } else {
                urls.forEach { url -> tabRepository.create(url = url, title = url) }
                tabRepository.getAll().firstOrNull()?.let { tabRepository.setActive(it.id) }
            }
            workspaceDao.activate(workspace.id)
        }
    }

    suspend fun delete(workspace: WorkspaceEntity) {
        runCatching { workspaceDao.deleteById(workspace.id) }
    }

    suspend fun deleteAll() {
        runCatching { workspaceDao.deleteAll() }
    }
}
