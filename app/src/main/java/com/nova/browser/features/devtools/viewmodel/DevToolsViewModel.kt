package com.nova.browser.features.devtools.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.repository.AIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class ConsoleEntry(
    val id: Long,
    val level: String,
    val message: String,
    val source: String,
    val line: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isError: Boolean get() = level.equals("ERROR", true)
    val isWarning: Boolean get() = level.equals("WARNING", true) || level.equals("WARN", true)
}

data class NetworkEntry(
    val id: Long,
    val url: String,
    val method: String,
    val resourceType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val blocked: Boolean = false
) {
    val host: String get() = com.nova.browser.core.utils.UrlUtils.host(url)
    val path: String get() = runCatching {
        android.net.Uri.parse(url).path.orEmpty().ifBlank { "/" }
    }.getOrDefault("/")
}

data class DevToolsUiState(
    val selectedTab: Int = 0,
    val console: List<ConsoleEntry> = emptyList(),
    val network: List<NetworkEntry> = emptyList(),
    val domTree: String = "",
    val storage: String = "",
    val source: String = "",
    val performance: String = "",
    val accessibility: String = "",
    val filter: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val aiExplanation: String? = null,
    val aiLoading: Boolean = false,
    val jsInput: String = "",
    val jsOutput: String = ""
) {
    val filteredConsole: List<ConsoleEntry>
        get() = if (filter.isBlank()) console
        else console.filter { it.message.contains(filter, true) || it.source.contains(filter, true) }

    val filteredNetwork: List<NetworkEntry>
        get() = if (filter.isBlank()) network
        else network.filter { it.url.contains(filter, true) }

    val errorCount: Int get() = console.count { it.isError }
    val warningCount: Int get() = console.count { it.isWarning }
}

/**
 * Collects live console/network events from the WebView layer and exposes the
 * DevTools panels. Buffers are capped so long sessions stay memory-safe.
 */
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val aiRepository: AIRepository
) : ViewModel() {

    private companion object {
        const val MAX_CONSOLE = 500
        const val MAX_NETWORK = 400
    }

    private val idGenerator = AtomicLong(0)
    private val _uiState = MutableStateFlow(DevToolsUiState())
    val uiState: StateFlow<DevToolsUiState> = _uiState.asStateFlow()

    fun onConsoleMessage(level: String, message: String, source: String, line: Int) {
        val entry = ConsoleEntry(idGenerator.incrementAndGet(), level, message, source, line)
        _uiState.value = _uiState.value.copy(
            console = (_uiState.value.console + entry).takeLast(MAX_CONSOLE)
        )
    }

    fun onNetworkRequest(url: String, method: String, resourceType: String, blocked: Boolean = false) {
        val entry = NetworkEntry(idGenerator.incrementAndGet(), url, method, resourceType, blocked = blocked)
        _uiState.value = _uiState.value.copy(
            network = (_uiState.value.network + entry).takeLast(MAX_NETWORK)
        )
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index, aiExplanation = null)
    }

    fun setFilter(filter: String) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun clearConsole() {
        _uiState.value = _uiState.value.copy(console = emptyList())
    }

    fun clearNetwork() {
        _uiState.value = _uiState.value.copy(network = emptyList())
    }

    fun clearAll() {
        _uiState.value = _uiState.value.copy(
            console = emptyList(),
            network = emptyList(),
            domTree = "",
            storage = "",
            source = "",
            performance = "",
            accessibility = "",
            aiExplanation = null,
            jsOutput = ""
        )
    }

    fun setLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = loading)
    }

    fun setDomTree(json: String) {
        _uiState.value = _uiState.value.copy(domTree = json, isLoading = false)
    }

    fun setStorage(json: String) {
        _uiState.value = _uiState.value.copy(storage = json, isLoading = false)
    }

    fun setSource(html: String) {
        _uiState.value = _uiState.value.copy(source = html, isLoading = false)
    }

    fun setPerformance(json: String) {
        _uiState.value = _uiState.value.copy(performance = json, isLoading = false)
    }

    fun setAccessibility(json: String) {
        _uiState.value = _uiState.value.copy(accessibility = json, isLoading = false)
    }

    fun onJsInputChange(value: String) {
        _uiState.value = _uiState.value.copy(jsInput = value)
    }

    fun setJsOutput(value: String) {
        _uiState.value = _uiState.value.copy(jsOutput = value)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissAiExplanation() {
        _uiState.value = _uiState.value.copy(aiExplanation = null)
    }

    /** AI-powered error explanation (spec 15 → AI DEBUGGER). */
    fun explainError(entry: ConsoleEntry) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(aiLoading = true, aiExplanation = null)
            val model = aiRepository.resolveModel(AIMode.CODE)
            if (model == null) {
                _uiState.value = _uiState.value.copy(
                    aiLoading = false,
                    error = "Add an AI API key in Settings to use the AI debugger."
                )
                return@launch
            }
            val prompt = """
                Explain this browser console error and how to fix it. Be concise and practical.

                Level: ${entry.level}
                Message: ${entry.message}
                Source: ${entry.source}:${entry.line}
            """.trimIndent()

            aiRepository.complete(AIMode.CODE, prompt, model, temperature = 0.3).fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(aiLoading = false, aiExplanation = text)
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        aiLoading = false,
                        error = throwable.message ?: "Couldn't explain that error."
                    )
                }
            )
        }
    }

    /** Summarizes all current errors at once. */
    fun analyzeAllErrors() {
        val errors = _uiState.value.console.filter { it.isError }
        if (errors.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "There are no errors to analyze.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(aiLoading = true, aiExplanation = null)
            val model = aiRepository.resolveModel(AIMode.CODE)
            if (model == null) {
                _uiState.value = _uiState.value.copy(
                    aiLoading = false,
                    error = "Add an AI API key in Settings to use the AI debugger."
                )
                return@launch
            }
            val prompt = buildString {
                appendLine("Group and explain these browser console errors, then suggest fixes:")
                errors.takeLast(20).forEach { appendLine("- [${it.source}:${it.line}] ${it.message}") }
            }
            aiRepository.complete(AIMode.CODE, prompt, model, temperature = 0.3).fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(aiLoading = false, aiExplanation = text)
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        aiLoading = false,
                        error = throwable.message ?: "The analysis failed."
                    )
                }
            )
        }
    }
}
