package com.nova.browser.features.agent.engine

import com.google.gson.annotations.SerializedName

/** Every action the agent can perform (spec 09_AI_AGENT). */
enum class AgentActionType {
    NAVIGATE, SEARCH, CLICK, CLICK_TEXT, TYPE, TYPE_LABEL, SELECT, CHECK,
    SCROLL, SCROLL_TO, WAIT, WAIT_FOR, EXTRACT, EXTRACT_LINKS, EXTRACT_TABLE,
    SCREENSHOT, READ_PAGE, SUMMARIZE, ANALYZE, BACK, FORWARD, NEW_TAB,
    SWITCH_TAB, CLOSE_TAB, DOWNLOAD, SAVE_NOTE, FINISH;

    companion object {
        fun from(value: String?): AgentActionType =
            entries.firstOrNull { it.name.equals(value?.replace(" ", "_"), true) } ?: FINISH
    }
}

/** A single planned step. */
data class AgentAction(
    @SerializedName("action") val action: String = "finish",
    @SerializedName("target") val target: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("reason") val reason: String? = null
) {
    val type: AgentActionType get() = AgentActionType.from(action)

    /** Human-readable description shown in the step indicator. */
    fun describe(): String = when (type) {
        AgentActionType.NAVIGATE -> "Open ${target.orEmpty()}"
        AgentActionType.SEARCH -> "Search for \"${target.orEmpty()}\""
        AgentActionType.CLICK -> "Click element ${target.orEmpty()}"
        AgentActionType.CLICK_TEXT -> "Click \"${target.orEmpty()}\""
        AgentActionType.TYPE -> "Type into ${target.orEmpty()}"
        AgentActionType.TYPE_LABEL -> "Fill \"${target.orEmpty()}\""
        AgentActionType.SELECT -> "Select \"${value.orEmpty()}\" in ${target.orEmpty()}"
        AgentActionType.CHECK -> "Toggle checkbox ${target.orEmpty()}"
        AgentActionType.SCROLL -> "Scroll ${value ?: "down"}"
        AgentActionType.SCROLL_TO -> "Scroll to ${target.orEmpty()}"
        AgentActionType.WAIT -> "Wait ${value ?: "2"}s"
        AgentActionType.WAIT_FOR -> "Wait for ${target.orEmpty()}"
        AgentActionType.EXTRACT -> "Extract ${target.orEmpty()}"
        AgentActionType.EXTRACT_LINKS -> "Extract all links"
        AgentActionType.EXTRACT_TABLE -> "Extract table data"
        AgentActionType.SCREENSHOT -> "Take a screenshot"
        AgentActionType.READ_PAGE -> "Read the page"
        AgentActionType.SUMMARIZE -> "Summarize the page"
        AgentActionType.ANALYZE -> "Analyze: ${target ?: value.orEmpty()}"
        AgentActionType.BACK -> "Go back"
        AgentActionType.FORWARD -> "Go forward"
        AgentActionType.NEW_TAB -> "Open a new tab"
        AgentActionType.SWITCH_TAB -> "Switch tab"
        AgentActionType.CLOSE_TAB -> "Close tab"
        AgentActionType.DOWNLOAD -> "Download ${target.orEmpty()}"
        AgentActionType.SAVE_NOTE -> "Save a note"
        AgentActionType.FINISH -> "Finish"
    }
}

/** The plan returned by the model. */
data class AgentPlan(
    @SerializedName("goal") val goal: String = "",
    @SerializedName("steps") val steps: List<AgentAction> = emptyList(),
    @SerializedName("summary") val summary: String = ""
)

/** Execution state of one step. */
enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, AWAITING_CONFIRMATION }

data class AgentStep(
    val index: Int,
    val action: AgentAction,
    val status: StepStatus = StepStatus.PENDING,
    val result: String = "",
    val error: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null
) {
    val durationMs: Long?
        get() = if (startedAt != null && finishedAt != null) finishedAt - startedAt else null
}

/** Outcome returned to the UI. */
data class AgentRun(
    val goal: String = "",
    val steps: List<AgentStep> = emptyList(),
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val finished: Boolean = false,
    val summary: String = "",
    val extractedData: List<String> = emptyList(),
    val error: String? = null,
    val pendingConfirmation: PendingConfirmation? = null
) {
    val currentIndex: Int get() = steps.indexOfFirst { it.status == StepStatus.RUNNING }
    val completedCount: Int get() = steps.count { it.status == StepStatus.SUCCESS }
    val progress: Float
        get() = if (steps.isEmpty()) 0f else steps.count {
            it.status in setOf(StepStatus.SUCCESS, StepStatus.FAILED, StepStatus.SKIPPED)
        }.toFloat() / steps.size
}

data class PendingConfirmation(
    val step: AgentStep,
    val risk: SafetyChecker.Risk,
    val explanation: String
)
