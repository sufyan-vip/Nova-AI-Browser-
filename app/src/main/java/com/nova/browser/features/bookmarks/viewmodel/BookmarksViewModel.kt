package com.nova.browser.features.bookmarks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.dao.BookmarkDao
import com.nova.browser.core.database.entities.BookmarkEntity
import com.nova.browser.core.utils.UrlUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarksUiState(
    val query: String = "",
    val folderId: Long? = null,
    val folderName: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val editing: BookmarkEntity? = null,
    val confirmDelete: BookmarkEntity? = null,
    val newFolderDialog: Boolean = false
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeAll()
        .catch { throwable ->
            _uiState.value = _uiState.value.copy(error = throwable.message)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeFolders()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            bookmarks.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    /** Applies the current folder + search filter. */
    fun visible(all: List<BookmarkEntity>): List<BookmarkEntity> {
        val state = _uiState.value
        return all.filter { entity ->
            val inFolder = state.folderId == null || entity.folderId == state.folderId
            val matches = state.query.isBlank() ||
                entity.title.contains(state.query, true) ||
                entity.url.contains(state.query, true)
            // At root, hide items that live inside a folder.
            val visibleAtRoot = state.folderId != null || entity.folderId == null || state.query.isNotBlank()
            inFolder && matches && visibleAtRoot
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun openFolder(folder: BookmarkEntity?) {
        _uiState.value = _uiState.value.copy(
            folderId = folder?.id,
            folderName = folder?.title,
            query = ""
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun startEdit(bookmark: BookmarkEntity?) {
        _uiState.value = _uiState.value.copy(editing = bookmark)
    }

    fun confirmDelete(bookmark: BookmarkEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = bookmark)
    }

    fun showNewFolderDialog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(newFolderDialog = visible)
    }

    fun save(bookmark: BookmarkEntity, title: String, url: String, folderId: Long?) {
        viewModelScope.launch {
            try {
                val normalized = if (bookmark.isFolder) url else UrlUtils.normalize(url)
                bookmarkDao.update(
                    bookmark.copy(
                        title = title.trim().ifBlank { UrlUtils.displayUrl(normalized) },
                        url = normalized,
                        folderId = folderId,
                        folderName = folders.value.firstOrNull { it.id == folderId }?.title
                    )
                )
                _uiState.value = _uiState.value.copy(editing = null, message = "Bookmark updated")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    editing = null,
                    error = e.message ?: "Couldn't save that bookmark"
                )
            }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                bookmarkDao.insert(
                    BookmarkEntity(
                        title = name.trim(),
                        url = "",
                        isFolder = true,
                        folderId = _uiState.value.folderId
                    )
                )
                _uiState.value = _uiState.value.copy(newFolderDialog = false, message = "Folder created")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    newFolderDialog = false,
                    error = e.message ?: "Couldn't create that folder"
                )
            }
        }
    }

    fun delete(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            try {
                if (bookmark.isFolder) bookmarkDao.deleteFolderContents(bookmark.id)
                bookmarkDao.delete(bookmark)
                _uiState.value = _uiState.value.copy(
                    confirmDelete = null,
                    message = if (bookmark.isFolder) "Folder deleted" else "Bookmark removed"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    confirmDelete = null,
                    error = e.message ?: "Couldn't delete that"
                )
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            runCatching { bookmarkDao.deleteAll() }
            _uiState.value = _uiState.value.copy(message = "All bookmarks removed")
        }
    }

    /** Netscape-format export used by every major browser. */
    fun exportHtml(all: List<BookmarkEntity>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>Bookmarks</TITLE>")
        appendLine("<H1>NOVA Bookmarks</H1>")
        appendLine("<DL><p>")
        all.filter { !it.isFolder }.forEach { bookmark ->
            val title = com.nova.browser.core.utils.HtmlUtils.escape(bookmark.title)
            val url = com.nova.browser.core.utils.HtmlUtils.escape(bookmark.url)
            appendLine("    <DT><A HREF=\"$url\" ADD_DATE=\"${bookmark.createdAt / 1000}\">$title</A>")
        }
        appendLine("</DL><p>")
    }

    /** Imports a Netscape bookmark file; returns how many were added. */
    fun importHtml(html: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val pattern = Regex("<A[^>]*HREF=\"([^\"]+)\"[^>]*>(.*?)</A>", RegexOption.IGNORE_CASE)
            var imported = 0
            try {
                pattern.findAll(html).forEach { match ->
                    val url = match.groupValues[1]
                    val title = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                    if (UrlUtils.isUrl(url) && bookmarkDao.findByUrl(url) == null) {
                        bookmarkDao.insert(
                            BookmarkEntity(
                                title = title.ifBlank { UrlUtils.displayUrl(url) },
                                url = url
                            )
                        )
                        imported++
                    }
                }
                _uiState.value = _uiState.value.copy(message = "Imported $imported bookmarks")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "That file couldn't be read")
            }
            onDone(imported)
        }
    }
}
