package com.nova.browser.features.ai.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nova.browser.core.network.GeminiApiService
import com.nova.browser.core.network.models.GeminiContent
import com.nova.browser.core.network.models.GeminiGenerationConfig
import com.nova.browser.core.network.models.GeminiInlineData
import com.nova.browser.core.network.models.GeminiPart
import com.nova.browser.core.network.models.GeminiRequest
import com.nova.browser.core.network.models.GeminiResponse
import com.nova.browser.core.network.models.GeminiSafetySetting
import com.nova.browser.core.network.models.GeminiSystemInstruction
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

/** Google Gemini implementation (spec 07_GEMINI_API). */
@Singleton
class GeminiEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GeminiApiService,
    private val secureStorage: SecureStorage,
    private val gson: Gson,
    @Named("streaming") private val streamingClient: OkHttpClient
) : AIEngine {

    override val provider = AIProvider.GEMINI

    override fun defaultModels(): List<AIModel> = listOf(
        AIModel("gemini-2.0-flash", "Gemini 2.0 Flash", provider, 1_048_576, true, "Fast, multimodal, default", fast = true),
        AIModel("gemini-1.5-flash", "Gemini 1.5 Flash", provider, 1_048_576, true, "Balanced speed and quality", fast = true),
        AIModel("gemini-1.5-pro", "Gemini 1.5 Pro", provider, 2_097_152, true, "Most capable, long context"),
        AIModel("gemini-1.5-flash-8b", "Gemini 1.5 Flash 8B", provider, 1_048_576, true, "Lightest and cheapest", fast = true)
    )

    override fun hasApiKey(): Boolean = secureStorage.geminiKey.isNotBlank()

    override suspend fun fetchModels(): List<AIModel> {
        val key = secureStorage.geminiKey
        if (key.isBlank()) return defaultModels()
        return try {
            val response = api.listModels(key)
            val remote = response.body()?.models
            if (!response.isSuccessful || remote.isNullOrEmpty()) return defaultModels()
            remote.filter { info ->
                info.supportedGenerationMethods?.any { it.contains("generateContent", true) } == true
            }.map { info ->
                val id = info.name.removePrefix("models/")
                AIModel(
                    id = id,
                    label = info.displayName ?: id,
                    provider = provider,
                    contextTokens = info.inputTokenLimit ?: Constants.DEFAULT_CONTEXT_TOKENS,
                    supportsVision = id.contains("vision") || id.contains("1.5") || id.contains("2.0"),
                    description = info.description.orEmpty(),
                    fast = id.contains("flash")
                )
            }.ifEmpty { defaultModels() }
        } catch (e: Exception) {
            defaultModels()
        }
    }

    override suspend fun complete(request: AIRequest): Result<Pair<String, AIUsage>> {
        val key = secureStorage.geminiKey
        if (key.isBlank()) return Result.failure(AIException(AIError.MissingKey(provider)))
        if (!NetworkUtils.isOnline(context)) return Result.failure(AIException(AIError.Offline))

        return try {
            val response = api.generateContent(request.model.id, key, buildRequest(request))
            if (!response.isSuccessful) {
                val body = response.errorBody()?.string().orEmpty()
                return Result.failure(AIException(mapHttpError(response.code(), body)))
            }
            val payload = response.body()
                ?: return Result.failure(AIException(AIError.EmptyResponse()))
            payload.error?.let {
                return Result.failure(AIException(AIError.Server(it.code ?: -1, it.message ?: "Gemini error")))
            }
            payload.blockReason?.let {
                return Result.failure(AIException(AIError.Blocked(it)))
            }
            val text = payload.text
            if (text.isNullOrBlank()) {
                val reason = payload.finishReason
                if (reason == "SAFETY" || reason == "RECITATION") {
                    return Result.failure(AIException(AIError.Blocked(reason)))
                }
                return Result.failure(AIException(AIError.EmptyResponse()))
            }
            Result.success(text to usageOf(payload))
        } catch (e: Exception) {
            Result.failure(AIException(mapException(e)))
        }
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> = flow {
        val key = secureStorage.geminiKey
        if (key.isBlank()) {
            emit(AIStreamEvent.Failure(AIError.MissingKey(provider)))
            return@flow
        }
        if (!NetworkUtils.isOnline(context)) {
            emit(AIStreamEvent.Failure(AIError.Offline))
            return@flow
        }

        emit(AIStreamEvent.Start)

        val url = Constants.GEMINI_BASE_URL +
            "models/${request.model.id}:streamGenerateContent?alt=sse&key=$key"
        val bodyJson = gson.toJson(buildRequest(request))
        val httpRequest = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .header("Accept", "text/event-stream")
            .build()

        val builder = StringBuilder()
        var usage = AIUsage()
        var blockedReason: String? = null

        try {
            streamingClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    emit(AIStreamEvent.Failure(mapHttpError(response.code, errorBody)))
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(AIStreamEvent.Failure(AIError.EmptyResponse()))
                    return@flow
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = try {
                        gson.fromJson(data, GeminiResponse::class.java)
                    } catch (e: JsonSyntaxException) {
                        null
                    } ?: continue

                    chunk.error?.let {
                        emit(
                            AIStreamEvent.Failure(
                                AIError.Server(it.code ?: -1, it.message ?: "Gemini error"),
                                builder.toString()
                            )
                        )
                        return@flow
                    }
                    chunk.blockReason?.let { blockedReason = it }
                    if (chunk.finishReason == "SAFETY" || chunk.finishReason == "RECITATION") {
                        blockedReason = chunk.finishReason
                    }
                    chunk.usageMetadata?.let { usage = usageOf(chunk) }
                    val delta = chunk.text
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
        when {
            full.isBlank() && blockedReason != null ->
                emit(AIStreamEvent.Failure(AIError.Blocked(blockedReason ?: "SAFETY")))
            full.isBlank() -> emit(AIStreamEvent.Failure(AIError.EmptyResponse()))
            else -> emit(
                AIStreamEvent.Complete(
                    fullText = full,
                    usage = if (usage.totalTokens > 0) usage else estimateUsage(request, full),
                    model = request.model
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /* ------------------------------------------------------------------ */

    private fun buildRequest(request: AIRequest): GeminiRequest {
        val contents = request.messages
            .filter { it.role != AIMessage.Role.SYSTEM }
            .map { message ->
                val parts = mutableListOf<GeminiPart>()
                if (message.content.isNotBlank()) parts += GeminiPart(text = message.content)
                message.images.forEach { image ->
                    parts += GeminiPart(
                        inlineData = GeminiInlineData(image.mimeType, image.base64Data)
                    )
                }
                if (parts.isEmpty()) parts += GeminiPart(text = " ")
                GeminiContent(
                    role = if (message.role == AIMessage.Role.ASSISTANT) "model" else "user",
                    parts = parts
                )
            }
            .ifEmpty { listOf(GeminiContent("user", listOf(GeminiPart(text = " ")))) }

        return GeminiRequest(
            contents = contents,
            systemInstruction = request.systemPrompt
                .takeIf { it.isNotBlank() }
                ?.let { GeminiSystemInstruction(listOf(GeminiPart(text = it))) },
            generationConfig = GeminiGenerationConfig(
                temperature = request.temperature,
                maxOutputTokens = request.maxOutputTokens
            ),
            safetySettings = GeminiSafetySetting.defaults()
        )
    }

    private fun usageOf(response: GeminiResponse): AIUsage {
        val meta = response.usageMetadata ?: return AIUsage()
        return AIUsage(meta.promptTokenCount, meta.candidatesTokenCount, meta.totalTokenCount)
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
            400 -> if (message.contains("API key", true)) AIError.InvalidKey(provider)
            else AIError.Server(code, "Invalid request: $message")
            401, 403 -> AIError.InvalidKey(provider)
            404 -> AIError.Server(code, "Model not found. Pick another model in the selector.")
            429 -> AIError.RateLimited(retryAfterSeconds = 30)
            in 500..599 -> AIError.Server(code, "Gemini is temporarily unavailable. $message")
            else -> AIError.Server(code, message)
        }
    }

    private fun extractMessage(body: String): String? = try {
        gson.fromJson(body, GeminiResponse::class.java)?.error?.message
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

/** Carrier so engines can fail a [Result] with a classified [AIError]. */
class AIException(val error: AIError) : Exception(error.message)
