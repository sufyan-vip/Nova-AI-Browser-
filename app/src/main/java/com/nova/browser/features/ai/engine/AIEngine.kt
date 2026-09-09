package com.nova.browser.features.ai.engine

import kotlinx.coroutines.flow.Flow

/** Which backend serves a request. */
enum class AIProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENROUTER("OpenRouter");

    companion object {
        fun from(name: String?): AIProvider =
            entries.firstOrNull { it.name.equals(name, true) } ?: GEMINI
    }
}

/** A model offered by a provider. */
data class AIModel(
    val id: String,
    val label: String,
    val provider: AIProvider,
    val contextTokens: Int,
    val supportsVision: Boolean = false,
    val description: String = "",
    val fast: Boolean = false
)

/** The task-specific persona the assistant adopts. */
enum class AIMode(val label: String, val systemPrompt: String) {
    CHAT(
        "Chat",
        "You are NOVA, the AI brain of a mobile web browser. Be concise, accurate and helpful. " +
            "Use markdown for structure. When you are unsure, say so plainly."
    ),
    PAGE_QA(
        "Page Q&A",
        "You answer questions strictly about the web page the user is viewing. " +
            "Use only the supplied page content. If the answer is not present in the page, say so and " +
            "offer to search the web instead. Quote short snippets when helpful."
    ),
    SUMMARIZE(
        "Summarize",
        "Summarize the supplied content. Output: a one-sentence TL;DR, then 3-7 bullet key points, " +
            "then any important numbers, dates or names. Never invent facts."
    ),
    TRANSLATE(
        "Translate",
        "You are a precise translator. Translate the supplied text into the requested target language, " +
            "preserving meaning, tone, formatting and code blocks. Output only the translation unless asked otherwise."
    ),
    EXPLAIN(
        "Explain",
        "Explain the supplied content simply, as if to a smart beginner. Define jargon, use analogies, " +
            "and end with a 'Key takeaways' list."
    ),
    RESEARCH(
        "Research",
        "You are a research analyst. Synthesise the supplied sources into a structured report: Summary, " +
            "Key findings, Source comparison, Conflicts or uncertainty, Citations (with the numbered source " +
            "links given to you), Follow-up questions, and a Confidence rating (low/medium/high). " +
            "Cite sources inline as [1], [2]."
    ),
    WRITE(
        "Write",
        "You are a writing assistant. Compose, rewrite, shorten, expand or proofread text as requested. " +
            "Match the requested tone. Return only the resulting text unless the user asks for commentary."
    ),
    CODE(
        "Code",
        "You are a senior software engineer. Produce correct, complete, runnable code in fenced blocks with " +
            "the language tag. Explain briefly after the code. Point out edge cases and security issues."
    ),
    VISION(
        "Vision",
        "You analyse images and screenshots. Describe what is present, extract any visible text verbatim, " +
            "and answer the user's question about the image. State clearly when something is unreadable."
    ),
    AGENT(
        "Agent",
        "You are a browser automation planner. You convert the user's goal into a strict JSON action plan. " +
            "Respond with JSON only — no prose, no markdown fences."
    ),
    SEARCH(
        "Search",
        "You are an AI search engine. Answer the query directly and completely first, then list the most " +
            "relevant follow-up angles. Be explicit about uncertainty and recency limits."
    );

    companion object {
        fun from(name: String?): AIMode = entries.firstOrNull { it.name.equals(name, true) } ?: CHAT
    }
}

/** One turn in a conversation sent to a provider. */
data class AIMessage(
    val role: Role,
    val content: String,
    val images: List<AIImage> = emptyList()
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

/** Base64-encoded image attachment. */
data class AIImage(val mimeType: String, val base64Data: String)

/** Everything needed to produce a completion. */
data class AIRequest(
    val messages: List<AIMessage>,
    val systemPrompt: String,
    val model: AIModel,
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 4096,
    val stream: Boolean = true
)

/** Token accounting for a completed call. */
data class AIUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

/** Classified failure so the UI can react appropriately (spec 24). */
sealed class AIError(open val message: String) {
    data class MissingKey(val provider: AIProvider) :
        AIError("No API key configured for ${provider.displayName}")

    data class InvalidKey(val provider: AIProvider) :
        AIError("The ${provider.displayName} API key was rejected. Check it in Settings → AI.")

    data class RateLimited(val retryAfterSeconds: Int) :
        AIError("Rate limit reached. Try again in ${retryAfterSeconds}s.")

    data object Offline : AIError("You are offline. Connect to the internet and retry.")

    data class Timeout(override val message: String = "The AI request timed out.") : AIError(message)

    data class Server(val code: Int, override val message: String) : AIError(message)

    data class Blocked(val reason: String) :
        AIError("The response was blocked by the provider's safety filter ($reason).")

    data class EmptyResponse(override val message: String = "AI couldn't generate a response.") :
        AIError(message)

    data class Parsing(val raw: String) :
        AIError("The AI response could not be parsed.")

    data class Unknown(override val message: String) : AIError(message)
}

/** Streaming events emitted while a completion is produced. */
sealed interface AIStreamEvent {
    data object Start : AIStreamEvent
    data class Chunk(val delta: String) : AIStreamEvent
    data class Complete(val fullText: String, val usage: AIUsage, val model: AIModel) : AIStreamEvent
    data class Failure(val error: AIError, val partialText: String = "") : AIStreamEvent
}

/** Contract implemented by every provider engine. */
interface AIEngine {
    val provider: AIProvider

    /** Models available without a network round-trip. */
    fun defaultModels(): List<AIModel>

    /** True when a usable API key is present. */
    fun hasApiKey(): Boolean

    /** Fetches the live model catalogue, falling back to [defaultModels]. */
    suspend fun fetchModels(): List<AIModel>

    /** Streaming completion. Always terminates with Complete or Failure. */
    fun stream(request: AIRequest): Flow<AIStreamEvent>

    /** One-shot completion. */
    suspend fun complete(request: AIRequest): Result<Pair<String, AIUsage>>
}
