package com.nova.browser.features.browser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nova.browser.core.utils.Constants
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.browser.viewmodel.BrowserCommand
import com.nova.browser.features.browser.viewmodel.BrowserViewModel
import com.nova.browser.features.browser.web.BrowserCallbacks
import com.nova.browser.features.browser.web.ChromeCallbacks
import com.nova.browser.features.browser.web.HomePage
import com.nova.browser.features.browser.web.JsScripts
import com.nova.browser.features.browser.web.NovaJsBridge
import com.nova.browser.features.browser.web.NovaWebChromeClient
import com.nova.browser.features.browser.web.NovaWebViewClient
import com.nova.browser.features.browser.web.WebViewFactory
import com.nova.browser.features.privacy.engine.TrackerBlocker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Dialog requests raised by the page (JS dialogs, permissions, SSL, auth). */
data class WebDialogRequest(
    val type: String,
    val title: String,
    val message: String,
    val defaultValue: String? = null,
    val onResult: (Boolean, String?) -> Unit
)

/**
 * Hosts the actual [WebView] inside Compose, wires every client callback back
 * into [BrowserViewModel], and executes one-shot [BrowserCommand]s.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    viewModel: BrowserViewModel,
    trackerBlocker: TrackerBlocker,
    modifier: Modifier = Modifier,
    onDialog: (WebDialogRequest?) -> Unit,
    onConsoleMessage: (String, String, String, Int) -> Unit,
    onNetworkRequest: (String, String, String) -> Unit,
    onFullscreen: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    onDownload: (url: String, userAgent: String, contentDisposition: String, mimeType: String, size: Long) -> Unit,
    onPageContext: (String) -> Unit,
    onWebViewReady: (WebView) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var swipeRefreshRef by remember { mutableStateOf<SwipeRefreshLayout?>(null) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val uris: Array<Uri>? = try {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } catch (e: Exception) {
            null
        }
        callback.onReceiveValue(uris)
    }

    val browserCallbacks = remember {
        object : BrowserCallbacks {
            override fun onPageStarted(url: String, favicon: Bitmap?) {
                viewModel.onPageStarted(url)
            }

            override fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
                viewModel.onPageFinished(url, title, canGoBack, canGoForward)
                swipeRefreshRef?.isRefreshing = false
                // Extract page context for the AI layer.
                webViewRef?.let { view ->
                    try {
                        view.evaluateJavascript(JsScripts.EXTRACT_CONTENT) { json -> onPageContext(json.orEmpty()) }
                    } catch (e: Exception) {
                        onPageContext("")
                    }
                }
            }

            override fun onProgress(progress: Int) = viewModel.onProgress(progress)

            override fun onTitle(title: String) = viewModel.onTitleChanged(title)

            override fun onFavicon(icon: Bitmap) = Unit

            override fun onError(url: String, code: Int, description: String) {
                viewModel.onError(description)
                swipeRefreshRef?.isRefreshing = false
            }

            override fun onSslError(url: String, error: String, proceed: (Boolean) -> Unit) {
                onDialog(
                    WebDialogRequest(
                        type = "ssl",
                        title = "Connection is not private",
                        message = "$error\n\n$url\n\nProceeding is unsafe if you don't trust this site.",
                        onResult = { confirmed, _ ->
                            proceed(confirmed)
                            onDialog(null)
                        }
                    )
                )
            }

            override fun onHttpAuth(host: String, realm: String, submit: (String?, String?) -> Unit) {
                onDialog(
                    WebDialogRequest(
                        type = "auth",
                        title = "Sign in to $host",
                        message = realm.ifBlank { "This site requires authentication." },
                        onResult = { confirmed, value ->
                            if (confirmed && !value.isNullOrBlank()) {
                                val parts = value.split("\u0000")
                                submit(parts.getOrNull(0), parts.getOrNull(1))
                            } else {
                                submit(null, null)
                            }
                            onDialog(null)
                        }
                    )
                )
            }

            override fun onExternalScheme(url: String): Boolean {
                return try {
                    val intent = if (url.startsWith("intent://")) {
                        android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                    } else {
                        android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                    }
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    viewModel.snackbar("No app can open this link")
                    true
                }
            }

            override fun onTrackerBlocked(domain: String, category: String) {
                viewModel.onTrackerBlocked()
            }

            override fun onNetworkRequest(url: String, method: String, resourceType: String) {
                onNetworkRequest(url, method, resourceType)
            }

            override fun onDownloadRequested(
                url: String,
                userAgent: String,
                contentDisposition: String,
                mimeType: String,
                contentLength: Long
            ) {
                onDownload(url, userAgent, contentDisposition, mimeType, contentLength)
            }

            override fun onFormResubmission(): Boolean = false
        }
    }

    val chromeCallbacks = remember {
        object : ChromeCallbacks {
            override fun onProgressChanged(progress: Int) = viewModel.onProgress(progress)

            override fun onTitleChanged(title: String) = viewModel.onTitleChanged(title)

            override fun onIconChanged(icon: Bitmap) = Unit

            override fun onConsoleMessage(level: String, message: String, source: String, line: Int) {
                onConsoleMessage(level, message, source, line)
            }

            override fun onJsDialog(
                type: String,
                message: String,
                defaultValue: String?,
                respond: (Boolean, String?) -> Unit
            ) {
                onDialog(
                    WebDialogRequest(
                        type = type,
                        title = when (type) {
                            "alert" -> "This page says"
                            "confirm" -> "Confirm"
                            else -> "Enter a value"
                        },
                        message = message,
                        defaultValue = defaultValue,
                        onResult = { confirmed, value ->
                            respond(confirmed, value)
                            onDialog(null)
                        }
                    )
                )
            }

            override fun onGeolocationRequest(origin: String, grant: (Boolean) -> Unit) {
                onDialog(
                    WebDialogRequest(
                        type = "permission",
                        title = "Share your location?",
                        message = "$origin wants to know your location.",
                        onResult = { confirmed, _ ->
                            grant(confirmed)
                            onDialog(null)
                        }
                    )
                )
            }

            override fun onPermissionRequest(resources: List<String>, grant: (Boolean) -> Unit) {
                val readable = resources.joinToString(", ") { resource ->
                    when {
                        resource.contains("VIDEO") -> "camera"
                        resource.contains("AUDIO") -> "microphone"
                        resource.contains("MIDI") -> "MIDI devices"
                        else -> "device features"
                    }
                }
                onDialog(
                    WebDialogRequest(
                        type = "permission",
                        title = "Allow access?",
                        message = "This site is requesting access to your $readable.",
                        onResult = { confirmed, _ ->
                            grant(confirmed)
                            onDialog(null)
                        }
                    )
                )
            }

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                viewModel.setFullscreen(true)
                onFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                viewModel.setFullscreen(false)
                onFullscreen(null, null)
            }

            override fun onFileChooser(
                callback: ValueCallback<Array<Uri>>,
                acceptTypes: Array<String>,
                allowMultiple: Boolean
            ): Boolean = try {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = acceptTypes.firstOrNull()?.takeIf { it.contains('/') } ?: "*/*"
                    if (acceptTypes.size > 1) putExtra(android.content.Intent.EXTRA_MIME_TYPES, acceptTypes)
                    putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                }
                filePicker.launch(android.content.Intent.createChooser(intent, "Choose a file"))
                true
            } catch (e: Exception) {
                fileChooserCallback = null
                callback.onReceiveValue(null)
                false
            }

            override fun onCreateWindow(isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                // Popups open as a new NOVA tab instead of a nested window.
                return false
            }

            override fun onCloseWindow() {
                state.activeTabId?.let { viewModel.closeTab(it) }
            }
        }
    }

    // Execute one-shot commands from the ViewModel.
    LaunchedEffect(webViewRef) {
        val view = webViewRef ?: return@LaunchedEffect
        viewModel.commands.collectLatest { command ->
            try {
                when (command) {
                    is BrowserCommand.Load -> {
                        if (command.url == Constants.HOME_URL || command.url.isBlank()) {
                            view.loadDataWithBaseURL(
                                Constants.HOME_URL,
                                HomePage.html(),
                                "text/html",
                                "utf-8",
                                null
                            )
                        } else {
                            view.loadUrl(
                                command.url,
                                NovaWebViewClient.extraHeaders(viewModel.webConfig())
                            )
                        }
                    }

                    BrowserCommand.Reload -> view.reload()
                    BrowserCommand.Stop -> view.stopLoading()
                    BrowserCommand.Back -> if (view.canGoBack()) view.goBack()
                    BrowserCommand.Forward -> if (view.canGoForward()) view.goForward()
                    is BrowserCommand.Find -> {
                        if (command.query.isBlank()) view.clearMatches() else view.findAllAsync(command.query)
                    }

                    is BrowserCommand.FindNext -> view.findNext(command.forward)
                    BrowserCommand.ClearFind -> view.clearMatches()
                    is BrowserCommand.RunJs -> view.evaluateJavascript(command.script, null)
                    BrowserCommand.ToggleReader -> view.evaluateJavascript(JsScripts.READER_MODE, null)
                    is BrowserCommand.SetDesktopMode -> {
                        WebViewFactory.apply(
                            view,
                            WebViewFactory.Options(
                                isPrivate = state.isPrivate,
                                javaScriptEnabled = settings.javaScriptEnabled,
                                desktopMode = command.enabled,
                                textZoom = settings.textZoom,
                                blockThirdPartyCookies = settings.blockThirdPartyCookies,
                                safeBrowsing = settings.safeBrowsing
                            )
                        )
                        view.reload()
                    }

                    BrowserCommand.PrintPage -> {
                        val manager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? PrintManager
                        if (manager != null) {
                            val adapter = view.createPrintDocumentAdapter(state.title.ifBlank { "NOVA page" })
                            manager.print(
                                state.title.ifBlank { "NOVA page" },
                                adapter,
                                PrintAttributes.Builder().build()
                            )
                        } else {
                            viewModel.snackbar("Printing is not available on this device")
                        }
                    }

                    BrowserCommand.SavePage -> {
                        val file = java.io.File(
                            context.getExternalFilesDir(null) ?: context.filesDir,
                            "saved/${UrlUtils.fileNameFromUrl(state.url)}-${System.currentTimeMillis()}.mhtml"
                        )
                        file.parentFile?.mkdirs()
                        view.saveWebArchive(file.absolutePath, false) { path ->
                            viewModel.snackbar(if (path != null) "Page saved" else "Couldn't save page")
                        }
                    }

                    else -> Unit // handled by BrowserScreen
                }
            } catch (e: Exception) {
                viewModel.snackbar("That action isn't available on this page")
            }
        }
    }

    // Apply settings changes (JS toggle, zoom, cookies) without recreating the WebView.
    LaunchedEffect(settings.javaScriptEnabled, settings.textZoom, settings.blockThirdPartyCookies, state.isPrivate) {
        webViewRef?.let { view ->
            WebViewFactory.apply(
                view,
                WebViewFactory.Options(
                    isPrivate = state.isPrivate,
                    javaScriptEnabled = settings.javaScriptEnabled,
                    desktopMode = state.isDesktopMode,
                    textZoom = settings.textZoom,
                    blockThirdPartyCookies = settings.blockThirdPartyCookies,
                    safeBrowsing = settings.safeBrowsing
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            WebViewFactory.destroy(webViewRef)
            webViewRef = null
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val webView = WebViewFactory.create(
                    ctx,
                    WebViewFactory.Options(
                        isPrivate = state.isPrivate,
                        javaScriptEnabled = settings.javaScriptEnabled,
                        textZoom = settings.textZoom,
                        blockThirdPartyCookies = settings.blockThirdPartyCookies,
                        safeBrowsing = settings.safeBrowsing
                    )
                )
                webView.webViewClient = NovaWebViewClient(
                    callbacks = browserCallbacks,
                    trackerBlocker = trackerBlocker,
                    configProvider = { viewModel.webConfig() }
                )
                webView.webChromeClient = NovaWebChromeClient(chromeCallbacks)
                webView.addJavascriptInterface(
                    NovaJsBridge(
                        onConsoleLine = { level, message -> onConsoleMessage(level, message, "page", 0) }
                    ),
                    NovaJsBridge.NAME
                )
                webView.setDownloadListener { url, userAgent, disposition, mimeType, length ->
                    browserCallbacks.onDownloadRequested(url, userAgent, disposition, mimeType, length)
                }
                webView.setFindListener { activeMatch, matches, isDoneCounting ->
                    if (isDoneCounting) viewModel.onFindResult(activeMatch, matches)
                }

                val refresh = SwipeRefreshLayout(ctx).apply {
                    setColorSchemeColors(android.graphics.Color.parseColor("#4A9EFF"))
                    setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#141929"))
                    addView(
                        webView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    setOnRefreshListener { webView.reload() }
                }

                webViewRef = webView
                swipeRefreshRef = refresh
                onWebViewReady(webView)

                // Load the tab's initial URL.
                val initial = state.url.ifBlank { Constants.HOME_URL }
                if (initial == Constants.HOME_URL) {
                    webView.loadDataWithBaseURL(Constants.HOME_URL, HomePage.html(), "text/html", "utf-8", null)
                } else {
                    webView.loadUrl(initial, NovaWebViewClient.extraHeaders(viewModel.webConfig()))
                }
                refresh
            },
            update = { refresh ->
                swipeRefreshRef = refresh
                refresh.isEnabled = !state.fullscreen
            }
        )
    }
}
