package com.nova.browser.features.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.dao.AutomationDao
import com.nova.browser.core.database.entities.AutomationEntity
import com.nova.browser.core.utils.Constants
import com.nova.browser.features.agent.engine.ActionExecutor
import com.nova.browser.features.agent.engine.AgentAction
import com.nova.browser.features.agent.engine.AgentActionType
import com.nova.browser.features.agent.engine.AgentRun
import com.nova.browser.features.agent.engine.AgentStep
import com.nova.browser.features.agent.engine.PageInteractor
import com.nova.browser.features.agent.engine.PendingConfirmation
import com.nova.browser.features.agent.engine.SafetyChecker
import com.nova.browser.features.agent.engine.StepStatus
import com.nova.browser.features.agent.engine.WorkflowEngine
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.engine.AIModel
import com.nova.browser.features.ai.repository.AIRepository
import com.nova.browser.features.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class AgentUiState(
    val visible: Boolean = false,
    val goal: String = "",
    val run: AgentRun = AgentRun(),
    val isPlanning: Boolean = false,
    val hasApiKey: Boolean = false,
    val model: AIModel? = null,
    val confirmEveryStep: Boolean = false,
    val maxSteps: Int = 15,
    val savedWorkflows: List<AutomationEntity> = emptyList(),
    val error: String? = null,
    val saveDialogVisible: Boolean = false
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val workflowEngine: WorkflowEngine,
    private val actionExecutor: ActionExecutor,
    private val pageInteractor: PageInteractor,
    private val safetyChecker: SafetyChecker,
    private val aiRepository: AIRepository,
    private val settingsRepository: SettingsRepository,
    private val automationDao: AutomationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private var bridge: ActionExecutor.BrowserBridge? = null
    private val confirmationChannel = Channel<Boolean>(Channel.CONFLATED)

    val workflows = automationDao.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    confirmEveryStep = settings.agentConfirmEveryStep,
                    maxSteps = settings.agentMaxSteps.coerceIn(1, Constants.MAX_AGENT_STEPS),
                    hasApiKey = aiRepository.hasAnyKey()
                )
            }
        }
        viewModelScope.launch {
            workflows.collect { list ->
                _uiState.value = _uiState.value.copy(savedWorkflows = list)
            }
        }
    }

    /** The browser screen installs the bridge and WebView provider. */
    fun bind(browserBridge: ActionExecutor.BrowserBridge, webViewProvider: () -> android.webkit.WebView?) {
        bridge = browserBridge
        pageInteractor.attach(webViewProvider)
    }

    fun unbind() {
        bridge = null
        pageInteractor.detach()
    }

    fun show() {
        _uiState.value = _uiState.value.copy(visible = true, error = null)
    }

    fun hide() {
        _uiState.value = _uiState.value.copy(visible = false)
    }

    fun onGoalChange(goal: String) {
        _uiState.value = _uiState.value.copy(goal = goal)
    }

    fun setConfirmEveryStep(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAgentConfirmEveryStep(enabled) }
    }

    fun setMaxSteps(steps: Int) {
        viewModelScope.launch {
            settingsRepository.setAgentMaxSteps(steps.coerceIn(1, Constants.MAX_AGENT_STEPS))
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /* --------------------------------- run --------------------------------- */

    fun start(goalOverride: String? = null) {
        val goal = (goalOverride ?: _uiState.value.goal).trim()
        if (goal.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Describe what you want the agent to do.")
            return
        }
        if (bridge == null) {
            _uiState.value = _uiState.value.copy(error = "The agent needs an open page to work with.")
            return
        }
        if (!aiRepository.hasAnyKey()) {
            _uiState.value = _uiState.value.copy(error = "Add an AI API key in Settings before running the agent.")
            return
        }

        runJob?.cancel()
        runJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                visible = true,
                goal = goal,
                isPlanning = true,
                error = null,
                run = AgentRun(goal = goal, isRunning = true)
            )

            val model = aiRepository.resolveModel(AIMode.AGENT)
            if (model == null) {
                fail("No AI model is available for the agent.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(model = model)

            val maxSteps = _uiState.value.maxSteps
            val elements = pageInteractor.interactiveElements()
            val url = pageInteractor.currentUrl()
            val title = pageInteractor.currentTitle()

            val planResult = workflowEngine.plan(goal, url, title, elements, model, maxSteps)
            val plan = planResult.getOrElse { throwable ->
                fail(throwable.message ?: "The agent couldn't build a plan.")
                return@launch
            }

            safetyChecker.validatePlan(plan.steps, maxSteps)?.let { problem ->
                fail(problem)
                return@launch
            }

            val steps = plan.steps.mapIndexed { index, action -> AgentStep(index, action) }
            _uiState.value = _uiState.value.copy(
                isPlanning = false,
                run = AgentRun(
                    goal = plan.goal.ifBlank { goal },
                    steps = steps,
                    isRunning = true,
                    summary = plan.summary
                )
            )

            executeAll(model)
        }
    }

    private suspend fun executeAll(model: AIModel) {
        var index = 0
        var replans = 0
        val extracted = mutableListOf<String>()

        while (index < _uiState.value.run.steps.size) {
            if (!_uiState.value.run.isRunning) return

            while (_uiState.value.run.isPaused) {
                delay(200)
                if (!_uiState.value.run.isRunning) return
            }

            val step = _uiState.value.run.steps[index]
            val currentUrl = pageInteractor.currentUrl()
            val verdict = safetyChecker.check(step.action, currentUrl)

            if (verdict.isBlocked) {
                updateStep(index) { it.copy(status = StepStatus.SKIPPED, error = verdict.reason) }
                index++
                continue
            }

            val needsConfirmation = verdict.requiresConfirmation || _uiState.value.confirmEveryStep
            if (needsConfirmation) {
                updateStep(index) { it.copy(status = StepStatus.AWAITING_CONFIRMATION) }
                _uiState.value = _uiState.value.copy(
                    run = _uiState.value.run.copy(
                        pendingConfirmation = PendingConfirmation(
                            step = _uiState.value.run.steps[index],
                            risk = verdict.risk,
                            explanation = verdict.reason.ifBlank {
                                "Confirm this step before the agent continues."
                            }
                        )
                    )
                )
                val approved = try {
                    confirmationChannel.receive()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                _uiState.value = _uiState.value.copy(
                    run = _uiState.value.run.copy(pendingConfirmation = null)
                )
                if (!approved) {
                    updateStep(index) { it.copy(status = StepStatus.SKIPPED, error = "Declined by you") }
                    finish("Stopped: you declined a step.", extracted)
                    return
                }
            }

            updateStep(index) {
                it.copy(status = StepStatus.RUNNING, startedAt = System.currentTimeMillis(), error = null)
            }

            val activeBridge = bridge
            if (activeBridge == null) {
                updateStep(index) {
                    it.copy(
                        status = StepStatus.FAILED,
                        error = "The browser page went away",
                        finishedAt = System.currentTimeMillis()
                    )
                }
                finish("Stopped: the page is no longer available.", extracted)
                return
            }

            val outcome = withTimeoutOrNull(Constants.AGENT_ACTION_TIMEOUT_MS * 3) {
                actionExecutor.execute(step.action, activeBridge)
            } ?: ActionExecutor.Outcome(false, "The step timed out")

            outcome.data?.takeIf { it.isNotBlank() }?.let { extracted += it }

            updateStep(index) {
                it.copy(
                    status = if (outcome.success) StepStatus.SUCCESS else StepStatus.FAILED,
                    result = outcome.message,
                    error = if (outcome.success) null else outcome.message,
                    finishedAt = System.currentTimeMillis()
                )
            }

            if (step.action.type == AgentActionType.FINISH) {
                finish(outcome.message, extracted)
                return
            }

            if (!outcome.success) {
                if (replans < 2) {
                    replans++
                    val completed = _uiState.value.run.steps.take(index + 1)
                    val remaining = (_uiState.value.maxSteps - index - 1).coerceAtLeast(1)
                    val recovery = workflowEngine.replan(
                        goal = _uiState.value.run.goal,
                        completed = completed,
                        failure = _uiState.value.run.steps[index],
                        currentUrl = pageInteractor.currentUrl(),
                        elements = pageInteractor.interactiveElements(),
                        model = model,
                        remainingSteps = remaining
                    ).getOrNull()

                    if (recovery != null && recovery.steps.isNotEmpty()) {
                        val newSteps = completed + recovery.steps.mapIndexed { offset, action ->
                            AgentStep(index + 1 + offset, action)
                        }
                        _uiState.value = _uiState.value.copy(
                            run = _uiState.value.run.copy(steps = newSteps)
                        )
                        index++
                        continue
                    }
                }
                finish("The agent got stuck: ${outcome.message}", extracted, isError = true)
                return
            }

            index++
        }

        finish("Finished all ${_uiState.value.run.steps.size} steps.", extracted)
    }

    private fun finish(summary: String, extracted: List<String>, isError: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            isPlanning = false,
            error = if (isError) summary else null,
            run = _uiState.value.run.copy(
                isRunning = false,
                isPaused = false,
                finished = true,
                summary = summary,
                extractedData = extracted,
                pendingConfirmation = null,
                error = if (isError) summary else null
            )
        )
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(
            isPlanning = false,
            error = message,
            run = _uiState.value.run.copy(isRunning = false, finished = true, error = message)
        )
    }

    private fun updateStep(index: Int, transform: (AgentStep) -> AgentStep) {
        val steps = _uiState.value.run.steps
        if (index !in steps.indices) return
        _uiState.value = _uiState.value.copy(
            run = _uiState.value.run.copy(
                steps = steps.toMutableList().also { it[index] = transform(it[index]) }
            )
        )
    }

    fun confirmStep(approved: Boolean) {
        confirmationChannel.trySend(approved)
    }

    fun pause() {
        _uiState.value = _uiState.value.copy(run = _uiState.value.run.copy(isPaused = true))
    }

    fun resume() {
        _uiState.value = _uiState.value.copy(run = _uiState.value.run.copy(isPaused = false))
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        confirmationChannel.trySend(false)
        _uiState.value = _uiState.value.copy(
            isPlanning = false,
            run = _uiState.value.run.copy(
                isRunning = false,
                isPaused = false,
                finished = true,
                summary = "Stopped by you.",
                pendingConfirmation = null
            )
        )
    }

    fun reset() {
        runJob?.cancel()
        _uiState.value = _uiState.value.copy(run = AgentRun(), goal = "", error = null)
    }

    /* ------------------------------ workflows ------------------------------ */

    fun showSaveDialog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(saveDialogVisible = visible)
    }

    fun saveCurrentAsWorkflow(name: String, description: String, onDone: (Boolean) -> Unit = {}) {
        val run = _uiState.value.run
        if (run.steps.isEmpty()) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val ok = runCatching {
                automationDao.insert(
                    AutomationEntity(
                        name = name.ifBlank { run.goal.take(60) },
                        description = description.ifBlank { run.summary },
                        steps = com.google.gson.Gson().toJson(run.steps.map { it.action }),
                        triggerType = "manual",
                        isEnabled = true
                    )
                )
            }.isSuccess
            _uiState.value = _uiState.value.copy(saveDialogVisible = false)
            onDone(ok)
        }
    }

    fun runWorkflow(automation: AutomationEntity) {
        val actions = runCatching {
            com.google.gson.Gson().fromJson(automation.steps, Array<AgentAction>::class.java)?.toList()
        }.getOrNull().orEmpty()

        if (actions.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "That workflow has no steps.")
            return
        }

        runJob?.cancel()
        runJob = viewModelScope.launch {
            val model = aiRepository.resolveModel(AIMode.AGENT)
            _uiState.value = _uiState.value.copy(
                visible = true,
                goal = automation.name,
                model = model,
                error = null,
                run = AgentRun(
                    goal = automation.name,
                    steps = actions.mapIndexed { index, action -> AgentStep(index, action) },
                    isRunning = true,
                    summary = automation.description
                )
            )
            runCatching { automationDao.recordRun(automation.id, System.currentTimeMillis(), "ok") }
            if (model != null) executeAll(model) else finish("Ran without AI recovery.", emptyList())
        }
    }

    fun deleteWorkflow(automation: AutomationEntity) {
        viewModelScope.launch { runCatching { automationDao.deleteById(automation.id) } }
    }

    fun toggleWorkflow(automation: AutomationEntity, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { automationDao.setEnabled(automation.id, enabled) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runJob?.cancel()
        pageInteractor.detach()
    }
}
