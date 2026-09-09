package com.nova.browser.features.ai.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nova.browser.core.network.OpenRouterApiService
import com.nova.browser.core.network.models.OpenRouterMessage
import com.nova.browser.core.network.models.OpenRouterRequest
import com.nova.browser.core.network.models.OpenRouterResponse
import com.nova.browser.core.security.SecureStorage
import com.nova.browser.core.utils.Constants
import com.nova.browser.core.utils.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** OpenRouter implementation (spec 08_OPENROUTER_API). */
@Singleton
class OpenRouterEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: OpenRouterApiService,
    private val secureStorage: SecureStorage,
    private val gson: Gson,
    @Named("streaming") private val streamingClient: OkHttpClient
) : AIEngine {

    override val provider = AIProvider.OPENROUTER

    override fun defaultModels(): List<AIModel> = listOf(
        AIModel("openai/gpt-4o-mini", "GPT-4o mini", provider, 128_000, true, "Fast and cheap", fast = true),
        AIModel("openai/gpt-4o", "GPT-4o", provider, 128_000, true, "Powerful multimodal"),
        AIModel("anthropic/claude-3-haiku", "Claude 3 Haiku", provider, 200_000, true, "Very fast", fast = true),
        AIModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", provider, 200_000, true, "Balanced and strong"),
        AIModel("anthropic/claude-3-opus", "Claude 3 Opus", provider, 200_000, true, "Most capable Claude"),
        AIModel("google/gemini-pro-1.5", "Gemini 1.5 Pro (OR)", provider, 1_000_000, true, "Google via OpenRouter"),
        AIModel("meta-llama/llama-3-70b-instruct", "Llama 3 70B", provider, 8_192, false, "Open weights"),
        AIModel("mistralai/mixtral-8x7b-instruct", "Mixtral 8x7B", provider, 32_768, false, "Fast open model", fast = true),
        AIModel("deepseek/deepseek-chat", "DeepSeek Chat", provider, 64_000, false, "Strong at code")
    )

    override fun hasApiKey(): Boolean = secureStorage.openRouterKey.isNotBlank()

    override suspend fun fetchModels(): List<AIModel> = try {
        val response = api.listModels()
        val remote = response.body()?.data
        if (!response.isSuccessful || remote.isNullOrEmpty()) {
            defaultModels()
        } else {
            remote.map { info ->
                AIModel(
                    id = info.id,
                    label = info.name ?: info.id,
                    provider = provider,
                    contextTokens = info.contextLength ?: Constants.DEFAULT_CONTEXT_TOKENS,
                    supportsVision = info.supportsVision,
                    description = info.description.orEmpty().take(160),
                    fast = info.id.contains("mini") || info.id.contains("haiku") || info.id.contains("flash")
                )
            }.sortedBy { it.label }
        }
    } catch (e: Exception) {
        defaultModels()
    }

    override suspend fun complete(request: AIRequest): Result<Pair<String, AIUsage>> {
        val key = secureStorage.openRouterKey
        if (key.isBlank()) return Result.failure(AIException(AIError.MissingKey(provider)))
        if (!NetworkUtils.isOnline(context)) return Result.failure(AIException(AIError.Offline))

        return try {
            val response = api.chatCompletion("Bearer $key", buildRequest(request, stream = false))
            if (!response.isSuccessful) {
                return Result.failure(
                    AIException(mapHttpError(response.code(), response.errorBody()?.string().orEmpty()))
                )
            }
            val payload = response.body() ?: return Result.failure(AIException(AIError.EmptyResponse()))
            payload.error?.let {
                return Result.failure(
                    AIException(AIError.Server(-1, it.message ?: "OpenRouter error"))
                )
            }
            val text = payload.text
                ?: return Result.failure(AIException(AIError.EmptyResponse()))
            Result.success(text to usageOf(payload))
        } catch (e: Exception) {
            Result.failure(AIException(mapException(e)))
        }
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> = flow {
        val key = secureStorage.openRouterKey
        if (key.isBlank()) {
            emit(AIStreamEvent.Failure(AIError.MissingKey(provider)))
            return@flow
        }
        if (!NetworkUtils.isOnline(context)) {
            emit(AIStreamEvent.Failure(AIError.Offline))
            return@flow
        }

        emit(AIStreamEvent.Start)

        val bodyJson = gson.toJson(buildRequest(request, stream = true))
        val httpRequest = Request.Builder()
            .url(Constants.OPENROUTER_BASE_URL + "chat/completions")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $key")
            .header("HTTP-Referer", Constants.OPENROUTER_REFERER)
            .header("X-Title", Constants.OPENROUTER_TITLE)
            .header("Accept", "text/event-stream")
            .build()

        val builder = StringBuilder()
        var usage = AIUsage()

        try {
            streamingClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(AIStreamEvent.Failure(mapHttpError(response.code, response.body?.string().orEmpty())))
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(AIStreamEvent.Failure(AIError.EmptyResponse()))
                    return@flow
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = try {
                        gson.fromJson(data, OpenRouterResponse::class.java)
                    } catch (e: JsonSyntaxException) {
                        null
                    } ?: continue

                    chunk.error?.let {
                        emit(
                            AIStreamEvent.Failure(
                                AIError.Server(-1, it.message ?: "OpenRouter error"),
                                builder.toString()
                            )
                        )
                        return@flow
                    }
                    chunk.usage?.let { usage = usageOf(chunk) }
                    val delta = chunk.choices?.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) {
                        builder.append(delta)
                        emit(AIStreamEvent.Chunk(delta))
                    }
                }
            }
        } catch (e: Exception) {
            emit(AIStreamEvent.Failure(mapException(e), builder.toString()))
            return@flow
        }

        val full = builder.toString()
        if (full.isBlank()) {
            emit(AIStreamEvent.Failure(AIError.EmptyResponse()))
        } else {
            emit(
                AIStreamEvent.Complete(
                    fullText = full,
                    usage = if (usage.totalTokens > 0) usage else estimateUsage(request, full),
                    model = request.model
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /* ------------------------------------------------------------------ */

    private fun buildRequest(request: AIRequest, stream: Boolean): OpenRouterRequest {
        val messages = mutableListOf<OpenRouterMessage>()
        if (request.systemPrompt.isNotBlank()) {
            messages += OpenRouterMessage.text("system", request.systemPrompt)
        }
        request.messages.forEach { message ->
            val role = when (message.role) {
                AIMessage.Role.SYSTEM -> "system"
                AIMessage.Role.USER -> "user"
                AIMessage.Role.ASSISTANT -> "assistant"
            }
            messages += if (message.images.isEmpty()) {
                OpenRouterMessage.text(role, message.content)
            } else {
                OpenRouterMessage.multimodal(
                    role = role,
                    text = message.content,
                    imageDataUrls = message.images.map { "data:${it.mimeType};base64,${it.base64Data}" }
                )
            }
        }
        return OpenRouterRequest(
            model = request.model.id,
            messages = messages,
            temperature = request.temperature,
            maxTokens = request.maxOutputTokens,
            stream = stream
        )
    }

    private fun usageOf(response: OpenRouterResponse): AIUsage {
        val usage = response.usage ?: return AIUsage()
        return AIUsage(usage.promptTokens, usage.completionTokens, usage.totalTokens)
    }

    private fun estimateUsage(request: AIRequest, output: String): AIUsage {
        val promptWords = request.messages.sumOf { it.content.split(" ").size } +
            request.systemPrompt.split(" ").size
        val prompt = (promptWords * 1.3).toInt()
        val completion = (output.split(" ").size * 1.3).toInt()
        return AIUsage(prompt, completion, prompt + completion)
    }

    private fun mapHttpError(code: Int, body: String): AIError {
        val message = extractMessage(body) ?: "HTTP $code"
        return when (code) {
            400 -> AIError.Server(code, "Invalid request: $message")
            401 -> AIError.InvalidKey(provider)
            402 -> AIError.Server(code, "Insufficient OpenRouter credits. Top up your account.")
            403 -> AIError.Server(code, "Access denied for this model: $message")
            404 -> AIError.Server(code, "Model not available. Pick another model.")
            429 -> AIError.RateLimited(retryAfterSeconds = 20)
            in 500..599 -> AIError.Server(code, "OpenRouter is temporarily unavailable. $message")
            else -> AIError.Server(code, message)
        }
    }

    private fun extractMessage(body: String): String? = try {
        gson.fromJson(body, OpenRouterResponse::class.java)?.error?.message
    } catch (e: Exception) {
        body.takeIf { it.isNotBlank() }?.take(200)
    }

    private fun mapException(e: Exception): AIError = when (e) {
        is SocketTimeoutException -> AIError.Timeout()
        is UnknownHostException -> AIError.Offline
        is IOException -> AIError.Unknown(e.message ?: "Network error")
        else -> AIError.Unknown(e.message ?: "Unexpected error")
    }
}
