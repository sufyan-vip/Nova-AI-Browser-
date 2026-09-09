package com.nova.browser.features.notes.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nova.browser.core.database.dao.NoteDao
import com.nova.browser.core.database.entities.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Note kinds: manual notes, highlighted page text and AI summaries. */
val NOTE_TYPES = listOf("All", "note", "highlight", "summary")

data class NotesUiState(
    val query: String = "",
    val typeFilter: String = "All",
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val editing: NoteEntity? = null,
    val editorVisible: Boolean = false,
    val confirmDelete: NoteEntity? = null,
    val expandedId: Long? = null
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val gson: Gson
) : ViewModel() {

    private val tagsType = object : TypeToken<List<String>>() {}.type

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = noteDao.observeAll()
        .catch { throwable ->
            _uiState.value = _uiState.value.copy(error = throwable.message)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val types = NOTE_TYPES

    init {
        viewModelScope.launch {
            notes.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun tagsOf(note: NoteEntity): List<String> = try {
        gson.fromJson<List<String>>(note.tags, tagsType) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun visible(all: List<NoteEntity>): List<NoteEntity> {
        val state = _uiState.value
        return all.filter { note ->
            (state.typeFilter == "All" || note.type.equals(state.typeFilter, true)) &&
                (
                    state.query.isBlank() ||
                        note.title.contains(state.query, true) ||
                        note.content.contains(state.query, true)
                    )
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setTypeFilter(type: String) {
        _uiState.value = _uiState.value.copy(typeFilter = type)
    }

    fun toggleExpanded(note: NoteEntity) {
        _uiState.value = _uiState.value.copy(
            expandedId = if (_uiState.value.expandedId == note.id) null else note.id
        )
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun showEditor(visible: Boolean, note: NoteEntity? = null) {
        _uiState.value = _uiState.value.copy(editorVisible = visible, editing = note)
    }

    fun confirmDelete(note: NoteEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = note)
    }

    fun save(existing: NoteEntity?, title: String, content: String, tags: String) {
        if (title.isBlank() && content.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Add a title or some text first")
            return
        }
        viewModelScope.launch {
            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            try {
                if (existing == null) {
                    noteDao.insert(
                        NoteEntity(
                            title = title.ifBlank { content.take(40) },
                            content = content,
                            tags = gson.toJson(tagList)
                        )
                    )
                } else {
                    noteDao.update(
                        existing.copy(
                            title = title.ifBlank { existing.title },
                            content = content,
                            tags = gson.toJson(tagList),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(
                    editorVisible = false,
                    editing = null,
                    message = if (existing == null) "Note saved" else "Note updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    editorVisible = false,
                    editing = null,
                    error = "Couldn't save that note"
                )
            }
        }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteDao.deleteById(note.id) }
            _uiState.value = _uiState.value.copy(confirmDelete = null, message = "Note deleted")
        }
    }

    /** Plain-text export of the currently visible notes, for sharing. */
    fun exportText(notes: List<NoteEntity>): String = buildString {
        appendLine("NOVA Notes export")
        appendLine("=================")
        notes.forEach { note ->
            appendLine()
            appendLine(note.title)
            note.sourceUrl?.let { appendLine("Source: $it") }
            appendLine(note.content)
            appendLine("---")
        }
    }
}
