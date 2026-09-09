package com.nova.browser

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.nova.browser.features.ai.engine.ContextEngine
import org.junit.Before
import org.junit.Test

/** evaluateJavascript hands back JSON-encoded values; unwrapping must be lossless. */
class ContextEngineTest {

    private lateinit var engine: ContextEngine

    @Before
    fun setUp() {
        engine = ContextEngine(Gson())
    }

    @Test
    fun `null and empty results become an empty object`() {
        assertThat(engine.unwrapEvaluateResult("null")).isEqualTo("{}")
        assertThat(engine.unwrapEvaluateResult("")).isEqualTo("{}")
        assertThat(engine.unwrapEvaluateResult("   ")).isEqualTo("{}")
    }

    @Test
    fun `plain json objects pass through untouched`() {
        val json = """{"ok":true,"title":"Example"}"""
        assertThat(engine.unwrapEvaluateResult(json)).isEqualTo(json)
    }

    @Test
    fun `json arrays pass through untouched`() {
        assertThat(engine.unwrapEvaluateResult("""[1,2,3]""")).isEqualTo("""[1,2,3]""")
    }

    @Test
    fun `double encoded json strings are unwrapped`() {
        val inner = """{"ok":true,"title":"Hello \"World\""}"""
        val doubleEncoded = Gson().toJson(inner)
        assertThat(engine.unwrapEvaluateResult(doubleEncoded)).isEqualTo(inner)
    }

    @Test
    fun `parsing malformed json yields an empty context instead of throwing`() {
        val context = engine.parse("{not json", fallbackUrl = "https://example.com", fallbackTitle = "Fallback")
        assertThat(context.url).isEqualTo("https://example.com")
        assertThat(context.title).isEqualTo("Fallback")
    }

    @Test
    fun `parsing null json yields an empty context`() {
        val context = engine.parse(null, fallbackUrl = "https://a.test", fallbackTitle = "T")
        assertThat(context.url).isEqualTo("https://a.test")
    }
}
