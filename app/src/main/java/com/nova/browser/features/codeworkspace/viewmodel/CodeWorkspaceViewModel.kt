package com.nova.browser.features.codeworkspace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.dao.NoteDao
import com.nova.browser.core.database.entities.NoteEntity
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.repository.AIRepository
import com.nova.browser.features.codeworkspace.repository.ApiTester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Languages offered in the snippet editor. */
val CODE_LANGUAGES = listOf(
    "kotlin", "javascript", "typescript", "python", "java", "html", "css", "sql", "bash", "json"
)

data class CodeWorkspaceUiState(
    val selectedTab: Int = 0,

    // Snippet editor
    val code: String = "",
    val language: String = CODE_LANGUAGES.first(),
    val instruction: String = "",
    val aiOutput: String = "",
    val aiLoading: Boolean = false,

    // API tester
    val method: String = "GET",
    val url: String = "",
    val headers: String = "Content-Type: application/json",
    val requestBody: String = "",
    val response: ApiTester.ApiResponse? = null,
    val requestLoading: Boolean = false,

    val error: String? = null,
    val message: String? = null,
    val hasApiKey: Boolean = false
)

@HiltViewModel
class CodeWorkspaceViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val apiTester: ApiTester,
    private val noteDao: NoteDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeWorkspaceUiState())
    val uiState: StateFlow<CodeWorkspaceUiState> = _uiState.asStateFlow()

    val methods = ApiTester.METHODS
    val languages = CODE_LANGUAGES

    init {
        _uiState.value = _uiState.value.copy(hasApiKey = aiRepository.hasAnyKey())
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /* ----------------------------- snippet editor ----------------------------- */

    fun onCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(code = value)
    }

    fun onLanguageChange(value: String) {
        _uiState.value = _uiState.value.copy(language = value)
    }

    fun onInstructionChange(value: String) {
        _uiState.value = _uiState.value.copy(instruction = value)
    }

    fun clearOutput() {
        _uiState.value = _uiState.value.copy(aiOutput = "")
    }

    /** Runs one of the preset code actions, or a free-form instruction. */
    fun runCodeAction(action: CodeAction) {
        val state = _uiState.value
        if (state.code.isBlank() && action != CodeAction.GENERATE) {
            _uiState.value = state.copy(error = "Paste some code first")
            return
        }
        if (action == CodeAction.GENERATE && state.instruction.isBlank()) {
            _uiState.value = state.copy(error = "Describe what you want generated")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(aiLoading = true, aiOutput = "", error = null)
            val model = aiRepository.resolveModel(AIMode.CODE)
            if (model == null) {
                _uiState.value = _uiState.value.copy(
                    aiLoading = false,
                    error = "Add an AI API key in Settings to use the code assistant."
                )
                return@launch
            }

            val prompt = buildPrompt(action, state)
            aiRepository.complete(AIMode.CODE, prompt, model, temperature = 0.2).fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(aiLoading = false, aiOutput = text)
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

    private fun buildPrompt(action: CodeAction, state: CodeWorkspaceUiState): String = when (action) {
        CodeAction.EXPLAIN -> """
            Explain this ${state.language} code clearly, step by step. Note anything surprising or buggy.

            ```${state.language}
            ${state.code}
            ```
        """.trimIndent()

        CodeAction.DEBUG -> """
            Find bugs in this ${state.language} code. List each problem, why it happens, and the fix.
            Finish with a corrected version.

            ```${state.language}
            ${state.code}
            ```
        """.trimIndent()

        CodeAction.OPTIMIZE -> """
            Optimise this ${state.language} code for readability and performance.
            Explain each change briefly, then give the improved version.

            ```${state.language}
            ${state.code}
            ```
        """.trimIndent()

        CodeAction.CONVERT -> """
            Convert this code to ${state.instruction.ifBlank { "TypeScript" }}.
            Keep behaviour identical and use idiomatic style for the target language.

            ```${state.language}
            ${state.code}
            ```
        """.trimIndent()

        CodeAction.TEST -> """
            Write thorough unit tests for this ${state.language} code, covering edge cases.
            Use the most common testing framework for the language.

            ```${state.language}
            ${state.code}
            ```
        """.trimIndent()

        CodeAction.DOCUMENT -> """
            Add clear documentation comments to this ${state.language} code and return the
            fully documented version. Don't change behaviour.

            ```${state.language}
            ${state.code}
            ```
        """.trimIndent()

        CodeAction.GENERATE -> """
            Write ${state.language} code that does the following. Return complete, runnable
            code with a short explanation.

            ${state.instruction}
        """.trimIndent()
    }

    /** Saves the current snippet plus AI output as a note. */
    fun saveAsNote() {
        val state = _uiState.value
        if (state.code.isBlank() && state.aiOutput.isBlank()) {
            _uiState.value = state.copy(error = "Nothing to save yet")
            return
        }
        viewModelScope.launch {
            val ok = runCatching {
                noteDao.insert(
                    NoteEntity(
                        title = "Code snippet (${state.language})",
                        content = buildString {
                            if (state.code.isNotBlank()) {
                                appendLine("```${state.language}")
                                appendLine(state.code)
                                appendLine("```")
                            }
                            if (state.aiOutput.isNotBlank()) {
                                appendLine()
                                appendLine(state.aiOutput)
                            }
                        },
                        type = "note",
                        tags = """["code","${state.language}"]"""
                    )
                )
            }.isSuccess
            _uiState.value = _uiState.value.copy(
                message = if (ok) "Saved to Notes" else null,
                error = if (ok) null else "Couldn't save that snippet"
            )
        }
    }

    /* ------------------------------- API tester ------------------------------- */

    fun onMethodChange(value: String) {
        _uiState.value = _uiState.value.copy(method = value)
    }

    fun onUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(url = value)
    }

    fun onHeadersChange(value: String) {
        _uiState.value = _uiState.value.copy(headers = value)
    }

    fun onRequestBodyChange(value: String) {
        _uiState.value = _uiState.value.copy(requestBody = value)
    }

    fun sendRequest() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(requestLoading = true, error = null, response = null)
            apiTester.send(state.method, state.url, state.headers, state.requestBody).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(requestLoading = false, response = response)
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        requestLoading = false,
                        error = throwable.message ?: "The request failed"
                    )
                }
            )
        }
    }

    /** Asks the AI to explain the last API response. */
    fun explainResponse() {
        val response = _uiState.value.response
        if (response == null) {
            _uiState.value = _uiState.value.copy(error = "Send a request first")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(aiLoading = true, error = null)
            val model = aiRepository.resolveModel(AIMode.CODE)
            if (model == null) {
                _uiState.value = _uiState.value.copy(
                    aiLoading = false,
                    error = "Add an AI API key in Settings to use the code assistant."
                )
                return@launch
            }
            val prompt = """
                Explain this HTTP response: what it means, whether it indicates a problem,
                and what to do next.

                Status: ${response.code} ${response.message}
                Body:
                ${response.body.take(4000)}
            """.trimIndent()

            aiRepository.complete(AIMode.CODE, prompt, model, temperature = 0.3).fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(
                        aiLoading = false,
                        aiOutput = text,
                        selectedTab = 0
                    )
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        aiLoading = false,
                        error = throwable.message ?: "Couldn't explain that response"
                    )
                }
            )
        }
    }
}

/** Preset actions the code assistant supports. */
enum class CodeAction(val label: String) {
    EXPLAIN("Explain"),
    DEBUG("Find bugs"),
    OPTIMIZE("Optimise"),
    CONVERT("Convert"),
    TEST("Write tests"),
    DOCUMENT("Document"),
    GENERATE("Generate")
}
