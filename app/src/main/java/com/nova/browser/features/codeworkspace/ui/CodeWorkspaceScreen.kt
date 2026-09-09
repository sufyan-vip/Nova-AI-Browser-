package com.nova.browser.features.codeworkspace.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.MonoTextStyle
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.copyToClipboard
import com.nova.browser.features.codeworkspace.viewmodel.CodeAction
import com.nova.browser.features.codeworkspace.viewmodel.CodeWorkspaceViewModel

/** Code assistant + REST client (spec phase 9). */
@Composable
fun CodeWorkspaceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodeWorkspaceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                title = "Code workspace",
                subtitle = if (state.selectedTab == 0) "AI code assistant" else "API tester",
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                },
                actions = {
                    if (state.selectedTab == 0) {
                        GlassIconButton(
                            icon = Icons.Default.Save,
                            contentDescription = "Save snippet to notes",
                            onClick = viewModel::saveAsNote
                        )
                    }
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
                    .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                GlassChip(
                    text = "Editor",
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) }
                )
                GlassChip(
                    text = "API tester",
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) }
                )
            }

            if (state.selectedTab == 0) {
                EditorTab(
                    code = state.code,
                    language = state.language,
                    languages = viewModel.languages,
                    instruction = state.instruction,
                    output = state.aiOutput,
                    loading = state.aiLoading,
                    hasApiKey = state.hasApiKey,
                    onCodeChange = viewModel::onCodeChange,
                    onLanguageChange = viewModel::onLanguageChange,
                    onInstructionChange = viewModel::onInstructionChange,
                    onAction = viewModel::runCodeAction,
                    onClearOutput = viewModel::clearOutput,
                    onCopyOutput = { context.copyToClipboard(state.aiOutput) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                ApiTesterTab(
                    state = state,
                    methods = viewModel.methods,
                    onMethodChange = viewModel::onMethodChange,
                    onUrlChange = viewModel::onUrlChange,
                    onHeadersChange = viewModel::onHeadersChange,
                    onBodyChange = viewModel::onRequestBodyChange,
                    onSend = viewModel::sendRequest,
                    onExplain = viewModel::explainResponse,
                    onCopyResponse = { context.copyToClipboard(state.response?.body.orEmpty()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EditorTab(
    code: String,
    language: String,
    languages: List<String>,
    instruction: String,
    output: String,
    loading: Boolean,
    hasApiKey: Boolean,
    onCodeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onInstructionChange: (String) -> Unit,
    onAction: (CodeAction) -> Unit,
    onClearOutput: () -> Unit,
    onCopyOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl)
    ) {
        if (!hasApiKey) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = NovaShapeTokens.medium,
                tint = NovaColors.Warning.copy(alpha = 0.10f),
                borderColor = NovaColors.Warning.copy(alpha = 0.3f)
            ) {
                Text(
                    "Add a Gemini or OpenRouter API key in Settings to use the code assistant. The API tester works without one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Dimens.md)
                )
            }
            Spacer(Modifier.height(Dimens.sm))
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            languages.forEach { option ->
                GlassChip(
                    text = option,
                    selected = option == language,
                    onClick = { onLanguageChange(option) }
                )
            }
        }

        Spacer(Modifier.height(Dimens.sm))
        GlassTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            label = "Code",
            placeholder = "Paste or write code here",
            singleLine = false,
            maxLines = 14,
            textStyle = MonoTextStyle
        )

        Spacer(Modifier.height(Dimens.sm))
        GlassTextField(
            value = instruction,
            onValueChange = onInstructionChange,
            modifier = Modifier.fillMaxWidth(),
            label = "Instruction (used by Generate and Convert)",
            placeholder = "e.g. a debounce function with a cancel method"
        )

        Spacer(Modifier.height(Dimens.md))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            CodeAction.entries.forEach { action ->
                GlassButton(
                    text = action.label,
                    onClick = { onAction(action) },
                    style = if (action == CodeAction.EXPLAIN) {
                        GlassButtonStyle.Primary
                    } else {
                        GlassButtonStyle.Secondary
                    },
                    enabled = !loading
                )
            }
        }

        Spacer(Modifier.height(Dimens.md))
        when {
            loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                Alignment.Center
            ) {
                NovaLoadingIndicator(label = "Thinking about your code")
            }

            output.isBlank() -> EmptyState(
                icon = Icons.Default.Code,
                title = "No output yet",
                message = "Paste code above and pick an action — explain, find bugs, optimise, convert, test or document it.",
                modifier = Modifier.fillMaxWidth()
            )

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AI output",
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaColors.Accent,
                        modifier = Modifier.weight(1f)
                    )
                    GlassIconButton(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copy AI output",
                        onClick = onCopyOutput,
                        size = 40.dp,
                        tint = NovaTheme.extended.textTertiary
                    )
                    GlassIconButton(
                        icon = Icons.Default.DeleteSweep,
                        contentDescription = "Clear AI output",
                        onClick = onClearOutput,
                        size = 40.dp,
                        tint = NovaTheme.extended.textTertiary
                    )
                }
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NovaShapeTokens.medium
                ) {
                    Text(
                        output,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Dimens.md)
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiTesterTab(
    state: com.nova.browser.features.codeworkspace.viewmodel.CodeWorkspaceUiState,
    methods: List<String>,
    onMethodChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onHeadersChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit,
    onExplain: () -> Unit,
    onCopyResponse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl)
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            methods.forEach { method ->
                GlassChip(
                    text = method,
                    selected = method == state.method,
                    onClick = { onMethodChange(method) }
                )
            }
        }

        Spacer(Modifier.height(Dimens.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            GlassTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier.weight(1f),
                label = "URL",
                placeholder = "https://api.example.com/items",
                keyboardType = KeyboardType.Uri,
                onImeAction = onSend
            )
            Spacer(Modifier.width(Dimens.sm))
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send request",
                onClick = onSend,
                enabled = !state.requestLoading && state.url.isNotBlank(),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(Dimens.sm))
        GlassTextField(
            value = state.headers,
            onValueChange = onHeadersChange,
            modifier = Modifier.fillMaxWidth(),
            label = "Headers (one per line)",
            placeholder = "Authorization: Bearer …",
            singleLine = false,
            maxLines = 4,
            textStyle = MonoTextStyle
        )

        if (state.method != "GET" && state.method != "HEAD") {
            Spacer(Modifier.height(Dimens.sm))
            GlassTextField(
                value = state.requestBody,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                label = "Body",
                placeholder = "{ \"name\": \"value\" }",
                singleLine = false,
                maxLines = 8,
                textStyle = MonoTextStyle
            )
        }

        Spacer(Modifier.height(Dimens.md))
        when {
            state.requestLoading -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                Alignment.Center
            ) {
                NovaLoadingIndicator(label = "Sending request")
            }

            state.response == null -> EmptyState(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "No response yet",
                message = "Enter an endpoint and send a request to inspect the status, headers and body.",
                modifier = Modifier.fillMaxWidth()
            )

            else -> {
                val response = state.response
                val statusColor = if (response.isSuccess) NovaColors.Success else NovaColors.Error

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(Dimens.iconSmall)
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        "${response.code} ${response.message}",
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${response.durationMs} ms · ${response.sizeBytes} B",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTheme.extended.textTertiary
                    )
                }

                Spacer(Modifier.height(Dimens.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(
                        text = "Explain with AI",
                        onClick = onExplain,
                        style = GlassButtonStyle.Ghost,
                        icon = Icons.Default.AutoAwesome,
                        enabled = !state.aiLoading,
                        loading = state.aiLoading
                    )
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copy response body",
                        onClick = onCopyResponse,
                        size = 40.dp,
                        tint = NovaTheme.extended.textTertiary
                    )
                }

                if (response.headers.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.sm))
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NovaShapeTokens.small
                    ) {
                        Column(Modifier.padding(Dimens.md)) {
                            response.headers.take(15).forEach { (name, value) ->
                                Text(
                                    "$name: $value",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NovaTheme.extended.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.sm))
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NovaShapeTokens.medium
                ) {
                    Text(
                        response.body.take(40_000).ifBlank { "(empty body)" },
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(Dimens.md)
                    )
                }
            }
        }
    }
}
