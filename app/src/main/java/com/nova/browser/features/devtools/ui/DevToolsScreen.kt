package com.nova.browser.features.devtools.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.MonoTextStyle
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.copyToClipboard
import com.nova.browser.features.browser.viewmodel.BrowserViewModel
import com.nova.browser.features.devtools.viewmodel.ConsoleEntry
import com.nova.browser.features.devtools.viewmodel.DevToolsViewModel
import com.nova.browser.features.devtools.viewmodel.NetworkEntry

private val TAB_TITLES = listOf(
    "Console", "Network", "DOM", "Storage", "Source", "Performance", "Accessibility"
)

/**
 * Developer tools. The panels that need the live page (DOM, storage, source,
 * performance, accessibility) read it through the shared PageInteractor, which
 * the browser screen keeps bound to the active WebView.
 */
@Composable
fun DevToolsScreen(
    browserViewModel: BrowserViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevToolsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val browserState by browserViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.selectedTab) {
        viewModel.refreshTab(state.selectedTab)
    }

    LaunchedEffect(state.error) {
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
                title = "DevTools",
                subtitle = browserState.url.ifBlank { "No page loaded" },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh this panel",
                        onClick = { viewModel.refreshTab(state.selectedTab) }
                    )
                    GlassIconButton(
                        icon = Icons.Default.DeleteSweep,
                        contentDescription = "Clear everything",
                        onClick = viewModel::clearAll,
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                TAB_TITLES.forEachIndexed { index, title ->
                    val badge = when (index) {
                        0 -> state.console.size
                        1 -> state.network.size
                        else -> 0
                    }
                    GlassChip(
                        text = if (badge > 0) "$title ($badge)" else title,
                        selected = index == state.selectedTab,
                        onClick = { viewModel.selectTab(index) }
                    )
                }
            }

            if (state.selectedTab <= 1) {
                GlassSearchBar(
                    query = state.filter,
                    onQueryChange = viewModel::setFilter,
                    onSearch = { },
                    placeholder = if (state.selectedTab == 0) "Filter console" else "Filter requests",
                    modifier = Modifier.padding(horizontal = Dimens.lg)
                )
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        NovaLoadingIndicator(label = "Reading the page")
                    }

                    else -> when (state.selectedTab) {
                        0 -> ConsolePanel(
                            entries = state.filteredConsole,
                            errorCount = state.errorCount,
                            warningCount = state.warningCount,
                            aiLoading = state.aiLoading,
                            onExplain = viewModel::explainError,
                            onAnalyzeAll = viewModel::analyzeAllErrors,
                            onClear = viewModel::clearConsole,
                            onCopy = { context.copyToClipboard(it) }
                        )

                        1 -> NetworkPanel(
                            entries = state.filteredNetwork,
                            onClear = viewModel::clearNetwork,
                            onCopy = { context.copyToClipboard(it) }
                        )

                        2 -> JsonPanel(
                            raw = state.domTree,
                            emptyTitle = "No DOM captured",
                            emptyMessage = "Load a page, then refresh to inspect its element tree.",
                            onRefresh = viewModel::refreshDom,
                            onCopy = { context.copyToClipboard(it) }
                        )

                        3 -> JsonPanel(
                            raw = state.storage,
                            emptyTitle = "No storage data",
                            emptyMessage = "Local storage, session storage and cookies for the current page appear here.",
                            onRefresh = viewModel::refreshStorage,
                            onCopy = { context.copyToClipboard(it) }
                        )

                        4 -> JsonPanel(
                            raw = state.source,
                            emptyTitle = "No page source",
                            emptyMessage = "Refresh to fetch the current document's HTML.",
                            onRefresh = viewModel::refreshSource,
                            onCopy = { context.copyToClipboard(it) }
                        )

                        5 -> JsonPanel(
                            raw = state.performance,
                            emptyTitle = "No performance data",
                            emptyMessage = "Refresh after the page finishes loading to see timings and slow resources.",
                            onRefresh = viewModel::refreshPerformance,
                            onCopy = { context.copyToClipboard(it) }
                        )

                        else -> JsonPanel(
                            raw = state.accessibility,
                            emptyTitle = "No audit yet",
                            emptyMessage = "Run an accessibility audit on the current page.",
                            onRefresh = viewModel::refreshAccessibility,
                            onCopy = { context.copyToClipboard(it) }
                        )
                    }
                }
            }

            if (state.selectedTab == 0) {
                ConsoleInput(
                    value = state.jsInput,
                    onValueChange = viewModel::onJsInputChange,
                    onRun = viewModel::runJsInput
                )
            }
        }
    }

    state.aiExplanation?.let { explanation ->
        GlassDialog(
            onDismiss = viewModel::dismissAiExplanation,
            title = "AI debugger",
            confirmText = "Copy",
            onConfirm = { context.copyToClipboard(explanation) },
            dismissText = "Close",
            content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }
}

