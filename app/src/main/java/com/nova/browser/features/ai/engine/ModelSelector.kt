package com.nova.browser.features.ai.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses the best model for a task from the models the user actually has
 * access to (spec 06 → per-task model selection + capability detection).
 */
@Singleton
class ModelSelector @Inject constructor() {

    fun select(
        mode: AIMode,
        available: List<AIModel>,
        preferred: AIModel?,
        requiresVision: Boolean = false
    ): AIModel? {
        if (available.isEmpty()) return null

        // Honour the user's explicit choice when it can do the job.
        if (preferred != null && available.any { it.id == preferred.id }) {
            if (!requiresVision || preferred.supportsVision) return preferred
        }

        val pool = if (requiresVision) available.filter { it.supportsVision } else available
        if (pool.isEmpty()) return available.firstOrNull { it.supportsVision } ?: available.first()

        return when (mode) {
            AIMode.CODE -> pool.firstOrNull { it.id.contains("deepseek") || it.id.contains("sonnet") || it.id.contains("gpt-4o") }
            AIMode.RESEARCH, AIMode.EXPLAIN -> pool.maxByOrNull { it.contextTokens }
            AIMode.VISION -> pool.firstOrNull { it.supportsVision }
            AIMode.TRANSLATE, AIMode.SUMMARIZE, AIMode.SEARCH -> pool.firstOrNull { it.fast }
            AIMode.AGENT -> pool.firstOrNull { !it.fast } ?: pool.first()
            else -> pool.firstOrNull { it.fast }
        } ?: pool.first()
    }

    /** Model to try when the primary one fails (different provider first). */
    fun fallback(failed: AIModel, available: List<AIModel>): AIModel? =
        available.firstOrNull { it.provider != failed.provider }
            ?: available.firstOrNull { it.id != failed.id }

    /** True when the content will not fit and needs compressing. */
    fun needsCompression(model: AIModel, estimatedTokens: Int): Boolean =
        estimatedTokens > usableContext(model)

    /** 75% of the window, leaving room for the response (spec 10). */
    fun usableContext(model: AIModel): Int = (model.contextTokens * 0.75).toInt().coerceAtLeast(2_000)
}
