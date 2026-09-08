package com.nova.browser.features.ai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.dao.AIMemoryDao
import com.nova.browser.core.database.dao.NoteDao
import com.nova.browser.core.database.entities.AIMemoryEntity
import com.nova.browser.core.database.entities.NoteEntity
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.ai.engine.AIError
import com.nova.browser.features.ai.engine.AIImage
import com.nova.browser.features.ai.engine.AIMessage
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.engine.AIModel
import com.nova.browser.features.ai.engine.AIProvider
import com.nova.browser.features.ai.engine.AIStreamEvent
import com.nova.browser.features.ai.engine.ContextEngine
import com.nova.browser.features.ai.engine.ModelSelector
import com.nova.browser.features.ai.engine.PageContext
import com.nova.browser.features.ai.repository.AIRepository
import com.nova.browser.features.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One rendered chat bubble. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val error: AIError? = null,
    val model: String? = null,
    val mode: AIMode = AIMode.CHAT,
    val sources: List<Source> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT }

    data class Source(val index: Int, val title: String, val url: String)
}

data class AIUiState(
    val visible: Boolean = false,
    val mode: AIMode = AIMode.CHAT,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val models: List<AIModel> = emptyList(),
    val selectedModel: AIModel? = null,
    val hasApiKey: Boolean = false,
    val pageContext: PageContext = PageContext.empty(),
    val contextEnabled: Boolean = true,
    val contextSummary: String = "",
    val error: String? = null,
    val modelPickerVisible: Boolean = false,
    val commandPaletteVisible: Boolean = false,
    val tokensToday: Int = 0,
    val tokenBudget: Int = 0,
    val researchSources: List<PageContext> = emptyList(),
    val attachedImage: AIImage? = null,
    val suggestedMemory: String? = null
)

