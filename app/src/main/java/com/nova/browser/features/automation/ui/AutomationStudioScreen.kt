package com.nova.browser.features.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.AutomationEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassFAB
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassSwitch
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.MonoTextStyle
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.features.automation.viewmodel.AutomationViewModel
import com.nova.browser.features.automation.viewmodel.TRIGGER_TYPES

/**
 * Automation studio. Workflows are executed by the agent, which lives on the
 * browser surface, so running one hands the id back to the browser screen.
 */
@Composable
fun AutomationStudioScreen(
    onBack: () -> Unit,
    onRun: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val all by viewModel.automations.collectAsStateWithLifecycle()
    val enabledCount by viewModel.enabledCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val visible = viewModel.visible(all)

    LaunchedEffect(state.message, state.error) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopBar(
                title = "Automation studio",
                subtitle = "$enabledCount enabled of ${all.size}",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                }
            )
        },
        floatingActionButton = {
            GlassFAB(
                icon = Icons.Default.Add,
                contentDescription = "Create an automation",
                onClick = { viewModel.showEditor(true) },
                glowing = false
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GlassSearchBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onSearch = { },
                placeholder = "Search automations",
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Loading automations")
                }

                visible.isEmpty() && state.query.isNotBlank() -> EmptyState(
                    icon = Icons.Default.SmartToy,
                    title = "No matches",
                    message = "No automation matches \"${state.query}\".",
                    actionText = "Clear search",
                    onAction = { viewModel.setQuery("") },
                    modifier = Modifier.fillMaxSize()
                )

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Default.SmartToy,
                    title = "No automations yet",
                    message = "Give the AI agent a goal on any page and save the resulting plan, or build a workflow here step by step.",
                    actionText = "Create one",
                    onAction = { viewModel.showEditor(true) },
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.lg, end = Dimens.lg, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    items(visible, key = { it.id }) { automation ->
                        AutomationCard(
                            automation = automation,
                            steps = viewModel.stepsOf(automation).map { it.describe() },
                            expanded = state.expandedId == automation.id,
                            onToggleExpand = { viewModel.toggleExpanded(automation) },
                            onToggleEnabled = { viewModel.setEnabled(automation, it) },
                            onRun = { onRun(automation.id) },
                            onEdit = { viewModel.showEditor(true, automation) },
                            onDuplicate = { viewModel.duplicate(automation) },
                            onDelete = { viewModel.confirmDelete(automation) }
                        )
                    }
                }
            }
        }
    }

    if (state.editorVisible) {
        AutomationEditorDialog(
            automation = state.editing,
            templateJson = viewModel.templateJson(),
            onDismiss = { viewModel.showEditor(false) },
            onSave = { name, description, steps, trigger, triggerValue ->
                viewModel.save(state.editing, name, description, steps, trigger, triggerValue)
            }
        )
    }

    state.confirmDelete?.let { automation ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = "Delete ${automation.name}?",
            message = "This automation and its ${viewModel.stepsOf(automation).size} steps will be removed.",
            confirmText = "Delete",
            onConfirm = { viewModel.delete(automation) },
            destructive = true
        )
    }
}

@Composable
private fun AutomationCard(
    automation: AutomationEntity,
    steps: List<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Collapse steps" else "Show steps") {
                onToggleExpand()
            },
        shape = NovaShapeTokens.medium,
        tint = if (automation.isEnabled) {
            NovaTheme.extended.glass
        } else {
            NovaTheme.extended.glass.copy(alpha = 0.4f)
        }
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (automation.isEnabled) NovaColors.Accent else NovaTheme.extended.textTertiary,
                    modifier = Modifier.size(Dimens.icon)
                )
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        automation.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        automation.description.ifBlank { "${steps.size} steps" },
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(Dimens.sm))
                GlassSwitch(checked = automation.isEnabled, onCheckedChange = onToggleEnabled)
            }

            Spacer(Modifier.height(Dimens.xs))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                GlassChip(
                    text = when (automation.triggerType) {
                        "on_page_load" -> "On page load"
                        "on_domain" -> "On ${automation.triggerValue.orEmpty()}"
                        "scheduled" -> "Scheduled"
                        else -> "Manual"
                    },
                    selected = false
                )
                GlassChip(text = "${steps.size} steps", selected = false)
                if (automation.runCount > 0) {
                    GlassChip(text = "Run ${automation.runCount}×", selected = false)
                }
                automation.lastRunAt?.let {
                    GlassChip(text = "Last ${DateUtils.relative(it)}", selected = false)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(Dimens.sm))
                    if (steps.isEmpty()) {
                        Text(
                            "This automation has no readable steps. Edit it to fix the step definition.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaColors.Warning
                        )
                    } else {
                        steps.forEachIndexed { index, description ->
                            Row(
                                Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "${index + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NovaColors.Accent,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Dimens.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(
                            text = "Run now",
                            onClick = onRun,
                            style = GlassButtonStyle.Primary,
                            icon = Icons.Default.PlayArrow,
                            enabled = steps.isNotEmpty()
                        )
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate ${automation.name}",
                            onClick = onDuplicate,
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GlassIconButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "Edit ${automation.name}",
                            onClick = onEdit,
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GlassIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete ${automation.name}",
                            onClick = onDelete,
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationEditorDialog(
    automation: AutomationEntity?,
    templateJson: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(automation?.name.orEmpty()) }
    var description by remember { mutableStateOf(automation?.description.orEmpty()) }
    var stepsJson by remember { mutableStateOf(automation?.workflowJson ?: templateJson) }
    var trigger by remember { mutableStateOf(automation?.triggerType ?: TRIGGER_TYPES.first()) }
    var triggerValue by remember { mutableStateOf(automation?.triggerValue.orEmpty()) }

    GlassDialog(
        onDismiss = onDismiss,
        title = if (automation == null) "New automation" else "Edit automation",
        confirmText = "Save",
        onConfirm = { onSave(name, description, stepsJson, trigger, triggerValue) },
        content = {
            Column {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Name"
                )
                Spacer(Modifier.height(Dimens.sm))
                GlassTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "What it does",
                    singleLine = false,
                    maxLines = 2
                )
                Spacer(Modifier.height(Dimens.md))
                Text(
                    "Trigger",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaTheme.extended.textTertiary
                )
                Spacer(Modifier.height(Dimens.xs))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    TRIGGER_TYPES.forEach { option ->
                        GlassChip(
                            text = when (option) {
                                "on_page_load" -> "On page load"
                                "on_domain" -> "On domain"
                                "scheduled" -> "Scheduled"
                                else -> "Manual"
                            },
                            selected = option == trigger,
                            onClick = { trigger = option }
                        )
                    }
                }
                if (trigger == "on_domain" || trigger == "scheduled") {
                    Spacer(Modifier.height(Dimens.sm))
                    GlassTextField(
                        value = triggerValue,
                        onValueChange = { triggerValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = if (trigger == "on_domain") "Domain" else "Every N minutes",
                        placeholder = if (trigger == "on_domain") "example.com" else "60"
                    )
                }
                Spacer(Modifier.height(Dimens.md))
                Text(
                    "Steps — a JSON array of {action, target, value, reason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaTheme.extended.textTertiary
                )
                Spacer(Modifier.height(Dimens.xs))
                GlassTextField(
                    value = stepsJson,
                    onValueChange = { stepsJson = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = null,
                    singleLine = false,
                    maxLines = 8,
                    textStyle = MonoTextStyle
                )
            }
        }
    )
}
