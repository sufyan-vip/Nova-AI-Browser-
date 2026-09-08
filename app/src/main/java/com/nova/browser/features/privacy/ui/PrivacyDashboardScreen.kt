package com.nova.browser.features.privacy.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.TrackerStatEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassSwitch
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaProgressBar
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.theme.SectionHeader
import com.nova.browser.core.utils.DateUtils
import com.nova.browser.features.browser.viewmodel.BrowserViewModel
import com.nova.browser.features.privacy.viewmodel.PrivacyViewModel

@Composable
fun PrivacyDashboardScreen(
    browserViewModel: BrowserViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val browserState by browserViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val totalBlocked by viewModel.totalBlocked.collectAsStateWithLifecycle()
    val topTrackers by viewModel.topTrackers.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(browserState.url) {
        viewModel.observeSite(browserState.url)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopBar(
                title = "Privacy",
                subtitle = "Your protection at a glance",
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
                        contentDescription = "Reset blocking statistics",
                        onClick = { confirmClear = true },
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            item {
                ShieldHero(
                    totalBlocked = totalBlocked,
                    sessionBlocked = state.sessionBlocked,
                    pageBlocked = browserState.trackersBlocked
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.lg),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    StatTile(
                        label = "This page",
                        value = browserState.trackersBlocked.toString(),
                        color = NovaColors.Accent,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "This session",
                        value = state.sessionBlocked.toString(),
                        color = NovaColors.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "All time",
                        value = formatCount(totalBlocked),
                        color = NovaColors.Success,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.currentDomain.isNotBlank()) {
                item { SectionHeader("This site") }
                item {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.lg),
                        shape = NovaShapeTokens.medium
                    ) {
                        Column(Modifier.padding(Dimens.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (browserState.isSecure) Icons.Default.Lock else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = if (browserState.isSecure) NovaColors.Success else NovaColors.Warning,
                                    modifier = Modifier.size(Dimens.icon)
                                )
                                Spacer(Modifier.width(Dimens.md))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        state.currentDomain,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (browserState.isSecure) {
                                            "Encrypted connection (HTTPS)"
                                        } else {
                                            "Not encrypted — avoid entering sensitive data"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (browserState.isSecure) NovaColors.Success else NovaColors.Warning
                                    )
                                }
                            }
                            Spacer(Modifier.height(Dimens.md))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Protection on this site",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (state.siteAllowlisted) {
                                            "Blocking is paused here"
                                        } else {
                                            "Trackers are being blocked"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NovaTheme.extended.textTertiary
                                    )
                                }
                                GlassSwitch(
                                    checked = !state.siteAllowlisted,
                                    onCheckedChange = { enabled ->
                                        viewModel.setSiteProtection(state.currentDomain, enabled)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Protections") }
            item {
                ProtectionRow(
                    icon = Icons.Default.Shield,
                    title = "Tracker blocking",
                    enabled = settings.blockTrackers,
                    onToggle = viewModel::setBlockTrackers
                )
            }
            item {
                ProtectionRow(
                    icon = Icons.Default.Shield,
                    title = "Ad blocking",
                    enabled = settings.blockAds,
                    onToggle = viewModel::setBlockAds
                )
            }
            item {
                ProtectionRow(
                    icon = Icons.Default.Cookie,
                    title = "Third-party cookies blocked",
                    enabled = settings.blockThirdPartyCookies,
                    onToggle = viewModel::setBlockThirdPartyCookies
                )
            }
            item {
                ProtectionRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Fingerprint protection",
                    enabled = settings.fingerprintProtection,
                    onToggle = viewModel::setFingerprintProtection
                )
            }
            item {
                ProtectionRow(
                    icon = Icons.Default.Lock,
                    title = "HTTPS-only mode",
                    enabled = settings.httpsOnly,
                    onToggle = viewModel::setHttpsOnly
                )
            }

            item { SectionHeader("Most blocked trackers") }

            if (topTrackers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Shield,
                        title = "Nothing blocked yet",
                        message = "As you browse, the trackers NOVA blocks will be listed here.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
            } else {
                val maxCount = topTrackers.maxOf { it.blockCount }.coerceAtLeast(1)
                items(topTrackers, key = { it.trackerDomain }) { tracker ->
                    TrackerRow(tracker = tracker, maxCount = maxCount)
                }
            }
        }
    }

    if (confirmClear) {
        GlassDialog(
            onDismiss = { confirmClear = false },
            title = "Reset statistics?",
            message = "Blocking counters go back to zero. Your protection settings are unchanged.",
            confirmText = "Reset",
            onConfirm = {
                viewModel.clearStats()
                confirmClear = false
            },
            destructive = true
        )
    }
}

@Composable
private fun ShieldHero(totalBlocked: Int, sessionBlocked: Int, pageBlocked: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(NovaColors.Success.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = NovaColors.Success,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(Dimens.lg))
        Text(
            formatCount(totalBlocked),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "trackers and ads blocked",
            style = MaterialTheme.typography.bodyMedium,
            color = NovaTheme.extended.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = NovaShapeTokens.medium,
        tint = color.copy(alpha = 0.10f),
        borderColor = color.copy(alpha = 0.25f)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = NovaTheme.extended.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProtectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) NovaColors.Success else NovaTheme.extended.textTertiary,
            modifier = Modifier.size(Dimens.icon)
        )
        Spacer(Modifier.width(Dimens.lg))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        GlassSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun TrackerRow(tracker: TrackerStatEntity, maxCount: Int) {
    val categoryColor = when (tracker.category.uppercase()) {
        "ADVERTISING" -> NovaColors.Warning
        "ANALYTICS" -> NovaColors.Primary
        "SOCIAL" -> NovaColors.Secondary
        "FINGERPRINTING" -> NovaColors.Error
        "CRYPTOMINING" -> NovaColors.Error
        else -> NovaColors.Accent
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg),
        shape = NovaShapeTokens.medium
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tracker.trackerDomain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${tracker.category.lowercase().replaceFirstChar { it.uppercase() }} · last ${
                            DateUtils.relative(tracker.lastBlockedAt)
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTheme.extended.textTertiary
                    )
                }
                Text(
                    formatCount(tracker.blockCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = categoryColor
                )
            }
            Spacer(Modifier.height(Dimens.xs))
            NovaProgressBar(
                progress = tracker.blockCount.toFloat() / maxCount,
                color = categoryColor,
                height = 3.dp
            )
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 100_000 / 10f}M"
    count >= 1_000 -> "${count / 100 / 10f}k"
    else -> count.toString()
}
