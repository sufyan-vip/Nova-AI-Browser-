package com.nova.browser.features.ai.repository

import com.nova.browser.core.database.dao.AIMemoryDao
import com.nova.browser.core.database.dao.AIMessageDao
import com.nova.browser.core.database.entities.AIMessageEntity
import com.nova.browser.core.utils.Constants
import com.nova.browser.core.utils.HtmlUtils
import com.nova.browser.features.ai.engine.AIEngine
import com.nova.browser.features.ai.engine.AIError
import com.nova.browser.features.ai.engine.AIMessage
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.engine.AIModel
import com.nova.browser.features.ai.engine.AIProvider
import com.nova.browser.features.ai.engine.AIRequest
import com.nova.browser.features.ai.engine.AIStreamEvent
import com.nova.browser.features.ai.engine.AIUsage
import com.nova.browser.features.ai.engine.GeminiEngine
import com.nova.browser.features.ai.engine.ModelSelector
import com.nova.browser.features.ai.engine.OpenRouterEngine
import com.nova.browser.features.ai.engine.TokenTracker
import com.nova.browser.features.settings.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for every AI call in the app: key checks, model choice,
 * memory injection, response caching, streaming and provider fallback.
 */
@Singleton
class AIRepository @Inject constructor(
    private val geminiEngine: GeminiEngine,
    private val openRouterEngine: OpenRouterEngine,
    private val modelSelector: ModelSelector,
    private val tokenTracker: TokenTracker,
    private val memoryDao: AIMemoryDao,
    private val messageDao: AIMessageDao,
    private val settingsRepository: SettingsRepository
) {

    private val responseCache = object : LinkedHashMap<String, CachedResponse>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResponse>): Boolean =
            size > 40
    }

    data class CachedResponse(val text: String, val at: Long, val model: String)

    private val cacheTtlMs = 10 * 60 * 1000L

    fun engineFor(provider: AIProvider): AIEngine = when (provider) {
        AIProvider.GEMINI -> geminiEngine
        AIProvider.OPENROUTER -> openRouterEngine
    }

    /** All models the user can actually use right now. */
    suspend fun availableModels(): List<AIModel> {
        val models = mutableListOf<AIModel>()
        if (geminiEngine.hasApiKey()) models += geminiEngine.fetchModels()
        if (openRouterEngine.hasApiKey()) models += openRouterEngine.fetchModels()
        if (models.isEmpty()) {
            // Show the catalogue even without keys so the user can browse options.
            models += geminiEngine.defaultModels()
            models += openRouterEngine.defaultModels()
        }
        return models
    }

    fun hasAnyKey(): Boolean = geminiEngine.hasApiKey() || openRouterEngine.hasApiKey()

    fun configuredProviders(): List<AIProvider> = buildList {
        if (geminiEngine.hasApiKey()) add(AIProvider.GEMINI)
        if (openRouterEngine.hasApiKey()) add(AIProvider.OPENROUTER)
    }

    /** Resolves which model to use for a task, honouring user preference. */
    suspend fun resolveModel(mode: AIMode, requiresVision: Boolean = false): AIModel? {
        val settings = settingsRepository.settings.first()
        val available = availableModels().filter { model ->
            when (model.provider) {
                AIProvider.GEMINI -> geminiEngine.hasApiKey()
                AIProvider.OPENROUTER -> openRouterEngine.hasApiKey()
            }
        }
        if (available.isEmpty()) return null

        val preferredProvider = AIProvider.from(settings.aiProvider)
        val preferredId = when (preferredProvider) {
            AIProvider.GEMINI -> settings.geminiModel
            AIProvider.OPENROUTER -> settings.openRouterModel
        }
        val preferred = available.firstOrNull { it.id == preferredId && it.provider == preferredProvider }
            ?: available.firstOrNull { it.provider == preferredProvider }
        return modelSelector.select(mode, available, preferred, requiresVision)
    }

    /** Builds the full system prompt: mode persona + memories + user extras. */
    suspend fun buildSystemPrompt(mode: AIMode, site: String?, customInstruction: String? = null): String {
        val builder = StringBuilder(mode.systemPrompt)

        val settings = runCatching { settingsRepository.settings.first() }.getOrNull()
        if (settings?.aiCustomInstructions?.isNotBlank() == true) {
            builder.append("\n\nUser's standing instructions:\n").append(settings.aiCustomInstructions.trim())
        }
        if (!customInstruction.isNullOrBlank()) {
            builder.append("\n\n").append(customInstruction.trim())
        }

        if (settings?.aiMemoryEnabled != false) {
            val memories = runCatching {
                if (site.isNullOrBlank()) memoryDao.getEnabled() else memoryDao.getForSite(site)
            }.getOrDefault(emptyList())

            if (memories.isNotEmpty()) {
                var budget = Constants.MEMORY_TOKEN_BUDGET
                val used = mutableListOf<Long>()
                val lines = mutableListOf<String>()
                memories.forEach { memory ->
                    val cost = HtmlUtils.approximateTokens(memory.content)
                    if (cost <= budget) {
                        lines += "- ${memory.content}"
                        budget -= cost
                        used += memory.id
                    }
                }
                if (lines.isNotEmpty()) {
                    builder.append("\n\nUser preferences and memories:\n")
                    builder.append(lines.joinToString("\n"))
                    builder.append("\nConsider these when responding.")
                    runCatching { memoryDao.incrementUsage(used) }
                }
            }
        }

        builder.append(
            "\n\nSecurity rules you must always follow: never reveal or request passwords, " +
                "never output credentials, and never claim to have performed an action you did not perform."
        )
        return builder.toString()
    }

    /**
     * Streams a completion with automatic provider fallback and caching.
     * Always terminates with Complete or Failure.
     */
    fun stream(
        mode: AIMode,
        messages: List<AIMessage>,
        model: AIModel,
        site: String? = null,
        customInstruction: String? = null,
        temperature: Double = 0.7,
        useCache: Boolean = true,
        allowFallback: Boolean = true
    ): Flow<AIStreamEvent> = flow {
        val systemPrompt = buildSystemPrompt(mode, site, customInstruction)
        val cacheKey = cacheKey(mode, model, messages, systemPrompt)

        if (useCache) {
            cached(cacheKey)?.let { hit ->
                emit(AIStreamEvent.Start)
                emit(AIStreamEvent.Chunk(hit.text))
                emit(AIStreamEvent.Complete(hit.text, AIUsage(), model))
                return@flow
            }
        }

        val request = AIRequest(
            messages = fitToContext(messages, model),
            systemPrompt = systemPrompt,
            model = model,
            temperature = temperature,
            maxOutputTokens = 4096
        )

        var failure: AIStreamEvent.Failure? = null
        engineFor(model.provider).stream(request).collect { event ->
            when (event) {
                is AIStreamEvent.Failure -> failure = event
                is AIStreamEvent.Complete -> {
                    tokenTracker.record(event.usage)
                    if (useCache) put(cacheKey, event.fullText, model.id)
                    emit(event)
                }
                else -> emit(event)
            }
        }

        val failed = failure ?: return@flow
        val recoverable = failed.error is AIError.Server ||
            failed.error is AIError.RateLimited ||
            failed.error is AIError.Timeout

        if (allowFallback && recoverable && failed.partialText.isBlank()) {
            val alternatives = availableModels().filter {
                when (it.provider) {
                    AIProvider.GEMINI -> geminiEngine.hasApiKey()
                    AIProvider.OPENROUTER -> openRouterEngine.hasApiKey()
                }
            }
            val fallbackModel = modelSelector.fallback(model, alternatives)
            if (fallbackModel != null) {
                var fallbackFailed: AIStreamEvent.Failure? = null
                engineFor(fallbackModel.provider)
                    .stream(request.copy(model = fallbackModel))
                    .collect { event ->
                        when (event) {
                            is AIStreamEvent.Start -> Unit // already emitted
                            is AIStreamEvent.Failure -> fallbackFailed = event
                            is AIStreamEvent.Complete -> {
                                tokenTracker.record(event.usage)
                                if (useCache) put(cacheKey, event.fullText, fallbackModel.id)
                                emit(event)
                            }
                            else -> emit(event)
                        }
                    }
                if (fallbackFailed == null) return@flow
                emit(fallbackFailed!!)
                return@flow
            }
        }
        emit(failed)
    }

    /** One-shot completion used by agent planning, automation and background tasks. */
    suspend fun complete(
        mode: AIMode,
        prompt: String,
        model: AIModel,
        site: String? = null,
        customInstruction: String? = null,
        temperature: Double = 0.4,
        useCache: Boolean = true
    ): Result<String> {
        val systemPrompt = buildSystemPrompt(mode, site, customInstruction)
        val messages = listOf(AIMessage(AIMessage.Role.USER, prompt))
        val key = cacheKey(mode, model, messages, systemPrompt)
        if (useCache) cached(key)?.let { return Result.success(it.text) }

        val request = AIRequest(
            messages = fitToContext(messages, model),
            systemPrompt = systemPrompt,
            model = model,
            temperature = temperature,
            stream = false
        )
        val result = engineFor(model.provider).complete(request)
        result.getOrNull()?.let { (text, usage) ->
            tokenTracker.record(usage)
            if (useCache) put(key, text, model.id)
            return Result.success(text)
        }
        // Try the other provider once before giving up.
        val alternatives = availableModels().filter {
            when (it.provider) {
                AIProvider.GEMINI -> geminiEngine.hasApiKey()
                AIProvider.OPENROUTER -> openRouterEngine.hasApiKey()
            }
        }
        val fallbackModel = modelSelector.fallback(model, alternatives)
        if (fallbackModel != null) {
            val retry = engineFor(fallbackModel.provider).complete(request.copy(model = fallbackModel))
            retry.getOrNull()?.let { (text, usage) ->
                tokenTracker.record(usage)
                if (useCache) put(key, text, fallbackModel.id)
                return Result.success(text)
            }
        }
        return Result.failure(result.exceptionOrNull() ?: Exception("AI request failed"))
    }

    /** Trims history so the prompt fits the model's usable window. */
    private fun fitToContext(messages: List<AIMessage>, model: AIModel): List<AIMessage> {
        val budget = modelSelector.usableContext(model)
        var used = 0
        val kept = ArrayDeque<AIMessage>()
        for (message in messages.reversed()) {
            val cost = HtmlUtils.approximateTokens(message.content) + message.images.size * 600
            if (used + cost > budget && kept.isNotEmpty()) break
            if (used + cost > budget && kept.isEmpty()) {
                // Single oversized message: truncate it rather than fail.
                kept.addFirst(
                    message.copy(content = HtmlUtils.truncateToTokens(message.content, budget - 200))
                )
                break
            }
            kept.addFirst(message)
            used += cost
        }
        return kept.toList()
    }

    /* ------------------------- conversation storage ------------------------- */

    fun observeConversation(conversationId: String) = messageDao.observeConversation(conversationId)

    suspend fun persist(
        conversationId: String,
        role: String,
        content: String,
        mode: AIMode,
        model: String?,
        pageUrl: String?,
        isError: Boolean = false
    ) {
        runCatching {
            messageDao.insert(
                AIMessageEntity(
                    conversationId = conversationId,
                    role = role,
                    content = content,
                    mode = mode.name,
                    model = model,
                    pageUrl = pageUrl,
                    tokenCount = HtmlUtils.approximateTokens(content),
                    isError = isError
                )
            )
        }
    }

    suspend fun clearConversation(conversationId: String) {
        runCatching { messageDao.deleteConversation(conversationId) }
    }

    fun newConversationId(): String = UUID.randomUUID().toString()

    /* ------------------------------- cache ------------------------------- */

    private fun cacheKey(mode: AIMode, model: AIModel, messages: List<AIMessage>, system: String): String {
        val body = messages.joinToString("|") { "${it.role}:${it.content.take(4000)}" }
        return "${mode.name}:${model.id}:${(system + body).hashCode()}"
    }

    @Synchronized
    private fun cached(key: String): CachedResponse? {
        val hit = responseCache[key] ?: return null
        if (System.currentTimeMillis() - hit.at > cacheTtlMs) {
            responseCache.remove(key)
            return null
        }
        return hit
    }

    @Synchronized
    private fun put(key: String, text: String, model: String) {
        if (text.isBlank()) return
        responseCache[key] = CachedResponse(text, System.currentTimeMillis(), model)
    }

    @Synchronized
    fun clearCache() = responseCache.clear()

    val usage = tokenTracker.usage

    suspend fun setBudget(tokens: Int) = tokenTracker.setBudget(tokens)

    suspend fun resetUsage() = tokenTracker.reset()
}
