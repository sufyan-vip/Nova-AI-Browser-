package com.nova.browser.features.workspaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.entities.WorkspaceEntity
import com.nova.browser.features.workspaces.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Emoji glyphs offered as workspace icons. */
val WORKSPACE_ICONS = listOf("💼", "🏠", "🎓", "🛒", "🎬", "🧪", "✈️", "💡")

/** Hex accents offered for workspace colour coding. */
val WORKSPACE_COLORS = listOf("#4A9EFF", "#00E5FF", "#B388FF", "#69F0AE", "#FFD740", "#FF5252")

data class WorkspaceUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val editorVisible: Boolean = false,
    val editing: WorkspaceEntity? = null,
    val confirmDelete: WorkspaceEntity? = null,
    val switching: Long? = null
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val repository: WorkspaceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    val workspaces: StateFlow<List<WorkspaceEntity>> = repository.workspaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            workspaces.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun urlsOf(workspace: WorkspaceEntity): List<String> = repository.urlsOf(workspace)

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun showEditor(visible: Boolean, workspace: WorkspaceEntity? = null) {
        _uiState.value = _uiState.value.copy(editorVisible = visible, editing = workspace)
    }

    fun confirmDelete(workspace: WorkspaceEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = workspace)
    }

    fun save(existing: WorkspaceEntity?, name: String, icon: String, color: String) {
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Give the workspace a name")
            return
        }
        viewModelScope.launch {
            if (existing == null) {
                val id = repository.createFromCurrentTabs(name, icon, color)
                _uiState.value = _uiState.value.copy(
                    editorVisible = false,
                    editing = null,
                    message = if (id > 0) "Workspace saved with your open tabs" else null,
                    error = if (id > 0) null else "Couldn't create that workspace"
                )
            } else {
                repository.rename(existing, name, icon, color)
                _uiState.value = _uiState.value.copy(
                    editorVisible = false,
                    editing = null,
                    message = "Workspace updated"
                )
            }
        }
    }

    fun switchTo(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(switching = workspace.id)
            repository.switchTo(workspace)
            _uiState.value = _uiState.value.copy(
                switching = null,
                message = "Switched to ${workspace.name}"
            )
        }
    }

    fun syncToCurrentTabs(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            repository.syncToCurrentTabs(workspace)
            _uiState.value = _uiState.value.copy(message = "${workspace.name} now matches your open tabs")
        }
    }

    fun delete(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            repository.delete(workspace)
            _uiState.value = _uiState.value.copy(
                confirmDelete = null,
                message = "Workspace deleted"
            )
        }
    }
}
