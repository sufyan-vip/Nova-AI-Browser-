package com.nova.browser.features.passwords.repository

import com.nova.browser.core.database.dao.PasswordDao
import com.nova.browser.core.database.entities.PasswordEntity
import com.nova.browser.core.security.PasswordEncryption
import com.nova.browser.core.utils.UrlUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Owns the encrypted credential vault. Plaintext passwords only ever exist
 * in memory for the duration of a call and are never logged or sent to AI.
 */
@Singleton
class PasswordRepository @Inject constructor(
    private val passwordDao: PasswordDao,
    private val encryption: PasswordEncryption
) {
    data class Credential(val id: Long, val domain: String, val username: String, val password: String)

    data class Strength(val score: Int, val label: String, val isWeak: Boolean)

    val passwords: Flow<List<PasswordEntity>> = passwordDao.observeAll().catch { emit(emptyList()) }
    val count: Flow<Int> = passwordDao.observeCount().catch { emit(0) }
    val weakCount: Flow<Int> = passwordDao.observeWeakCount().catch { emit(0) }
    val reusedCount: Flow<Int> = passwordDao.observeReusedCount().catch { emit(0) }
    val compromisedCount: Flow<Int> = passwordDao.observeCompromisedCount().catch { emit(0) }

    fun search(query: String): Flow<List<PasswordEntity>> =
        passwordDao.observeSearch(query).catch { emit(emptyList()) }

    /** Saves or updates a credential for [domain]. Returns false if encryption fails. */
    suspend fun save(
        domain: String,
        username: String,
        password: String,
        notes: String? = null,
        category: String? = null
    ): Boolean {
        val normalizedDomain = UrlUtils.domain(domain).ifBlank { domain.trim() }
        if (normalizedDomain.isBlank() || username.isBlank() || password.isBlank()) return false

        val encrypted = encryption.encrypt(password) ?: return false
        val strength = evaluateStrength(password)
        val existing = passwordDao.findForDomain(normalizedDomain).firstOrNull { it.username == username }

        return try {
            if (existing == null) {
                passwordDao.insert(
                    PasswordEntity(
                        domain = normalizedDomain,
                        username = username.trim(),
                        encryptedPassword = encrypted.cipherText,
                        iv = encrypted.iv,
                        favicon = UrlUtils.faviconUrl("https://$normalizedDomain"),
                        notes = notes,
                        category = category,
                        isWeak = strength.isWeak,
                        strengthScore = strength.score
                    )
                )
            } else {
                passwordDao.update(
                    existing.copy(
                        encryptedPassword = encrypted.cipherText,
                        iv = encrypted.iv,
                        notes = notes ?: existing.notes,
                        category = category ?: existing.category,
                        isWeak = strength.isWeak,
                        strengthScore = strength.score,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            refreshReuseFlags()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Decrypts a stored password. Returns null when the Keystore entry is gone. */
    fun reveal(entity: PasswordEntity): String? =
        encryption.decrypt(entity.encryptedPassword, entity.iv)

    /** Credentials matching a page URL, best match first. */
    suspend fun forUrl(url: String): List<Credential> {
        val domain = UrlUtils.domain(url)
        if (domain.isBlank()) return emptyList()
        return passwordDao.findForDomain(domain).mapNotNull { entity ->
            val plain = reveal(entity) ?: return@mapNotNull null
            Credential(entity.id, entity.domain, entity.username, plain)
        }
    }

    suspend fun markUsed(id: Long) = passwordDao.markUsed(id, System.currentTimeMillis())

    suspend fun delete(id: Long) {
        passwordDao.deleteById(id)
        refreshReuseFlags()
    }

    suspend fun deleteAll() = passwordDao.deleteAll()

    /**
     * Flags reused passwords by comparing SHA-256 digests of the decrypted
     * values in memory — digests are never persisted.
     */
    suspend fun refreshReuseFlags() {
        try {
            val all = passwordDao.getAll()
            val digests = mutableMapOf<Long, String>()
            all.forEach { entity ->
                reveal(entity)?.let { plain -> digests[entity.id] = sha256(plain) }
            }
            val counts = digests.values.groupingBy { it }.eachCount()
            all.forEach { entity ->
                val digest = digests[entity.id]
                val reused = digest != null && (counts[digest] ?: 0) > 1
                if (reused != entity.isReused) {
                    passwordDao.updateHealth(
                        id = entity.id,
                        compromised = entity.isCompromised,
                        weak = entity.isWeak,
                        reused = reused,
                        score = entity.strengthScore
                    )
                }
            }
        } catch (e: Exception) {
            // Health flags are advisory; failures must not break the vault.
        }
    }

    /** Re-scores every stored password (used by the security audit action). */
    suspend fun runAudit() {
        try {
            passwordDao.getAll().forEach { entity ->
                val plain = reveal(entity) ?: return@forEach
                val strength = evaluateStrength(plain)
                passwordDao.updateHealth(
                    id = entity.id,
                    compromised = entity.isCompromised,
                    weak = strength.isWeak,
                    reused = entity.isReused,
                    score = strength.score
                )
            }
            refreshReuseFlags()
        } catch (e: Exception) {
            // Ignore; the UI shows the last known state.
        }
    }

    /** Generates a strong random password from a mixed alphabet. */
    fun generate(
        length: Int = 20,
        useSymbols: Boolean = true,
        useDigits: Boolean = true,
        useUpper: Boolean = true
    ): String {
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val digits = "23456789"
        val symbols = "!@#$%^&*()-_=+[]{};:,.?"

        val alphabet = buildString {
            append(lower)
            if (useUpper) append(upper)
            if (useDigits) append(digits)
            if (useSymbols) append(symbols)
        }
        val random = java.security.SecureRandom()
        val size = length.coerceIn(8, 64)

        // Guarantee at least one character from each selected class.
        val required = buildList {
            add(lower[random.nextInt(lower.length)])
            if (useUpper) add(upper[random.nextInt(upper.length)])
            if (useDigits) add(digits[random.nextInt(digits.length)])
            if (useSymbols) add(symbols[random.nextInt(symbols.length)])
        }
        val rest = (0 until (size - required.size).coerceAtLeast(0)).map {
            alphabet[random.nextInt(alphabet.length)]
        }
        return (required + rest).shuffled(random).joinToString("")
    }

    /** Heuristic strength score out of 100. */
    fun evaluateStrength(password: String): Strength {
        if (password.isEmpty()) return Strength(0, "Empty", true)

        var score = 0
        score += min(password.length * 4, 40)
        if (password.any { it.isLowerCase() }) score += 10
        if (password.any { it.isUpperCase() }) score += 10
        if (password.any { it.isDigit() }) score += 10
        if (password.any { !it.isLetterOrDigit() }) score += 15
        if (password.toSet().size >= password.length * 0.7) score += 10

        val lower = password.lowercase()
        val common = listOf(
            "password", "123456", "qwerty", "letmein", "welcome", "admin",
            "iloveyou", "abc123", "monkey", "dragon", "111111", "sunshine"
        )
        if (common.any { lower.contains(it) }) score -= 40
        if (Regex("^\\d+$").matches(password)) score -= 20
        if (password.length < 8) {
            // Complexity cannot compensate for an unsafe length. Capping the
            // score also keeps the user-facing label consistent with isWeak.
            score = min(score - 20, 39)
        }

        val clamped = score.coerceIn(0, 100)
        val label = when {
            clamped >= 80 -> "Strong"
            clamped >= 60 -> "Good"
            clamped >= 40 -> "Fair"
            else -> "Weak"
        }
        return Strength(clamped, label, clamped < 50)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
