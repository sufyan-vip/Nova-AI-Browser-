package com.nova.browser.features.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme

/**
 * Renders every dialog a web page can raise: alert, confirm, prompt,
 * beforeunload, HTTP auth and SSL warnings — all in the Nova Glass style so
 * pages can't spoof system UI.
 *
 * HTTP-auth results are returned as `username\u0000password` in the value slot,
 * matching the contract in [WebDialogRequest].
 */
@Composable
fun WebDialog(
    request: WebDialogRequest,
    onDismiss: () -> Unit
) {
    var promptValue by remember(request) { mutableStateOf(request.defaultValue.orEmpty()) }
    var username by remember(request) { mutableStateOf("") }
    var password by remember(request) { mutableStateOf("") }

    fun finish(confirmed: Boolean, value: String?) {
        request.onResult(confirmed, value)
        onDismiss()
    }

    val isSsl = request.type == "ssl"
    val accent = if (isSsl) NovaColors.Error else MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = { finish(false, null) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = request.type == "alert"
        )
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = NovaShapeTokens.large,
            tint = MaterialTheme.colorScheme.surface,
            borderColor = accent.copy(alpha = 0.35f)
        ) {
            Column(Modifier.padding(Dimens.lg)) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSsl) NovaColors.Error else MaterialTheme.colorScheme.onSurface
                )

                if (request.message.isNotBlank()) {
                    Spacer(Modifier.height(Dimens.sm))
                    Text(
                        text = request.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (request.type) {
                    "prompt" -> {
                        Spacer(Modifier.height(Dimens.lg))
                        GlassTextField(
                            value = promptValue,
                            onValueChange = { promptValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Your answer"
                        )
                    }

                    "auth" -> {
                        Spacer(Modifier.height(Dimens.lg))
                        GlassTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Username"
                        )
                        Spacer(Modifier.height(Dimens.sm))
                        GlassTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Password",
                            isPassword = true,
                            keyboardType = KeyboardType.Password
                        )
                    }
                }

                if (isSsl) {
                    Spacer(Modifier.height(Dimens.md))
                    Text(
                        text = "Attackers might be trying to steal your information. NOVA strongly recommends going back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary
                    )
                }

                Spacer(Modifier.height(Dimens.xl))

                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    if (request.type != "alert") {
                        GlassButton(
                            text = if (isSsl) "Go back (safe)" else "Cancel",
                            onClick = { finish(false, null) },
                            style = if (isSsl) GlassButtonStyle.Primary else GlassButtonStyle.Ghost,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    GlassButton(
                        text = when (request.type) {
                            "alert" -> "OK"
                            "ssl" -> "Proceed anyway"
                            "auth" -> "Sign in"
                            "beforeunload" -> "Leave page"
                            else -> "OK"
                        },
                        onClick = {
                            when (request.type) {
                                "prompt" -> finish(true, promptValue)
                                "auth" -> finish(true, "$username\u0000$password")
                                else -> finish(true, null)
                            }
                        },
                        style = if (isSsl) GlassButtonStyle.Danger else GlassButtonStyle.Primary,
                        enabled = request.type != "auth" || username.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
