package com.nova.browser.features.history.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.dao.HistoryDao
import com.nova.browser.core.database.entities.HistoryEntity
import com.nova.browser.core.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How far back a "clear history" action reaches. */
enum class ClearRange(val label: String) {
    LAST_HOUR("Last hour"),
    TODAY("Today"),
    LAST_WEEK("Last 7 days"),
    ALL_TIME("All time");

    fun since(now: Long = System.currentTimeMillis()): Long = when (this) {
        LAST_HOUR -> now - 60 * 60 * 1000L
        TODAY -> DateUtils.startOfDay(now)
        LAST_WEEK -> now - 7 * 24 * 60 * 60 * 1000L
        ALL_TIME -> 0L
    }
}

data class HistoryUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val clearDialogVisible: Boolean = false,
    val confirmDelete: HistoryEntity? = null
)

/** History grouped under Today / Yesterday / date headers. */
data class HistorySection(val label: String, val entries: List<HistoryEntity>)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: HistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<HistoryEntity>> = historyDao.observeRecent(1000)
        .catch { throwable ->
            _uiState.value = _uiState.value.copy(error = throwable.message)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            history.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun sections(all: List<HistoryEntity>): List<HistorySection> {
        val query = _uiState.value.query
        val filtered = if (query.isBlank()) all else all.filter {
            it.title.contains(query, true) || it.url.contains(query, true)
        }
        return filtered
            .groupBy { DateUtils.dayLabel(it.visitedAt) }
            .map { (label, entries) -> HistorySection(label, entries) }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun showClearDialog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(clearDialogVisible = visible)
    }

    fun confirmDelete(entry: HistoryEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = entry)
    }

    fun delete(entry: HistoryEntity) {
        viewModelScope.launch {
            runCatching { historyDao.delete(entry) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = "Couldn't remove that entry")
                }
            _uiState.value = _uiState.value.copy(confirmDelete = null, message = "Removed from history")
        }
    }

    fun clear(range: ClearRange) {
        viewModelScope.launch {
            runCatching {
                if (range == ClearRange.ALL_TIME) historyDao.deleteAll()
                else historyDao.deleteSince(range.since())
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = "Couldn't clear history")
            }
            _uiState.value = _uiState.value.copy(
                clearDialogVisible = false,
                message = "Cleared history (${range.label.lowercase()})"
            )
        }
    }
}
