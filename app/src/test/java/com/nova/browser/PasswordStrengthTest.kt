package com.nova.browser

import com.google.common.truth.Truth.assertThat
import com.nova.browser.core.security.PasswordEncryption
import com.nova.browser.features.passwords.repository.PasswordRepository
import org.junit.Before
import org.junit.Test

/**
 * Strength scoring and generation are pure logic, so they can be exercised
 * with a repository whose Room/Keystore collaborators are never touched.
 */
class PasswordStrengthTest {

    private lateinit var repository: PasswordRepository

    @Before
    fun setUp() {
        // evaluateStrength and generate touch neither the DAO nor the cipher.
        repository = PasswordRepository(
            passwordDao = FakePasswordDao(),
            encryption = PasswordEncryption()
        )
    }

    @Test
    fun `an empty password scores zero and is weak`() {
        val strength = repository.evaluateStrength("")
        assertThat(strength.score).isEqualTo(0)
        assertThat(strength.isWeak).isTrue()
    }

    @Test
    fun `common passwords are penalised`() {
        assertThat(repository.evaluateStrength("password123").isWeak).isTrue()
        assertThat(repository.evaluateStrength("qwerty").isWeak).isTrue()
    }

    @Test
    fun `all-digit and short passwords are weak`() {
        assertThat(repository.evaluateStrength("12345678").isWeak).isTrue()
        assertThat(repository.evaluateStrength("aB3!").isWeak).isTrue()
    }

    @Test
    fun `a long mixed password is strong`() {
        val strength = repository.evaluateStrength("T7v!qWm2#zLpXr4@bNs9")
        assertThat(strength.isWeak).isFalse()
        assertThat(strength.score).isAtLeast(80)
        assertThat(strength.label).isEqualTo("Strong")
    }

    @Test
    fun `scores always stay within bounds`() {
        listOf("", "a", "password", "correct horse battery staple", "T7v!qWm2#zLpXr4@bNs9")
            .forEach { assertThat(repository.evaluateStrength(it).score).isIn(0..100) }
    }

    @Test
    fun `generated passwords honour the requested length`() {
        assertThat(repository.generate(length = 24).length).isEqualTo(24)
        assertThat(repository.generate(length = 4).length).isEqualTo(8)
        assertThat(repository.generate(length = 999).length).isEqualTo(64)
    }

    @Test
    fun `generated passwords include every requested class`() {
        val password = repository.generate(length = 20)
        assertThat(password.any { it.isDigit() }).isTrue()
        assertThat(password.any { it.isUpperCase() }).isTrue()
        assertThat(password.any { !it.isLetterOrDigit() }).isTrue()
    }

    @Test
    fun `symbols and digits can be excluded`() {
        val password = repository.generate(length = 20, useSymbols = false, useDigits = false)
        assertThat(password.any { !it.isLetterOrDigit() }).isFalse()
        assertThat(password.any { it.isDigit() }).isFalse()
    }

    @Test
    fun `generated passwords are not repeated`() {
        val generated = List(20) { repository.generate(length = 20) }
        assertThat(generated.toSet()).hasSize(20)
    }

    @Test
    fun `generated passwords rate as strong`() {
        repeat(10) {
            assertThat(repository.evaluateStrength(repository.generate(length = 20)).isWeak).isFalse()
        }
    }
}
