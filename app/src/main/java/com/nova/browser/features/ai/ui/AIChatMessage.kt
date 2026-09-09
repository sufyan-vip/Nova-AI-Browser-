package com.nova.browser.features.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.MonoTextStyle
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.theme.rememberInfiniteProgress
import com.nova.browser.core.utils.copyToClipboard
import com.nova.browser.core.utils.toast
import com.nova.browser.features.ai.viewmodel.ChatMessage

@Composable
fun AIChatMessage(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatMessage.Role.USER
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.92f else 1f),
            shape = if (isUser) {
                RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            } else {
                RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
            },
            tint = when {
                message.error != null -> NovaColors.Error.copy(alpha = 0.10f)
                isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else -> NovaTheme.extended.glass
            },
            borderColor = when {
                message.error != null -> NovaColors.Error.copy(alpha = 0.3f)
                isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else -> NovaTheme.extended.glassBorder
            }
        ) {
            Column(Modifier.padding(Dimens.md)) {
                if (!isUser && message.model != null) {
                    Text(
                        text = "${message.mode.label} · ${message.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTheme.extended.textTertiary
                    )
                    Spacer(Modifier.height(Dimens.xs))
                }

                if (message.error != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = NovaColors.Error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(Dimens.sm))
                        Text(
                            message.error.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaColors.Error
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Spacer(Modifier.height(Dimens.sm))
                        MarkdownText(message.text)
                    }
                } else if (message.text.isBlank() && message.isStreaming) {
                    ThinkingIndicator()
                } else {
                    MarkdownText(message.text)
                    if (message.isStreaming) {
                        Spacer(Modifier.height(Dimens.xs))
                        StreamingCaret()
                    }
                }

                if (message.sources.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.md))
                    Text(
                        "Sources",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTheme.extended.textTertiary
                    )
                    message.sources.forEach { source ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(NovaShapeTokens.small)
                                .clickable { onOpenSource(source.url) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = NovaColors.Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(Dimens.sm))
                            Text(
                                "[${source.index}] ${source.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovaColors.Primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (!isUser && !message.isStreaming && (message.text.isNotBlank() || message.error != null)) {
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.text.isNotBlank()) {
                    GlassIconButton(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copy response",
                        onClick = onCopy,
                        size = 36.dp,
                        tint = NovaTheme.extended.textTertiary
                    )
                }
                GlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Regenerate response",
                    onClick = onRetry,
                    size = 36.dp,
                    tint = NovaTheme.extended.textTertiary
                )
            }
        }
    }
}

/** Animated "…" while waiting for the first token. */
@Composable
private fun ThinkingIndicator() {
    val progress by rememberInfiniteProgress(durationMillis = 1200)
    val dots = ((progress * 3).toInt() % 3) + 1
    Text(
        text = "Thinking" + ".".repeat(dots),
        style = MaterialTheme.typography.bodyMedium,
        color = NovaTheme.extended.textTertiary
    )
}

@Composable
private fun StreamingCaret() {
    val progress by rememberInfiniteProgress(durationMillis = 900, reverse = true)
    Box(
        Modifier
            .width(8.dp)
            .height(14.dp)
            .background(NovaColors.Accent.copy(alpha = 0.3f + progress * 0.7f), RoundedCornerShape(2.dp))
    )
}

/**
 * Lightweight markdown renderer: headings, bold, italic, inline code, fenced
 * code blocks, bullet and numbered lists, blockquotes and horizontal rules.
 * Compose has no built-in markdown, and a full parser is overkill here.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block.language, block.code)

                is MdBlock.Heading -> Text(
                    text = inline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = color
                )

                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        "•  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NovaColors.Accent
                    )
                    Text(inline(block.text), style = MaterialTheme.typography.bodyMedium, color = color)
                }

                is MdBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${block.number}.  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NovaColors.Accent
                    )
                    Text(inline(block.text), style = MaterialTheme.typography.bodyMedium, color = color)
                }

                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(NovaColors.Primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        inline(block.text),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = NovaTheme.extended.textTertiary
                    )
                }

                MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NovaTheme.extended.glassBorder)
                )

                is MdBlock.Paragraph -> Text(
                    inline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = NovaShapeTokens.small,
        tint = Color(0xFF0B1120),
        borderColor = NovaTheme.extended.glassBorder
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.sm, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTheme.extended.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    onClick = {
                        context.copyToClipboard(code)
                        context.toast("Code copied")
                    },
                    size = 32.dp,
                    tint = NovaTheme.extended.textTertiary
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.md, vertical = Dimens.sm)
            ) {
                Text(text = code, style = MonoTextStyle, color = Color(0xFFD6E2FF))
            }
        }
    }
}

/* --------------------------- tiny markdown parser --------------------------- */

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data object Divider : MdBlock
}

private fun parseBlocks(text: String): List<MdBlock> {
    if (text.isBlank()) return emptyList()
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var index = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.appendLine(lines[index])
                    index++
                }
                blocks += MdBlock.Code(language, code.toString().trimEnd())
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks += MdBlock.Heading(level, trimmed.drop(level).trim())
            }

            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                flushParagraph()
                blocks += MdBlock.Divider
            }

            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks += MdBlock.Quote(trimmed.removePrefix("> ").trim())
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flushParagraph()
                blocks += MdBlock.Bullet(trimmed.drop(2).trim())
            }

            Regex("^\\d+[.)]\\s+.*").matches(trimmed) -> {
                flushParagraph()
                val number = trimmed.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                blocks += MdBlock.Numbered(number, trimmed.dropWhile { it.isDigit() }.drop(1).trim())
            }

            trimmed.isEmpty() -> flushParagraph()

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

/** Applies inline bold / italic / code / link styling. */
private fun inline(text: String) = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index]); index++
                }
            }

            text.startsWith("`", index) -> {
                val end = text.indexOf("`", index + 1)
                if (end > 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            background = Color(0x33000000),
                            color = NovaColors.Secondary
                        )
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index]); index++
                }
            }

            text.startsWith("*", index) || text.startsWith("_", index) -> {
                val marker = text[index]
                val end = text.indexOf(marker, index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index]); index++
                }
            }

            text.startsWith("[", index) -> {
                val close = text.indexOf(']', index)
                val open = if (close > 0) text.indexOf('(', close) else -1
                val end = if (open == close + 1) text.indexOf(')', open) else -1
                if (close > 0 && end > 0) {
                    withStyle(
                        SpanStyle(color = NovaColors.Primary, textDecoration = TextDecoration.Underline)
                    ) {
                        append(text.substring(index + 1, close))
                    }
                    index = end + 1
                } else {
                    append(text[index]); index++
                }
            }

            else -> {
                append(text[index]); index++
            }
        }
    }
}
