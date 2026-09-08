package com.nova.browser.features.passwords.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.database.entities.PasswordEntity
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassFAB
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSearchBar
import com.nova.browser.core.theme.GlassSlider
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassSwitch
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.MonoTextStyle
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaProgressBar
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.copyToClipboard
import com.nova.browser.features.common.ui.FaviconImage
import com.nova.browser.features.passwords.viewmodel.PasswordViewModel

@Composable
fun PasswordManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasswordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val all by viewModel.passwords.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val visible = viewModel.visible(all)

    // Attempt biometric unlock as soon as the screen appears.
    LaunchedEffect(state.biometricRequired) {
        if (state.biometricRequired && !state.unlocked) {
            (context as? FragmentActivity)?.let { viewModel.unlock(it) }
        }
    }

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
                title = "Passwords",
                subtitle = if (state.unlocked) "${all.size} saved logins" else "Locked",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                },
                actions = {
                    if (state.unlocked) {
                        GlassIconButton(
                            icon = Icons.Default.Casino,
                            contentDescription = "Password generator",
                            onClick = { viewModel.showGenerator(true) }
                        )
                        GlassIconButton(
                            icon = Icons.Default.HealthAndSafety,
                            contentDescription = "Run security check",
                            onClick = viewModel::runAudit
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.unlocked) {
                GlassFAB(
                    icon = Icons.Default.Add,
                    contentDescription = "Add a login",
                    onClick = { viewModel.showAddDialog(true) },
                    glowing = false
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.unlocked -> LockedState(
                    unlocking = state.unlocking,
                    biometricAvailable = state.biometricAvailable,
                    onUnlock = { (context as? FragmentActivity)?.let { viewModel.unlock(it) } }
                )

                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Opening your vault")
                }

                else -> Column(Modifier.fillMaxSize()) {
                    GlassSearchBar(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        onSearch = { },
                        placeholder = "Search logins",
                        modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
                    )

                    if (state.weakCount > 0 || state.reusedCount > 0 || state.compromisedCount > 0) {
                        HealthBanner(
                            weak = state.weakCount,
                            reused = state.reusedCount,
                            compromised = state.compromisedCount,
                            running = state.auditRunning,
                            onAudit = viewModel::runAudit
                        )
                    }

                    when {
                        visible.isEmpty() && state.query.isNotBlank() -> EmptyState(
                            icon = Icons.Default.Password,
                            title = "No matches",
                            message = "No saved logins match \"${state.query}\".",
                            modifier = Modifier.fillMaxSize()
                        )

                        visible.isEmpty() -> EmptyState(
                            icon = Icons.Default.Password,
                            title = "No saved passwords",
                            message = "When you sign in to a site, NOVA offers to save the login — encrypted with the Android Keystore.",
                            actionText = "Add one manually",
                            onAction = { viewModel.showAddDialog(true) },
                            modifier = Modifier.fillMaxSize()
                        )

                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Dimens.lg, end = Dimens.lg, bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                        ) {
                            items(visible, key = { it.id }) { entity ->
                                PasswordRow(
                                    entity = entity,
                                    revealed = state.revealedId == entity.id,
                                    revealedValue = state.revealedValue,
                                    onToggleReveal = { viewModel.toggleReveal(entity) },
                                    onCopy = {
                                        viewModel.copyPassword(entity) { plain ->
                                            context.copyToClipboard(plain, sensitive = true)
                                        }
                                    },
                                    onEdit = { viewModel.startEdit(entity) },
                                    onDelete = { viewModel.confirmDelete(entity) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.addDialogVisible || state.editing != null) {
        EditPasswordDialog(
            entity = state.editing,
            strengthOf = { viewModel.strengthOf(it) },
            onDismiss = {
                viewModel.showAddDialog(false)
                viewModel.startEdit(null)
            },
            onSave = viewModel::save,
            onGenerate = { viewModel.showGenerator(true) }
        )
    }

    if (state.generatorVisible) {
        GeneratorDialog(
            generated = state.generated,
            onRegenerate = viewModel::regenerate,
            onDismiss = { viewModel.showGenerator(false) },
            onCopy = {
                context.copyToClipboard(state.generated, sensitive = true)
            }
        )
    }

    state.confirmDelete?.let { entity ->
        GlassDialog(
            onDismiss = { viewModel.confirmDelete(null) },
            title = "Delete this login?",
            message = "${entity.username} at ${entity.domain} will be permanently removed.",
            confirmText = "Delete",
            onConfirm = { viewModel.delete(entity) },
            destructive = true
        )
    }
}

@Composable
private fun LockedState(
    unlocking: Boolean,
    biometricAvailable: Boolean,
    onUnlock: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(Dimens.lg))
        Text(
            "Your vault is locked",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Dimens.sm))
        Text(
            if (biometricAvailable) {
                "Verify with your fingerprint, face or device PIN to see your saved logins."
            } else {
                "This device has no biometrics enrolled. Add a screen lock in system settings for protection."
            },
            style = MaterialTheme.typography.bodySmall,
            color = NovaTheme.extended.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.xl))
        GlassButton(
            text = if (unlocking) "Waiting…" else "Unlock",
            onClick = onUnlock,
            style = GlassButtonStyle.Primary,
            loading = unlocking,
            enabled = !unlocking
        )
    }
}

@Composable
private fun HealthBanner(
    weak: Int,
    reused: Int,
    compromised: Int,
    running: Boolean,
    onAudit: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
        shape = NovaShapeTokens.medium,
        tint = NovaColors.Warning.copy(alpha = 0.10f),
        borderColor = NovaColors.Warning.copy(alpha = 0.3f)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = NovaColors.Warning,
                modifier = Modifier.size(Dimens.icon)
            )
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "Security check",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaColors.Warning
                )
                Text(
                    buildList {
                        if (compromised > 0) add("$compromised compromised")
                        if (weak > 0) add("$weak weak")
                        if (reused > 0) add("$reused reused")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GlassButton(
                text = if (running) "Checking…" else "Re-check",
                onClick = onAudit,
                style = GlassButtonStyle.Ghost,
                enabled = !running,
                loading = running
            )
        }
    }
}

