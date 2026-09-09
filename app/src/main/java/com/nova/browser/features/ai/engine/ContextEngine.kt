package com.nova.browser.features.ai.engine

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nova.browser.core.utils.HtmlUtils
import javax.inject.Inject
import javax.inject.Singleton

/** Structured page snapshot produced by [JsScripts.EXTRACT_CONTENT]. */
data class PageContext(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("url") val url: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("siteName") val siteName: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("published") val published: String = "",
    @SerializedName("lang") val lang: String = "",
    @SerializedName("contentType") val contentType: String = "page",
    @SerializedName("text") val text: String = "",
    @SerializedName("wordCount") val wordCount: Int = 0,
    @SerializedName("headings") val headings: List<Heading> = emptyList(),
    @SerializedName("links") val links: List<Link> = emptyList(),
    @SerializedName("images") val images: List<Image> = emptyList(),
    @SerializedName("tables") val tables: List<List<List<String>>> = emptyList(),
    @SerializedName("codeBlocks") val codeBlocks: List<String> = emptyList(),
    @SerializedName("videos") val videos: List<Video> = emptyList(),
    @SerializedName("error") val error: String? = null
) {
    data class Heading(@SerializedName("level") val level: Int = 1, @SerializedName("text") val text: String = "")
    data class Link(@SerializedName("text") val text: String = "", @SerializedName("url") val url: String = "")
    data class Image(
        @SerializedName("src") val src: String = "",
        @SerializedName("alt") val alt: String = "",
        @SerializedName("width") val width: Int = 0,
        @SerializedName("height") val height: Int = 0
    )
    data class Video(@SerializedName("src") val src: String = "", @SerializedName("title") val title: String = "")

    val isEmpty: Boolean get() = text.isBlank() && headings.isEmpty()

    companion object {
        fun empty(url: String = "", title: String = "") = PageContext(ok = false, url = url, title = title)
    }
}

/**
 * Turns raw page snapshots into compact, token-budgeted prompt context
 * (spec 10_CONTEXT_ENGINE).
 */
