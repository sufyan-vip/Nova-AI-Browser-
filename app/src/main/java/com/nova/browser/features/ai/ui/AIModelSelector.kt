package com.nova.browser.features.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.features.ai.engine.AIModel
import com.nova.browser.features.ai.engine.AIProvider
import java.util.Locale

/**
 * Model picker shared by the AI sidebar. Models are grouped by provider and
 * expose their important capabilities before the user commits to a choice.
 */
@Composable
fun AIModelSelector(
    models: List<AIModel>,
    selected: AIModel?,
    onSelect: (AIModel) -> Unit,
    onDismiss: () -> Unit
) {
    GlassDialog(
        onDismiss = onDismiss,
        title = "Choose an AI model",
        message = "The selected model is used for future requests. NOVA may use another configured provider if it needs to recover from a temporary failure.",
        dismissText = "Close",
        content = {
            if (models.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = "No models available",
                    message = "Add a Gemini or OpenRouter API key in Settings, then try again.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                )
            } else {
                val grouped = models.groupBy { it.provider }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    AIProvider.entries.forEach { provider ->
                        val providerModels = grouped[provider].orEmpty()
                        if (providerModels.isNotEmpty()) {
                            item(key = "header-${provider.name}") {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (provider == AIProvider.GEMINI) {
                                        NovaColors.Secondary
                                    } else {
                                        NovaColors.Tertiary
                                    },
                                    modifier = Modifier.padding(
                                        top = Dimens.sm,
                                        start = Dimens.xs,
                                        bottom = Dimens.xs
                                    )
                                )
                            }
                            items(
                                items = providerModels,
                                key = { model -> "${model.provider.name}:${model.id}" }
                            ) { model ->
                                ModelRow(
                                    model = model,
                                    selected = selected?.provider == model.provider && selected.id == model.id,
                                    onClick = { onSelect(model) }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ModelRow(
    model: AIModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = if (model.provider == AIProvider.GEMINI) NovaColors.Secondary else NovaColors.Tertiary
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NovaShapeTokens.medium)
            .clickable(onClick = onClick),
        shape = NovaShapeTokens.medium,
        tint = if (selected) accent.copy(alpha = 0.15f) else NovaTheme.extended.glass,
        borderColor = if (selected) accent.copy(alpha = 0.55f) else NovaTheme.extended.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (model.supportsVision) Icons.Default.Visibility else Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .padding(8.dp)
            )
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val capability = buildList {
                    add(formatContextWindow(model.contextTokens))
                    if (model.fast) add("Fast")
                    if (model.supportsVision) add("Vision")
                }.joinToString(" · ")
                Text(
                    text = capability,
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.extended.textTertiary
                )
                if (model.description.isNotBlank()) {
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(Dimens.sm))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected model",
                    tint = accent,
                    modifier = Modifier.size(Dimens.icon)
                )
            }
        }
    }
}

private fun formatContextWindow(tokens: Int): String = when {
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM context", tokens / 1_000_000f)
    tokens >= 1_000 -> "${tokens / 1_000}K context"
    else -> "$tokens context"
}
