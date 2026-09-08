package com.nova.browser.features.passwords.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.browser.core.database.entities.PasswordEntity
import com.nova.browser.core.security.BiometricAuth
import com.nova.browser.features.passwords.repository.PasswordRepository
import com.nova.browser.features.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PasswordUiState(
    val unlocked: Boolean = false,
    val unlocking: Boolean = false,
    val biometricRequired: Boolean = true,
    val biometricAvailable: Boolean = true,
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val revealedId: Long? = null,
    val revealedValue: String? = null,
    val editing: PasswordEntity? = null,
    val addDialogVisible: Boolean = false,
    val confirmDelete: PasswordEntity? = null,
    val generatorVisible: Boolean = false,
    val generated: String = "",
    val auditRunning: Boolean = false,
    val weakCount: Int = 0,
    val reusedCount: Int = 0,
    val compromisedCount: Int = 0
)

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val repository: PasswordRepository,
    private val biometricAuth: BiometricAuth,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    val passwords: StateFlow<List<PasswordEntity>> = repository.passwords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        _uiState.value = _uiState.value.copy(biometricAvailable = biometricAuth.isAvailable())
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val required = settings.biometricUnlock && biometricAuth.isAvailable()
            _uiState.value = _uiState.value.copy(
                biometricRequired = required,
                unlocked = !required
            )
        }
        viewModelScope.launch {
            passwords.collect { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
        viewModelScope.launch {
            repository.weakCount.collect { _uiState.value = _uiState.value.copy(weakCount = it) }
        }
        viewModelScope.launch {
            repository.reusedCount.collect { _uiState.value = _uiState.value.copy(reusedCount = it) }
        }
        viewModelScope.launch {
            repository.compromisedCount.collect {
                _uiState.value = _uiState.value.copy(compromisedCount = it)
            }
        }
    }

    fun visible(all: List<PasswordEntity>): List<PasswordEntity> {
        val query = _uiState.value.query
        return if (query.isBlank()) all else all.filter {
            it.domain.contains(query, true) || it.username.contains(query, true)
        }
    }

    fun unlock(activity: FragmentActivity) {
        if (_uiState.value.unlocked || _uiState.value.unlocking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(unlocking = true, error = null)
            when (val result = biometricAuth.authenticate(
                activity,
                title = "Unlock passwords",
                subtitle = "Verify your identity to view saved logins"
            )) {
                is BiometricAuth.Result.Success ->
                    _uiState.value = _uiState.value.copy(unlocked = true, unlocking = false)

                is BiometricAuth.Result.Error -> _uiState.value = _uiState.value.copy(
                    unlocking = false,
                    error = result.message
                )

                BiometricAuth.Result.Cancelled -> _uiState.value = _uiState.value.copy(
                    unlocking = false,
                    error = "Unlock cancelled"
                )
            }
        }
    }

    fun lock() {
        _uiState.value = _uiState.value.copy(
            unlocked = !_uiState.value.biometricRequired,
            revealedId = null,
            revealedValue = null
        )
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Toggles plaintext display for a single entry. */
    fun toggleReveal(entity: PasswordEntity) {
        if (_uiState.value.revealedId == entity.id) {
            _uiState.value = _uiState.value.copy(revealedId = null, revealedValue = null)
            return
        }
        val plain = repository.reveal(entity)
        if (plain == null) {
            _uiState.value = _uiState.value.copy(
                error = "This password can't be decrypted — the device key may have been reset."
            )
            return
        }
        _uiState.value = _uiState.value.copy(revealedId = entity.id, revealedValue = plain)
    }

    fun copyPassword(entity: PasswordEntity, onCopy: (String) -> Unit) {
        val plain = repository.reveal(entity)
        if (plain == null) {
            _uiState.value = _uiState.value.copy(error = "Couldn't decrypt that password")
            return
        }
        onCopy(plain)
        viewModelScope.launch { repository.markUsed(entity.id) }
        _uiState.value = _uiState.value.copy(message = "Password copied — clipboard clears shortly")
    }

    fun showAddDialog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(addDialogVisible = visible)
    }

    fun startEdit(entity: PasswordEntity?) {
        _uiState.value = _uiState.value.copy(editing = entity)
    }

    fun confirmDelete(entity: PasswordEntity?) {
        _uiState.value = _uiState.value.copy(confirmDelete = entity)
    }

    fun save(domain: String, username: String, password: String, notes: String) {
        viewModelScope.launch {
            val ok = repository.save(domain, username, password, notes.takeIf { it.isNotBlank() })
            _uiState.value = _uiState.value.copy(
                addDialogVisible = false,
                editing = null,
                message = if (ok) "Password saved securely" else null,
                error = if (ok) null else "Couldn't save that login"
            )
        }
    }

    fun delete(entity: PasswordEntity) {
        viewModelScope.launch {
            repository.delete(entity.id)
            _uiState.value = _uiState.value.copy(
                confirmDelete = null,
                revealedId = null,
                revealedValue = null,
                message = "Login deleted"
            )
        }
    }

    fun showGenerator(visible: Boolean) {
        _uiState.value = _uiState.value.copy(
            generatorVisible = visible,
            generated = if (visible) repository.generate() else ""
        )
    }

    fun regenerate(length: Int, symbols: Boolean, digits: Boolean, upper: Boolean) {
        _uiState.value = _uiState.value.copy(
            generated = repository.generate(length, symbols, digits, upper)
        )
    }

    fun strengthOf(password: String) = repository.evaluateStrength(password)

    fun runAudit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(auditRunning = true)
            repository.runAudit()
            _uiState.value = _uiState.value.copy(
                auditRunning = false,
                message = "Security check complete"
            )
        }
    }
}
