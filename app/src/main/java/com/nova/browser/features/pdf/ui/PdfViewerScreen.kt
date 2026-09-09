package com.nova.browser.features.pdf.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.ErrorState
import com.nova.browser.core.theme.GlassButton
import com.nova.browser.core.theme.GlassButtonStyle
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.GlassTextField
import com.nova.browser.core.theme.GlassTopBar
import com.nova.browser.core.theme.NovaLoadingIndicator
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.shareText
import com.nova.browser.features.pdf.viewmodel.PdfViewModel

/** Local PDF reader with an AI helper panel. */
@Composable
fun PdfViewerScreen(
    filePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(filePath) { viewModel.open(filePath) }

    // Keep the page counter in sync with what's on screen.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { viewModel.setCurrentPage(it) }
    }

    LaunchedEffect(state.message, state.error) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val renderWidthPx = remember(configuration.screenWidthDp, state.zoom) {
        with(density) { (configuration.screenWidthDp.dp * state.zoom).toPx().toInt() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopBar(
                title = state.fileName.ifBlank { "PDF" },
                subtitle = if (state.pageCount > 0) {
                    "Page ${state.currentPage + 1} of ${state.pageCount}"
                } else {
                    null
                },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        onClick = onBack
                    )
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Default.ZoomOut,
                        contentDescription = "Zoom out",
                        onClick = { viewModel.setZoom(state.zoom - 0.25f) },
                        enabled = state.zoom > 0.5f
                    )
                    GlassIconButton(
                        icon = Icons.Default.ZoomIn,
                        contentDescription = "Zoom in",
                        onClick = { viewModel.setZoom(state.zoom + 0.25f) },
                        enabled = state.zoom < 3f
                    )
                    GlassIconButton(
                        icon = Icons.Default.AutoAwesome,
                        contentDescription = "Ask NOVA about this PDF",
                        onClick = { viewModel.showAiPanel(!state.aiPanelVisible) },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    NovaLoadingIndicator(label = "Opening document")
                }

                state.error != null && state.pageCount == 0 -> ErrorState(
                    title = "Can't open this PDF",
                    message = state.error,
                    onRetry = { viewModel.open(filePath) },
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.lg),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    items(state.pageCount) { index ->
                        PdfPage(
                            index = index,
                            widthPx = renderWidthPx,
                            render = viewModel::renderPage
                        )
                    }
                }
            }

            if (state.aiPanelVisible) {
                AiPanel(
                    question = state.question,
                    output = state.aiOutput,
                    loading = state.aiLoading,
                    hasApiKey = state.hasApiKey,
                    error = state.error,
                    onQuestionChange = viewModel::onQuestionChange,
                    onAsk = { viewModel.askAi(state.question) },
                    onSummarize = {
                        viewModel.askAi("Summarise what a document with this name is likely to cover, and suggest what to look for.")
                    },
                    onSave = viewModel::saveAiOutputAsNote,
                    onShare = { context.shareText(state.aiOutput, state.fileName) },
                    onDismissError = viewModel::dismissError,
                    onClose = { viewModel.showAiPanel(false) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    index: Int,
    widthPx: Int,
    render: suspend (Int, Int) -> Bitmap?
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index, widthPx) {
        value = render(index, widthPx)
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.small
    ) {
        val image = bitmap
        if (image == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
                contentAlignment = Alignment.Center
            ) {
                NovaLoadingIndicator(label = "Page ${index + 1}")
            }
        } else {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NovaShapeTokens.small)
            )
        }
    }
}

@Composable
private fun AiPanel(
    question: String,
    output: String,
    loading: Boolean,
    hasApiKey: Boolean,
    error: String?,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onSummarize: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismissError: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.md),
        shape = NovaShapeTokens.xLarge,
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(Modifier.padding(Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ask NOVA",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close the AI panel",
                    onClick = onClose,
                    size = 40.dp,
                    tint = NovaTheme.extended.textTertiary
                )
            }

            if (!hasApiKey) {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "Add a Gemini or OpenRouter API key in Settings to use AI here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (error != null) {
                Spacer(Modifier.height(Dimens.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    GlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Dismiss error",
                        onClick = onDismissError,
                        size = 36.dp,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(Dimens.sm))
            when {
                loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    Alignment.Center
                ) {
                    NovaLoadingIndicator(label = "Thinking")
                }

                output.isNotBlank() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(NovaShapeTokens.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        output,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(Dimens.md)
                    )
                }
            }

            Spacer(Modifier.height(Dimens.sm))
            GlassTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Ask something about this document",
                onImeAction = onAsk
            )

            Spacer(Modifier.height(Dimens.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(
                    text = "Ask",
                    onClick = onAsk,
                    style = GlassButtonStyle.Primary,
                    enabled = !loading && question.isNotBlank()
                )
                Spacer(Modifier.width(Dimens.sm))
                GlassButton(
                    text = "Overview",
                    onClick = onSummarize,
                    style = GlassButtonStyle.Secondary,
                    enabled = !loading
                )
                Spacer(Modifier.weight(1f))
                if (output.isNotBlank()) {
                    GlassIconButton(
                        icon = Icons.Default.Save,
                        contentDescription = "Save answer to notes",
                        onClick = onSave,
                        size = 40.dp,
                        tint = NovaTheme.extended.textTertiary
                    )
                    GlassIconButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share answer",
                        onClick = onShare,
                        size = 40.dp,
                        tint = NovaTheme.extended.textTertiary
                    )
                }
            }
        }
    }
}
