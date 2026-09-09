package com.nova.browser.features.notes.ui

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.NoteEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassFAB
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.core.utils.copyToClipboard
import com.nova.browser.core.utils.shareText
import com.nova.browser.features.notes.viewmodel.NotesViewModel

@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val all by viewModel.notes.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                title = "Notes",
                subtitle = "${all.size} saved clippings",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                },
                actions = {
                    if (visible.isNotEmpty()) {
                        GlassIconButton(
                            icon = Icons.Default.Share,
                            contentDescription = "Export notes",
                            onClick = {
                                context.shareText(viewModel.exportText(visible), "NOVA Notes")
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            GlassFAB(
                icon = Icons.Default.Add,
                contentDescription = "Write a note",
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
                placeholder = "Search notes",
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.lg),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                viewModel.types.forEach { type ->
                    GlassChip(
                        text = when (type) {
                            "note" -> "Notes"
                            "highlight" -> "Highlights"
                            "summary" -> "AI summaries"
                            else -> type
                        },
                        selected = type == state.typeFilter,
                        onClick = { viewModel.setTypeFilter(type) }
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Loading notes")
                }

                visible.isEmpty() && (state.query.isNotBlank() || state.typeFilter != "All") -> EmptyState(
                    icon = Icons.Default.StickyNote2,
                    title = "No matching notes",
                    message = "Try a different search or filter.",
                    actionText = "Clear filters",
                    onAction = {
                        viewModel.setQuery("")
                        viewModel.setTypeFilter("All")
                    },
                    modifier = Modifier.fillMaxSize()
                )

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Default.StickyNote2,
                    title = "Nothing saved yet",
                    message = "Highlight text on a page and choose Save to NOVA, ask the AI to summarise an article, or write your own note.",
                    actionText = "Write a note",
                    onAction = { viewModel.showEditor(true) },
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.lg, end = Dimens.lg, top = Dimens.sm, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    items(visible, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            tags = viewModel.tagsOf(note),
                            expanded = state.expandedId == note.id,
                            onToggle = { viewModel.toggleExpanded(note) },
                            onEdit = { viewModel.showEditor(true, note) },
                            onDelete = { viewModel.confirmDelete(note) },
                            onCopy = {
                                context.copyToClipboard(note.content)
                            },
                            onOpenSource = { note.sourceUrl?.let(onOpenUrl) }
                        )
                    }
                }
            }
        }
    }

    if (state.editorVisible) {
        NoteEditorDialog(
            note = state.editing,
            initialTags = state.editing?.let { viewModel.tagsOf(it).joinToString(", ") }.orEmpty(),
            onDismiss = { viewModel.showEditor(false) },
            onSave = { title, content, tags -> viewModel.save(state.editing, title, content, tags) }
        )
    }

    state.confirmDelete?.let { note ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = "Delete this note?",
            message = note.title,
            confirmText = "Delete",
            onConfirm = { viewModel.delete(note) },
            destructive = true
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    tags: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onOpenSource: () -> Unit
) {
    val (typeIcon, typeColor) = when (note.type.lowercase()) {
        "highlight" -> Icons.Default.FormatQuote to NovaColors.Warning
        "summary" -> Icons.Default.AutoAwesome to NovaColors.Accent
        else -> Icons.Default.StickyNote2 to NovaColors.Primary
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Collapse note" else "Expand note") { onToggle() },
        shape = NovaShapeTokens.medium
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    typeIcon,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(Dimens.iconSmall)
                )
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    note.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Dimens.xs))
            Text(
                note.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.sm))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    tags.forEach { tag -> GlassChip(text = tag, selected = false) }
                }
            }

            Spacer(Modifier.height(Dimens.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(DateUtils.relative(note.updatedAt))
                        note.sourceUrl?.let { append(" · ${UrlUtils.domain(it)}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!note.sourceUrl.isNullOrBlank()) {
                        GlassIconButton(
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open the page this note came from",
                            onClick = onOpenSource,
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassIconButton(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copy note text",
                        onClick = onCopy,
                        size = 40.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit note",
                        onClick = onEdit,
                        size = 40.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GlassIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete note",
                        onClick = onDelete,
                        size = 40.dp,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    note: NoteEntity?,
    initialTags: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(note?.title.orEmpty()) }
    var content by remember { mutableStateOf(note?.content.orEmpty()) }
    var tags by remember { mutableStateOf(initialTags) }

    GlassDialog(
        onDismiss = onDismiss,
        title = if (note == null) "New note" else "Edit note",
        confirmText = "Save",
        onConfirm = { onSave(title, content, tags) },
        content = {
            Column {
                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Title"
                )
                Spacer(Modifier.height(Dimens.sm))
                GlassTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Note",
                    singleLine = false,
                    maxLines = 8
                )
                Spacer(Modifier.height(Dimens.sm))
                GlassTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Tags (comma separated)",
                    placeholder = "research, android"
                )
            }
        }
    )
}
