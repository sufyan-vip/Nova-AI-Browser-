package com.nova.browser.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256/GCM encryption backed by the Android Keystore.
 * Keys never leave the TEE; only ciphertext + IV are persisted.
 */
@Singleton
class PasswordEncryption @Inject constructor() {

    data class Encrypted(val cipherText: String, val iv: String)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nova_password_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** Returns null when the platform keystore is unavailable rather than crashing. */
    fun encrypt(plainText: String): Encrypted? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val bytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        Encrypted(
            cipherText = Base64.encodeToString(bytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    } catch (e: Exception) {
        null
    }

    fun decrypt(cipherText: String, iv: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun deleteKey(): Boolean = try {
        keyStore().deleteEntry(KEY_ALIAS)
        true
    } catch (e: Exception) {
        false
    }
}
