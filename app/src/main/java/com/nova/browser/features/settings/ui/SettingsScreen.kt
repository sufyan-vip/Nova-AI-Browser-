package com.nova.browser.features.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.BuildConfig
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassDialog
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSlider
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassSwitch
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.theme.SectionHeader
import com.nova.browser.core.utils.Constants
import com.nova.browser.core.utils.SearchEngines
import com.nova.browser.features.ai.engine.AIProvider
import com.nova.browser.features.settings.viewmodel.SettingsViewModel
import com.nova.browser.navigation.Routes

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = "Settings",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
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
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            /* ------------------------------- AI ------------------------------- */
            item { SectionHeader("AI") }

            item {
                SettingRow(
                    icon = Icons.Default.Key,
                    title = "Gemini API key",
                    subtitle = state.keys.geminiMasked,
                    onClick = { viewModel.showKeyDialog("GEMINI") },
                    trailing = {
                        if (state.keys.geminiSet) {
                            GlassIconButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Remove Gemini key",
                                onClick = { viewModel.removeApiKey("GEMINI") },
                                size = 40.dp,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }

            item {
                SettingRow(
                    icon = Icons.Default.Key,
                    title = "OpenRouter API key",
                    subtitle = state.keys.openRouterMasked,
                    onClick = { viewModel.showKeyDialog("OPENROUTER") },
                    trailing = {
                        if (state.keys.openRouterSet) {
                            GlassIconButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Remove OpenRouter key",
                                onClick = { viewModel.removeApiKey("OPENROUTER") },
                                size = 40.dp,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }

            item {
                InfoCard(
                    "Keys are encrypted with the Android Keystore and never leave your device " +
                        "except in requests to the provider you chose. Saved passwords are never sent to any AI."
                )
            }

            item {
                ChoiceRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Preferred provider",
                    options = AIProvider.entries.map { it.name },
                    selected = settings.aiProvider,
                    labelOf = { it.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    onSelect = viewModel::setAiProvider
                )
            }

            item {
                val geminiModels = state.models.filter { it.provider == AIProvider.GEMINI }
                if (geminiModels.isNotEmpty()) {
                    ChoiceRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Gemini model",
                        options = geminiModels.map { it.id },
                        selected = settings.geminiModel,
                        labelOf = { id -> geminiModels.firstOrNull { it.id == id }?.label ?: id },
                        onSelect = viewModel::setGeminiModel
                    )
                }
            }

            item {
                val orModels = state.models.filter { it.provider == AIProvider.OPENROUTER }
                if (orModels.isNotEmpty()) {
                    ChoiceRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "OpenRouter model",
                        options = orModels.map { it.id },
                        selected = settings.openRouterModel,
                        labelOf = { id -> orModels.firstOrNull { it.id == id }?.label ?: id },
                        onSelect = viewModel::setOpenRouterModel
                    )
                }
            }

            item {
                SwitchRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Stream responses",
                    subtitle = "Show words as they're generated",
                    checked = settings.aiStreaming,
                    onCheckedChange = viewModel::setAiStreaming
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Read the current page",
                    subtitle = "Let AI use the page you're viewing as context",
                    checked = settings.aiPageContextEnabled,
                    onCheckedChange = viewModel::setAiPageContext
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI memory",
                    subtitle = "Remember your preferences across conversations",
                    checked = settings.aiMemoryEnabled,
                    onCheckedChange = viewModel::setAiMemoryEnabled
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Auto-summarize long pages",
                    subtitle = "Offer a summary when a page is very long",
                    checked = settings.aiAutoSummarize,
                    onCheckedChange = viewModel::setAiAutoSummarize
                )
            }
            item {
                SliderRow(
                    title = "Creativity",
                    subtitle = "Lower is more factual, higher is more imaginative",
                    value = settings.aiTemperature.toFloat(),
                    range = 0f..150f,
                    steps = 14,
                    display = { "${(it / 100f).format2()}" },
                    onValueChange = { viewModel.setAiTemperature(it.toInt()) }
                )
            }
            item {
                TextFieldRow(
                    title = "Custom instructions",
                    placeholder = "e.g. Always answer in British English and keep it short",
                    value = settings.aiCustomInstructions,
                    onValueChange = viewModel::setAiCustomInstructions
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI memory bank",
                    subtitle = "Review and edit what NOVA remembers",
                    onClick = { onNavigate(Routes.MEMORY) },
                    trailing = { NavChevron() }
                )
            }

            /* ------------------------------ Agent ------------------------------ */
            item { SectionHeader("AI Agent") }
            item {
                SwitchRow(
                    icon = Icons.Default.SmartToy,
                    title = "Always confirm risky steps",
                    subtitle = "Payments, deletions and sending messages",
                    checked = settings.agentConfirmDestructive,
                    onCheckedChange = viewModel::setAgentConfirmDestructive
                )
            }
            item {
                SliderRow(
                    title = "Maximum steps per run",
                    subtitle = "Stops runaway automations",
                    value = settings.agentMaxSteps.toFloat(),
                    range = 1f..Constants.MAX_AGENT_STEPS.toFloat(),
                    steps = 0,
                    display = { it.toInt().toString() },
                    onValueChange = { viewModel.setAgentMaxSteps(it.toInt()) }
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.SmartToy,
                    title = "Automation studio",
                    subtitle = "Build and schedule saved workflows",
                    onClick = { onNavigate(Routes.AUTOMATION) },
                    trailing = { NavChevron() }
                )
            }

            /* ------------------------------ Search ----------------------------- */
            item { SectionHeader("Search & startup") }
            item {
                ChoiceRow(
                    icon = Icons.Default.Search,
                    title = "Search engine",
                    options = SearchEngines.all.map { it.name },
                    selected = settings.searchEngine,
                    labelOf = { it },
                    onSelect = viewModel::setSearchEngine
                )
            }
            item {
                TextFieldRow(
                    title = "Homepage",
                    placeholder = Constants.HOME_URL,
                    value = settings.homepage,
                    onValueChange = viewModel::setHomepage,
                    singleLine = true
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Search,
                    title = "Restore tabs on launch",
                    subtitle = "Reopen the tabs you had last time",
                    checked = settings.restoreTabs,
                    onCheckedChange = viewModel::setRestoreTabs
                )
            }
            item {
                SwitchRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open links in a new tab",
                    checked = settings.openLinksInNewTab,
                    onCheckedChange = viewModel::setOpenLinksInNewTab
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Search,
                    title = "Confirm before closing tabs",
                    checked = settings.confirmCloseTabs,
                    onCheckedChange = viewModel::setConfirmCloseTabs
                )
            }

            /* --------------------------- Appearance ---------------------------- */
            item { SectionHeader("Appearance") }
            item {
                ChoiceRow(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    options = listOf("system", "light", "dark"),
                    selected = settings.themeMode,
                    labelOf = { it.replaceFirstChar { c -> c.uppercase() } },
                    onSelect = viewModel::setThemeMode
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Palette,
                    title = "Use wallpaper colours",
                    subtitle = "Material You dynamic colour (Android 12+)",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Palette,
                    title = "Reduce motion",
                    subtitle = "Minimise animations across the app",
                    checked = settings.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Palette,
                    title = "Show tab count badge",
                    checked = settings.showTabCount,
                    onCheckedChange = viewModel::setShowTabCount
                )
            }
            item {
                SliderRow(
                    title = "Text size",
                    subtitle = "Zoom level for web pages",
                    value = settings.textZoom.toFloat(),
                    range = 50f..200f,
                    steps = 5,
                    display = { "${it.toInt()}%" },
                    onValueChange = { viewModel.setTextZoom(it.toInt()) }
                )
            }

            /* ---------------------------- Privacy ------------------------------ */
            item { SectionHeader("Privacy & security") }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Block trackers",
                    subtitle = "Stop cross-site tracking scripts",
                    checked = settings.blockTrackers,
                    onCheckedChange = viewModel::setBlockTrackers
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Block ads",
                    checked = settings.blockAds,
                    onCheckedChange = viewModel::setBlockAds
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Block third-party cookies",
                    checked = settings.blockThirdPartyCookies,
                    onCheckedChange = viewModel::setBlockThirdPartyCookies
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Fingerprint protection",
                    subtitle = "Randomise canvas and hardware signals",
                    checked = settings.fingerprintProtection,
                    onCheckedChange = viewModel::setFingerprintProtection
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "HTTPS-only mode",
                    subtitle = "Upgrade insecure links automatically",
                    checked = settings.httpsOnly,
                    onCheckedChange = viewModel::setHttpsOnly
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Send Do Not Track",
                    checked = settings.doNotTrack,
                    onCheckedChange = viewModel::setDoNotTrack
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Global Privacy Control",
                    subtitle = "Legally binding opt-out signal in some regions",
                    checked = settings.globalPrivacyControl,
                    onCheckedChange = viewModel::setGlobalPrivacyControl
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Strip tracking parameters",
                    subtitle = "Remove utm_, fbclid and similar from URLs",
                    checked = settings.stripTrackingParams,
                    onCheckedChange = viewModel::setStripTrackingParams
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Security,
                    title = "Safe Browsing",
                    subtitle = "Warn about known malicious sites",
                    checked = settings.safeBrowsing,
                    onCheckedChange = viewModel::setSafeBrowsing
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Code,
                    title = "Enable JavaScript",
                    subtitle = "Turning this off breaks most sites",
                    checked = settings.javaScriptEnabled,
                    onCheckedChange = viewModel::setJavaScriptEnabled
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Delete,
                    title = "Clear data when I exit",
                    subtitle = "Wipe cookies and history on app close",
                    checked = settings.clearOnExit,
                    onCheckedChange = viewModel::setClearOnExit
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Security,
                    title = "Privacy dashboard",
                    subtitle = "See what NOVA has blocked",
                    onClick = { onNavigate(Routes.PRIVACY) },
                    trailing = { NavChevron() }
                )
            }

            /* --------------------------- Passwords ----------------------------- */
            item { SectionHeader("Passwords") }
            item {
                SwitchRow(
                    icon = Icons.Default.Password,
                    title = "Offer to save passwords",
                    checked = settings.savePasswords,
                    onCheckedChange = viewModel::setSavePasswords
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Password,
                    title = "Autofill saved logins",
                    checked = settings.autofillPasswords,
                    onCheckedChange = viewModel::setAutofillPasswords
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Password,
                    title = "Require biometrics",
                    subtitle = "Unlock the password vault with fingerprint or face",
                    checked = settings.biometricUnlock,
                    onCheckedChange = viewModel::setBiometricUnlock
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Password,
                    title = "Password manager",
                    subtitle = "View and edit saved logins",
                    onClick = { onNavigate(Routes.PASSWORDS) },
                    trailing = { NavChevron() }
                )
            }

            /* --------------------------- Downloads ----------------------------- */
            item { SectionHeader("Downloads") }
            item {
                SliderRow(
                    title = "Parallel connections",
                    subtitle = "More connections can be faster on good networks",
                    value = settings.downloadThreads.toFloat(),
                    range = 1f..8f,
                    steps = 6,
                    display = { it.toInt().toString() },
                    onValueChange = { viewModel.setDownloadThreads(it.toInt()) }
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Download,
                    title = "Ask where to save",
                    checked = settings.askBeforeDownloading,
                    onCheckedChange = viewModel::setAskBeforeDownloading
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Download,
                    title = "Wi-Fi only",
                    subtitle = "Pause downloads on mobile data",
                    checked = settings.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnlyDownloads
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.Download,
                    title = "Download notifications",
                    checked = settings.downloadNotifications,
                    onCheckedChange = viewModel::setDownloadNotifications
                )
            }

            /* ------------------------------ Media ------------------------------ */
            item { SectionHeader("Media") }
            item {
                SwitchRow(
                    icon = Icons.Default.PlayCircle,
                    title = "Background playback",
                    subtitle = "Keep audio playing when you leave the app",
                    checked = settings.backgroundPlayback,
                    onCheckedChange = viewModel::setBackgroundPlayback
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Default.PlayCircle,
                    title = "Picture-in-picture",
                    subtitle = "Float videos over other apps",
                    checked = settings.pictureInPicture,
                    onCheckedChange = viewModel::setPictureInPicture
                )
            }

            /* ----------------------------- Tools ------------------------------- */
            item { SectionHeader("Developer tools") }
            item {
                SettingRow(
                    icon = Icons.Default.Code,
                    title = "DevTools",
                    subtitle = "Console, network, DOM and storage inspector",
                    onClick = { onNavigate(Routes.DEVTOOLS) },
                    trailing = { NavChevron() }
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Code,
                    title = "Code workspace",
                    subtitle = "Scratchpad, snippets and API tester",
                    onClick = { onNavigate(Routes.CODE) },
                    trailing = { NavChevron() }
                )
            }

            /* ------------------------------ Data ------------------------------- */
            item { SectionHeader("Data") }
            item {
                SettingRow(
                    icon = Icons.Default.Delete,
                    title = "Clear browsing data",
                    subtitle = "History, cookies, cache and open tabs",
                    onClick = { viewModel.showClearDataDialog(true) },
                    titleColor = MaterialTheme.colorScheme.error
                )
            }
            item {
                SettingRow(
                    icon = Icons.Default.Delete,
                    title = "Reset all settings",
                    subtitle = "Restore every option to its default",
                    onClick = { viewModel.showResetDialog(true) },
                    titleColor = MaterialTheme.colorScheme.error
                )
            }

            /* ------------------------------ About ------------------------------ */
            item { SectionHeader("About") }
            item {
                SettingRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "NOVA AI Browser",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = { }
                )
            }
        }
    }

    state.keyDialogProvider?.let { provider ->
        ApiKeyDialog(
            provider = provider,
            verifying = state.verifying,
            onDismiss = { viewModel.showKeyDialog(null) },
            onSave = { key -> viewModel.saveApiKey(provider, key) }
        )
    }

    if (state.resetDialogVisible) {
        GlassDialog(
            onDismiss = { viewModel.showResetDialog(false) },
            title = "Reset all settings?",
            message = "Every preference goes back to its default. Your bookmarks, history and passwords are kept.",
            confirmText = "Reset",
            onConfirm = viewModel::resetAllSettings,
            destructive = true
        )
    }

    if (state.clearDataDialogVisible) {
        GlassDialog(
            onDismiss = { viewModel.showClearDataDialog(false) },
            title = "Clear browsing data?",
            message = "History, cookies, cache and open tabs will be deleted. Bookmarks and saved passwords are kept.",
            confirmText = "Clear data",
            onConfirm = viewModel::clearBrowsingData,
            destructive = true
        )
    }
}