@Singleton
class ContextEngine @Inject constructor(
    private val gson: Gson
) {

    /** Parses the JSON produced by the injected extractor. Never throws. */
    fun parse(json: String?, fallbackUrl: String = "", fallbackTitle: String = ""): PageContext {
        if (json.isNullOrBlank()) return PageContext.empty(fallbackUrl, fallbackTitle)
        val cleaned = unwrapEvaluateResult(json)
        return try {
            gson.fromJson(cleaned, PageContext::class.java) ?: PageContext.empty(fallbackUrl, fallbackTitle)
        } catch (e: Exception) {
            PageContext.empty(fallbackUrl, fallbackTitle)
        }
    }

    /**
     * evaluateJavascript returns a JSON-encoded value, so a returned JSON string
     * arrives double-encoded. This normalises both shapes.
     */
    fun unwrapEvaluateResult(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed == "null" || trimmed.isEmpty()) return "{}"
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
        return try {
            gson.fromJson(trimmed, String::class.java) ?: "{}"
        } catch (e: Exception) {
            trimmed.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
        }
    }

    /**
     * Builds the context block appended to the user's prompt, compressed to fit
     * [maxTokens]. Priority: metadata → headings → main text → structured data.
     */
    fun buildPrompt(
        context: PageContext,
        maxTokens: Int,
        query: String = "",
        includeLinks: Boolean = false,
        includeTables: Boolean = true,
        includeCode: Boolean = true
    ): String {
        if (context.isEmpty) {
            return if (context.url.isBlank()) {
                ""
            } else {
                "[PAGE]\nURL: ${context.url}\nTitle: ${context.title}\n" +
                    "(The page content could not be extracted — it may still be loading, " +
                    "be a PDF, or block scripting.)\n[/PAGE]"
            }
        }

        val header = buildString {
            appendLine("[PAGE CONTEXT]")
            appendLine("URL: ${context.url}")
            if (context.title.isNotBlank()) appendLine("Title: ${context.title}")
            if (context.siteName.isNotBlank()) appendLine("Site: ${context.siteName}")
            if (context.author.isNotBlank()) appendLine("Author: ${context.author}")
            if (context.published.isNotBlank()) appendLine("Published: ${context.published}")
            if (context.description.isNotBlank()) appendLine("Description: ${context.description}")
            appendLine("Content type: ${context.contentType}")
            appendLine("Word count: ${context.wordCount}")
        }

        val outline = if (context.headings.isNotEmpty()) {
            buildString {
                appendLine()
                appendLine("Outline:")
                context.headings.take(30).forEach { heading ->
                    appendLine("${"  ".repeat((heading.level - 1).coerceIn(0, 4))}- ${heading.text}")
                }
            }
        } else ""

        val extras = buildString {
            if (includeCode && context.codeBlocks.isNotEmpty()) {
                appendLine()
                appendLine("Code blocks:")
                context.codeBlocks.take(5).forEach { block ->
                    appendLine("```")
                    appendLine(block.take(2000))
                    appendLine("```")
                }
            }
            if (includeTables && context.tables.isNotEmpty()) {
                appendLine()
                appendLine("Tables:")
                context.tables.take(3).forEachIndexed { index, rows ->
                    appendLine("Table ${index + 1}:")
                    rows.take(20).forEach { row -> appendLine("| " + row.joinToString(" | ")) }
                }
            }
            if (includeLinks && context.links.isNotEmpty()) {
                appendLine()
                appendLine("Links:")
                context.links.take(40).forEach { link -> appendLine("- ${link.text} → ${link.url}") }
            }
            if (context.images.isNotEmpty()) {
                val described = context.images.filter { it.alt.isNotBlank() }.take(10)
                if (described.isNotEmpty()) {
                    appendLine()
                    appendLine("Images (alt text): " + described.joinToString("; ") { it.alt })
                }
            }
        }

        val fixedTokens = HtmlUtils.approximateTokens(header + outline + extras)
        val bodyBudget = (maxTokens - fixedTokens - 100).coerceAtLeast(300)
        val body = compressText(context.text, bodyBudget, query)

        return buildString {
            append(header)
            append(outline)
            appendLine()
            appendLine("Content:")
            appendLine(body)
            append(extras)
            appendLine("[/PAGE CONTEXT]")
        }
    }

    /**
     * Query-aware compression: when the text overflows the budget we keep the
     * opening (usually the lede) plus the passages most relevant to the query.
     */
    fun compressText(text: String, maxTokens: Int, query: String = ""): String {
        val clean = text.replace(Regex("\\n{3,}"), "\n\n").trim()
        if (clean.isBlank()) return "(no readable text)"
        if (HtmlUtils.approximateTokens(clean) <= maxTokens) return clean

        val keywords = query.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 3 }
            .toSet()

        val paragraphs = clean.split(Regex("\n{2,}")).filter { it.isNotBlank() }
        if (paragraphs.size <= 1 || keywords.isEmpty()) {
            return HtmlUtils.truncateToTokens(clean, maxTokens)
        }

        val leadCount = 3.coerceAtMost(paragraphs.size)
        val lead = paragraphs.take(leadCount)
        val rest = paragraphs.drop(leadCount)

        // Keep the types explicit here. This avoids overload ambiguity around
        // sumOf/plus on older Kotlin compiler frontends used by Android CI.
        val scored: List<Triple<Int, String, Int>> = rest.mapIndexed { index: Int, paragraph: String ->
            val lower = paragraph.lowercase()
            val score: Int = keywords.count { keyword: String -> lower.contains(keyword) }
            Triple(index, paragraph, score)
        }.sortedByDescending { triple: Triple<Int, String, Int> -> triple.third }

        val selected = sortedSetOf<Int>()
        var used = HtmlUtils.approximateTokens(lead.joinToString("\n\n"))
        for ((index, paragraph, score) in scored) {
            if (score == 0) continue
            val cost = HtmlUtils.approximateTokens(paragraph)
            if (used + cost > maxTokens) continue
            selected.add(index)
            used += cost
        }
        // Backfill with sequential paragraphs if budget remains.
        rest.indices.forEach { index ->
            if (index in selected) return@forEach
            val cost = HtmlUtils.approximateTokens(rest[index])
            if (used + cost <= maxTokens) {
                selected.add(index)
                used += cost
            }
        }

        val builder = StringBuilder(lead.joinToString("\n\n"))
        var previous = -1
        selected.forEach { index ->
            if (previous >= 0 && index != previous + 1) builder.append("\n\n…")
            builder.append("\n\n").append(rest[index])
            previous = index
        }
        return builder.toString()
    }

    /** A one-line description used in the AI header chip. */
    fun describe(context: PageContext): String = when {
        context.isEmpty -> "No page context"
        context.wordCount > 0 -> "${context.contentType.replaceFirstChar { it.uppercase() }} · ${context.wordCount} words"
        else -> context.contentType
    }

    /** Formats multiple research sources with numbered citations (spec 11). */
    fun buildResearchContext(sources: List<PageContext>, tokensPerSource: Int): String = buildString {
        appendLine("[SOURCES]")
        sources.forEachIndexed { index, source ->
            appendLine()
            appendLine("--- SOURCE [${index + 1}] ---")
            appendLine("Title: ${source.title}")
            appendLine("URL: ${source.url}")
            if (source.published.isNotBlank()) appendLine("Published: ${source.published}")
            appendLine("Content:")
            appendLine(compressText(source.text, tokensPerSource))
        }
        appendLine("[/SOURCES]")
    }
}
