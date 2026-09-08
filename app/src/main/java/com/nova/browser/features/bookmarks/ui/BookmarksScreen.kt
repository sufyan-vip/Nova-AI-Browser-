package com.nova.browser.features.bookmarks.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.nova.browser.core.database.entities.BookmarkEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.bookmarks.viewmodel.BookmarksViewModel
import com.nova.browser.features.common.ui.FaviconImage

@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val all by viewModel.bookmarks.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowOpen by remember { mutableStateOf(false) }

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
                title = state.folderName ?: "Bookmarks",
                subtitle = "${all.count { !it.isFolder }} saved",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = {
                            if (state.folderId != null) viewModel.openFolder(null) else onBack()
                        }
                    )
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Default.CreateNewFolder,
                        contentDescription = "New folder",
                        onClick = { viewModel.showNewFolderDialog(true) }
                    )
                    Box {
                        GlassIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            onClick = { overflowOpen = true }
                        )
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete all bookmarks") },
                                onClick = {
                                    overflowOpen = false
                                    viewModel.deleteAll()
                                }
                            )
                        }
                    }
                }
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
                placeholder = "Search bookmarks",
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Loading bookmarks")
                }

                visible.isEmpty() && state.query.isNotBlank() -> EmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = "No matches",
                    message = "Nothing here matches \"${state.query}\".",
                    modifier = Modifier.fillMaxSize()
                )

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = "No bookmarks yet",
                    message = "Tap the bookmark icon in the browser menu to save pages for later.",
                    actionText = "Back to browsing",
                    onAction = onBack,
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.lg, end = Dimens.lg, bottom = Dimens.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    items(visible, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            onClick = {
                                if (bookmark.isFolder) viewModel.openFolder(bookmark)
                                else onOpenUrl(bookmark.url)
                            },
                            onEdit = { viewModel.startEdit(bookmark) },
                            onDelete = { viewModel.confirmDelete(bookmark) }
                        )
                    }
                }
            }
        }
    }

    state.editing?.let { bookmark ->
        EditBookmarkDialog(
            bookmark = bookmark,
            folders = folders,
            onDismiss = { viewModel.startEdit(null) },
            onSave = { title, url, folderId -> viewModel.save(bookmark, title, url, folderId) }
        )
    }

    if (state.newFolderDialog) {
        NewFolderDialog(
            onDismiss = { viewModel.showNewFolderDialog(false) },
            onCreate = viewModel::createFolder
        )
    }

    state.confirmDelete?.let { bookmark ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = if (bookmark.isFolder) "Delete folder?" else "Remove bookmark?",
            message = if (bookmark.isFolder) {
                "\"${bookmark.title}\" and everything inside it will be deleted."
            } else {
                "\"${bookmark.title}\" will be removed from your bookmarks."
            },
            confirmText = "Delete",
            onConfirm = { viewModel.delete(bookmark) },
            destructive = true
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.medium
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bookmark.isFolder) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.icon)
                )
            } else {
                FaviconImage(url = bookmark.url, size = Dimens.icon)
            }

            Spacer(Modifier.width(Dimens.md))

            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.title.ifBlank { UrlUtils.displayUrl(bookmark.url) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (bookmark.isFolder) DateUtils.relative(bookmark.createdAt)
                    else UrlUtils.displayUrl(bookmark.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            GlassIconButton(
                icon = Icons.Default.Edit,
                contentDescription = "Edit ${bookmark.title}",
                onClick = onEdit,
                size = 40.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GlassIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Delete ${bookmark.title}",
                onClick = onDelete,
                size = 40.dp,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun EditBookmarkDialog(
    bookmark: BookmarkEntity,
    folders: List<BookmarkEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?) -> Unit
) {
    var title by remember(bookmark.id) { mutableStateOf(bookmark.title) }
    var url by remember(bookmark.id) { mutableStateOf(bookmark.url) }
    var folderId by remember(bookmark.id) { mutableStateOf(bookmark.folderId) }

    GlassDialog(
        onDismiss = onDismiss,
        title = if (bookmark.isFolder) "Rename folder" else "Edit bookmark",
        confirmText = "Save",
        onConfirm = { onSave(title, url, folderId) },
        content = {
            Column {
                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Title"
                )
                if (!bookmark.isFolder) {
                    Spacer(Modifier.height(Dimens.sm))
                    GlassTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Address"
                    )
                    if (folders.isNotEmpty()) {
                        Spacer(Modifier.height(Dimens.md))
                        Text(
                            "Folder",
                            style = MaterialTheme.typography.labelMedium,
                            color = NovaTheme.extended.textTertiary
                        )
                        Spacer(Modifier.height(Dimens.xs))
                        Column {
                            FolderChoice("No folder", folderId == null) { folderId = null }
                            folders.forEach { folder ->
                                FolderChoice(folder.title, folderId == folder.id) {
                                    folderId = folder.id
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun FolderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else NovaTheme.extended.textTertiary,
            modifier = Modifier.size(Dimens.iconSmall)
        )
        Spacer(Modifier.width(Dimens.sm))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    GlassDialog(
        onDismiss = onDismiss,
        title = "New folder",
        confirmText = "Create",
        onConfirm = { onCreate(name) },
        content = {
            GlassTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "Folder name"
            )
        }
    )
}
