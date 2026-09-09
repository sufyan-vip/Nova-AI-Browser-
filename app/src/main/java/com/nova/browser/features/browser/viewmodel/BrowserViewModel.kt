package com.nova.browser.features.browser.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.utils.Constants
import com.nova.browser.core.utils.SearchEngines
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.browser.repository.BrowserRepository
import com.nova.browser.features.browser.repository.Suggestion
import com.nova.browser.features.browser.web.NovaWebViewClient
import com.nova.browser.features.privacy.engine.TrackerBlocker
import com.nova.browser.features.settings.repository.NovaSettings
import com.nova.browser.features.settings.repository.SettingsRepository
import com.nova.browser.features.tabs.repository.Tab
import com.nova.browser.features.tabs.repository.TabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the browser chrome needs to render. */
data class BrowserUiState(
    val tabs: List<Tab> = emptyList(),
    val activeTabId: String? = null,
    val url: String = Constants.HOME_URL,
    val displayUrl: String = "",
    val title: String = "New Tab",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val isBookmarked: Boolean = false,
    val isPrivate: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isReaderMode: Boolean = false,
    val isHome: Boolean = true,
    val trackersBlocked: Int = 0,
    val addressBarFocused: Boolean = false,
    val addressBarQuery: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    val findInPageVisible: Boolean = false,
    val findQuery: String = "",
    val findMatches: Int = 0,
    val findActiveMatch: Int = 0,
    val error: String? = null,
    val isOffline: Boolean = false,
    val fullscreen: Boolean = false
) {
    val activeTab: Tab? get() = tabs.firstOrNull { it.id == activeTabId }
    val tabCount: Int get() = tabs.size
}

/** One-shot commands sent to the WebView host. */
sealed interface BrowserCommand {
    data class Load(val url: String) : BrowserCommand
    data object Reload : BrowserCommand
    data object Stop : BrowserCommand
    data object Back : BrowserCommand
    data object Forward : BrowserCommand
    data class Find(val query: String) : BrowserCommand
    data class FindNext(val forward: Boolean) : BrowserCommand
    data object ClearFind : BrowserCommand
    data class RunJs(val script: String, val tag: String) : BrowserCommand
    data object ToggleReader : BrowserCommand
    data class SetDesktopMode(val enabled: Boolean) : BrowserCommand
    data object CaptureScreenshot : BrowserCommand
    data object CaptureFullPage : BrowserCommand
    data object PrintPage : BrowserCommand
    data object SavePage : BrowserCommand
    data class Snackbar(val message: String, val actionLabel: String? = null) : BrowserCommand
    data class OpenExternal(val url: String) : BrowserCommand
    data class Share(val text: String) : BrowserCommand
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val browserRepository: BrowserRepository,
    private val tabRepository: TabRepository,
    private val settingsRepository: SettingsRepository,
    val trackerBlocker: TrackerBlocker
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<BrowserCommand>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()

