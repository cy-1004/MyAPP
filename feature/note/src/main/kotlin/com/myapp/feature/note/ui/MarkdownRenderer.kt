package com.myapp.feature.note.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.myapp.core.designsystem.theme.appColors

/**
 * 简易 Markdown 解析与渲染（PRD 3.4）。
 *
 * 只支持「标题 / 列表 / 加粗 / 代码块 / 引用 / 行内代码」这几样最常用的轻语法，
 * 不引入第三方 Markdown 库--自用项目，需求范围明确，自己写 200 行就能覆盖。
 *
 * 解析与渲染分开：
 *   - [parseMarkdown] 是纯 Kotlin 函数，返回块结构，可 JVM 单测
 *   - [MarkdownText] 是 Composable，把块结构渲染成 [AnnotatedString]
 *
 * **letterSpacing 硬约束**：所有 [SpanStyle] 不设 `letterSpacing`（默认 null），
 * 避免触发 Em/Sp lerp 崩溃（PRD 5.2、`Type.kt` 注释）。
 *
 * 不支持嵌套（`**bold `code` bold**` 这种），V1 用不到。
 */

/** 行内片段。代码块内不解析行内格式，所以只有这三种。 */
sealed interface InlineSpan {
    val text: String
    data class Text(override val text: String) : InlineSpan
    data class Bold(override val text: String) : InlineSpan
    data class Code(override val text: String) : InlineSpan
}

/** 块级元素。 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val spans: List<InlineSpan>) : MarkdownBlock
    data class ListItem(val spans: List<InlineSpan>) : MarkdownBlock
    data class Quote(val spans: List<InlineSpan>) : MarkdownBlock
    data class CodeBlock(val content: String) : MarkdownBlock
    data class Paragraph(val spans: List<InlineSpan>) : MarkdownBlock
}

private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
private val INLINE_CODE_REGEX = Regex("`(.+?)`")

/**
 * 把一行文本拆成行内片段（普通 / 加粗 / 行内代码）。
 *
 * 用两个正则交替迭代：找最近的 `**...**` 或 `` `...` ``，切出前缀 + 命中段 + 递归剩余。
 * 未闭合的 `**` 或 `` ` `` 按字面量输出（不吞字符）。
 */
internal fun parseInline(text: String): List<InlineSpan> {
    if (text.isEmpty()) return emptyList()
    val spans = mutableListOf<InlineSpan>()
    var rest = text
    while (rest.isNotEmpty()) {
        val boldMatch = BOLD_REGEX.find(rest)
        val codeMatch = INLINE_CODE_REGEX.find(rest)

        // 取两个匹配里更早出现的；都没匹配则把剩余当作纯文本
        val next = when {
            boldMatch == null && codeMatch == null -> null
            boldMatch == null -> Match.Code(codeMatch!!)
            codeMatch == null -> Match.Bold(boldMatch!!)
            boldMatch.range.first <= codeMatch.range.first -> Match.Bold(boldMatch)
            else -> Match.Code(codeMatch)
        }

        if (next == null) {
            spans += InlineSpan.Text(rest)
            break
        }

        // 前缀
        val prefix = rest.substring(0, next.match.range.first)
        if (prefix.isNotEmpty()) spans += InlineSpan.Text(prefix)
        // 命中段
        val inner = next.match.groupValues[1]
        spans += when (next) {
            is Match.Bold -> InlineSpan.Bold(inner)
            is Match.Code -> InlineSpan.Code(inner)
        }
        rest = rest.substring(next.match.range.last + 1)
    }
    return spans
}

private sealed interface Match {
    val match: MatchResult
    data class Bold(override val match: MatchResult) : Match
    data class Code(override val match: MatchResult) : Match
}

/**
 * 把整段 Markdown 拆成块结构。
 *
 * 状态机：`inCodeBlock` 标志位控制 ``` 切换。代码块内的 `#` `-` `>` 不再解析为
 * 标题/列表/引用，按字面量保留。
 */
