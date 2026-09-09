package com.nova.browser.features.downloads.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.entities.DownloadEntity
import com.nova.browser.core.utils.FileUtils
import com.nova.browser.features.downloads.repository.DownloadRepository
import com.nova.browser.features.downloads.repository.DownloadStatus
import com.nova.browser.features.downloads.service.DownloadService
import com.nova.browser.features.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DownloadsUiState(
    val query: String = "",
    val category: String = "All",
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val confirmDelete: DownloadEntity? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloads
        .catch { throwable ->
            _uiState.value = _uiState.value.copy(error = throwable.message)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = repository.activeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val categories = listOf("All") + FileUtils.Category.entries.map { it.label }

    init {
        viewModelScope.launch {
            downloads.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun visible(all: List<DownloadEntity>): List<DownloadEntity> {
        val state = _uiState.value
        return all.filter { entity ->
            (state.category == "All" || entity.category == state.category) &&
                (state.query.isBlank() || entity.fileName.contains(state.query, true))
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setCategory(category: String) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Called by the browser when a page triggers a download. */
    fun enqueue(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val entity = repository.enqueue(
                    url = url,
                    suggestedName = null,
                    mimeType = mimeType,
                    contentDisposition = contentDisposition,
                    totalSize = contentLength,
                    threadCount = settings.downloadThreads
                )
                DownloadService.start(context, entity.id)
                _uiState.value = _uiState.value.copy(message = "Downloading ${entity.fileName}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Couldn't start that download"
                )
            }
        }
    }

    fun pause(entity: DownloadEntity) {
        DownloadService.pause(context, entity.id)
    }

    fun resume(entity: DownloadEntity) {
        viewModelScope.launch {
            repository.updateStatus(entity.id, DownloadStatus.PENDING)
            DownloadService.start(context, entity.id)
        }
    }

    fun retry(entity: DownloadEntity) {
        viewModelScope.launch {
            runCatching { File(entity.filePath).delete() }
            repository.updateProgress(entity.id, 0, entity.totalSize)
            repository.updateStatus(entity.id, DownloadStatus.PENDING, null)
            DownloadService.start(context, entity.id)
        }
    }

    fun cancel(entity: DownloadEntity) {
        DownloadService.cancel(context, entity.id)
    }

    fun confirmDelete(entity: DownloadEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = entity)
    }

    fun delete(entity: DownloadEntity, deleteFile: Boolean) {
        viewModelScope.launch {
            DownloadService.cancel(context, entity.id)
            repository.delete(entity.id, deleteFile)
            _uiState.value = _uiState.value.copy(
                confirmDelete = null,
                message = if (deleteFile) "Deleted ${entity.fileName}" else "Removed from list"
            )
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompleted()
            _uiState.value = _uiState.value.copy(message = "Cleared finished downloads")
        }
    }

    /** Opens a finished file with the best-matching installed app. */
    fun open(entity: DownloadEntity) {
        val file = File(entity.filePath)
        if (!file.exists()) {
            _uiState.value = _uiState.value.copy(error = "That file no longer exists")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, entity.mimeType.ifBlank { FileUtils.mimeType(entity.fileName) })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "No app can open ${entity.fileName}")
        }
    }

    fun share(entity: DownloadEntity) {
        val file = File(entity.filePath)
        if (!file.exists()) {
            _uiState.value = _uiState.value.copy(error = "That file no longer exists")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = entity.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${entity.fileName}"))
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Couldn't share that file")
        }
    }

    fun isPdf(entity: DownloadEntity): Boolean =
        entity.mimeType.contains("pdf", true) || entity.fileName.endsWith(".pdf", true)
}