    val settings: StateFlow<NovaSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, NovaSettings())

    private var suggestionJob: Job? = null
    private var restored = false

    init {
        viewModelScope.launch {
            tabRepository.tabs.collect { tabs ->
                val active = tabs.firstOrNull { it.isActive } ?: tabs.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    tabs = tabs,
                    activeTabId = active?.id ?: _uiState.value.activeTabId
                )
            }
        }
        viewModelScope.launch { restoreSession() }
    }

    private suspend fun restoreSession() {
        if (restored) return
        restored = true
        val config = settingsRepository.settings.first()
        if (!config.restoreTabs) {
            runCatching { tabRepository.closeAll(includePinned = false) }
        }
        val tab = tabRepository.ensureAtLeastOne()
        selectTab(tab.id, load = true)
    }

    /* ------------------------------ navigation ------------------------------ */

    fun navigate(input: String) {
        val engine = SearchEngines.byName(settings.value.searchEngine)
        var target = UrlUtils.toUrlOrSearch(input, engine)
        if (settings.value.stripTrackingParams) target = UrlUtils.stripTrackingParams(target)
        if (settings.value.httpsOnly) target = UrlUtils.upgradeToHttps(target)

        _uiState.value = _uiState.value.copy(
            url = target,
            displayUrl = UrlUtils.displayUrl(target),
            addressBarFocused = false,
            addressBarQuery = "",
            suggestions = emptyList(),
            error = null,
            isHome = target == Constants.HOME_URL,
            isSecure = UrlUtils.isSecure(target)
        )
        emit(BrowserCommand.Load(target))
        persistActiveTab(url = target)
    }

    fun goHome() = navigate(settings.value.homepage.ifBlank { Constants.HOME_URL })

    fun reload() = emit(BrowserCommand.Reload)
    fun stopLoading() = emit(BrowserCommand.Stop)
    fun goBack() = emit(BrowserCommand.Back)
    fun goForward() = emit(BrowserCommand.Forward)

    /* --------------------------- WebView feedback --------------------------- */

    fun onPageStarted(url: String) {
        _uiState.value = _uiState.value.copy(
            url = url,
            displayUrl = UrlUtils.displayUrl(url),
            isLoading = true,
            progress = 5,
            error = null,
            isSecure = UrlUtils.isSecure(url),
            isHome = url == Constants.HOME_URL || url.isBlank(),
            isReaderMode = false,
            trackersBlocked = 0
        )
    }

    fun onProgress(progress: Int) {
        _uiState.value = _uiState.value.copy(
            progress = progress,
            isLoading = progress in 1..99
        )
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: UrlUtils.displayUrl(url)
        _uiState.value = _uiState.value.copy(
            url = url,
            displayUrl = UrlUtils.displayUrl(url),
            title = resolvedTitle,
            isLoading = false,
            progress = 100,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            isSecure = UrlUtils.isSecure(url),
            isHome = url == Constants.HOME_URL || url.isBlank(),
            trackersBlocked = trackerBlocker.blockedOnPage(url)
        )
        persistActiveTab(url = url, title = resolvedTitle)
        viewModelScope.launch {
            val private = _uiState.value.isPrivate
            browserRepository.recordVisit(resolvedTitle, url, UrlUtils.faviconUrl(url), private)
            refreshBookmarkState(url)
        }
        viewModelScope.launch {
            delay(400)
            _uiState.value = _uiState.value.copy(progress = 0)
        }
    }

    fun onTitleChanged(title: String) {
        if (title.isBlank()) return
        _uiState.value = _uiState.value.copy(title = title)
        persistActiveTab(title = title)
    }

    fun onError(message: String) {
        _uiState.value = _uiState.value.copy(
            error = message,
            isLoading = false,
            progress = 0
        )
    }

    fun onTrackerBlocked() {
        val url = _uiState.value.url
        _uiState.value = _uiState.value.copy(trackersBlocked = trackerBlocker.blockedOnPage(url))
    }

    fun setOffline(offline: Boolean) {
        _uiState.value = _uiState.value.copy(isOffline = offline)
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(fullscreen = fullscreen)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /* ------------------------------- address bar ------------------------------- */

    fun onAddressBarFocus(focused: Boolean) {
        _uiState.value = _uiState.value.copy(
            addressBarFocused = focused,
            addressBarQuery = if (focused) _uiState.value.url.takeIf { !UrlUtils.isInternal(it) }.orEmpty() else "",
            suggestions = if (focused) _uiState.value.suggestions else emptyList()
        )
    }

    fun onAddressBarQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(addressBarQuery = query)
        suggestionJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(suggestions = emptyList())
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(140)
            val engine = SearchEngines.byName(settings.value.searchEngine)
            val results = browserRepository.suggestions(query, engine)
            _uiState.value = _uiState.value.copy(suggestions = results)
        }
    }

    fun clearAddressBar() {
        _uiState.value = _uiState.value.copy(addressBarQuery = "", suggestions = emptyList())
    }

    /* --------------------------------- tabs --------------------------------- */

    fun newTab(url: String = Constants.HOME_URL, isPrivate: Boolean = false, select: Boolean = true) {
        viewModelScope.launch {
            val tab = tabRepository.create(url = url, isPrivate = isPrivate)
            if (select) {
                _uiState.value = _uiState.value.copy(
                    activeTabId = tab.id,
                    isPrivate = isPrivate,
                    url = url,
                    displayUrl = UrlUtils.displayUrl(url),
                    title = tab.title,
                    isHome = url == Constants.HOME_URL,
                    canGoBack = false,
                    canGoForward = false,
                    trackersBlocked = 0
                )
                emit(BrowserCommand.Load(url))
            }
        }
    }

    fun selectTab(id: String, load: Boolean = true) {
        viewModelScope.launch {
            tabRepository.setActive(id)
            val tab = tabRepository.get(id) ?: return@launch
            _uiState.value = _uiState.value.copy(
                activeTabId = id,
                url = tab.url.ifBlank { Constants.HOME_URL },
                displayUrl = UrlUtils.displayUrl(tab.url),
                title = tab.displayTitle,
                isPrivate = tab.isPrivate,
                isHome = tab.isHome,
                isSecure = UrlUtils.isSecure(tab.url),
                error = null,
                trackersBlocked = trackerBlocker.blockedOnPage(tab.url)
            )
            if (load) emit(BrowserCommand.Load(tab.url.ifBlank { Constants.HOME_URL }))
            refreshBookmarkState(tab.url)
        }
    }

    fun closeTab(id: String) {
        viewModelScope.launch {
            val nextId = tabRepository.close(id)
            if (nextId != null) {
                selectTab(nextId)
            } else if (tabRepository.getAll().isEmpty()) {
                newTab()
            }
        }
    }

    fun closeOtherTabs(keepId: String) {
        viewModelScope.launch { tabRepository.closeOthers(keepId) }
    }

    fun closeAllTabs() {
        viewModelScope.launch {
            tabRepository.closeAll(includePinned = false)
            newTab()
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch { tabRepository.togglePin(id) }
    }

    fun duplicateTab(id: String) {
        viewModelScope.launch {
            tabRepository.duplicate(id)?.let { selectTab(it.id) }
        }
    }

    fun groupTabs(ids: List<String>, name: String) {
        viewModelScope.launch { tabRepository.groupTabs(ids, name) }
    }

    fun ungroupTabs(ids: List<String>) {
        viewModelScope.launch { tabRepository.ungroup(ids) }
    }

    fun reorderTabs(orderedIds: List<String>) {
        viewModelScope.launch { tabRepository.reorder(orderedIds) }
    }

    fun toggleIncognito() {
        val goingPrivate = !_uiState.value.isPrivate
        newTab(isPrivate = goingPrivate)
    }

    private fun persistActiveTab(url: String? = null, title: String? = null) {
        val id = _uiState.value.activeTabId ?: return
        viewModelScope.launch {
            val tab = tabRepository.get(id) ?: return@launch
            tabRepository.save(
                tab.copy(
                    url = url ?: tab.url,
                    title = title ?: tab.title,
                    favicon = UrlUtils.faviconUrl(url ?: tab.url),
                    lastAccessed = System.currentTimeMillis(),
                    isActive = true
                )
            )
        }
    }

    /* ------------------------------- bookmarks ------------------------------- */

    private suspend fun refreshBookmarkState(url: String) {
        val bookmarked = runCatching { browserRepository.isBookmarked(url).first() }.getOrDefault(false)
        _uiState.value = _uiState.value.copy(isBookmarked = bookmarked)
    }

    fun toggleBookmark() {
        val state = _uiState.value
        if (state.url.isBlank() || UrlUtils.isInternal(state.url)) {
            emit(BrowserCommand.Snackbar("This page can't be bookmarked"))
            return
        }
        viewModelScope.launch {
            val added = browserRepository.toggleBookmark(
                state.title,
                state.url,
                UrlUtils.faviconUrl(state.url)
            )
            _uiState.value = _uiState.value.copy(isBookmarked = added)
            emit(BrowserCommand.Snackbar(if (added) "Bookmark saved" else "Bookmark removed"))
        }
    }

    /* ----------------------------- find in page ----------------------------- */

    fun showFindInPage() {
        _uiState.value = _uiState.value.copy(findInPageVisible = true)
    }

    fun hideFindInPage() {
        _uiState.value = _uiState.value.copy(
            findInPageVisible = false,
            findQuery = "",
            findMatches = 0,
            findActiveMatch = 0
        )
        emit(BrowserCommand.ClearFind)
    }

    fun onFindQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(findQuery = query)
        emit(BrowserCommand.Find(query))
    }

    fun findNext(forward: Boolean) = emit(BrowserCommand.FindNext(forward))

    fun onFindResult(activeMatch: Int, matches: Int) {
        _uiState.value = _uiState.value.copy(
            findActiveMatch = if (matches == 0) 0 else activeMatch + 1,
            findMatches = matches
        )
    }

    /* ------------------------------ page tools ------------------------------ */

    fun toggleReaderMode() {
        _uiState.value = _uiState.value.copy(isReaderMode = !_uiState.value.isReaderMode)
        emit(BrowserCommand.ToggleReader)
    }

    fun toggleDesktopMode() {
        val enabled = !_uiState.value.isDesktopMode
        _uiState.value = _uiState.value.copy(isDesktopMode = enabled)
        emit(BrowserCommand.SetDesktopMode(enabled))
    }

    fun sharePage() {
        val state = _uiState.value
        if (state.url.isBlank() || UrlUtils.isInternal(state.url)) {
            emit(BrowserCommand.Snackbar("Nothing to share"))
            return
        }
        emit(BrowserCommand.Share("${state.title}\n${state.url}"))
    }

    fun takeScreenshot(fullPage: Boolean) {
        emit(if (fullPage) BrowserCommand.CaptureFullPage else BrowserCommand.CaptureScreenshot)
    }

    fun printPage() = emit(BrowserCommand.PrintPage)

    fun savePage() = emit(BrowserCommand.SavePage)

    fun openExternally() {
        val url = _uiState.value.url
        if (url.isBlank() || UrlUtils.isInternal(url)) {
            emit(BrowserCommand.Snackbar("Nothing to open"))
            return
        }
        emit(BrowserCommand.OpenExternal(url))
    }

    fun webConfig(): NovaWebViewClient.WebConfig {
        val config = settings.value
        return NovaWebViewClient.WebConfig(
            blockTrackers = config.blockTrackers,
            blockAds = config.blockAds,
            httpsOnly = config.httpsOnly,
            stripTrackingParams = config.stripTrackingParams,
            fingerprintProtection = config.fingerprintProtection,
            doNotTrack = config.doNotTrack,
            globalPrivacyControl = config.globalPrivacyControl,
            isPrivate = _uiState.value.isPrivate,
            devToolsEnabled = true
        )
    }

    /**
     * DevTools needs the live WebView, so it is shown as an overlay on the
     * browser surface rather than as its own destination. Other screens raise
     * this flag and pop back to the browser.
     */
    private val _devToolsRequested = MutableStateFlow(false)
    val devToolsRequested: StateFlow<Boolean> = _devToolsRequested.asStateFlow()

    private val _agentRequested = MutableStateFlow(false)
    val agentRequested: StateFlow<Boolean> = _agentRequested.asStateFlow()

    fun requestAgent() {
        _agentRequested.value = true
    }

    fun consumeAgentRequest() {
        _agentRequested.value = false
    }

    fun requestDevTools() {
        _devToolsRequested.value = true
    }

    fun consumeDevToolsRequest() {
        _devToolsRequested.value = false
    }

    /**
     * Automations run against the live page, so the studio hands the id back to
     * the browser surface, where the agent is bound to the WebView.
     */
    private val _pendingAutomationId = MutableStateFlow<Long?>(null)
    val pendingAutomationId: StateFlow<Long?> = _pendingAutomationId.asStateFlow()

    fun requestAutomation(id: Long) {
        _pendingAutomationId.value = id
    }

    fun consumeAutomationRequest() {
        _pendingAutomationId.value = null
    }

    fun snackbar(message: String) = emit(BrowserCommand.Snackbar(message))

    fun runScript(script: String, tag: String) = emit(BrowserCommand.RunJs(script, tag))

    private fun emit(command: BrowserCommand) {
        viewModelScope.launch { _commands.emit(command) }
    }
}
