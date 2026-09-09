package com.nova.browser.features.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.rememberPulse
import com.nova.browser.core.theme.LocalReduceMotion

@Composable
fun BrowserToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    isPrivate: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    onAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.toolbarHeight)
                .padding(horizontal = Dimens.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                onClick = onBack,
                enabled = canGoBack,
                tint = MaterialTheme.colorScheme.onSurface
            )
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Go forward",
                onClick = onForward,
                enabled = canGoForward,
                tint = MaterialTheme.colorScheme.onSurface
            )
            AiOrbButton(onClick = onAi)
            TabCounterButton(count = tabCount, isPrivate = isPrivate, onClick = onTabs)
            GlassIconButton(
                icon = Icons.Default.MoreVert,
                contentDescription = "Browser menu",
                onClick = onMenu,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AiOrbButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse by rememberPulse(0.9f, 1.08f, 2400)
    val scale = if (LocalReduceMotion.current) 1f else pulse
    Box(
        modifier = modifier
            .size(Dimens.touchTarget)
            .semantics { contentDescription = "Open NOVA AI assistant" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp * scale)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(
                            NovaColors.Accent.copy(alpha = 0.36f),
                            NovaColors.Secondary.copy(alpha = 0.14f)
                        )
                    )
                )
                .border(
                    1.dp,
                    NovaColors.Accent.copy(alpha = 0.55f),
                    androidx.compose.foundation.shape.CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NovaColors.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TabCounterButton(
    count: Int,
    isPrivate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isPrivate) NovaColors.Tertiary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(Dimens.touchTarget)
            .semantics { contentDescription = "Open tab switcher, $count tabs open" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.8.dp, accent, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

@Composable
fun QuickActionRow(
    onNewTab: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            icon = Icons.Default.Add,
            contentDescription = "New tab",
            onClick = onNewTab,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GlassIconButton(
            icon = Icons.Default.Home,
            contentDescription = "Go home",
            onClick = onHome,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
