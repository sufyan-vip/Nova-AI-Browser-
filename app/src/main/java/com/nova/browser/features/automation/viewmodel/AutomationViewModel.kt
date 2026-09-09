package com.nova.browser.features.automation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nova.browser.core.database.dao.AutomationDao
import com.nova.browser.core.database.entities.AutomationEntity
import com.nova.browser.features.agent.engine.AgentAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Trigger kinds an automation can use. */
val TRIGGER_TYPES = listOf("manual", "on_page_load", "on_domain", "scheduled")

data class AutomationUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val editorVisible: Boolean = false,
    val editing: AutomationEntity? = null,
    val confirmDelete: AutomationEntity? = null,
    val expandedId: Long? = null
)

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val automationDao: AutomationDao,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    val automations: StateFlow<List<AutomationEntity>> = automationDao.observeAll()
        .catch { throwable ->
            _uiState.value = _uiState.value.copy(error = throwable.message)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledCount: StateFlow<Int> = automationDao.observeEnabledCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            automations.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    /** Decodes the stored steps, tolerating hand-edited or corrupt JSON. */
    fun stepsOf(automation: AutomationEntity): List<AgentAction> = try {
        gson.fromJson(automation.workflowJson, Array<AgentAction>::class.java)?.toList().orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    fun visible(all: List<AutomationEntity>): List<AutomationEntity> {
        val query = _uiState.value.query
        return if (query.isBlank()) {
            all
        } else {
            all.filter {
                it.name.contains(query, true) || it.description.contains(query, true)
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun toggleExpanded(automation: AutomationEntity) {
        _uiState.value = _uiState.value.copy(
            expandedId = if (_uiState.value.expandedId == automation.id) null else automation.id
        )
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun showEditor(visible: Boolean, automation: AutomationEntity? = null) {
        _uiState.value = _uiState.value.copy(editorVisible = visible, editing = automation)
    }

    fun confirmDelete(automation: AutomationEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = automation)
    }

    /** Validates the step JSON before saving so a broken workflow never persists. */
    fun validateSteps(json: String): String? {
        if (json.isBlank()) return "Add at least one step"
        return try {
            val parsed = gson.fromJson(json, Array<AgentAction>::class.java)
            if (parsed.isNullOrEmpty()) "Add at least one step" else null
        } catch (e: Exception) {
            "That isn't valid step JSON"
        }
    }

    fun save(
        existing: AutomationEntity?,
        name: String,
        description: String,
        stepsJson: String,
        triggerType: String,
        triggerValue: String
    ) {
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Give the automation a name")
            return
        }
        validateSteps(stepsJson)?.let { problem ->
            _uiState.value = _uiState.value.copy(error = problem)
            return
        }
        viewModelScope.launch {
            try {
                val trigger = triggerValue.trim().takeIf { it.isNotBlank() }
                if (existing == null) {
                    automationDao.insert(
                        AutomationEntity(
                            name = name.trim(),
                            description = description.trim(),
                            workflowJson = stepsJson,
                            triggerType = triggerType,
                            triggerValue = trigger
                        )
                    )
                } else {
                    automationDao.update(
                        existing.copy(
                            name = name.trim(),
                            description = description.trim(),
                            workflowJson = stepsJson,
                            triggerType = triggerType,
                            triggerValue = trigger
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(
                    editorVisible = false,
                    editing = null,
                    message = if (existing == null) "Automation saved" else "Automation updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    editorVisible = false,
                    editing = null,
                    error = "Couldn't save that automation"
                )
            }
        }
    }

    fun setEnabled(automation: AutomationEntity, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { automationDao.setEnabled(automation.id, enabled) }
        }
    }

    fun duplicate(automation: AutomationEntity) {
        viewModelScope.launch {
            runCatching {
                automationDao.insert(
                    automation.copy(
                        id = 0,
                        name = "${automation.name} copy",
                        runCount = 0,
                        lastRunAt = null,
                        lastResult = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.value = _uiState.value.copy(message = "Automation duplicated")
        }
    }

    fun delete(automation: AutomationEntity) {
        viewModelScope.launch {
            runCatching { automationDao.deleteById(automation.id) }
            _uiState.value = _uiState.value.copy(
                confirmDelete = null,
                message = "Automation deleted"
            )
        }
    }

    /** A starter template used by the "New automation" button. */
    fun templateJson(): String = gson.toJson(
        listOf(
            AgentAction(action = "navigate", target = "https://example.com", reason = "Open the site"),
            AgentAction(action = "click_text", target = "Sign in", reason = "Start the flow"),
            AgentAction(action = "finish", reason = "Done")
        )
    )
}
