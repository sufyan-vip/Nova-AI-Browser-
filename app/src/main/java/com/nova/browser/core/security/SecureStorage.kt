package com.nova.browser.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nova.browser.core.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedSharedPreferences wrapper for API keys and other secrets.
 * Falls back to in-memory storage if the encrypted store cannot be created,
 * so the app degrades instead of crashing.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_OPENROUTER = "openrouter_api_key"
        const val KEY_MASTER_PIN_HASH = "master_pin_hash"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }

    private val memoryFallback = mutableMapOf<String, String>()

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                Constants.SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            try {
                context.deleteSharedPreferences(Constants.SECURE_PREFS)
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    Constants.SECURE_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                null
            }
        }
    }

    private val _keysVersion = MutableStateFlow(0)
    /** Increments whenever a secret changes, so engines can re-read keys reactively. */
    val keysVersion: StateFlow<Int> = _keysVersion

    fun putString(key: String, value: String) {
        try {
            prefs?.edit()?.putString(key, value)?.apply() ?: run { memoryFallback[key] = value }
        } catch (e: Exception) {
            memoryFallback[key] = value
        }
        _keysVersion.value = _keysVersion.value + 1
    }

    fun getString(key: String, default: String? = null): String? = try {
        prefs?.getString(key, default) ?: memoryFallback[key] ?: default
    } catch (e: Exception) {
        memoryFallback[key] ?: default
    }

    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    fun remove(key: String) {
        try {
            prefs?.edit()?.remove(key)?.apply()
        } catch (e: Exception) {
            // ignore
        }
        memoryFallback.remove(key)
        _keysVersion.value = _keysVersion.value + 1
    }

    fun clearAll() {
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            // ignore
        }
        memoryFallback.clear()
        _keysVersion.value = _keysVersion.value + 1
    }

    var geminiKey: String
        get() = getString(KEY_GEMINI).orEmpty()
        set(value) = if (value.isBlank()) remove(KEY_GEMINI) else putString(KEY_GEMINI, value.trim())

    var openRouterKey: String
        get() = getString(KEY_OPENROUTER).orEmpty()
        set(value) = if (value.isBlank()) remove(KEY_OPENROUTER) else putString(KEY_OPENROUTER, value.trim())

    fun hasAnyKey(): Boolean = geminiKey.isNotBlank() || openRouterKey.isNotBlank()

    /** Masks a key for display: "AIza••••••4f2a". */
    fun maskedKey(value: String): String = when {
        value.isBlank() -> "Not set"
        value.length <= 8 -> "••••••••"
        else -> value.take(4) + "••••••" + value.takeLast(4)
    }
}
