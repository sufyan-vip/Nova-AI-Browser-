package com.nova.browser.features.agent.engine

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.engine.AIModel
import com.nova.browser.features.ai.repository.AIRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the model for a JSON plan, parses it defensively, and can re-plan
 * mid-run when the page turns out different from what was expected.
 */
@Singleton
class WorkflowEngine @Inject constructor(
    private val aiRepository: AIRepository,
    private val gson: Gson
) {
    private val actionList = AgentActionType.entries.joinToString(", ") { it.name.lowercase() }

    private fun planningPrompt(
        goal: String,
        currentUrl: String,
        pageTitle: String,
        elements: String,
        maxSteps: Int
    ): String = """
        You are NOVA's browser agent planner. Produce a step-by-step plan that a mobile
        browser can execute to accomplish the user's goal.

        Current page: ${pageTitle.ifBlank { "(blank)" }}
        Current URL: ${currentUrl.ifBlank { "(none)" }}

        Interactive elements available on the page (JSON):
        ${elements.take(6000)}

        Allowed actions: $actionList

        Rules:
        - Output ONLY a JSON object. No prose, no markdown fences.
        - Maximum $maxSteps steps. Fewer is better.
        - "target" is a CSS selector, visible text, URL or search query depending on the action.
        - "value" is the text to type, option to select, or wait duration in seconds.
        - Never plan steps that pay money, delete accounts, or send messages unless the
          user's goal explicitly asks for it.
        - Never type passwords, PINs or verification codes.
        - Prefer click_text and type_label over brittle CSS selectors.
        - End with a "finish" action whose "value" summarizes what was achieved.

        Schema:
        {"goal":"restated goal","steps":[{"action":"navigate","target":"https://…","value":"","reason":"why"}],"summary":"plan in one sentence"}

        User goal: $goal
    """.trimIndent()

    suspend fun plan(
        goal: String,
        currentUrl: String,
        pageTitle: String,
        elements: String,
        model: AIModel,
        maxSteps: Int
    ): Result<AgentPlan> {
        val prompt = planningPrompt(goal, currentUrl, pageTitle, elements, maxSteps)
        val response = aiRepository.complete(
            mode = AIMode.AGENT,
            prompt = prompt,
            model = model,
            temperature = 0.2
        )
        return response.fold(
            onSuccess = { text ->
                val parsed = parsePlan(text, goal)
                if (parsed == null || parsed.steps.isEmpty()) {
                    Result.failure(IllegalStateException("The model didn't return a usable plan. Try rephrasing your goal."))
                } else {
                    Result.success(parsed.copy(steps = parsed.steps.take(maxSteps)))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** Asks the model what to do next when a step failed. */
    suspend fun replan(
        goal: String,
        completed: List<AgentStep>,
        failure: AgentStep,
        currentUrl: String,
        elements: String,
        model: AIModel,
        remainingSteps: Int
    ): Result<AgentPlan> {
        val history = completed.joinToString("\n") { step ->
            "- ${step.action.describe()} → ${step.status.name.lowercase()}${step.error?.let { ": $it" }.orEmpty()}"
        }
        val prompt = """
            The agent was pursuing this goal: $goal

            Steps already attempted:
            $history

            The last step failed: ${failure.action.describe()} — ${failure.error ?: "unknown error"}

            Current URL: $currentUrl
            Interactive elements now on the page (JSON):
            ${elements.take(5000)}

            Produce a corrected plan of at most $remainingSteps remaining steps using the same
            JSON schema and the same allowed actions ($actionList). Output ONLY JSON.
            {"goal":"…","steps":[{"action":"…","target":"…","value":"…","reason":"…"}],"summary":"…"}
        """.trimIndent()

        val response = aiRepository.complete(
            mode = AIMode.AGENT,
            prompt = prompt,
            model = model,
            temperature = 0.2
        )
        return response.fold(
            onSuccess = { text ->
                val parsed = parsePlan(text, goal)
                if (parsed == null || parsed.steps.isEmpty()) {
                    Result.failure(IllegalStateException("Couldn't recover from the failed step."))
                } else {
                    Result.success(parsed.copy(steps = parsed.steps.take(remainingSteps)))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** Tolerates fenced code blocks and surrounding prose. */
    internal fun parsePlan(raw: String, fallbackGoal: String): AgentPlan? =
        parsePlan(raw, fallbackGoal, gson)

    companion object {
        /**
         * Pure plan parsing, kept free of injected collaborators so it can be
         * exercised directly. Never throws: unusable output returns null.
         */
        fun parsePlan(raw: String, fallbackGoal: String, gson: Gson): AgentPlan? {
            val json = extractJson(raw) ?: return null
            return try {
                val plan = gson.fromJson(json, AgentPlan::class.java) ?: return null
                val steps = plan.steps.filter { it.action.isNotBlank() }
                if (steps.isEmpty()) null
                else plan.copy(goal = plan.goal.ifBlank { fallbackGoal }, steps = steps)
            } catch (e: JsonSyntaxException) {
                null
            } catch (e: Exception) {
                null
            }
        }

        /** Finds the first balanced JSON object, ignoring fences and prose. */
        fun extractJson(raw: String): String? {
            val text = raw.trim()
            val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)?.groupValues?.get(1)?.trim()
            val candidate = fenced ?: text
            val start = candidate.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until candidate.length) {
                val char = candidate[index]
                when {
                    escaped -> escaped = false
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    inString -> Unit
                    char == '{' -> depth++
                    char == '}' -> {
                        depth--
                        if (depth == 0) return candidate.substring(start, index + 1)
                    }
                }
            }
            return null
        }
    }
}