/* ------------------------------- building blocks ------------------------------- */

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = NovaTheme.extended.textTertiary,
            modifier = Modifier.size(Dimens.icon)
        )
        Spacer(Modifier.width(Dimens.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailing = {
            GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    options: List<String>,
    selected: String,
    labelOf: (String) -> String,
    onSelect: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = NovaTheme.extended.textTertiary,
                modifier = Modifier.size(Dimens.icon)
            )
            Spacer(Modifier.width(Dimens.lg))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Dimens.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs),
            modifier = Modifier.padding(start = Dimens.icon + Dimens.lg)
        ) {
            options.forEach { option ->
                GlassChip(
                    text = labelOf(option),
                    selected = option.equals(selected, true),
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit,
    subtitle: String? = null
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary
                    )
                }
            }
            Text(
                display(local),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        GlassSlider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        LaunchedEffect(local) {
            kotlinx.coroutines.delay(250)
            if (local != value) onValueChange(local)
        }
    }
}

@Composable
private fun TextFieldRow(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Dimens.sm))
        GlassTextField(
            value = local,
            onValueChange = { local = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4
        )
        LaunchedEffect(local) {
            kotlinx.coroutines.delay(600)
            if (local != value) onValueChange(local)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
        shape = NovaShapeTokens.medium,
        tint = NovaColors.Primary.copy(alpha = 0.08f),
        borderColor = NovaColors.Primary.copy(alpha = 0.25f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Dimens.md)
        )
    }
}

@Composable
private fun NavChevron() {
    Icon(
        Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        tint = NovaTheme.extended.textTertiary,
        modifier = Modifier.size(Dimens.iconSmall)
    )
}

@Composable
private fun ApiKeyDialog(
    provider: String,
    verifying: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    val label = if (provider == "GEMINI") "Google Gemini" else "OpenRouter"
    val hint = if (provider == "GEMINI") {
        "Get a free key at aistudio.google.com/apikey"
    } else {
        "Get a key at openrouter.ai/keys"
    }

    GlassDialog(
        onDismiss = onDismiss,
        title = "$label API key",
        message = hint,
        confirmText = if (verifying) "Saving…" else "Save",
        onConfirm = { onSave(key) },
        content = {
            GlassTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (provider == "GEMINI") "AIza…" else "sk-or-…",
                isPassword = true,
                keyboardType = KeyboardType.Password
            )
        }
    )
}

private fun Float.format2(): String = String.format(java.util.Locale.US, "%.2f", this)
