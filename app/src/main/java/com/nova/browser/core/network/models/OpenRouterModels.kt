package com.nova.browser.core.network.models

import com.google.gson.annotations.SerializedName

/* ----------------------------- Requests ----------------------------- */

data class OpenRouterRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<OpenRouterMessage>,
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    @SerializedName("top_p") val topP: Double = 0.95,
    @SerializedName("stream") val stream: Boolean = false
)

data class OpenRouterMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: Any
) {
    companion object {
        fun text(role: String, content: String) = OpenRouterMessage(role, content)

        /** Multimodal message: text plus one or more base64 data-URL images. */
        fun multimodal(role: String, text: String, imageDataUrls: List<String>): OpenRouterMessage {
            val parts = mutableListOf<Map<String, Any>>()
            parts += mapOf("type" to "text", "text" to text)
            imageDataUrls.forEach { url ->
                parts += mapOf("type" to "image_url", "image_url" to mapOf("url" to url))
            }
            return OpenRouterMessage(role, parts)
        }
    }
}

/* ----------------------------- Responses ----------------------------- */

data class OpenRouterResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("created") val created: Long? = null,
    @SerializedName("choices") val choices: List<OpenRouterChoice>? = null,
    @SerializedName("usage") val usage: OpenRouterUsage? = null,
    @SerializedName("error") val error: OpenRouterError? = null
) {
    val text: String?
        get() = choices?.firstOrNull()?.let { it.message?.content ?: it.delta?.content }
            ?.takeIf { it.isNotBlank() }
}

data class OpenRouterChoice(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("message") val message: OpenRouterResponseMessage? = null,
    @SerializedName("delta") val delta: OpenRouterResponseMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class OpenRouterResponseMessage(
    @SerializedName("role") val role: String? = null,
    @SerializedName("content") val content: String? = null
)

data class OpenRouterUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

data class OpenRouterError(
    @SerializedName("code") val code: Any? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("type") val type: String? = null
)

/* ----------------------------- Model catalogue ----------------------------- */

data class OpenRouterModelsResponse(
    @SerializedName("data") val data: List<OpenRouterModelInfo>? = null
)

data class OpenRouterModelInfo(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("context_length") val contextLength: Int? = null,
    @SerializedName("pricing") val pricing: OpenRouterPricing? = null,
    @SerializedName("architecture") val architecture: OpenRouterArchitecture? = null
) {
    val supportsVision: Boolean
        get() = architecture?.modality?.contains("image", ignoreCase = true) == true ||
            architecture?.inputModalities?.any { it.equals("image", true) } == true
}

data class OpenRouterPricing(
    @SerializedName("prompt") val prompt: String? = null,
    @SerializedName("completion") val completion: String? = null
)

data class OpenRouterArchitecture(
    @SerializedName("modality") val modality: String? = null,
    @SerializedName("input_modalities") val inputModalities: List<String>? = null,
    @SerializedName("tokenizer") val tokenizer: String? = null
)
