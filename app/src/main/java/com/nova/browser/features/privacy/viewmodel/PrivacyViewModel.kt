package com.nova.browser.features.privacy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.entities.TrackerStatEntity
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.privacy.engine.TrackerBlocker
import com.nova.browser.features.settings.repository.NovaSettings
import com.nova.browser.features.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val currentDomain: String = "",
    val siteAllowlisted: Boolean = false,
    val sessionBlocked: Int = 0,
    val message: String? = null
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val trackerBlocker: TrackerBlocker,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    val settings: StateFlow<NovaSettings> = settingsRepository.settings
        .catch { emit(NovaSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NovaSettings())

    val totalBlocked: StateFlow<Int> = trackerBlocker.totalBlockedFlow
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topTrackers: StateFlow<List<TrackerStatEntity>> = trackerBlocker.topTrackersFlow
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Refreshes the per-site panel for the page the browser is showing. */
    fun observeSite(url: String) {
        val domain = UrlUtils.domain(url)
        _uiState.value = _uiState.value.copy(
            currentDomain = domain,
            siteAllowlisted = domain.isNotBlank() && trackerBlocker.isSiteAllowlisted(domain),
            sessionBlocked = trackerBlocker.sessionBlocked()
        )
    }

    fun setSiteProtection(domain: String, enabled: Boolean) {
        if (domain.isBlank()) return
        if (enabled) trackerBlocker.disallowSite(domain) else trackerBlocker.allowSite(domain)
        _uiState.value = _uiState.value.copy(
            siteAllowlisted = !enabled,
            message = if (enabled) "Protection resumed on $domain" else "Protection paused on $domain"
        )
    }

    fun setBlockTrackers(value: Boolean) = edit { settingsRepository.setBlockTrackers(value) }
    fun setBlockAds(value: Boolean) = edit { settingsRepository.setBlockAds(value) }
    fun setBlockThirdPartyCookies(value: Boolean) = edit { settingsRepository.setBlockThirdPartyCookies(value) }
    fun setFingerprintProtection(value: Boolean) = edit { settingsRepository.setFingerprintProtection(value) }
    fun setHttpsOnly(value: Boolean) = edit { settingsRepository.setHttpsOnly(value) }

    fun clearStats() {
        viewModelScope.launch {
            runCatching { trackerBlocker.clearStats() }
            _uiState.value = _uiState.value.copy(
                sessionBlocked = 0,
                message = "Blocking statistics reset"
            )
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