@HiltViewModel
class AIViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val contextEngine: ContextEngine,
    private val modelSelector: ModelSelector,
    private val settingsRepository: SettingsRepository,
    private val memoryDao: AIMemoryDao,
    private val noteDao: NoteDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    private var conversationId = aiRepository.newConversationId()
    private var generationJob: Job? = null

    val usage = aiRepository.usage.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.nova.browser.features.ai.engine.TokenTracker.Usage()
    )

    init {
        refreshModels()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(contextEnabled = settings.aiPageContextEnabled)
            }
        }
        viewModelScope.launch {
            usage.collect { u ->
                _uiState.value = _uiState.value.copy(tokensToday = u.todayTokens, tokenBudget = u.budget)
            }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            val models = aiRepository.availableModels()
            val selected = aiRepository.resolveModel(_uiState.value.mode)
            _uiState.value = _uiState.value.copy(
                models = models,
                selectedModel = selected ?: models.firstOrNull(),
                hasApiKey = aiRepository.hasAnyKey()
            )
        }
    }

    /* ------------------------------- visibility ------------------------------- */

    fun show(mode: AIMode = _uiState.value.mode) {
        _uiState.value = _uiState.value.copy(visible = true, mode = mode, error = null)
        if (_uiState.value.models.isEmpty()) refreshModels()
    }

    fun hide() {
        _uiState.value = _uiState.value.copy(visible = false, commandPaletteVisible = false)
    }

    fun toggle() = if (_uiState.value.visible) hide() else show()

    fun setMode(mode: AIMode) {
        _uiState.value = _uiState.value.copy(mode = mode, error = null)
        viewModelScope.launch {
            aiRepository.resolveModel(mode)?.let { model ->
                _uiState.value = _uiState.value.copy(selectedModel = model)
            }
        }
    }

    fun showModelPicker(visible: Boolean) {
        _uiState.value = _uiState.value.copy(modelPickerVisible = visible)
    }

    fun showCommandPalette(visible: Boolean) {
        _uiState.value = _uiState.value.copy(commandPaletteVisible = visible)
    }

    fun selectModel(model: AIModel) {
        _uiState.value = _uiState.value.copy(selectedModel = model, modelPickerVisible = false)
        viewModelScope.launch {
            when (model.provider) {
                AIProvider.GEMINI -> {
                    settingsRepository.setAiProvider(AIProvider.GEMINI.name)
                    settingsRepository.setGeminiModel(model.id)
                }
                AIProvider.OPENROUTER -> {
                    settingsRepository.setAiProvider(AIProvider.OPENROUTER.name)
                    settingsRepository.setOpenRouterModel(model.id)
                }
            }
        }
    }

    fun setContextEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(contextEnabled = enabled)
        viewModelScope.launch { settingsRepository.setAiPageContext(enabled) }
    }

    /* ----------------------------- page context ----------------------------- */

    /** Called by the browser after each page load with the extractor's JSON. */
    fun updatePageContext(json: String, fallbackUrl: String = "", fallbackTitle: String = "") {
        val context = contextEngine.parse(json, fallbackUrl, fallbackTitle)
        _uiState.value = _uiState.value.copy(
            pageContext = context,
            contextSummary = contextEngine.describe(context)
        )
    }

    fun attachImage(image: AIImage?) {
        _uiState.value = _uiState.value.copy(attachedImage = image)
    }

    /* -------------------------------- chat -------------------------------- */

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.copy(input = value)
    }

    fun send(promptOverride: String? = null, modeOverride: AIMode? = null) {
        val state = _uiState.value
        val prompt = (promptOverride ?: state.input).trim()
        if (prompt.isBlank() || state.isGenerating) return

        val mode = modeOverride ?: state.mode
        val model = state.selectedModel
        if (model == null) {
            pushError("No AI model available. Add an API key in Settings → AI.")
            return
        }
        if (!aiRepository.hasAnyKey()) {
            pushError("No API key configured. Add a Gemini or OpenRouter key in Settings → AI.")
            return
        }

        val userMessage = ChatMessage(role = ChatMessage.Role.USER, text = prompt, mode = mode)
        val placeholder = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            text = "",
            isStreaming = true,
            model = model.label,
            mode = mode
        )
        _uiState.value = state.copy(
            messages = state.messages + userMessage + placeholder,
            input = if (promptOverride == null) "" else state.input,
            isGenerating = true,
            error = null,
            mode = mode,
            visible = true
        )

        viewModelScope.launch {
            aiRepository.persist(conversationId, "user", prompt, mode, model.id, state.pageContext.url)
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val history = buildHistory(prompt, mode, model)
            val builder = StringBuilder()

            aiRepository.stream(
                mode = mode,
                messages = history,
                model = model,
                site = UrlUtils.domain(_uiState.value.pageContext.url),
                temperature = settings.temperature
            ).collect { event ->
                when (event) {
                    AIStreamEvent.Start -> Unit

                    is AIStreamEvent.Chunk -> {
                        builder.append(event.delta)
                        updateLastAssistant { it.copy(text = builder.toString(), isStreaming = true) }
                    }

                    is AIStreamEvent.Complete -> {
                        updateLastAssistant {
                            it.copy(
                                text = event.fullText,
                                isStreaming = false,
                                model = event.model.label,
                                sources = sourcesFor(mode)
                            )
                        }
                        _uiState.value = _uiState.value.copy(isGenerating = false)
                        aiRepository.persist(
                            conversationId, "assistant", event.fullText, mode, event.model.id,
                            _uiState.value.pageContext.url
                        )
                        maybeSuggestMemory(prompt)
                    }

                    is AIStreamEvent.Failure -> {
                        val text = event.partialText.ifBlank { "" }
                        updateLastAssistant {
                            it.copy(text = text, isStreaming = false, error = event.error)
                        }
                        _uiState.value = _uiState.value.copy(
                            isGenerating = false,
                            error = event.error.message
                        )
                        aiRepository.persist(
                            conversationId, "assistant", event.error.message, mode, model.id,
                            _uiState.value.pageContext.url, isError = true
                        )
                    }
                }
            }
        }
    }

    private suspend fun buildHistory(prompt: String, mode: AIMode, model: AIModel): List<AIMessage> {
        val state = _uiState.value
        val messages = mutableListOf<AIMessage>()

        // Prior turns (exclude the streaming placeholder and errored replies).
        state.messages
            .dropLast(2)
            .filter { it.error == null && it.text.isNotBlank() }
            .takeLast(10)
            .forEach { message ->
                messages += AIMessage(
                    role = if (message.role == ChatMessage.Role.USER) AIMessage.Role.USER else AIMessage.Role.ASSISTANT,
                    content = message.text
                )
            }

        val needsContext = state.contextEnabled && mode in setOf(
            AIMode.PAGE_QA, AIMode.SUMMARIZE, AIMode.TRANSLATE, AIMode.EXPLAIN, AIMode.CHAT, AIMode.WRITE
        )
        val contextBlock = if (needsContext && !state.pageContext.isEmpty) {
            val budget = (modelSelector.usableContext(model) * 0.6).toInt()
            contextEngine.buildPrompt(
                context = state.pageContext,
                maxTokens = budget,
                query = prompt,
                includeLinks = mode == AIMode.RESEARCH,
                includeCode = mode == AIMode.CODE || state.pageContext.contentType == "code"
            )
        } else if (mode == AIMode.RESEARCH && state.researchSources.isNotEmpty()) {
            contextEngine.buildResearchContext(
                state.researchSources,
                (modelSelector.usableContext(model) * 0.7).toInt() / state.researchSources.size.coerceAtLeast(1)
            )
        } else ""

        val content = if (contextBlock.isBlank()) prompt else "$contextBlock\n\nUser request: $prompt"
        messages += AIMessage(
            role = AIMessage.Role.USER,
            content = content,
            images = listOfNotNull(state.attachedImage)
        )
        return messages
    }

    private fun sourcesFor(mode: AIMode): List<ChatMessage.Source> {
        if (mode != AIMode.RESEARCH) return emptyList()
        return _uiState.value.researchSources.mapIndexed { index, source ->
            ChatMessage.Source(index + 1, source.title.ifBlank { UrlUtils.displayUrl(source.url) }, source.url)
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        updateLastAssistant { message ->
            if (message.isStreaming) {
                message.copy(isStreaming = false, text = message.text.ifBlank { "Stopped." })
            } else message
        }
        _uiState.value = _uiState.value.copy(isGenerating = false)
    }

    fun retryLast() {
        val messages = _uiState.value.messages
        val lastUser = messages.lastOrNull { it.role == ChatMessage.Role.USER } ?: return
        _uiState.value = _uiState.value.copy(
            messages = messages.dropLastWhile { it.role == ChatMessage.Role.ASSISTANT }
                .dropLastWhile { it.id == lastUser.id }
        )
        send(lastUser.text)
    }

    fun clearConversation() {
        generationJob?.cancel()
        viewModelScope.launch { aiRepository.clearConversation(conversationId) }
        conversationId = aiRepository.newConversationId()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            isGenerating = false,
            error = null,
            suggestedMemory = null
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /* ----------------------------- quick actions ----------------------------- */

    fun summarizePage() {
        val context = _uiState.value.pageContext
        if (context.isEmpty) {
            pushError("There's no page content to summarize yet.")
            return
        }
        send("Summarize this page.", AIMode.SUMMARIZE)
    }

    fun explainPage() = send("Explain this page in simple terms.", AIMode.EXPLAIN)

    fun translatePage(language: String) =
        send("Translate the page content into $language.", AIMode.TRANSLATE)

    fun keyPoints() = send("List the key points and any important numbers or dates.", AIMode.SUMMARIZE)

    fun askAboutSelection(selection: String, question: String = "Explain this") {
        if (selection.isBlank()) return
        send("$question:\n\n\"$selection\"", AIMode.EXPLAIN)
    }

    fun analyzeImage(image: AIImage, question: String) {
        attachImage(image)
        send(question.ifBlank { "Describe this image and extract any text." }, AIMode.VISION)
    }

    fun setResearchSources(sources: List<PageContext>) {
        _uiState.value = _uiState.value.copy(researchSources = sources)
    }

    /**
     * Blocking single-shot completion used by the agent's analyze/summarize
     * steps. Returns plain text, or a human-readable error string.
     */
    suspend fun completeForAgent(instruction: String, pageText: String): String {
        val model = aiRepository.resolveModel(AIMode.AGENT) ?: return "No AI model is configured."
        val prompt = if (pageText.isBlank()) {
            instruction
        } else {
            "Page content:\n\n$pageText\n\nTask: $instruction"
        }
        return aiRepository.complete(
            mode = AIMode.AGENT,
            prompt = prompt,
            model = model,
            temperature = 0.3
        ).fold(
            onSuccess = { it },
            onFailure = { it.message ?: "The model call failed." }
        )
    }

    /* -------------------------------- memory -------------------------------- */

    private fun maybeSuggestMemory(prompt: String) {
        val lower = prompt.lowercase()
        val triggers = listOf("always", "i prefer", "remember", "from now on", "never ", "my name is")
        if (triggers.any { lower.contains(it) } && prompt.length in 8..200) {
            _uiState.value = _uiState.value.copy(suggestedMemory = prompt.trim())
        }
    }

    fun acceptSuggestedMemory() {
        val content = _uiState.value.suggestedMemory ?: return
        viewModelScope.launch {
            runCatching {
                memoryDao.insert(
                    AIMemoryEntity(
                        content = content,
                        type = "instruction",
                        relatedSite = UrlUtils.domain(_uiState.value.pageContext.url).takeIf { it.isNotBlank() }
                    )
                )
            }
            _uiState.value = _uiState.value.copy(suggestedMemory = null)
        }
    }

    fun dismissSuggestedMemory() {
        _uiState.value = _uiState.value.copy(suggestedMemory = null)
    }

    /** Saves the last AI reply as a note (spec 11 → save research). */
    fun saveLastAsNote(onSaved: (Boolean) -> Unit = {}) {
        val last = _uiState.value.messages.lastOrNull { it.role == ChatMessage.Role.ASSISTANT && it.text.isNotBlank() }
        if (last == null) {
            onSaved(false)
            return
        }
        val context = _uiState.value.pageContext
        viewModelScope.launch {
            val ok = runCatching {
                noteDao.insert(
                    NoteEntity(
                        title = context.title.ifBlank { "NOVA ${last.mode.label}" }.take(120),
                        content = last.text,
                        sourceUrl = context.url.takeIf { it.isNotBlank() },
                        sourceTitle = context.title.takeIf { it.isNotBlank() },
                        type = when (last.mode) {
                            AIMode.RESEARCH -> "research"
                            AIMode.SUMMARIZE -> "summary"
                            AIMode.CODE -> "code"
                            else -> "note"
                        }
                    )
                )
            }.isSuccess
            onSaved(ok)
        }
    }

    /* -------------------------------- helpers -------------------------------- */

    private fun updateLastAssistant(transform: (ChatMessage) -> ChatMessage) {
        val messages = _uiState.value.messages
        val index = messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT }
        if (index < 0) return
        _uiState.value = _uiState.value.copy(
            messages = messages.toMutableList().also { it[index] = transform(it[index]) }
        )
    }

    private fun pushError(message: String) {
        _uiState.value = _uiState.value.copy(error = message, visible = true)
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