@Composable
private fun ConsolePanel(
    entries: List<ConsoleEntry>,
    errorCount: Int,
    warningCount: Int,
    aiLoading: Boolean,
    onExplain: (ConsoleEntry) -> Unit,
    onAnalyzeAll: () -> Unit,
    onClear: () -> Unit,
    onCopy: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$errorCount errors · $warningCount warnings",
                style = MaterialTheme.typography.labelMedium,
                color = if (errorCount > 0) NovaColors.Error else NovaTheme.extended.textTertiary,
                modifier = Modifier.weight(1f)
            )
            if (errorCount > 0) {
                GlassButton(
                    text = if (aiLoading) "Analyzing…" else "Analyze with AI",
                    onClick = onAnalyzeAll,
                    style = GlassButtonStyle.Ghost,
                    icon = Icons.Default.AutoAwesome,
                    enabled = !aiLoading,
                    loading = aiLoading
                )
            }
            GlassIconButton(
                icon = Icons.Default.DeleteSweep,
                contentDescription = "Clear console",
                onClick = onClear,
                size = 40.dp,
                tint = NovaTheme.extended.textTertiary
            )
        }

        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Terminal,
                title = "Console is empty",
                message = "Console output from the current page appears here as it happens.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.lg, end = Dimens.lg, bottom = Dimens.lg
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                items(entries, key = { it.id }) { entry ->
                    ConsoleRow(
                        entry = entry,
                        onExplain = { onExplain(entry) },
                        onCopy = { onCopy(entry.message) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleRow(entry: ConsoleEntry, onExplain: () -> Unit, onCopy: () -> Unit) {
    val color = when {
        entry.isError -> NovaColors.Error
        entry.isWarning -> NovaColors.Warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.small,
        tint = when {
            entry.isError -> NovaColors.Error.copy(alpha = 0.08f)
            entry.isWarning -> NovaColors.Warning.copy(alpha = 0.08f)
            else -> NovaTheme.extended.glass
        }
    ) {
        Column(Modifier.padding(Dimens.sm)) {
            Text(
                entry.message,
                style = MonoTextStyle,
                color = color
            )
            Spacer(Modifier.height(Dimens.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entry.level.uppercase()} · ${entry.source}:${entry.line}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy this message",
                    onClick = onCopy,
                    size = 36.dp,
                    tint = NovaTheme.extended.textTertiary
                )
                if (entry.isError || entry.isWarning) {
                    GlassIconButton(
                        icon = Icons.Default.AutoAwesome,
                        contentDescription = "Explain this with AI",
                        onClick = onExplain,
                        size = 36.dp,
                        tint = NovaColors.Accent
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkPanel(
    entries: List<NetworkEntry>,
    onClear: () -> Unit,
    onCopy: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${entries.size} requests · ${entries.count { it.blocked }} blocked",
                style = MaterialTheme.typography.labelMedium,
                color = NovaTheme.extended.textTertiary,
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(
                icon = Icons.Default.DeleteSweep,
                contentDescription = "Clear network log",
                onClick = onClear,
                size = 40.dp,
                tint = NovaTheme.extended.textTertiary
            )
        }

        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Refresh,
                title = "No requests recorded",
                message = "Reload the page with DevTools open to capture its network activity.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.lg, end = Dimens.lg, bottom = Dimens.lg
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                items(entries, key = { it.id }) { entry ->
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Copy request URL") { onCopy(entry.url) },
                        shape = NovaShapeTokens.small,
                        tint = if (entry.blocked) {
                            NovaColors.Error.copy(alpha = 0.08f)
                        } else {
                            NovaTheme.extended.glass
                        }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(Dimens.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (entry.blocked) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = "Blocked",
                                    tint = NovaColors.Error,
                                    modifier = Modifier.size(Dimens.iconSmall)
                                )
                                Spacer(Modifier.width(Dimens.sm))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.path.ifBlank { "/" },
                                    style = MonoTextStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${entry.method} · ${entry.resourceType} · ${entry.host}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NovaTheme.extended.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonPanel(
    raw: String,
    emptyTitle: String,
    emptyMessage: String,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit
) {
    if (raw.isBlank() || raw == "{}" || raw == "null") {
        EmptyState(
            icon = Icons.Default.Refresh,
            title = emptyTitle,
            message = emptyMessage,
            actionText = "Refresh",
            onAction = onRefresh,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${raw.length} characters",
                style = MaterialTheme.typography.labelSmall,
                color = NovaTheme.extended.textTertiary,
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy panel contents",
                onClick = { onCopy(raw) },
                size = 40.dp,
                tint = NovaTheme.extended.textTertiary
            )
            GlassIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Refresh panel",
                onClick = onRefresh,
                size = 40.dp,
                tint = NovaTheme.extended.textTertiary
            )
        }
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Dimens.lg)
                .padding(bottom = Dimens.lg),
            shape = NovaShapeTokens.medium
        ) {
            Text(
                // Very large documents are trimmed so the text layout stays responsive.
                raw.take(60_000),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(Dimens.md)
            )
        }
    }
}

@Composable
private fun ConsoleInput(value: String, onValueChange: (String) -> Unit, onRun: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = "Run JavaScript on this page",
            imeAction = ImeAction.Go,
            onImeAction = onRun
        )
        Spacer(Modifier.width(Dimens.sm))
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Run",
            onClick = onRun,
            enabled = value.isNotBlank(),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
