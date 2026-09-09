package com.nova.browser.features.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.HistoryEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.theme.SectionHeader
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.common.ui.FaviconImage
import com.nova.browser.features.history.viewmodel.ClearRange
import com.nova.browser.features.history.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val all by viewModel.history.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sections = viewModel.sections(all)

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
                title = "History",
                subtitle = "${all.size} pages",
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
                        contentDescription = "Clear browsing history",
                        onClick = { viewModel.showClearDialog(true) },
                        tint = MaterialTheme.colorScheme.error
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
                placeholder = "Search history",
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Loading history")
                }

                sections.isEmpty() && state.query.isNotBlank() -> EmptyState(
                    icon = Icons.Default.History,
                    title = "No matches",
                    message = "No visited pages match \"${state.query}\".",
                    modifier = Modifier.fillMaxSize()
                )

                sections.isEmpty() -> EmptyState(
                    icon = Icons.Default.History,
                    title = "No history yet",
                    message = "Pages you visit will appear here. Private tabs are never recorded.",
                    actionText = "Start browsing",
                    onAction = onBack,
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.lg, end = Dimens.lg, bottom = Dimens.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    sections.forEach { section ->
                        item(key = "header-${section.label}") {
                            SectionHeader(section.label)
                        }
                        items(section.entries, key = { it.id }) { entry ->
                            HistoryRow(
                                entry = entry,
                                onClick = { onOpenUrl(entry.url) },
                                onDelete = { viewModel.confirmDelete(entry) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.clearDialogVisible) {
        ClearHistoryDialog(
            onDismiss = { viewModel.showClearDialog(false) },
            onClear = viewModel::clear
        )
    }

    state.confirmDelete?.let { entry ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = "Remove from history?",
            message = entry.title.ifBlank { UrlUtils.displayUrl(entry.url) },
            confirmText = "Remove",
            onConfirm = { viewModel.delete(entry) },
            destructive = true
        )
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntity,
    onClick: () -> Unit,
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
            FaviconImage(url = entry.url, faviconUrl = entry.favicon, size = Dimens.icon)
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { UrlUtils.displayUrl(entry.url) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateUtils.time(entry.visitedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTheme.extended.textTertiary
                    )
                    Text(
                        " · ${UrlUtils.displayUrl(entry.url)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entry.visitCount > 1) {
                Text(
                    "${entry.visitCount}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.extended.textTertiary
                )
                Spacer(Modifier.width(Dimens.sm))
            }
            GlassIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Remove ${entry.title} from history",
                onClick = onDelete,
                size = 40.dp,
                tint = NovaTheme.extended.textTertiary
            )
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onClear: (ClearRange) -> Unit
) {
    GlassDialog(
        onDismiss = onDismiss,
        title = "Clear browsing history",
        message = "Choose how far back to clear. This can't be undone.",
        confirmText = null,
        dismissText = "Cancel",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                ClearRange.entries.forEach { range ->
                    GlassButton(
                        text = range.label,
                        onClick = { onClear(range) },
                        style = if (range == ClearRange.ALL_TIME) {
                            GlassButtonStyle.Danger
                        } else {
                            GlassButtonStyle.Secondary
                        },
                        fillWidth = true
                    )
                }
            }
        }
    )
}
