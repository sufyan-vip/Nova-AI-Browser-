package com.nova.browser.features.memory.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
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
import com.nova.browser.core.database.entities.AIMemoryEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassFAB
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassSwitch
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.features.memory.viewmodel.AIMemoryViewModel
import com.nova.browser.features.memory.viewmodel.MEMORY_TYPES

@Composable
fun AIMemoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AIMemoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val all by viewModel.memories.collectAsStateWithLifecycle()
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
                title = "AI memory",
                subtitle = "$enabledCount active of ${all.size}",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                },
                actions = {
                    if (all.isNotEmpty()) {
                        GlassIconButton(
                            icon = Icons.Default.DeleteSweep,
                            contentDescription = "Forget everything",
                            onClick = { viewModel.confirmDeleteAll(true) },
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            GlassFAB(
                icon = Icons.Default.Add,
                contentDescription = "Add a memory",
                onClick = { viewModel.showAddDialog(true) },
                glowing = false
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                shape = NovaShapeTokens.medium,
                tint = NovaColors.Accent.copy(alpha = 0.08f),
                borderColor = NovaColors.Accent.copy(alpha = 0.22f)
            ) {
                Text(
                    "NOVA uses these notes to personalise every AI answer. Everything stays on this device " +
                        "and only the relevant entries are sent with a request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Dimens.md)
                )
            }

            GlassSearchBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onSearch = { },
                placeholder = "Search memories",
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.xs)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                viewModel.types.forEach { type ->
                    GlassChip(
                        text = type.replaceFirstChar { it.uppercase() },
                        selected = type == state.typeFilter,
                        onClick = { viewModel.setTypeFilter(type) }
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Loading memories")
                }

                visible.isEmpty() && (state.query.isNotBlank() || state.typeFilter != "All") -> EmptyState(
                    icon = Icons.Default.Psychology,
                    title = "No matches",
                    message = "Nothing matches your filters.",
                    actionText = "Clear filters",
                    onAction = {
                        viewModel.setQuery("")
                        viewModel.setTypeFilter("All")
                    },
                    modifier = Modifier.fillMaxSize()
                )

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Default.Psychology,
                    title = "NOVA doesn't know you yet",
                    message = "Tell the AI things like \"always reply in Spanish\" and it will offer to remember them, or add one yourself.",
                    actionText = "Add a memory",
                    onAction = { viewModel.showAddDialog(true) },
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.lg, end = Dimens.lg, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    items(visible, key = { it.id }) { memory ->
                        MemoryRow(
                            memory = memory,
                            onToggle = { viewModel.setEnabled(memory, it) },
                            onEdit = { viewModel.startEdit(memory) },
                            onDelete = { viewModel.confirmDelete(memory) }
                        )
                    }
                }
            }
        }
    }

    if (state.addDialogVisible || state.editing != null) {
        EditMemoryDialog(
            memory = state.editing,
            onDismiss = {
                viewModel.showAddDialog(false)
                viewModel.startEdit(null)
            },
            onSave = { content, type, site -> viewModel.save(state.editing, content, type, site) }
        )
    }

    state.confirmDelete?.let { memory ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = "Forget this?",
            message = memory.content,
            confirmText = "Forget",
            onConfirm = { viewModel.delete(memory) },
            destructive = true
        )
    }

    if (state.confirmDeleteAll) {
        GlassDialog(
            onDismiss = { viewModel.confirmDeleteAll(false) },
            title = "Forget everything?",
            message = "All ${all.size} memories will be deleted. NOVA will start fresh.",
            confirmText = "Forget all",
            onConfirm = viewModel::deleteAll,
            destructive = true
        )
    }
}

@Composable
private fun MemoryRow(
    memory: AIMemoryEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when (memory.type.lowercase()) {
        "instruction" -> NovaColors.Accent
        "preference" -> NovaColors.Secondary
        "fact" -> NovaColors.Primary
        else -> NovaColors.Success
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.medium,
        tint = if (memory.isEnabled) NovaTheme.extended.glass else NovaTheme.extended.glass.copy(alpha = 0.4f)
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        memory.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (memory.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            NovaTheme.extended.textTertiary
                        }
                    )
                    Spacer(Modifier.height(Dimens.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            memory.type.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor
                        )
                        memory.relatedSite?.let { site ->
                            Text(
                                " · $site",
                                style = MaterialTheme.typography.labelSmall,
                                color = NovaTheme.extended.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            " · used ${memory.usageCount}×",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTheme.extended.textTertiary
                        )
                        Text(
                            " · ${DateUtils.relative(memory.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTheme.extended.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(Dimens.sm))
                GlassSwitch(checked = memory.isEnabled, onCheckedChange = onToggle)
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                GlassIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit memory",
                    onClick = onEdit,
                    size = 36.dp,
                    tint = NovaTheme.extended.textTertiary
                )
                GlassIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete memory",
                    onClick = onDelete,
                    size = 36.dp,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EditMemoryDialog(
    memory: AIMemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var content by remember { mutableStateOf(memory?.content.orEmpty()) }
    var type by remember { mutableStateOf(memory?.type ?: MEMORY_TYPES.first()) }
    var site by remember { mutableStateOf(memory?.relatedSite.orEmpty()) }

    GlassDialog(
        onDismiss = onDismiss,
        title = if (memory == null) "Add a memory" else "Edit memory",
        confirmText = "Save",
        onConfirm = { onSave(content, type, site) },
        content = {
            Column {
                GlassTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "What should NOVA remember?",
                    placeholder = "e.g. I prefer concise answers with code examples",
                    singleLine = false,
                    maxLines = 4
                )
                Spacer(Modifier.height(Dimens.md))
                Text(
                    "Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaTheme.extended.textTertiary
                )
                Spacer(Modifier.height(Dimens.xs))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    MEMORY_TYPES.forEach { option ->
                        GlassChip(
                            text = option.replaceFirstChar { it.uppercase() },
                            selected = option == type,
                            onClick = { type = option }
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.md))
                GlassTextField(
                    value = site,
                    onValueChange = { site = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Only on this site (optional)",
                    placeholder = "example.com"
                )
            }
        }
    )
}
