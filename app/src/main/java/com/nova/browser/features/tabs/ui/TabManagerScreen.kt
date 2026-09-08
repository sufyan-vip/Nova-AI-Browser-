package com.nova.browser.features.tabs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassFAB
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTabRow
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.browser.viewmodel.BrowserViewModel
import com.nova.browser.features.common.ui.FaviconImage
import com.nova.browser.features.tabs.repository.Tab
import com.nova.browser.navigation.Routes

@Composable
fun TabManagerScreen(
    browserViewModel: BrowserViewModel,
    onClose: () -> Unit,
    onOpenTab: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by browserViewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(0) }
    var confirmCloseAll by remember { mutableStateOf(false) }

    val filters = listOf("All", "Normal", "Private", "Pinned")
    val tabs = when (selectedFilter) {
        1 -> state.tabs.filter { !it.isPrivate }
        2 -> state.tabs.filter { it.isPrivate }
        3 -> state.tabs.filter { it.isPinned }
        else -> state.tabs
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassTopBar(
                title = "Tabs",
                subtitle = "${state.tabs.size} open",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close tab manager",
                        onClick = onClose
                    )
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Default.VisibilityOff,
                        contentDescription = "New private tab",
                        onClick = {
                            browserViewModel.newTab(isPrivate = true)
                            onOpenTab()
                        },
                        tint = NovaColors.Secondary
                    )
                    GlassIconButton(
                        icon = Icons.Default.Tab,
                        contentDescription = "Close all tabs",
                        onClick = { confirmCloseAll = true },
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        },
        floatingActionButton = {
            GlassFAB(
                icon = Icons.Default.Add,
                contentDescription = "New tab",
                onClick = {
                    browserViewModel.newTab()
                    onOpenTab()
                },
                glowing = false
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GlassTabRow(
                tabs = filters,
                selectedIndex = selectedFilter,
                onSelect = { selectedFilter = it },
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
            )

            if (tabs.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Tab,
                    title = if (selectedFilter == 0) "No open tabs" else "No ${filters[selectedFilter].lowercase()} tabs",
                    message = "Open a new tab to start browsing.",
                    actionText = "New tab",
                    onAction = {
                        browserViewModel.newTab()
                        onOpenTab()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.lg, end = Dimens.lg, bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.md),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        TabCard(
                            tab = tab,
                            isActive = tab.id == state.activeTabId,
                            onClick = {
                                browserViewModel.selectTab(tab.id)
                                onOpenTab()
                            },
                            onClose = { browserViewModel.closeTab(tab.id) },
                            onPin = { browserViewModel.togglePin(tab.id) },
                            onDuplicate = { browserViewModel.duplicateTab(tab.id) }
                        )
                    }
                }
            }
        }
    }

    if (confirmCloseAll) {
        GlassDialog(
            onDismiss = { confirmCloseAll = false },
            title = "Close all tabs?",
            message = "All ${state.tabs.size} tabs will be closed. Pinned tabs are kept.",
            confirmText = "Close all",
            onConfirm = {
                browserViewModel.closeAllTabs()
                confirmCloseAll = false
                onClose()
            },
            destructive = true
        )
    }
}

@Composable
private fun TabCard(
    tab: Tab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onDuplicate: () -> Unit
) {
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        tab.isPrivate -> NovaColors.Secondary.copy(alpha = 0.5f)
        else -> NovaTheme.extended.glassBorder
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = borderColor,
                shape = NovaShapeTokens.large
            ),
        shape = NovaShapeTokens.large,
        tint = if (tab.isPrivate) {
            NovaColors.Secondary.copy(alpha = 0.08f)
        } else {
            NovaTheme.extended.glass
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            // Header strip: favicon, title, close.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Dimens.sm, end = Dimens.xs, top = Dimens.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.isPrivate) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "Private tab",
                        tint = NovaColors.Secondary,
                        modifier = Modifier.size(Dimens.iconSmall)
                    )
                } else {
                    FaviconImage(url = tab.url, faviconUrl = tab.favicon, size = Dimens.iconSmall)
                }
                Spacer(Modifier.width(Dimens.xs))
                Text(
                    tab.displayTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close ${tab.displayTitle}",
                    onClick = onClose,
                    size = 32.dp,
                    tint = NovaTheme.extended.textTertiary
                )
            }

            // Preview area.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .padding(Dimens.sm)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                if (tab.isSleeping) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = NovaTheme.extended.textTertiary,
                            modifier = Modifier.size(Dimens.iconLarge)
                        )
                        Text(
                            "Sleeping",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTheme.extended.textTertiary
                        )
                    }
                } else {
                    FaviconImage(url = tab.url, faviconUrl = tab.favicon, size = 40.dp)
                }
            }

            // Footer: host + actions.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Dimens.sm, end = Dimens.xs, bottom = Dimens.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.url.startsWith("https://")) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Secure",
                        tint = NovaColors.Success,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    if (tab.isHome) "Start page" else UrlUtils.displayUrl(tab.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(
                    icon = Icons.Default.PushPin,
                    contentDescription = if (tab.isPinned) "Unpin tab" else "Pin tab",
                    onClick = onPin,
                    size = 32.dp,
                    tint = if (tab.isPinned) MaterialTheme.colorScheme.primary else NovaTheme.extended.textTertiary
                )
                GlassIconButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Duplicate tab",
                    onClick = onDuplicate,
                    size = 32.dp,
                    tint = NovaTheme.extended.textTertiary
                )
            }
        }
    }
}
