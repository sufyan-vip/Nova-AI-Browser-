package com.nova.browser.features.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.AutomationEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaProgressBar
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.theme.SectionHeader
import com.nova.browser.core.utils.copyToClipboard
import com.nova.browser.core.utils.toast
import com.nova.browser.features.agent.engine.AgentStep
import com.nova.browser.features.agent.engine.SafetyChecker
import com.nova.browser.features.agent.engine.StepStatus
import com.nova.browser.features.agent.viewmodel.AgentViewModel

/** Bottom-sheet agent console shown over the browser. */
@Composable
fun AgentPanel(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrimInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(state.run.completedCount) {
        val index = state.run.steps.indexOfFirst { it.status == StepStatus.RUNNING }
        if (index >= 0) runCatching { listState.animateScrollToItem(index) }
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(NovaColors.Scrim)
                .clickable(indication = null, interactionSource = scrimInteraction) {
                    if (!state.run.isRunning) viewModel.hide()
                }
        ) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(tween(280)) { it },
                exit = slideOutVertically(tween(220)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(0.86f)
                        .clickable(indication = null, interactionSource = sheetInteraction) { },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        AgentHeader(
                            isRunning = state.run.isRunning,
                            isPaused = state.run.isPaused,
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onStop = viewModel::stop,
                            onClose = viewModel::hide
                        )

                        if (state.run.steps.isNotEmpty()) {
                            AgentProgress(
                                goal = state.run.goal,
                                progress = state.run.progress,
                                completed = state.run.completedCount,
                                total = state.run.steps.size
                            )
                        }

                        Box(Modifier.weight(1f)) {
                            when {
                                state.isPlanning -> PlanningState(state.goal)

                                state.run.steps.isEmpty() -> AgentIdle(
                                    goal = state.goal,
                                    hasApiKey = state.hasApiKey,
                                    workflows = state.savedWorkflows,
                                    confirmEveryStep = state.confirmEveryStep,
                                    onGoalChange = viewModel::onGoalChange,
                                    onStart = { viewModel.start() },
                                    onExample = { viewModel.start(it) },
                                    onRunWorkflow = viewModel::runWorkflow,
                                    onDeleteWorkflow = viewModel::deleteWorkflow,
                                    onConfirmEveryStep = viewModel::setConfirmEveryStep
                                )

                                else -> LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(Dimens.lg),
                                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                                ) {
                                    itemsIndexed(state.run.steps, key = { index, _ -> index }) { _, step ->
                                        AgentStepRow(step)
                                    }

                                    if (state.run.finished) {
                                        item {
                                            AgentSummaryCard(
                                                summary = state.run.summary,
                                                data = state.run.extractedData,
                                                isError = state.run.error != null,
                                                onCopy = {
                                                    context.copyToClipboard(
                                                        state.run.extractedData.joinToString("\n\n")
                                                            .ifBlank { state.run.summary }
                                                    )
                                                    context.toast("Copied")
                                                },
                                                onSave = { viewModel.showSaveDialog(true) },
                                                onReset = viewModel::reset
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        state.error?.let { error ->
                            AgentErrorBar(error, viewModel::dismissError)
                        }
                    }
                }
            }

            state.run.pendingConfirmation?.let { pending ->
                AgentConfirmDialog(
                    step = pending.step,
                    risk = pending.risk,
                    explanation = pending.explanation,
                    onApprove = { viewModel.confirmStep(true) },
                    onDecline = { viewModel.confirmStep(false) }
                )
            }

            if (state.saveDialogVisible) {
                SaveWorkflowDialog(
                    defaultName = state.run.goal,
                    onDismiss = { viewModel.showSaveDialog(false) },
                    onSave = { name, description ->
                        viewModel.saveCurrentAsWorkflow(name, description) { ok ->
                            context.toast(if (ok) "Workflow saved" else "Couldn't save workflow")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AgentHeader(
    isRunning: Boolean,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(NovaColors.Secondary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = NovaColors.Secondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(Dimens.sm))
        Text(
            "AI Agent",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isRunning) {
            GlassIconButton(
                icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume agent" else "Pause agent",
                onClick = if (isPaused) onResume else onPause,
                size = 40.dp,
                tint = NovaColors.Warning
            )
            GlassIconButton(
                icon = Icons.Default.Stop,
                contentDescription = "Stop agent",
                onClick = onStop,
                size = 40.dp,
                tint = NovaColors.Error
            )
        } else {
            GlassIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close agent panel",
                onClick = onClose,
                size = 40.dp,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AgentProgress(goal: String, progress: Float, completed: Int, total: Int) {
    Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.xs)) {
        Text(
            goal,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Dimens.xs))
        NovaProgressBar(progress = progress, color = NovaColors.Secondary)
        Spacer(Modifier.height(2.dp))
        Text(
            "$completed of $total steps",
            style = MaterialTheme.typography.labelSmall,
            color = NovaTheme.extended.textTertiary
        )
    }
}

@Composable
private fun PlanningState(goal: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Bolt,
            contentDescription = null,
            tint = NovaColors.Secondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(Dimens.md))
        Text(
            "Planning your steps…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(
            goal,
            style = MaterialTheme.typography.bodySmall,
            color = NovaTheme.extended.textTertiary
        )
        Spacer(Modifier.height(Dimens.lg))
        IndeterminateBar(color = NovaColors.Secondary)
    }
}

/** Indeterminate sweeping bar used while the plan is being generated. */
@Composable
private fun IndeterminateBar(color: Color) {
    val progress by com.nova.browser.core.theme.rememberInfiniteProgress(durationMillis = 1400)
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(NovaShapeTokens.pill)
            .background(NovaTheme.extended.glass)
    ) {
        val barWidth = maxWidth * 0.35f
        Box(
            Modifier
                .offset(x = (maxWidth - barWidth) * progress)
                .width(barWidth)
                .height(4.dp)
                .clip(NovaShapeTokens.pill)
                .background(color)
        )
    }
}

@Composable
private fun AgentIdle(
    goal: String,
    hasApiKey: Boolean,
    workflows: List<AutomationEntity>,
    confirmEveryStep: Boolean,
    onGoalChange: (String) -> Unit,
    onStart: () -> Unit,
    onExample: (String) -> Unit,
    onRunWorkflow: (AutomationEntity) -> Unit,
    onDeleteWorkflow: (AutomationEntity) -> Unit,
    onConfirmEveryStep: (Boolean) -> Unit
) {
    if (!hasApiKey) {
        EmptyState(
            icon = Icons.Default.SmartToy,
            title = "The agent needs an AI key",
            message = "Add a Gemini or OpenRouter API key in Settings → AI to let NOVA browse for you.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.lg),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        item {
            Text(
                "Tell NOVA what to do on the web. It plans the steps, shows you each one, and asks before anything risky.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTheme.extended.textTertiary
            )
        }
        item {
            GlassTextField(
                value = goal,
                onValueChange = onGoalChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "e.g. Find the cheapest flight to Tokyo next month",
                singleLine = false,
                maxLines = 4,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = onStart
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(
                    text = "Run agent",
                    onClick = onStart,
                    style = GlassButtonStyle.Primary,
                    icon = Icons.Default.PlayArrow,
                    enabled = goal.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(NovaShapeTokens.medium)
                    .clickable { onConfirmEveryStep(!confirmEveryStep) }
                    .padding(vertical = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (confirmEveryStep) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (confirmEveryStep) NovaColors.Success else NovaTheme.extended.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    "Confirm every step before it runs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SectionHeader("Try one of these") }
        items(
            listOf(
                "Summarize the top 3 results for \"best budget laptop 2026\"",
                "Find this site's contact email and save it as a note",
                "Extract every link on this page",
                "Compare the pricing tiers on this page"
            )
        ) { example ->
            GlassButton(
                text = example,
                onClick = { onExample(example) },
                style = GlassButtonStyle.Secondary,
                fillWidth = true
            )
        }

        if (workflows.isNotEmpty()) {
            item { SectionHeader("Saved workflows") }
            items(workflows, key = { it.id }) { workflow ->
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NovaShapeTokens.medium
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRunWorkflow(workflow) }
                            .padding(Dimens.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                workflow.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (workflow.runCount > 0) "Run ${workflow.runCount}×" else "Never run",
                                style = MaterialTheme.typography.labelSmall,
                                color = NovaTheme.extended.textTertiary
                            )
                        }
                        GlassIconButton(
                            icon = Icons.Default.PlayArrow,
                            contentDescription = "Run ${workflow.name}",
                            onClick = { onRunWorkflow(workflow) },
                            size = 40.dp,
                            tint = NovaColors.Success
                        )
                        GlassIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Delete ${workflow.name}",
                            onClick = { onDeleteWorkflow(workflow) },
                            size = 40.dp,
                            tint = NovaColors.Error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentStepRow(step: AgentStep) {
    val (icon, color) = when (step.status) {
        StepStatus.PENDING -> Icons.Default.Bolt to NovaTheme.extended.textTertiary
        StepStatus.RUNNING -> Icons.Default.PlayArrow to NovaColors.Secondary
        StepStatus.SUCCESS -> Icons.Default.Check to NovaColors.Success
        StepStatus.FAILED -> Icons.Default.ErrorOutline to NovaColors.Error
        StepStatus.SKIPPED -> Icons.Default.Close to NovaColors.Warning
        StepStatus.AWAITING_CONFIRMATION -> Icons.Default.Warning to NovaColors.Warning
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.medium,
        tint = if (step.status == StepStatus.RUNNING) {
            NovaColors.Secondary.copy(alpha = 0.10f)
        } else {
            NovaTheme.extended.glass
        },
        borderColor = if (step.status == StepStatus.RUNNING) {
            NovaColors.Secondary.copy(alpha = 0.35f)
        } else {
            NovaTheme.extended.glassBorder
        }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "${step.index + 1}. ${step.action.describe()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                step.action.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTheme.extended.textTertiary
                    )
                }
                if (step.result.isNotBlank() && step.status == StepStatus.SUCCESS) {
                    Text(
                        step.result,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaColors.Success,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                step.error?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaColors.Error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            step.durationMs?.let { duration ->
                Text(
                    "${duration / 1000}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.extended.textTertiary
                )
            }
        }
    }
}

@Composable
private fun AgentSummaryCard(
    summary: String,
    data: List<String>,
    isError: Boolean,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.md),
        shape = NovaShapeTokens.large,
        tint = if (isError) NovaColors.Error.copy(alpha = 0.10f) else NovaColors.Success.copy(alpha = 0.10f),
        borderColor = if (isError) NovaColors.Error.copy(alpha = 0.3f) else NovaColors.Success.copy(alpha = 0.3f)
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Text(
                if (isError) "Run ended early" else "Run complete",
                style = MaterialTheme.typography.titleSmall,
                color = if (isError) NovaColors.Error else NovaColors.Success
            )
            Spacer(Modifier.height(Dimens.xs))
            Text(
                summary.ifBlank { "No summary was produced." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (data.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    data.joinToString("\n\n").take(2000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Dimens.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                GlassButton("Copy", onCopy, style = GlassButtonStyle.Ghost, icon = Icons.Default.ContentCopy)
                GlassButton("Save workflow", onSave, style = GlassButtonStyle.Ghost, icon = Icons.Default.Save)
                GlassButton("New run", onReset, style = GlassButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun AgentErrorBar(message: String, onDismiss: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.md),
        shape = NovaShapeTokens.medium,
        tint = NovaColors.Error.copy(alpha = 0.12f),
        borderColor = NovaColors.Error.copy(alpha = 0.35f)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = NovaColors.Error,
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(Icons.Default.Close, "Dismiss", onDismiss, size = 40.dp, tint = NovaColors.Error)
        }
    }
}

@Composable
private fun SaveWorkflowDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName.take(60)) }
    var description by remember { mutableStateOf("") }
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(NovaColors.Scrim)
            .clickable(indication = null, interactionSource = interaction) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(Dimens.lg),
            shape = NovaShapeTokens.large
        ) {
            Column(Modifier.padding(Dimens.lg)) {
                Text(
                    "Save as workflow",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Dimens.md))
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Workflow name"
                )
                Spacer(Modifier.height(Dimens.sm))
                GlassTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "What it does (optional)"
                )
                Spacer(Modifier.height(Dimens.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    GlassButton("Cancel", onDismiss, style = GlassButtonStyle.Ghost, modifier = Modifier.weight(1f))
                    GlassButton(
                        "Save",
                        { onSave(name, description) },
                        style = GlassButtonStyle.Primary,
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
