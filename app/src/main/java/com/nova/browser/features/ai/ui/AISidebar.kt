package com.nova.browser.features.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.EmptyState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassChip
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaProgressBar
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.copyToClipboard
import com.nova.browser.core.utils.toast
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.viewmodel.AIViewModel

@Composable
fun AISidebar(
    viewModel: AIViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val interactionSource = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(state.messages.lastIndex) }
        }
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween(250)) { it } + fadeOut(tween(150)),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(NovaColors.Scrim)
                .clickable(indication = null, interactionSource = interactionSource) { viewModel.hide() }
        ) {
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.94f)
                    .clickable(indication = null, interactionSource = panelInteraction) { },
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    AIHeader(
                        modelLabel = state.selectedModel?.label ?: "No model",
                        contextSummary = state.contextSummary,
                        contextEnabled = state.contextEnabled,
                        tokensToday = state.tokensToday,
                        tokenBudget = state.tokenBudget,
                        onToggleContext = viewModel::setContextEnabled,
                        onModelClick = { viewModel.showModelPicker(true) },
                        onClear = viewModel::clearConversation,
                        onClose = viewModel::hide
                    )

                    AIModeSelector(
                        selected = state.mode,
                        onSelect = viewModel::setMode,
                        modifier = Modifier.padding(vertical = Dimens.xs)
                    )

                    if (state.isGenerating) {
                        NovaProgressBar(
                            progress = 0.35f,
                            modifier = Modifier.padding(horizontal = Dimens.lg),
                            color = NovaColors.Accent
                        )
                    }

                    Box(Modifier.weight(1f)) {
                        when {
                            !state.hasApiKey && state.messages.isEmpty() -> NoApiKeyState(onOpenSettings)
                            state.messages.isEmpty() -> AIWelcome(
                                mode = state.mode,
                                hasPage = !state.pageContext.isEmpty,
                                onQuickAction = { prompt, mode -> viewModel.send(prompt, mode) }
                            )

                            else -> LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = Dimens.lg, end = Dimens.lg, top = Dimens.sm, bottom = Dimens.lg
                                ),
                                verticalArrangement = Arrangement.spacedBy(Dimens.md)
                            ) {
                                items(state.messages, key = { it.id }) { message ->
                                    AIChatMessage(
                                        message = message,
                                        onCopy = {
                                            context.copyToClipboard(message.text)
                                            context.toast("Copied")
                                        },
                                        onRetry = viewModel::retryLast,
                                        onOpenSource = onOpenUrl
                                    )
                                }
                            }
                        }
                    }

                    state.suggestedMemory?.let { memory ->
                        MemorySuggestionBar(
                            memory = memory,
                            onAccept = viewModel::acceptSuggestedMemory,
                            onDismiss = viewModel::dismissSuggestedMemory
                        )
                    }

                    state.error?.let { error ->
                        ErrorBanner(
                            message = error,
                            onDismiss = viewModel::dismissError,
                            onOpenSettings = onOpenSettings
                        )
                    }

                    AIComposer(
                        input = state.input,
                        isGenerating = state.isGenerating,
                        hasMessages = state.messages.isNotEmpty(),
                        onInputChange = viewModel::onInputChange,
                        onSend = { viewModel.send() },
                        onStop = viewModel::stopGeneration,
                        onSaveNote = {
                            viewModel.saveLastAsNote { ok ->
                                context.toast(if (ok) "Saved to notes" else "Nothing to save")
                            }
                        }
                    )
                }
            }

            if (state.modelPickerVisible) {
                AIModelSelector(
                    models = state.models,
                    selected = state.selectedModel,
                    onSelect = viewModel::selectModel,
                    onDismiss = { viewModel.showModelPicker(false) }
                )
            }
        }
    }
}

@Composable
private fun AIHeader(
    modelLabel: String,
    contextSummary: String,
    contextEnabled: Boolean,
    tokensToday: Int,
    tokenBudget: Int,
    onToggleContext: (Boolean) -> Unit,
    onModelClick: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.md, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(NovaColors.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NovaColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Dimens.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    "NOVA AI",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modelLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(onClick = onModelClick)
                    )
                    if (tokenBudget > 0) {
                        Text(
                            " · ${tokensToday / 1000}k/${tokenBudget / 1000}k",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tokensToday >= tokenBudget) NovaColors.Warning else NovaTheme.extended.textTertiary
                        )
                    }
                }
            }
            GlassIconButton(
                icon = Icons.Default.Tune,
                contentDescription = "Choose AI model",
                onClick = onModelClick,
                size = 40.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GlassIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Clear conversation",
                onClick = onClear,
                size = 40.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GlassIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close AI sidebar",
                onClick = onClose,
                size = 40.dp,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (contextSummary.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassChip(
                    text = if (contextEnabled) "Page context: $contextSummary" else "Page context off",
                    selected = contextEnabled,
                    icon = Icons.Default.Bolt,
                    accent = NovaColors.Secondary,
                    onClick = { onToggleContext(!contextEnabled) }
                )
            }
        }
    }
}