internal fun parseMarkdown(content: String): List<MarkdownBlock> {
    if (content.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val codeBlockLines = mutableListOf<String>()
    var inCodeBlock = false

    content.split("\n").forEach { line ->
        when {
            line.trimStart().startsWith("```") -> {
                if (inCodeBlock) {
                    // 闭合代码块
                    blocks += MarkdownBlock.CodeBlock(content = codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    // 开启代码块
                    inCodeBlock = true
                }
            }

            inCodeBlock -> codeBlockLines += line

            line.startsWith("# ") -> blocks += MarkdownBlock.Heading(1, parseInline(line.removePrefix("# ")))
            line.startsWith("## ") -> blocks += MarkdownBlock.Heading(2, parseInline(line.removePrefix("## ")))
            line.startsWith("### ") -> blocks += MarkdownBlock.Heading(3, parseInline(line.removePrefix("### ")))
            line.startsWith("- ") -> blocks += MarkdownBlock.ListItem(parseInline(line.removePrefix("- ")))
            line.startsWith("* ") -> blocks += MarkdownBlock.ListItem(parseInline(line.removePrefix("* ")))
            line.startsWith("> ") -> blocks += MarkdownBlock.Quote(parseInline(line.removePrefix("> ")))
            line.isBlank() -> Unit // 空行作为段落分隔，不生成块
            else -> blocks += MarkdownBlock.Paragraph(parseInline(line))
        }
    }

    // 代码块未闭合：把已累积的内容当 CodeBlock 输出，不丢字符
    if (inCodeBlock && codeBlockLines.isNotEmpty()) {
        blocks += MarkdownBlock.CodeBlock(content = codeBlockLines.joinToString("\n"))
    }

    return blocks
}

@Stable
private data class MarkdownStyle(
    val h1: SpanStyle,
    val h2: SpanStyle,
    val h3: SpanStyle,
    val bold: SpanStyle,
    val inlineCode: SpanStyle,
    val codeBlock: SpanStyle,
    val quote: SpanStyle,
    val highlight: SpanStyle,
)

@Composable
private fun markdownStyle(): MarkdownStyle {
    val scheme = MaterialTheme.colorScheme
    return MarkdownStyle(
        h1 = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        h2 = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        h3 = SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        bold = SpanStyle(fontWeight = FontWeight.Bold),
        inlineCode = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = scheme.surfaceVariant,
        ),
        codeBlock = SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = scheme.surfaceVariant,
        ),
        quote = SpanStyle(color = MaterialTheme.appColors.textSecondary),
        highlight = SpanStyle(
            background = scheme.primaryContainer,
            color = scheme.onPrimaryContainer,
        ),
    )
}

/**
 * 把 [parseMarkdown] 的块结构渲染成 [AnnotatedString]。
 *
 * 列表项前缀「• 」、引用前缀「> 」用纯文本拼，不用 SpanStyle--
 * 简单且渲染开销低。代码块用 `\n` 包裹保留块感。
 */
private fun renderBlocks(
    blocks: List<MarkdownBlock>,
    style: MarkdownStyle,
    highlight: String?,
): AnnotatedString = buildAnnotatedString {
    blocks.forEachIndexed { index, block ->
        if (index > 0) append("\n")
        when (block) {
            is MarkdownBlock.Heading -> {
                val span = when (block.level) {
                    1 -> style.h1
                    2 -> style.h2
                    else -> style.h3
                }
                withStyle(span) { appendSpans(block.spans, style) }
            }

            is MarkdownBlock.ListItem -> {
                append("•  ")
                appendSpans(block.spans, style)
            }

            is MarkdownBlock.Quote -> {
                withStyle(style.quote) {
                    append("│ ")
                    appendSpans(block.spans, style)
                }
            }

            is MarkdownBlock.CodeBlock -> {
                withStyle(style.codeBlock) {
                    append(block.content)
                }
            }

            is MarkdownBlock.Paragraph -> appendSpans(block.spans, style)
        }
    }
    applyHighlight(highlight, style.highlight)
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendSpans(
    spans: List<InlineSpan>,
    style: MarkdownStyle,
) {
    spans.forEach { span ->
        when (span) {
            is InlineSpan.Text -> append(span.text)
            is InlineSpan.Bold -> withStyle(style.bold) { append(span.text) }
            is InlineSpan.Code -> withStyle(style.inlineCode) { append(span.text) }
        }
    }
}

/** 在最终字符串上叠加搜索高亮：扫描所有命中区段，覆盖 highlight 样式。 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.applyHighlight(
    highlight: String?,
    style: SpanStyle,
) {
    if (highlight.isNullOrBlank()) return
    val text = this.toString()
    var from = 0
    while (from < text.length) {
        val idx = text.indexOf(highlight, from, ignoreCase = true)
        if (idx < 0) break
        addStyle(style, idx, idx + highlight.length)
        from = idx + highlight.length
    }
}

/** 笔记正文渲染入口。 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val style = markdownStyle()
    val blocks = parseMarkdown(content)
    val text = renderBlocks(blocks, style, highlight)
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
    )
}

/** 列表卡片用：取首行非空文本作为标题。空笔记返回「无标题」。 */
fun firstLine(content: String): String {
    val blocks = parseMarkdown(content)
    return blocks.firstOrNull()?.let { block ->
        when (block) {
            is MarkdownBlock.Heading -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.ListItem -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.Quote -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.CodeBlock -> block.content.lineSequence().firstOrNull().orEmpty()
            is MarkdownBlock.Paragraph -> block.spans.joinToString("") { it.text }
        }
    }?.ifBlank { "无标题" } ?: "无标题"
}
