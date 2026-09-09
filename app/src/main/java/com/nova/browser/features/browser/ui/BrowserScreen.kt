package com.nova.browser.features.browser.ui

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.utils.Constants
import com.nova.browser.features.agent.engine.ActionExecutor
import com.nova.browser.features.agent.ui.AgentPanel
import com.nova.browser.features.agent.viewmodel.AgentViewModel
import com.nova.browser.features.ai.engine.AIMode
import com.nova.browser.features.ai.ui.AISidebar
import com.nova.browser.features.ai.viewmodel.AIViewModel
import com.nova.browser.features.browser.viewmodel.BrowserCommand
import com.nova.browser.features.browser.viewmodel.BrowserViewModel
import com.nova.browser.features.media.ScreenshotCapture
import com.nova.browser.features.devtools.ui.DevToolsScreen
import com.nova.browser.features.devtools.viewmodel.DevToolsViewModel
import com.nova.browser.features.downloads.viewmodel.DownloadsViewModel
import com.nova.browser.navigation.Routes
import kotlinx.coroutines.launch

/**
 * The main browsing surface: WebView, address bar, toolbar, AI sidebar,
 * agent console, find bar, menu sheet and every web-originated dialog.
 */
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    aiViewModel: AIViewModel = hiltViewModel(),
    agentViewModel: AgentViewModel = hiltViewModel(),
    devToolsViewModel: DevToolsViewModel = hiltViewModel(),
    downloadsViewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val devToolsState by devToolsViewModel.uiState.collectAsStateWithLifecycle()
    val devToolsRequested by viewModel.devToolsRequested.collectAsStateWithLifecycle()
    val pendingAutomationId by viewModel.pendingAutomationId.collectAsStateWithLifecycle()
    val agentRequested by viewModel.agentRequested.collectAsStateWithLifecycle()
    val trackerBlocker = viewModel.trackerBlocker
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rootView = LocalView.current

    var menuVisible by remember { mutableStateOf(false) }
    var dialogRequest by remember { mutableStateOf<WebDialogRequest?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Other screens can ask for DevTools; honour the request once we're back here.
    LaunchedEffect(devToolsRequested) {
        if (devToolsRequested) {
            devToolsViewModel.show()
            viewModel.consumeDevToolsRequest()
        }
    }

    // Deep links and other screens can ask for the agent panel.
    LaunchedEffect(agentRequested) {
        if (agentRequested) {
            agentViewModel.show()
            viewModel.consumeAgentRequest()
        }
    }

    // The automation studio asks the agent (bound here) to run a saved workflow.
    LaunchedEffect(pendingAutomationId, webViewRef) {
        val id = pendingAutomationId
        if (id != null && webViewRef != null) {
            agentViewModel.runWorkflowById(id)
            viewModel.consumeAutomationRequest()
        }
    }

    /* --------------------------- agent wiring --------------------------- */

    DisposableEffect(webViewRef) {
        val view = webViewRef
        if (view != null) {
            agentViewModel.bind(
                browserBridge = BrowserAgentBridge(
                    viewModel = viewModel,
                    aiViewModel = aiViewModel,
                    downloadsViewModel = downloadsViewModel
                ),
                webViewProvider = { webViewRef }
            )
        }
        onDispose { agentViewModel.unbind() }
    }

    /* ------------------------------ snackbar ------------------------------ */

    LaunchedEffect(Unit) {
        viewModel.commands.collect { command ->
            when (command) {
                is BrowserCommand.Snackbar -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = command.message,
                        actionLabel = command.actionLabel,
                        withDismissAction = command.actionLabel == null
                    )
                }

                is BrowserCommand.Share -> runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, command.text)
                        putExtra(Intent.EXTRA_SUBJECT, state.title)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share page"))
                }.onFailure { viewModel.snackbar("No app can share this") }

                is BrowserCommand.OpenExternal -> runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(command.url)).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    )
                }.onFailure { viewModel.snackbar("No app can open this link") }

                BrowserCommand.CaptureScreenshot, BrowserCommand.CaptureFullPage -> {
                    val view = webViewRef
                    if (view == null) {
                        viewModel.snackbar("Nothing to capture")
                    } else {
                        val fullPage = command == BrowserCommand.CaptureFullPage
                        scope.launch {
                            val path = ScreenshotCapture.capture(context, view, fullPage)
                            viewModel.snackbar(
                                if (path != null) "Screenshot saved to Pictures/NOVA"
                                else "Couldn't capture the screenshot"
                            )
                        }
                    }
                }

                else -> Unit // handled by WebViewContainer
            }
        }
    }

    /* ------------------------------ back nav ------------------------------ */

    BackHandler(enabled = true) {
        when {
            fullscreenView != null -> fullscreenCallback?.onCustomViewHidden()
            devToolsState.visible -> devToolsViewModel.hide()
            aiViewModel.uiState.value.visible -> aiViewModel.hide()
            agentViewModel.uiState.value.visible -> agentViewModel.hide()
            state.findInPageVisible -> viewModel.hideFindInPage()
            state.addressBarFocused -> viewModel.onAddressBarFocus(false)
            state.canGoBack -> viewModel.goBack()
            else -> (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (!state.fullscreen) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        AddressBar(
                            state = state,
                            onNavigate = viewModel::navigate,
                            onQueryChange = viewModel::onAddressBarQueryChange,
                            onFocusChange = viewModel::onAddressBarFocus,
                            onReload = viewModel::reload,
                            onStop = viewModel::stopLoading,
                            onShieldClick = { onNavigate(Routes.PRIVACY) },
                            modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                        )
                        PageLoadingIndicator(
                            progress = state.progress,
                            isLoading = state.isLoading
                        )
                        if (state.findInPageVisible) {
                            FindInPageBar(
                                query = state.findQuery,
                                matches = state.findMatches,
                                activeMatch = state.findActiveMatch,
                                onQueryChange = viewModel::onFindQueryChange,
                                onNext = { viewModel.findNext(true) },
                                onPrevious = { viewModel.findNext(false) },
                                onClose = viewModel::hideFindInPage,
                                modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    WebViewContainer(
                        viewModel = viewModel,
                        trackerBlocker = trackerBlocker,
                        modifier = Modifier.fillMaxSize(),
                        onDialog = { dialogRequest = it },
                        onConsoleMessage = { level, message, source, line ->
                            devToolsViewModel.onConsoleMessage(level, message, source, line)
                        },
                        onNetworkRequest = { url, method, type ->
                            devToolsViewModel.onNetworkRequest(url, method, type)
                        },
                        onFullscreen = { view, callback ->
                            fullscreenView = view
                            fullscreenCallback = callback
                            viewModel.setFullscreen(view != null)
                        },
                        onDownload = { url, userAgent, disposition, mimeType, size ->
                            downloadsViewModel.enqueue(url, userAgent, disposition, mimeType, size)
                            viewModel.snackbar("Download started")
                        },
                        onPageContext = { json ->
                            aiViewModel.updatePageContext(json, state.url, state.title)
                        },
                        onWebViewReady = { webViewRef = it }
                    )

                    if (state.addressBarFocused && state.suggestions.isNotEmpty()) {
                        SearchSuggestions(
                            suggestions = state.suggestions,
                            onSelect = { suggestion ->
                                viewModel.onAddressBarFocus(false)
                                if (suggestion.url.startsWith("nova://ai")) {
                                    aiViewModel.show(AIMode.CHAT)
                                    aiViewModel.send(state.addressBarQuery)
                                } else {
                                    viewModel.navigate(suggestion.url)
                                }
                            },
                            onFill = viewModel::onAddressBarQueryChange,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = Dimens.sm)
                        )
                    }
                }

                if (!state.fullscreen) {
                    BrowserToolbar(
                        canGoBack = state.canGoBack,
                        canGoForward = state.canGoForward,
                        tabCount = state.tabCount,
                        isPrivate = state.isPrivate,
                        onBack = viewModel::goBack,
                        onForward = viewModel::goForward,
                        onHome = viewModel::goHome,
                        onTabs = { onNavigate(Routes.TABS) },
                        onMenu = { menuVisible = true },
                        onAi = { aiViewModel.toggle() },
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            }

            // Fullscreen video surface hosted above everything else.
            fullscreenView?.let { view ->
                AndroidView(
                    factory = { ctx ->
                        android.widget.FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { container ->
                        if (view.parent !== container) {
                            (view.parent as? ViewGroup)?.removeView(view)
                            container.removeAllViews()
                            container.addView(view)
                        }
                    },
                    onRelease = { container -> container.removeAllViews() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            AISidebar(
                viewModel = aiViewModel,
                onOpenSettings = { onNavigate(Routes.SETTINGS) },
                onOpenUrl = { url ->
                    aiViewModel.hide()
                    viewModel.navigate(url)
                }
            )

            AgentPanel(viewModel = agentViewModel)

            if (devToolsState.visible) {
                DevToolsScreen(
                    browserViewModel = viewModel,
                    onBack = devToolsViewModel::hide,
                    viewModel = devToolsViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (menuVisible) {
        BrowserMenuSheet(
            state = state,
            onDismiss = { menuVisible = false },
            onAction = { action ->
                menuVisible = false
                handleMenuAction(
                    action = action,
                    viewModel = viewModel,
                    aiViewModel = aiViewModel,
                    agentViewModel = agentViewModel,
                    devToolsViewModel = devToolsViewModel,
                    onNavigate = onNavigate
                )
            }
        )
    }

    dialogRequest?.let { request ->
        WebDialog(
            request = request,
            onDismiss = { dialogRequest = null }
        )
    }
}

private fun handleMenuAction(
    action: MenuAction,
    viewModel: BrowserViewModel,
    aiViewModel: AIViewModel,
    agentViewModel: AgentViewModel,
    devToolsViewModel: DevToolsViewModel,
    onNavigate: (String) -> Unit
) {
    when (action) {
        MenuAction.NewTab -> viewModel.newTab()
        MenuAction.NewPrivateTab -> viewModel.newTab(isPrivate = true)
        MenuAction.Bookmark -> viewModel.toggleBookmark()
        MenuAction.Bookmarks -> onNavigate(Routes.BOOKMARKS)
        MenuAction.History -> onNavigate(Routes.HISTORY)
        MenuAction.Downloads -> onNavigate(Routes.DOWNLOADS)
        MenuAction.FindInPage -> viewModel.showFindInPage()
        MenuAction.ReaderMode -> viewModel.toggleReaderMode()
        MenuAction.DesktopMode -> viewModel.toggleDesktopMode()
        MenuAction.Screenshot -> viewModel.takeScreenshot(false)
        MenuAction.FullPageScreenshot -> viewModel.takeScreenshot(true)
        MenuAction.Share -> viewModel.sharePage()
        MenuAction.Print -> viewModel.printPage()
        MenuAction.SavePage -> viewModel.savePage()
        MenuAction.OpenExternal -> viewModel.openExternally()
        MenuAction.Translate -> {
            aiViewModel.show(AIMode.TRANSLATE)
            aiViewModel.translatePage("English")
        }
        MenuAction.Summarize -> {
            aiViewModel.show(AIMode.SUMMARIZE)
            aiViewModel.summarizePage()
        }
        MenuAction.Agent -> agentViewModel.show()
        MenuAction.DevTools -> devToolsViewModel.show()
        MenuAction.CodeWorkspace -> onNavigate(Routes.CODE)
        MenuAction.Automation -> onNavigate(Routes.AUTOMATION)
        MenuAction.Passwords -> onNavigate(Routes.PASSWORDS)
        MenuAction.Privacy -> onNavigate(Routes.PRIVACY)
        MenuAction.Memory -> onNavigate(Routes.MEMORY)
        MenuAction.Notes -> onNavigate(Routes.NOTES)
        MenuAction.Workspaces -> onNavigate(Routes.WORKSPACES)
        MenuAction.Settings -> onNavigate(Routes.SETTINGS)
    }
}

/** Adapts the browser + AI layers to what the agent executor needs. */
private class BrowserAgentBridge(
    private val viewModel: BrowserViewModel,
    private val aiViewModel: AIViewModel,
    private val downloadsViewModel: DownloadsViewModel
) : ActionExecutor.BrowserBridge {

    override suspend fun navigate(url: String) = viewModel.navigate(url)

    override suspend fun search(query: String) = viewModel.navigate(query)

    override suspend fun goBack() = viewModel.goBack()

    override suspend fun goForward() = viewModel.goForward()

    override suspend fun newTab(url: String?) =
        viewModel.newTab(url ?: Constants.HOME_URL)

    override suspend fun switchTab(indexOrTitle: String) {
        val tabs = viewModel.uiState.value.tabs
        val index = indexOrTitle.toIntOrNull()
        val target = when {
            index != null -> tabs.getOrNull(index - 1)
            else -> tabs.firstOrNull {
                it.title.contains(indexOrTitle, true) || it.url.contains(indexOrTitle, true)
            }
        }
        target?.let { viewModel.selectTab(it.id) }
    }

    override suspend fun closeTab() {
        viewModel.uiState.value.activeTabId?.let { viewModel.closeTab(it) }
    }

    override suspend fun download(url: String) {
        downloadsViewModel.enqueue(url, null, null, null, 0L)
    }

    override suspend fun screenshot(): String? {
        viewModel.takeScreenshot(false)
        return "screenshot requested"
    }

    override suspend fun saveNote(title: String, content: String) {
        aiViewModel.saveLastAsNote()
    }

    override suspend fun askAi(instruction: String, pageText: String): String =
        aiViewModel.completeForAgent(instruction, pageText)
}
