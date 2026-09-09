package com.nova.browser.features.memory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.dao.AIMemoryDao
import com.nova.browser.core.database.entities.AIMemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Memory kinds NOVA understands, in the order they're offered in the UI. */
val MEMORY_TYPES = listOf("instruction", "preference", "fact", "interest")

data class AIMemoryUiState(
    val query: String = "",
    val typeFilter: String = "All",
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val editing: AIMemoryEntity? = null,
    val addDialogVisible: Boolean = false,
    val confirmDelete: AIMemoryEntity? = null,
    val confirmDeleteAll: Boolean = false
)

@HiltViewModel
class AIMemoryViewModel @Inject constructor(
    private val memoryDao: AIMemoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIMemoryUiState())
    val uiState: StateFlow<AIMemoryUiState> = _uiState.asStateFlow()

    val memories: StateFlow<List<AIMemoryEntity>> = memoryDao.observeAll()
        .catch { throwable ->
            _uiState.value = _uiState.value.copy(error = throwable.message)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledCount: StateFlow<Int> = memoryDao.observeEnabledCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val types = listOf("All") + MEMORY_TYPES

    init {
        viewModelScope.launch {
            memories.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun visible(all: List<AIMemoryEntity>): List<AIMemoryEntity> {
        val state = _uiState.value
        return all.filter { memory ->
            (state.typeFilter == "All" || memory.type.equals(state.typeFilter, true)) &&
                (state.query.isBlank() || memory.content.contains(state.query, true))
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setTypeFilter(type: String) {
        _uiState.value = _uiState.value.copy(typeFilter = type)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun showAddDialog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(addDialogVisible = visible)
    }

    fun startEdit(memory: AIMemoryEntity?) {
        _uiState.value = _uiState.value.copy(editing = memory)
    }

    fun confirmDelete(memory: AIMemoryEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = memory)
    }

    fun confirmDeleteAll(visible: Boolean) {
        _uiState.value = _uiState.value.copy(confirmDeleteAll = visible)
    }

    fun save(existing: AIMemoryEntity?, content: String, type: String, relatedSite: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                val site = relatedSite.trim().takeIf { it.isNotBlank() }
                if (existing == null) {
                    memoryDao.insert(
                        AIMemoryEntity(content = content.trim(), type = type, relatedSite = site)
                    )
                } else {
                    memoryDao.update(
                        existing.copy(
                            content = content.trim(),
                            type = type,
                            relatedSite = site,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(
                    addDialogVisible = false,
                    editing = null,
                    message = if (existing == null) "Memory added" else "Memory updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    addDialogVisible = false,
                    editing = null,
                    error = "Couldn't save that memory"
                )
            }
        }
    }

    fun setEnabled(memory: AIMemoryEntity, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { memoryDao.setEnabled(memory.id, enabled) }
        }
    }

    fun delete(memory: AIMemoryEntity) {
        viewModelScope.launch {
            runCatching { memoryDao.deleteById(memory.id) }
            _uiState.value = _uiState.value.copy(confirmDelete = null, message = "Memory deleted")
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            runCatching { memoryDao.deleteAll() }
            _uiState.value = _uiState.value.copy(
                confirmDeleteAll = false,
                message = "All memories cleared"
            )
        }
    }
}
