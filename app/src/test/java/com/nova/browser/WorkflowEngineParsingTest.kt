package com.nova.browser

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.nova.browser.features.agent.engine.AgentActionType
import com.nova.browser.features.agent.engine.WorkflowEngine
import org.junit.Test

/**
 * Models often wrap JSON in prose or fences, so plan extraction must be
 * forgiving without ever throwing.
 */
class WorkflowEngineParsingTest {

    private val gson = Gson()

    private fun parse(raw: String, fallback: String) =
        WorkflowEngine.parsePlan(raw, fallback, gson)

    @Test
    fun `a bare json plan parses`() {
        val raw = """
            {"goal":"Find prices","steps":[{"action":"navigate","target":"https://example.com"}],"summary":"ok"}
        """.trimIndent()
        val plan = parse(raw, "fallback")
        assertThat(plan).isNotNull()
        assertThat(plan!!.steps).hasSize(1)
        assertThat(plan.steps[0].type).isEqualTo(AgentActionType.NAVIGATE)
    }

    @Test
    fun `a fenced json plan parses`() {
        val raw = """
            Sure, here's the plan:
            ```json
            {"goal":"g","steps":[{"action":"scroll","value":"down"}]}
            ```
            Let me know if that works.
        """.trimIndent()
        val plan = parse(raw, "fallback")
        assertThat(plan).isNotNull()
        assertThat(plan!!.steps).hasSize(1)
    }

    @Test
    fun `a plan wrapped in prose parses`() {
        val raw = """I'll do this: {"goal":"g","steps":[{"action":"back"}]} — done."""
        assertThat(parse(raw, "fallback")).isNotNull()
    }

    @Test
    fun `nested braces and strings do not confuse extraction`() {
        val raw = """{"goal":"a {b} c","steps":[{"action":"type","value":"}{"}]}"""
        val plan = parse(raw, "fallback")
        assertThat(plan).isNotNull()
        assertThat(plan!!.steps).hasSize(1)
    }

    @Test
    fun `plans with no steps are rejected`() {
        assertThat(parse("""{"goal":"g","steps":[]}""", "fallback")).isNull()
    }

    @Test
    fun `non-json text yields null instead of throwing`() {
        assertThat(parse("I can't help with that.", "fallback")).isNull()
        assertThat(parse("", "fallback")).isNull()
    }

    @Test
    fun `malformed json yields null instead of throwing`() {
        assertThat(parse("""{"goal":"g","steps":[{"action":}]}""", "fallback")).isNull()
    }

    @Test
    fun `a blank goal falls back to the requested goal`() {
        val plan = parse("""{"steps":[{"action":"back"}]}""", "my goal")
        assertThat(plan!!.goal).isEqualTo("my goal")
    }
}
