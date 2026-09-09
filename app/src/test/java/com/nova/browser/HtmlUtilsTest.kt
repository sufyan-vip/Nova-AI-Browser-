package com.nova.browser

import com.google.common.truth.Truth.assertThat
import com.nova.browser.core.utils.HtmlUtils
import org.junit.Test

/** Pure-JVM tests for the token budgeting and escaping helpers. */
class HtmlUtilsTest {

    @Test
    fun `approximateTokens returns zero for blank text`() {
        assertThat(HtmlUtils.approximateTokens("")).isEqualTo(0)
        assertThat(HtmlUtils.approximateTokens("   \n ")).isEqualTo(0)
    }

    @Test
    fun `approximateTokens scales with word count`() {
        val short = HtmlUtils.approximateTokens("one two three")
        val long = HtmlUtils.approximateTokens(List(100) { "word" }.joinToString(" "))
        assertThat(short).isAtLeast(1)
        assertThat(long).isGreaterThan(short)
    }

    @Test
    fun `truncateToTokens keeps short text untouched`() {
        val text = "a short sentence"
        assertThat(HtmlUtils.truncateToTokens(text, 100)).isEqualTo(text)
    }

    @Test
    fun `truncateToTokens trims long text and marks it`() {
        val text = List(500) { "word" }.joinToString(" ")
        val result = HtmlUtils.truncateToTokens(text, 20)
        assertThat(result.length).isLessThan(text.length)
        assertThat(result).contains("content truncated")
    }

    @Test
    fun `truncateToTokens returns empty for a non-positive budget`() {
        assertThat(HtmlUtils.truncateToTokens("anything at all", 0)).isEmpty()
    }

    @Test
    fun `escapeJsString neutralises quotes newlines and tag openers`() {
        val escaped = HtmlUtils.escapeJsString("it's \"x\"\n<script>")
        assertThat(escaped).doesNotContain("\n")
        assertThat(escaped).contains("\\'")
        assertThat(escaped).contains("\\u003C")
    }

    @Test
    fun `escapeJsString escapes backslashes first`() {
        assertThat(HtmlUtils.escapeJsString("a\\b")).isEqualTo("a\\\\b")
    }

    @Test
    fun `decodeEntities resolves the common named entities`() {
        val decoded = HtmlUtils.decodeEntities("a&nbsp;b &amp; c &lt;d&gt;")
        assertThat(decoded).contains("&")
        assertThat(decoded).doesNotContain("&amp;")
        assertThat(decoded).doesNotContain("&nbsp;")
    }
}