@Composable
fun AIModeSelector(
    selected: AIMode,
    onSelect: (AIMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        AIMode.entries.filter { it != AIMode.AGENT }.forEach { mode ->
            GlassChip(
                text = mode.label,
                selected = mode == selected,
                accent = NovaColors.Accent,
                onClick = { onSelect(mode) }
            )
        }
    }
}

@Composable
private fun AIWelcome(
    mode: AIMode,
    hasPage: Boolean,
    onQuickAction: (String, AIMode) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Dimens.lg),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "How can I help?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(
            if (hasPage) "I can read this page, answer questions, summarize, translate or research further."
            else "Ask anything, or open a page and I'll read it with you.",
            style = MaterialTheme.typography.bodyMedium,
            color = NovaTheme.extended.textTertiary
        )
        Spacer(Modifier.height(Dimens.xl))

        val actions = if (hasPage) {
            listOf(
                "Summarize this page" to AIMode.SUMMARIZE,
                "What are the key points?" to AIMode.PAGE_QA,
                "Explain this simply" to AIMode.EXPLAIN,
                "Translate to English" to AIMode.TRANSLATE,
                "Find the main claims and check them" to AIMode.RESEARCH
            )
        } else {
            listOf(
                "Explain a concept to me" to AIMode.EXPLAIN,
                "Help me write an email" to AIMode.WRITE,
                "Write a Kotlin function" to AIMode.CODE,
                "Research a topic with sources" to AIMode.RESEARCH
            )
        }

        actions.forEach { (prompt, actionMode) ->
            GlassButton(
                text = prompt,
                onClick = { onQuickAction(prompt, actionMode) },
                style = GlassButtonStyle.Secondary,
                fillWidth = true,
                modifier = Modifier.padding(bottom = Dimens.sm)
            )
        }
    }
}

@Composable
private fun NoApiKeyState(onOpenSettings: () -> Unit) {
    EmptyState(
        icon = Icons.Default.AutoAwesome,
        title = "Add an API key to unlock AI",
        message = "NOVA works with Google Gemini and OpenRouter. Your key is encrypted with the Android Keystore and never leaves your device except to call the provider.",
        actionText = "Open AI settings",
        onAction = onOpenSettings,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun MemorySuggestionBar(
    memory: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
        shape = NovaShapeTokens.medium,
        tint = NovaColors.Accent.copy(alpha = 0.10f),
        borderColor = NovaColors.Accent.copy(alpha = 0.3f)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Remember this?",
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaColors.Accent
                )
                Text(
                    memory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GlassButton("Save", onAccept, style = GlassButtonStyle.Accent)
            Spacer(Modifier.width(Dimens.xs))
            GlassIconButton(
                Icons.Default.Close,
                "Dismiss memory suggestion",
                onDismiss,
                size = 40.dp
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val needsKey = message.contains("API key", true) || message.contains("key", true)
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.xs),
        shape = NovaShapeTokens.medium,
        tint = NovaColors.Error.copy(alpha = 0.12f),
        borderColor = NovaColors.Error.copy(alpha = 0.35f)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = NovaColors.Error,
                modifier = Modifier.weight(1f)
            )
            if (needsKey) {
                GlassButton("Settings", onOpenSettings, style = GlassButtonStyle.Ghost)
            }
            GlassIconButton(Icons.Default.Close, "Dismiss error", onDismiss, size = 40.dp, tint = NovaColors.Error)
        }
    }
}

@Composable
private fun AIComposer(
    input: String,
    isGenerating: Boolean,
    hasMessages: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSaveNote: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(Dimens.md),
        verticalAlignment = Alignment.Bottom
    ) {
        GlassTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = "Ask NOVA anything…",
            singleLine = false,
            maxLines = 5,
            imeAction = androidx.compose.ui.text.input.ImeAction.Send,
            onImeAction = onSend
        )
        Spacer(Modifier.width(Dimens.sm))
        if (hasMessages && !isGenerating) {
            GlassIconButton(
                icon = Icons.Default.NoteAdd,
                contentDescription = "Save answer as note",
                onClick = onSaveNote,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GlassIconButton(
            icon = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
            contentDescription = if (isGenerating) "Stop generating" else "Send message",
            onClick = if (isGenerating) onStop else onSend,
            tint = if (isGenerating) NovaColors.Error else NovaColors.Accent,
            background = NovaColors.Accent.copy(alpha = if (isGenerating) 0f else 0.14f)
        )
    }
}