@Composable
private fun PasswordRow(
    entity: PasswordEntity,
    revealed: Boolean,
    revealedValue: String?,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.medium
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaviconImage(
                    url = "https://${entity.domain}",
                    faviconUrl = entity.favicon,
                    size = Dimens.iconLarge
                )
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        entity.domain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        entity.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GlassIconButton(
                    icon = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (revealed) "Hide password" else "Show password",
                    onClick = onToggleReveal,
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassIconButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy password for ${entity.domain}",
                    onClick = onCopy,
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (revealed && revealedValue != null) {
                Spacer(Modifier.height(Dimens.sm))
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NovaShapeTokens.small,
                    tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        revealedValue,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Dimens.md)
                    )
                }
            }

            Spacer(Modifier.height(Dimens.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val strengthColor = when {
                    entity.strengthScore >= 80 -> NovaColors.Success
                    entity.strengthScore >= 50 -> NovaColors.Warning
                    else -> NovaColors.Error
                }
                NovaProgressBar(
                    progress = entity.strengthScore / 100f,
                    color = strengthColor,
                    height = 3.dp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    when {
                        entity.isCompromised -> "Compromised"
                        entity.isReused -> "Reused"
                        entity.isWeak -> "Weak"
                        else -> "Strong"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = strengthColor
                )
                GlassIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit login for ${entity.domain}",
                    onClick = onEdit,
                    size = 36.dp,
                    tint = NovaTheme.extended.textTertiary
                )
                GlassIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete login for ${entity.domain}",
                    onClick = onDelete,
                    size = 36.dp,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EditPasswordDialog(
    entity: PasswordEntity?,
    strengthOf: (String) -> com.nova.browser.features.passwords.repository.PasswordRepository.Strength,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onGenerate: () -> Unit
) {
    var domain by remember { mutableStateOf(entity?.domain.orEmpty()) }
    var username by remember { mutableStateOf(entity?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(entity?.notes.orEmpty()) }
    val strength = strengthOf(password)

    GlassDialog(
        onDismiss = onDismiss,
        title = if (entity == null) "Add a login" else "Edit login",
        confirmText = "Save",
        onConfirm = { onSave(domain, username, password, notes) },
        content = {
            Column {
                GlassTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Website",
                    placeholder = "example.com"
                )
                Spacer(Modifier.height(Dimens.sm))
                GlassTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Username or email"
                )
                Spacer(Modifier.height(Dimens.sm))
                GlassTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Password",
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                    trailingContent = {
                        GlassIconButton(
                            icon = Icons.Default.Casino,
                            contentDescription = "Generate a strong password",
                            onClick = onGenerate,
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NovaProgressBar(
                            progress = strength.score / 100f,
                            color = if (strength.isWeak) NovaColors.Error else NovaColors.Success,
                            height = 3.dp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Dimens.sm))
                        Text(
                            strength.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (strength.isWeak) NovaColors.Error else NovaColors.Success
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                GlassTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Notes (optional)",
                    singleLine = false,
                    maxLines = 3
                )
            }
        }
    )
}

@Composable
private fun GeneratorDialog(
    generated: String,
    onRegenerate: (Int, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    var length by remember { mutableFloatStateOf(20f) }
    var symbols by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }

    GlassDialog(
        onDismiss = onDismiss,
        title = "Password generator",
        confirmText = "Copy",
        onConfirm = onCopy,
        dismissText = "Close",
        content = {
            Column {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NovaShapeTokens.small,
                    tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        generated.ifBlank { "Tap regenerate" },
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Dimens.md)
                    )
                }
                Spacer(Modifier.height(Dimens.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Length",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        length.toInt().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                GlassSlider(
                    value = length,
                    onValueChange = {
                        length = it
                        onRegenerate(it.toInt(), symbols, digits, upper)
                    },
                    valueRange = 8f..64f,
                    modifier = Modifier.fillMaxWidth()
                )
                ToggleLine("Uppercase letters", upper) {
                    upper = it
                    onRegenerate(length.toInt(), symbols, digits, it)
                }
                ToggleLine("Digits", digits) {
                    digits = it
                    onRegenerate(length.toInt(), symbols, it, upper)
                }
                ToggleLine("Symbols", symbols) {
                    symbols = it
                    onRegenerate(length.toInt(), it, digits, upper)
                }
                Spacer(Modifier.height(Dimens.sm))
                GlassButton(
                    text = "Regenerate",
                    onClick = { onRegenerate(length.toInt(), symbols, digits, upper) },
                    style = GlassButtonStyle.Secondary,
                    fillWidth = true
                )
            }
        }
    )
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        GlassSwitch(checked = checked, onCheckedChange = onChange)
    }
}
