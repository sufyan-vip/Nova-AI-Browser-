package com.nova.browser.features.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.features.agent.engine.AgentStep
import com.nova.browser.features.agent.engine.SafetyChecker

/**
 * Hard gate for risky agent actions. The user must explicitly approve;
 * dismissing counts as a decline (spec 09 → CONFIRMATION DIALOGS).
 */
@Composable
fun AgentConfirmDialog(
    step: AgentStep,
    risk: SafetyChecker.Risk,
    explanation: String,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val accent = when (risk) {
        SafetyChecker.Risk.DESTRUCTIVE, SafetyChecker.Risk.BLOCKED -> NovaColors.Error
        SafetyChecker.Risk.CAUTION -> NovaColors.Warning
        SafetyChecker.Risk.SAFE -> NovaColors.Primary
    }
    val icon = if (risk == SafetyChecker.Risk.DESTRUCTIVE) Icons.Default.Warning else Icons.Default.GppMaybe

    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = NovaShapeTokens.large,
            tint = MaterialTheme.colorScheme.surface,
            borderColor = accent.copy(alpha = 0.4f)
        ) {
            Column(Modifier.padding(Dimens.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(Dimens.md))
                    Text(
                        when (risk) {
                            SafetyChecker.Risk.DESTRUCTIVE -> "Confirm a sensitive action"
                            SafetyChecker.Risk.CAUTION -> "Confirm this step"
                            else -> "Approve step"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(Dimens.lg))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NovaShapeTokens.medium,
                    tint = accent.copy(alpha = 0.08f)
                ) {
                    Column(Modifier.padding(Dimens.md)) {
                        Text(
                            "Step ${step.index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTheme.extended.textTertiary
                        )
                        Text(
                            step.action.describe(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        step.action.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                            Spacer(Modifier.height(Dimens.xs))
                            Text(
                                reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.md))
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent
                )

                Spacer(Modifier.height(Dimens.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    GlassButton(
                        text = "Don't do it",
                        onClick = onDecline,
                        style = GlassButtonStyle.Ghost,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Allow",
                        onClick = onApprove,
                        style = if (risk == SafetyChecker.Risk.DESTRUCTIVE) {
                            GlassButtonStyle.Danger
                        } else {
                            GlassButtonStyle.Primary
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
