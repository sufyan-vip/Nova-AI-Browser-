package com.nova.browser.features.settings.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.NovaDatabase
import com.nova.browser.core.security.SecureStorage
import com.nova.browser.features.ai.engine.AIModel
import com.nova.browser.features.ai.repository.AIRepository
import com.nova.browser.features.settings.repository.NovaSettings
import com.nova.browser.features.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApiKeyState(
    val geminiMasked: String = "Not set",
    val openRouterMasked: String = "Not set",
    val geminiSet: Boolean = false,
    val openRouterSet: Boolean = false
)

data class SettingsUiState(
    val keys: ApiKeyState = ApiKeyState(),
    val models: List<AIModel> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val keyDialogProvider: String? = null,
    val resetDialogVisible: Boolean = false,
    val clearDataDialogVisible: Boolean = false,
    val verifying: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val secureStorage: SecureStorage,
    private val aiRepository: AIRepository,
    private val database: NovaDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val settings: StateFlow<NovaSettings> = settingsRepository.settings
        .catch { emit(NovaSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NovaSettings())

    init {
        refreshKeys()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(models = aiRepository.availableModels())
        }
    }

    private fun refreshKeys() {
        _uiState.value = _uiState.value.copy(
            keys = ApiKeyState(
                geminiMasked = secureStorage.maskedKey(secureStorage.geminiKey),
                openRouterMasked = secureStorage.maskedKey(secureStorage.openRouterKey),
                geminiSet = secureStorage.geminiKey.isNotBlank(),
                openRouterSet = secureStorage.openRouterKey.isNotBlank()
            )
        )
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun showKeyDialog(provider: String?) {
        _uiState.value = _uiState.value.copy(keyDialogProvider = provider)
    }

    fun showResetDialog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(resetDialogVisible = visible)
    }

    fun showClearDataDialog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(clearDataDialogVisible = visible)
    }

    /** Stores an API key in the Keystore-backed encrypted store. */
    fun saveApiKey(provider: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(keyDialogProvider = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(verifying = true)
            try {
                when (provider.uppercase()) {
                    "GEMINI" -> secureStorage.geminiKey = trimmed
                    "OPENROUTER" -> secureStorage.openRouterKey = trimmed
                }
                refreshKeys()
                val models = aiRepository.availableModels()
                _uiState.value = _uiState.value.copy(
                    models = models,
                    verifying = false,
                    keyDialogProvider = null,
                    message = "API key saved and encrypted"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    verifying = false,
                    keyDialogProvider = null,
                    error = "Couldn't save that key securely"
                )
            }
        }
    }

    fun removeApiKey(provider: String) {
        when (provider.uppercase()) {
            "GEMINI" -> secureStorage.geminiKey = ""
            "OPENROUTER" -> secureStorage.openRouterKey = ""
        }
        refreshKeys()
        _uiState.value = _uiState.value.copy(message = "API key removed")
    }

    /* ------------------------- preference mutators ------------------------- */

    fun setSearchEngine(value: String) = launchEdit { settingsRepository.setSearchEngine(value) }
    fun setHomepage(value: String) = launchEdit { settingsRepository.setHomepage(value) }
    fun setRestoreTabs(value: Boolean) = launchEdit { settingsRepository.setRestoreTabs(value) }
    fun setOpenLinksInNewTab(value: Boolean) = launchEdit { settingsRepository.setOpenLinksInNewTab(value) }
    fun setConfirmCloseTabs(value: Boolean) = launchEdit { settingsRepository.setConfirmCloseTabs(value) }
    fun setAskBeforeDownloading(value: Boolean) = launchEdit { settingsRepository.setAskBeforeDownloading(value) }

    fun setThemeMode(value: String) = launchEdit { settingsRepository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = launchEdit { settingsRepository.setDynamicColor(value) }
    fun setBottomToolbar(value: Boolean) = launchEdit { settingsRepository.setBottomToolbar(value) }
    fun setReduceMotion(value: Boolean) = launchEdit { settingsRepository.setReduceMotion(value) }
    fun setTextZoom(value: Int) = launchEdit { settingsRepository.setTextZoom(value) }
    fun setShowTabCount(value: Boolean) = launchEdit { settingsRepository.setShowTabCount(value) }

    fun setBlockTrackers(value: Boolean) = launchEdit { settingsRepository.setBlockTrackers(value) }
    fun setBlockAds(value: Boolean) = launchEdit { settingsRepository.setBlockAds(value) }
    fun setBlockThirdPartyCookies(value: Boolean) = launchEdit { settingsRepository.setBlockThirdPartyCookies(value) }
    fun setFingerprintProtection(value: Boolean) = launchEdit { settingsRepository.setFingerprintProtection(value) }
    fun setHttpsOnly(value: Boolean) = launchEdit { settingsRepository.setHttpsOnly(value) }
    fun setDoNotTrack(value: Boolean) = launchEdit { settingsRepository.setDoNotTrack(value) }
    fun setGlobalPrivacyControl(value: Boolean) = launchEdit { settingsRepository.setGlobalPrivacyControl(value) }
    fun setStripTrackingParams(value: Boolean) = launchEdit { settingsRepository.setStripTrackingParams(value) }
    fun setClearOnExit(value: Boolean) = launchEdit { settingsRepository.setClearOnExit(value) }
    fun setSafeBrowsing(value: Boolean) = launchEdit { settingsRepository.setSafeBrowsing(value) }
    fun setJavaScriptEnabled(value: Boolean) = launchEdit { settingsRepository.setJavaScriptEnabled(value) }

    fun setAiProvider(value: String) = launchEdit { settingsRepository.setAiProvider(value) }
    fun setGeminiModel(value: String) = launchEdit { settingsRepository.setGeminiModel(value) }
    fun setOpenRouterModel(value: String) = launchEdit { settingsRepository.setOpenRouterModel(value) }
    fun setAiStreaming(value: Boolean) = launchEdit { settingsRepository.setAiStreaming(value) }
    fun setAiMemoryEnabled(value: Boolean) = launchEdit { settingsRepository.setAiMemoryEnabled(value) }
    fun setAiPageContext(value: Boolean) = launchEdit { settingsRepository.setAiPageContext(value) }
    fun setAiCustomInstructions(value: String) = launchEdit { settingsRepository.setAiCustomInstructions(value) }
    fun setAiTemperature(value: Int) = launchEdit { settingsRepository.setAiTemperature(value) }
    fun setAiAutoSummarize(value: Boolean) = launchEdit { settingsRepository.setAiAutoSummarize(value) }
    fun setAgentConfirmDestructive(value: Boolean) = launchEdit { settingsRepository.setAgentConfirmDestructive(value) }
    fun setAgentMaxSteps(value: Int) = launchEdit { settingsRepository.setAgentMaxSteps(value) }

    fun setDownloadThreads(value: Int) = launchEdit { settingsRepository.setDownloadThreads(value) }
    fun setWifiOnlyDownloads(value: Boolean) = launchEdit { settingsRepository.setWifiOnlyDownloads(value) }
    fun setDownloadNotifications(value: Boolean) = launchEdit { settingsRepository.setDownloadNotifications(value) }

    fun setSavePasswords(value: Boolean) = launchEdit { settingsRepository.setSavePasswords(value) }
    fun setBiometricUnlock(value: Boolean) = launchEdit { settingsRepository.setBiometricUnlock(value) }
    fun setAutofillPasswords(value: Boolean) = launchEdit { settingsRepository.setAutofillPasswords(value) }

    fun setBackgroundPlayback(value: Boolean) = launchEdit { settingsRepository.setBackgroundPlayback(value) }
    fun setPictureInPicture(value: Boolean) = launchEdit { settingsRepository.setPictureInPicture(value) }

    private fun launchEdit(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                _uiState.value = _uiState.value.copy(error = "Couldn't save that setting")
            }
        }
    }

    /* ----------------------------- destructive ----------------------------- */

    fun resetAllSettings() {
        viewModelScope.launch {
            runCatching { settingsRepository.resetAll() }
            _uiState.value = _uiState.value.copy(
                resetDialogVisible = false,
                message = "Settings restored to defaults"
            )
        }
    }

    /** Wipes browsing data: history, cookies, cache, tabs. Keeps passwords. */
    fun clearBrowsingData() {
        viewModelScope.launch {
            runCatching {
                database.historyDao().deleteAll()
                database.tabDao().deleteAll()
                database.downloadDao().clearCompleted()
                com.nova.browser.features.browser.web.WebViewFactory.clearAllCookies()
                com.nova.browser.features.browser.web.WebViewFactory.clearCache(context)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    clearDataDialogVisible = false,
                    error = "Some data couldn't be cleared"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                clearDataDialogVisible = false,
                message = "Browsing data cleared"
            )
        }
    }
}
