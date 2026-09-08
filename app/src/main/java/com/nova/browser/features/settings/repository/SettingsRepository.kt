package com.nova.browser.features.settings.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nova.browser.core.di.settingsDataStore
import com.nova.browser.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Every user-visible preference, with sane defaults. */
data class NovaSettings(
    // General
    val searchEngine: String = "Google",
    val homepage: String = Constants.HOME_URL,
    val restoreTabs: Boolean = true,
    val openLinksInNewTab: Boolean = false,
    val confirmBeforeClosingTabs: Boolean = true,
    val askBeforeDownloading: Boolean = true,
    // Appearance
    val themeMode: String = "dark", // dark | light | system
    val dynamicColor: Boolean = false,
    val bottomToolbar: Boolean = true,
    val reduceMotion: Boolean = false,
    val textZoom: Int = 100,
    val showTabCount: Boolean = true,
    // Privacy
    val blockTrackers: Boolean = true,
    val blockAds: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val fingerprintProtection: Boolean = true,
    val httpsOnly: Boolean = true,
    val doNotTrack: Boolean = true,
    val globalPrivacyControl: Boolean = true,
    val stripTrackingParams: Boolean = true,
    val clearOnExit: Boolean = false,
    val safeBrowsing: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    // AI
    val aiProvider: String = "GEMINI",
    val geminiModel: String = "gemini-2.0-flash",
    val openRouterModel: String = "openai/gpt-4o-mini",
    val aiStreaming: Boolean = true,
    val aiMemoryEnabled: Boolean = true,
    val aiPageContextEnabled: Boolean = true,
    val aiCustomInstructions: String = "",
    val aiTemperature: Int = 70,
    val aiAutoSummarize: Boolean = false,
    val agentConfirmDestructive: Boolean = true,
    val agentConfirmEveryStep: Boolean = false,
    val agentMaxSteps: Int = 25,
    // Downloads
    val downloadThreads: Int = 4,
    val wifiOnlyDownloads: Boolean = false,
    val downloadNotifications: Boolean = true,
    // Passwords
    val savePasswords: Boolean = true,
    val biometricUnlock: Boolean = true,
    val autofillPasswords: Boolean = true,
    // Media
    val backgroundPlayback: Boolean = true,
    val pictureInPicture: Boolean = true,
    // Onboarding
    val onboardingComplete: Boolean = false
) {
    val temperature: Double get() = (aiTemperature / 100.0).coerceIn(0.0, 1.5)
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val searchEngine = stringPreferencesKey("search_engine")
        val homepage = stringPreferencesKey("homepage")
        val restoreTabs = booleanPreferencesKey("restore_tabs")
        val openLinksInNewTab = booleanPreferencesKey("open_links_new_tab")
        val confirmCloseTabs = booleanPreferencesKey("confirm_close_tabs")
        val askBeforeDownloading = booleanPreferencesKey("ask_before_downloading")

        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val bottomToolbar = booleanPreferencesKey("bottom_toolbar")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val textZoom = intPreferencesKey("text_zoom")
        val showTabCount = booleanPreferencesKey("show_tab_count")

        val blockTrackers = booleanPreferencesKey("block_trackers")
        val blockAds = booleanPreferencesKey("block_ads")
        val blockThirdPartyCookies = booleanPreferencesKey("block_third_party_cookies")
        val fingerprintProtection = booleanPreferencesKey("fingerprint_protection")
        val httpsOnly = booleanPreferencesKey("https_only")
        val doNotTrack = booleanPreferencesKey("do_not_track")
        val gpc = booleanPreferencesKey("global_privacy_control")
        val stripTrackingParams = booleanPreferencesKey("strip_tracking_params")
        val clearOnExit = booleanPreferencesKey("clear_on_exit")
        val safeBrowsing = booleanPreferencesKey("safe_browsing")
        val javaScriptEnabled = booleanPreferencesKey("javascript_enabled")

        val aiProvider = stringPreferencesKey("ai_provider")
        val geminiModel = stringPreferencesKey("gemini_model")
        val openRouterModel = stringPreferencesKey("openrouter_model")
        val aiStreaming = booleanPreferencesKey("ai_streaming")
        val aiMemoryEnabled = booleanPreferencesKey("ai_memory_enabled")
        val aiPageContext = booleanPreferencesKey("ai_page_context")
        val aiCustomInstructions = stringPreferencesKey("ai_custom_instructions")
        val aiTemperature = intPreferencesKey("ai_temperature")
        val aiAutoSummarize = booleanPreferencesKey("ai_auto_summarize")
        val agentConfirmDestructive = booleanPreferencesKey("agent_confirm_destructive")
        val agentConfirmEveryStep = booleanPreferencesKey("agent_confirm_every_step")
        val agentMaxSteps = intPreferencesKey("agent_max_steps")

        val downloadThreads = intPreferencesKey("download_threads")
        val wifiOnlyDownloads = booleanPreferencesKey("wifi_only_downloads")
        val downloadNotifications = booleanPreferencesKey("download_notifications")

        val savePasswords = booleanPreferencesKey("save_passwords")
        val biometricUnlock = booleanPreferencesKey("biometric_unlock")
        val autofillPasswords = booleanPreferencesKey("autofill_passwords")

        val backgroundPlayback = booleanPreferencesKey("background_playback")
        val pictureInPicture = booleanPreferencesKey("picture_in_picture")

        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<NovaSettings> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs.toSettings() }

    private fun Preferences.toSettings(): NovaSettings {
        val defaults = NovaSettings()
        return NovaSettings(
            searchEngine = this[Keys.searchEngine] ?: defaults.searchEngine,
            homepage = this[Keys.homepage] ?: defaults.homepage,
            restoreTabs = this[Keys.restoreTabs] ?: defaults.restoreTabs,
            openLinksInNewTab = this[Keys.openLinksInNewTab] ?: defaults.openLinksInNewTab,
            confirmBeforeClosingTabs = this[Keys.confirmCloseTabs] ?: defaults.confirmBeforeClosingTabs,
            askBeforeDownloading = this[Keys.askBeforeDownloading] ?: defaults.askBeforeDownloading,
            themeMode = this[Keys.themeMode] ?: defaults.themeMode,
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            bottomToolbar = this[Keys.bottomToolbar] ?: defaults.bottomToolbar,
            reduceMotion = this[Keys.reduceMotion] ?: defaults.reduceMotion,
            textZoom = this[Keys.textZoom] ?: defaults.textZoom,
            showTabCount = this[Keys.showTabCount] ?: defaults.showTabCount,
            blockTrackers = this[Keys.blockTrackers] ?: defaults.blockTrackers,
            blockAds = this[Keys.blockAds] ?: defaults.blockAds,
            blockThirdPartyCookies = this[Keys.blockThirdPartyCookies] ?: defaults.blockThirdPartyCookies,
            fingerprintProtection = this[Keys.fingerprintProtection] ?: defaults.fingerprintProtection,
            httpsOnly = this[Keys.httpsOnly] ?: defaults.httpsOnly,
            doNotTrack = this[Keys.doNotTrack] ?: defaults.doNotTrack,
            globalPrivacyControl = this[Keys.gpc] ?: defaults.globalPrivacyControl,
            stripTrackingParams = this[Keys.stripTrackingParams] ?: defaults.stripTrackingParams,
            clearOnExit = this[Keys.clearOnExit] ?: defaults.clearOnExit,
            safeBrowsing = this[Keys.safeBrowsing] ?: defaults.safeBrowsing,
            javaScriptEnabled = this[Keys.javaScriptEnabled] ?: defaults.javaScriptEnabled,
            aiProvider = this[Keys.aiProvider] ?: defaults.aiProvider,
            geminiModel = this[Keys.geminiModel] ?: defaults.geminiModel,
            openRouterModel = this[Keys.openRouterModel] ?: defaults.openRouterModel,
            aiStreaming = this[Keys.aiStreaming] ?: defaults.aiStreaming,
            aiMemoryEnabled = this[Keys.aiMemoryEnabled] ?: defaults.aiMemoryEnabled,
            aiPageContextEnabled = this[Keys.aiPageContext] ?: defaults.aiPageContextEnabled,
            aiCustomInstructions = this[Keys.aiCustomInstructions] ?: defaults.aiCustomInstructions,
            aiTemperature = this[Keys.aiTemperature] ?: defaults.aiTemperature,
            aiAutoSummarize = this[Keys.aiAutoSummarize] ?: defaults.aiAutoSummarize,
            agentConfirmDestructive = this[Keys.agentConfirmDestructive] ?: defaults.agentConfirmDestructive,
            agentConfirmEveryStep = this[Keys.agentConfirmEveryStep] ?: defaults.agentConfirmEveryStep,
            agentMaxSteps = this[Keys.agentMaxSteps] ?: defaults.agentMaxSteps,
            downloadThreads = this[Keys.downloadThreads] ?: defaults.downloadThreads,
            wifiOnlyDownloads = this[Keys.wifiOnlyDownloads] ?: defaults.wifiOnlyDownloads,
            downloadNotifications = this[Keys.downloadNotifications] ?: defaults.downloadNotifications,
            savePasswords = this[Keys.savePasswords] ?: defaults.savePasswords,
            biometricUnlock = this[Keys.biometricUnlock] ?: defaults.biometricUnlock,
            autofillPasswords = this[Keys.autofillPasswords] ?: defaults.autofillPasswords,
            backgroundPlayback = this[Keys.backgroundPlayback] ?: defaults.backgroundPlayback,
            pictureInPicture = this[Keys.pictureInPicture] ?: defaults.pictureInPicture,
            onboardingComplete = this[Keys.onboardingComplete] ?: defaults.onboardingComplete
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.settingsDataStore.edit(block)
        } catch (e: Exception) {
            // Preference writes must never crash the UI.
        }
    }

    suspend fun setSearchEngine(value: String) = edit { it[Keys.searchEngine] = value }
    suspend fun setHomepage(value: String) = edit { it[Keys.homepage] = value }
    suspend fun setRestoreTabs(value: Boolean) = edit { it[Keys.restoreTabs] = value }
    suspend fun setOpenLinksInNewTab(value: Boolean) = edit { it[Keys.openLinksInNewTab] = value }
    suspend fun setConfirmCloseTabs(value: Boolean) = edit { it[Keys.confirmCloseTabs] = value }
    suspend fun setAskBeforeDownloading(value: Boolean) = edit { it[Keys.askBeforeDownloading] = value }

    suspend fun setThemeMode(value: String) = edit { it[Keys.themeMode] = value }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamicColor] = value }
    suspend fun setBottomToolbar(value: Boolean) = edit { it[Keys.bottomToolbar] = value }
    suspend fun setReduceMotion(value: Boolean) = edit { it[Keys.reduceMotion] = value }
    suspend fun setTextZoom(value: Int) = edit { it[Keys.textZoom] = value.coerceIn(50, 200) }
    suspend fun setShowTabCount(value: Boolean) = edit { it[Keys.showTabCount] = value }

    suspend fun setBlockTrackers(value: Boolean) = edit { it[Keys.blockTrackers] = value }
    suspend fun setBlockAds(value: Boolean) = edit { it[Keys.blockAds] = value }
    suspend fun setBlockThirdPartyCookies(value: Boolean) = edit { it[Keys.blockThirdPartyCookies] = value }
    suspend fun setFingerprintProtection(value: Boolean) = edit { it[Keys.fingerprintProtection] = value }
    suspend fun setHttpsOnly(value: Boolean) = edit { it[Keys.httpsOnly] = value }
    suspend fun setDoNotTrack(value: Boolean) = edit { it[Keys.doNotTrack] = value }
    suspend fun setGlobalPrivacyControl(value: Boolean) = edit { it[Keys.gpc] = value }
    suspend fun setStripTrackingParams(value: Boolean) = edit { it[Keys.stripTrackingParams] = value }
    suspend fun setClearOnExit(value: Boolean) = edit { it[Keys.clearOnExit] = value }
    suspend fun setSafeBrowsing(value: Boolean) = edit { it[Keys.safeBrowsing] = value }
    suspend fun setJavaScriptEnabled(value: Boolean) = edit { it[Keys.javaScriptEnabled] = value }

    suspend fun setAiProvider(value: String) = edit { it[Keys.aiProvider] = value }
    suspend fun setGeminiModel(value: String) = edit { it[Keys.geminiModel] = value }
    suspend fun setOpenRouterModel(value: String) = edit { it[Keys.openRouterModel] = value }
    suspend fun setAiStreaming(value: Boolean) = edit { it[Keys.aiStreaming] = value }
    suspend fun setAiMemoryEnabled(value: Boolean) = edit { it[Keys.aiMemoryEnabled] = value }
    suspend fun setAiPageContext(value: Boolean) = edit { it[Keys.aiPageContext] = value }
    suspend fun setAiCustomInstructions(value: String) = edit { it[Keys.aiCustomInstructions] = value }
    suspend fun setAiTemperature(value: Int) = edit { it[Keys.aiTemperature] = value.coerceIn(0, 150) }
    suspend fun setAiAutoSummarize(value: Boolean) = edit { it[Keys.aiAutoSummarize] = value }
    suspend fun setAgentConfirmDestructive(value: Boolean) = edit { it[Keys.agentConfirmDestructive] = value }

    suspend fun setAgentConfirmEveryStep(value: Boolean) = edit { it[Keys.agentConfirmEveryStep] = value }
    suspend fun setAgentMaxSteps(value: Int) = edit { it[Keys.agentMaxSteps] = value.coerceIn(1, Constants.MAX_AGENT_STEPS) }

    suspend fun setDownloadThreads(value: Int) = edit { it[Keys.downloadThreads] = value.coerceIn(1, 8) }
    suspend fun setWifiOnlyDownloads(value: Boolean) = edit { it[Keys.wifiOnlyDownloads] = value }
    suspend fun setDownloadNotifications(value: Boolean) = edit { it[Keys.downloadNotifications] = value }

    suspend fun setSavePasswords(value: Boolean) = edit { it[Keys.savePasswords] = value }
    suspend fun setBiometricUnlock(value: Boolean) = edit { it[Keys.biometricUnlock] = value }
    suspend fun setAutofillPasswords(value: Boolean) = edit { it[Keys.autofillPasswords] = value }

    suspend fun setBackgroundPlayback(value: Boolean) = edit { it[Keys.backgroundPlayback] = value }
    suspend fun setPictureInPicture(value: Boolean) = edit { it[Keys.pictureInPicture] = value }

    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboardingComplete] = value }

    suspend fun resetAll() = edit { it.clear() }
}
