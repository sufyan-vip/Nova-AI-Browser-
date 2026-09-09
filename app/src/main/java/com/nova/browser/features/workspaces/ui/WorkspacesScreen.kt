package com.nova.browser.features.workspaces.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Workspaces
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.WorkspaceEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassFAB
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.workspaces.viewmodel.WORKSPACE_COLORS
import com.nova.browser.features.workspaces.viewmodel.WORKSPACE_ICONS
import com.nova.browser.features.workspaces.viewmodel.WorkspaceViewModel

@Composable
fun WorkspacesScreen(
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = "Workspaces",
                subtitle = "${workspaces.size} saved tab sets",
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
                contentDescription = "Save current tabs as a workspace",
                onClick = { viewModel.showEditor(true) },
                glowing = false
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Loading workspaces")
                }

                workspaces.isEmpty() -> EmptyState(
                    icon = Icons.Default.Workspaces,
                    title = "No workspaces yet",
                    message = "Group your tabs by project — work, study, shopping — and switch between them in one tap.",
                    actionText = "Save current tabs",
                    onAction = { viewModel.showEditor(true) },
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.lg, end = Dimens.lg, top = Dimens.sm, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    items(workspaces, key = { it.id }) { workspace ->
                        WorkspaceCard(
                            workspace = workspace,
                            urls = viewModel.urlsOf(workspace),
                            switching = state.switching == workspace.id,
                            onOpen = {
                                viewModel.switchTo(workspace)
                                onOpenBrowser()
                            },
                            onSync = { viewModel.syncToCurrentTabs(workspace) },
                            onEdit = { viewModel.showEditor(true, workspace) },
                            onDelete = { viewModel.confirmDelete(workspace) }
                        )
                    }
                }
            }
        }
    }

    if (state.editorVisible) {
        WorkspaceEditorDialog(
            workspace = state.editing,
            onDismiss = { viewModel.showEditor(false) },
            onSave = { name, icon, color -> viewModel.save(state.editing, name, icon, color) }
        )
    }

    state.confirmDelete?.let { workspace ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = "Delete ${workspace.name}?",
            message = "The workspace is removed. Any tabs currently open stay open.",
            confirmText = "Delete",
            onConfirm = { viewModel.delete(workspace) },
            destructive = true
        )
    }
}

@Composable
private fun WorkspaceCard(
    workspace: WorkspaceEntity,
    urls: List<String>,
    switching: Boolean,
    onOpen: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = parseColor(workspace.color) ?: NovaColors.Primary

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.large,
        tint = if (workspace.isActive) accent.copy(alpha = 0.12f) else NovaTheme.extended.glass,
        borderColor = if (workspace.isActive) accent.copy(alpha = 0.4f) else NovaTheme.extended.glassBorder
    ) {
        Column(Modifier.padding(Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(Dimens.xxl)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        workspace.icon ?: "💼",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            workspace.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (workspace.isActive) {
                            Spacer(Modifier.width(Dimens.sm))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Active workspace",
                                tint = accent,
                                modifier = Modifier.size(Dimens.iconSmall)
                            )
                        }
                    }
                    Text(
                        "${urls.size} tabs · created ${DateUtils.relative(workspace.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary
                    )
                }
            }

            if (urls.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    urls.take(4).joinToString(" · ") { UrlUtils.domain(it).ifBlank { it } } +
                        if (urls.size > 4) " +${urls.size - 4} more" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "This workspace has no saved tabs yet — open some pages and tap sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.extended.textTertiary
                )
            }

            Spacer(Modifier.height(Dimens.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(
                    text = if (switching) "Opening…" else "Open",
                    onClick = onOpen,
                    style = GlassButtonStyle.Primary,
                    enabled = !switching,
                    loading = switching
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    icon = Icons.Default.Sync,
                    contentDescription = "Update ${workspace.name} with the tabs open now",
                    onClick = onSync,
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Rename ${workspace.name}",
                    onClick = onEdit,
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete ${workspace.name}",
                    onClick = onDelete,
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun WorkspaceEditorDialog(
    workspace: WorkspaceEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(workspace?.name.orEmpty()) }
    var icon by remember { mutableStateOf(workspace?.icon ?: WORKSPACE_ICONS.first()) }
    var color by remember { mutableStateOf(workspace?.color ?: WORKSPACE_COLORS.first()) }

    GlassDialog(
        onDismiss = onDismiss,
        title = if (workspace == null) "New workspace" else "Edit workspace",
        message = if (workspace == null) {
            "Your currently open tabs will be saved into this workspace."
        } else {
            null
        },
        confirmText = "Save",
        onConfirm = { onSave(name, icon, color) },
        content = {
            Column {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Name",
                    placeholder = "Work"
                )
                Spacer(Modifier.height(Dimens.md))
                Text(
                    "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaTheme.extended.textTertiary
                )
                Spacer(Modifier.height(Dimens.xs))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    WORKSPACE_ICONS.forEach { option ->
                        val selected = option == icon
                        Box(
                            Modifier
                                .size(Dimens.touchTarget)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    } else {
                                        NovaTheme.extended.glass
                                    }
                                )
                                .semantics { contentDescription = "Icon $option" }
                                .clickable { icon = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(option, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(Dimens.md))
                Text(
                    "Colour",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaTheme.extended.textTertiary
                )
                Spacer(Modifier.height(Dimens.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    WORKSPACE_COLORS.forEach { option ->
                        val parsed = parseColor(option) ?: NovaColors.Primary
                        val selected = option == color
                        Box(
                            Modifier
                                .size(Dimens.touchTarget)
                                .semantics { contentDescription = "Colour $option" }
                                .clickable { color = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(if (selected) 28.dp else 22.dp)
                                    .clip(CircleShape)
                                    .background(parsed)
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun parseColor(hex: String?): Color? = try {
    hex?.takeIf { it.startsWith("#") }?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (e: IllegalArgumentException) {
    null
}
