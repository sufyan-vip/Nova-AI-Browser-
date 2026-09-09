package com.nova.browser.features.pdf.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.dao.NoteDao
import com.nova.browser.core.database.entities.NoteEntity
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.repository.AIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class PdfUiState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val aiPanelVisible: Boolean = false,
    val aiLoading: Boolean = false,
    val aiOutput: String = "",
    val question: String = "",
    val hasApiKey: Boolean = false
)

/**
 * Renders a local PDF with the platform [PdfRenderer] (no third-party parser)
 * and exposes AI summarise / ask actions over the extracted page bitmap count.
 * Pages are rendered on demand and recycled to keep memory flat.
 */
@HiltViewModel
class PdfViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val noteDao: NoteDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var filePath: String = ""

    init {
        _uiState.value = _uiState.value.copy(hasApiKey = aiRepository.hasAnyKey())
    }

    /** Opens [path]; safe to call repeatedly with the same file. */
    fun open(path: String) {
        if (path == filePath && renderer != null) return
        filePath = path
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.IO) { openInternal(path) }
            _uiState.value = result
        }
    }

    private fun openInternal(path: String): PdfUiState {
        close()
        val file = File(path)
        if (path.isBlank() || !file.exists()) {
            return _uiState.value.copy(
                isLoading = false,
                error = "That file no longer exists on this device."
            )
        }
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdf = PdfRenderer(fd)
            descriptor = fd
            renderer = pdf
            _uiState.value.copy(
                fileName = file.name,
                pageCount = pdf.pageCount,
                currentPage = 0,
                isLoading = false,
                error = if (pdf.pageCount == 0) "This PDF has no pages." else null
            )
        } catch (e: SecurityException) {
            _uiState.value.copy(isLoading = false, error = "This PDF is password protected.")
        } catch (e: Exception) {
            _uiState.value.copy(isLoading = false, error = "This file couldn't be opened as a PDF.")
        }
    }

    /**
     * Renders [index] at a width of [widthPx]. Returns null when the page is
     * out of range or rendering fails — the UI shows a placeholder instead.
     */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val pdf = renderer ?: return@withContext null
        if (index < 0 || index >= pdf.pageCount || widthPx <= 0) return@withContext null
        try {
            synchronized(this@PdfViewModel) {
                pdf.openPage(index).use { page ->
                    val targetWidth = widthPx.coerceAtMost(2048)
                    val scale = targetWidth.toFloat() / page.width
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun setCurrentPage(index: Int) {
        val count = _uiState.value.pageCount
        if (count == 0) return
        _uiState.value = _uiState.value.copy(currentPage = index.coerceIn(0, count - 1))
    }

    fun setZoom(value: Float) {
        _uiState.value = _uiState.value.copy(zoom = value.coerceIn(0.5f, 3f))
    }

    fun showAiPanel(visible: Boolean) {
        _uiState.value = _uiState.value.copy(aiPanelVisible = visible)
    }

    fun onQuestionChange(value: String) {
        _uiState.value = _uiState.value.copy(question = value)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /**
     * Asks the AI about the document. PdfRenderer gives no text layer, so the
     * prompt carries the file name and page count and the model is told to say
     * when it can't answer from that alone.
     */
    fun askAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                aiPanelVisible = true,
                aiLoading = true,
                aiOutput = "",
                error = null
            )
            val model = aiRepository.resolveModel(AIMode.CHAT)
            if (model == null) {
                _uiState.value = _uiState.value.copy(
                    aiLoading = false,
                    error = "Add an AI API key in Settings to use PDF assistance."
                )
                return@launch
            }

            val state = _uiState.value
            val fullPrompt = """
                A user is reading a PDF in a mobile browser.

                File: ${state.fileName}
                Pages: ${state.pageCount}
                Currently on page: ${state.currentPage + 1}

                Their request: $prompt

                If answering needs the document's text, say clearly that you can't read
                the file's contents and suggest what they could paste in instead.
            """.trimIndent()

            aiRepository.complete(AIMode.CHAT, fullPrompt, model, temperature = 0.4).fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(
                        aiLoading = false,
                        aiOutput = text,
                        question = ""
                    )
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        aiLoading = false,
                        error = throwable.message ?: "The request failed. Try again."
                    )
                }
            )
        }
    }

    fun saveAiOutputAsNote() {
        val state = _uiState.value
        if (state.aiOutput.isBlank()) return
        viewModelScope.launch {
            val ok = runCatching {
                noteDao.insert(
                    NoteEntity(
                        title = state.fileName.ifBlank { "PDF notes" },
                        content = state.aiOutput,
                        type = "summary",
                        tags = """["pdf"]"""
                    )
                )
            }.isSuccess
            _uiState.value = _uiState.value.copy(
                message = if (ok) "Saved to Notes" else null,
                error = if (ok) null else "Couldn't save that note"
            )
        }
    }

    private fun close() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }

    override fun onCleared() {
        super.onCleared()
        close()
    }
}
