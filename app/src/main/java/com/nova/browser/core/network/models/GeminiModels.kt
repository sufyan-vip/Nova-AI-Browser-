package com.nova.browser.core.network.models

import com.google.gson.annotations.SerializedName

/* ----------------------------- Requests ----------------------------- */

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>,
    @SerializedName("systemInstruction") val systemInstruction: GeminiSystemInstruction? = null,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @SerializedName("safetySettings") val safetySettings: List<GeminiSafetySetting>? = null
)

data class GeminiContent(
    @SerializedName("role") val role: String,
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("data") val data: String
)

data class GeminiSystemInstruction(
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiGenerationConfig(
    @SerializedName("temperature") val temperature: Double = 0.7,
    @SerializedName("topP") val topP: Double = 0.95,
    @SerializedName("topK") val topK: Int = 40,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 8192,
    @SerializedName("candidateCount") val candidateCount: Int = 1
)

data class GeminiSafetySetting(
    @SerializedName("category") val category: String,
    @SerializedName("threshold") val threshold: String
) {
    companion object {
        /** Permissive-but-safe defaults so ordinary browsing content is not blocked. */
        fun defaults(): List<GeminiSafetySetting> = listOf(
            GeminiSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_ONLY_HIGH"),
            GeminiSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_ONLY_HIGH"),
            GeminiSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_ONLY_HIGH"),
            GeminiSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_ONLY_HIGH")
        )
    }
}

/* ----------------------------- Responses ----------------------------- */

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null,
    @SerializedName("promptFeedback") val promptFeedback: GeminiPromptFeedback? = null,
    @SerializedName("usageMetadata") val usageMetadata: GeminiUsageMetadata? = null,
    @SerializedName("error") val error: GeminiError? = null
) {
    val text: String?
        get() = candidates?.firstOrNull()?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }

    val finishReason: String?
        get() = candidates?.firstOrNull()?.finishReason

    val blockReason: String?
        get() = promptFeedback?.blockReason
}

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null,
    @SerializedName("safetyRatings") val safetyRatings: List<GeminiSafetyRating>? = null
)

data class GeminiSafetyRating(
    @SerializedName("category") val category: String? = null,
    @SerializedName("probability") val probability: String? = null,
    @SerializedName("blocked") val blocked: Boolean? = null
)

data class GeminiPromptFeedback(
    @SerializedName("blockReason") val blockReason: String? = null,
    @SerializedName("safetyRatings") val safetyRatings: List<GeminiSafetyRating>? = null
)

data class GeminiUsageMetadata(
    @SerializedName("promptTokenCount") val promptTokenCount: Int = 0,
    @SerializedName("candidatesTokenCount") val candidatesTokenCount: Int = 0,
    @SerializedName("totalTokenCount") val totalTokenCount: Int = 0
)

data class GeminiError(
    @SerializedName("code") val code: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String? = null
)

/* ----------------------------- Model list ----------------------------- */

data class GeminiModelListResponse(
    @SerializedName("models") val models: List<GeminiModelInfo>? = null
)

data class GeminiModelInfo(
    @SerializedName("name") val name: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("inputTokenLimit") val inputTokenLimit: Int? = null,
    @SerializedName("outputTokenLimit") val outputTokenLimit: Int? = null,
    @SerializedName("supportedGenerationMethods") val supportedGenerationMethods: List<String>? = null
)
