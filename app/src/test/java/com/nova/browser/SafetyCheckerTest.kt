package com.nova.browser

import com.google.common.truth.Truth.assertThat
import com.nova.browser.features.agent.engine.AgentAction
import com.nova.browser.features.agent.engine.SafetyChecker
import org.junit.Before
import org.junit.Test

/** The agent must never auto-execute payments or destructive actions. */
class SafetyCheckerTest {

    private lateinit var checker: SafetyChecker

    @Before
    fun setUp() {
        checker = SafetyChecker()
    }

    @Test
    fun `a plain scroll is safe`() {
        val verdict = checker.check(AgentAction(action = "scroll", value = "down"), "https://example.com")
        assertThat(verdict.risk).isEqualTo(SafetyChecker.Risk.SAFE)
        assertThat(verdict.requiresConfirmation).isFalse()
    }

    @Test
    fun `purchase actions require confirmation`() {
        val verdict = checker.check(
            AgentAction(action = "click_text", target = "Buy now", reason = "Complete the purchase"),
            "https://shop.example.com"
        )
        assertThat(verdict.requiresConfirmation).isTrue()
    }

    @Test
    fun `delete actions require confirmation`() {
        val verdict = checker.check(
            AgentAction(action = "click_text", target = "Delete account"),
            "https://example.com/settings"
        )
        assertThat(verdict.requiresConfirmation).isTrue()
    }

    @Test
    fun `an empty plan is rejected`() {
        assertThat(checker.validatePlan(emptyList(), maxSteps = 10)).isNotNull()
    }

    @Test
    fun `a plan longer than the limit is rejected`() {
        val plan = List(12) { AgentAction(action = "scroll") }
        assertThat(checker.validatePlan(plan, maxSteps = 5)).isNotNull()
    }

    @Test
    fun `a plan within the limit is accepted`() {
        val plan = List(3) { AgentAction(action = "scroll") }
        assertThat(checker.validatePlan(plan, maxSteps = 5)).isNull()
    }

    @Test
    fun `review only reports non-safe steps`() {
        val plan = listOf(
            AgentAction(action = "scroll"),
            AgentAction(action = "click_text", target = "Confirm payment")
        )
        val verdicts = checker.review(plan, "https://example.com")
        assertThat(verdicts.keys).doesNotContain(0)
        assertThat(verdicts.keys).contains(1)
    }
}
