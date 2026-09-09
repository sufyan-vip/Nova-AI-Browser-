package com.nova.browser.features.downloads.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.DownloadEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaProgressBar
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.core.utils.FileUtils
import com.nova.browser.features.downloads.repository.DownloadStatus
import com.nova.browser.features.downloads.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val all by viewModel.downloads.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
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
                title = "Downloads",
                subtitle = if (activeCount > 0) "$activeCount active" else "${all.size} files",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Default.DeleteSweep,
                        contentDescription = "Clear finished downloads",
                        onClick = viewModel::clearCompleted,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                placeholder = "Search downloads",
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                viewModel.categories.forEach { category ->
                    GlassChip(
                        text = category,
                        selected = category == state.category,
                        onClick = { viewModel.setCategory(category) }
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Loading downloads")
                }

                visible.isEmpty() && (state.query.isNotBlank() || state.category != "All") -> EmptyState(
                    icon = Icons.Default.Download,
                    title = "Nothing here",
                    message = "No downloads match your filters.",
                    actionText = "Clear filters",
                    onAction = {
                        viewModel.setQuery("")
                        viewModel.setCategory("All")
                    },
                    modifier = Modifier.fillMaxSize()
                )

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Default.Download,
                    title = "No downloads yet",
                    message = "Files you download will appear here with progress, pause and resume.",
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
                    items(visible, key = { it.id }) { entity ->
                        DownloadRow(
                            entity = entity,
                            onOpen = {
                                if (viewModel.isPdf(entity) && entity.status == DownloadStatus.COMPLETED) {
                                    onOpenPdf(entity.filePath)
                                } else {
                                    viewModel.open(entity)
                                }
                            },
                            onPause = { viewModel.pause(entity) },
                            onResume = { viewModel.resume(entity) },
                            onRetry = { viewModel.retry(entity) },
                            onCancel = { viewModel.cancel(entity) },
                            onShare = { viewModel.share(entity) },
                            onDelete = { viewModel.confirmDelete(entity) }
                        )
                    }
                }
            }
        }
    }

    state.confirmDelete?.let { entity ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = "Delete download?",
            message = "Remove \"${entity.fileName}\" from the list and delete the file from this device.",
            confirmText = "Delete file",
            onConfirm = { viewModel.delete(entity, deleteFile = true) },
            dismissText = "Keep file",
            destructive = true
        )
    }
}

@Composable
private fun DownloadRow(
    entity: DownloadEntity,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = entity.status == DownloadStatus.DOWNLOADING || entity.status == DownloadStatus.PENDING
    val progress = if (entity.totalSize > 0) {
        (entity.downloadedSize.toFloat() / entity.totalSize).coerceIn(0f, 1f)
    } else 0f

    val statusColor = when (entity.status) {
        DownloadStatus.COMPLETED -> NovaColors.Success
        DownloadStatus.FAILED -> NovaColors.Error
        DownloadStatus.PAUSED -> NovaColors.Warning
        DownloadStatus.CANCELLED -> NovaTheme.extended.textTertiary
        else -> MaterialTheme.colorScheme.primary
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.medium
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = entity.status == DownloadStatus.COMPLETED,
                    onClick = onOpen
                )
                .padding(Dimens.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(entity.category)
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        entity.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(statusLabel(entity))
                            if (entity.totalSize > 0) {
                                append(" · ${FileUtils.formatSize(entity.downloadedSize)}")
                                append(" / ${FileUtils.formatSize(entity.totalSize)}")
                            } else if (entity.downloadedSize > 0) {
                                append(" · ${FileUtils.formatSize(entity.downloadedSize)}")
                            }
                            if (entity.status == DownloadStatus.COMPLETED && entity.completedAt != null) {
                                append(" · ${DateUtils.relative(entity.completedAt)}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when (entity.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                        GlassIconButton(
                            icon = Icons.Default.Pause,
                            contentDescription = "Pause ${entity.fileName}",
                            onClick = onPause,
                            size = 40.dp,
                            tint = NovaColors.Warning
                        )
                        GlassIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Cancel ${entity.fileName}",
                            onClick = onCancel,
                            size = 40.dp,
                            tint = NovaColors.Error
                        )
                    }

                    DownloadStatus.PAUSED -> GlassIconButton(
                        icon = Icons.Default.PlayArrow,
                        contentDescription = "Resume ${entity.fileName}",
                        onClick = onResume,
                        size = 40.dp,
                        tint = NovaColors.Success
                    )

                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> GlassIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Retry ${entity.fileName}",
                        onClick = onRetry,
                        size = 40.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    else -> GlassIconButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share ${entity.fileName}",
                        onClick = onShare,
                        size = 40.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GlassIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete ${entity.fileName}",
                    onClick = onDelete,
                    size = 40.dp,
                    tint = NovaTheme.extended.textTertiary
                )
            }

            if (isActive || entity.status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(Dimens.sm))
                NovaProgressBar(
                    progress = progress,
                    color = statusColor,
                    height = 3.dp
                )
                if (entity.threadCount > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${entity.threadCount} parallel connections",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTheme.extended.textTertiary
                    )
                }
            }

            entity.errorMessage?.takeIf { entity.status == DownloadStatus.FAILED }?.let { error ->
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaColors.Error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val color = when (category) {
        "Image" -> Color(0xFF10B981)
        "Video" -> Color(0xFFA855F7)
        "Audio" -> Color(0xFFF59E0B)
        "Document" -> Color(0xFF6366F1)
        "Archive" -> Color(0xFF06B6D4)
        "App" -> Color(0xFFEF4444)
        else -> NovaTheme.extended.textTertiary
    }
    Box(
        Modifier
            .size(Dimens.iconLarge)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = category,
            tint = color,
            modifier = Modifier.size(Dimens.icon)
        )
    }
}

private fun statusLabel(entity: DownloadEntity): String = when (entity.status) {
    DownloadStatus.PENDING -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.CANCELLED -> "Cancelled"
    else -> entity.status.replaceFirstChar { it.uppercase() }
}
