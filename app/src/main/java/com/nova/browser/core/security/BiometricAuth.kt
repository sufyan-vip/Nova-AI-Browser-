package com.nova.browser.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuth @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Availability { AVAILABLE, NO_HARDWARE, NOT_ENROLLED, UNAVAILABLE }

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data class Error(val message: String) : Result
    }

    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(): Availability = try {
        when (BiometricManager.from(context).canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
            else -> Availability.UNAVAILABLE
        }
    } catch (e: Exception) {
        Availability.UNAVAILABLE
    }

    fun isAvailable(): Boolean = availability() == Availability.AVAILABLE

    /**
     * Shows the system prompt. When biometrics are unavailable the call
     * succeeds silently so the feature stays usable on devices without sensors.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock NOVA",
        subtitle: String = "Verify your identity to continue"
    ): Result = suspendCancellableCoroutine { continuation ->
        if (availability() != Availability.AVAILABLE) {
            continuation.resume(Result.Error("Biometric authentication is not available on this device"))
            return@suspendCancellableCoroutine
        }
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(Result.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!continuation.isActive) return
                        val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        continuation.resume(
                            if (cancelled) Result.Cancelled else Result.Error(errString.toString())
                        )
                    }

                    override fun onAuthenticationFailed() {
                        // User can retry; prompt stays open.
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()
            prompt.authenticate(info)
            continuation.invokeOnCancellation {
                try { prompt.cancelAuthentication() } catch (e: Exception) { /* ignore */ }
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(Result.Error(e.message ?: "Authentication failed"))
        }
    }
}
