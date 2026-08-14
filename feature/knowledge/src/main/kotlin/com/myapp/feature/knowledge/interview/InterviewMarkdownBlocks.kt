package com.myapp.feature.knowledge.interview

/**
 * 面试题正文的块结构。纯函数解析，可 JVM 单测。
 *
 * 为什么不复用 `:feature:note` 的 `MarkdownRenderer`：
 * 一是 feature 之间不能互相依赖（PRD 4.7.1）；
 * 二是那个渲染器把所有块拼成一个 `AnnotatedString`，没有图片，
 * 代码块也只能靠 background span 表现、拿不到真正的块状底色与横向滚动。
 * 面试题正文里代码块和配图是主要内容，必须按块渲染。
 */
sealed interface InterviewBlock {
    data class Heading(val level: Int, val text: String) : InterviewBlock
    data class Paragraph(val text: String) : InterviewBlock
    data class Bullet(val text: String) : InterviewBlock
    data class Ordered(val index: String, val text: String) : InterviewBlock
    data class Quote(val text: String) : InterviewBlock

    /** [language] 可能为空（```后面没写语言）。 */
    data class Code(val language: String?, val content: String) : InterviewBlock

    /** [path] 是相对 assets 文档目录的路径，如 `img/image 1.png`。 */
    data class Image(val alt: String, val path: String) : InterviewBlock
}

private val IMAGE_REGEX = Regex("""^!\[([^\]]*)]\(([^)]+)\)\s*$""")
private val FENCE_REGEX = Regex("""^\s{0,3}(`{3,}|~{3,})(.*)$""")
private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+?)\s*$""")
private val ORDERED_REGEX = Regex("""^\s*(\d+)[.、)]\s+(.+?)\s*$""")

/**
 * 把一道题的正文拆成块。
 *
 * 与 [InterviewMarkdownParser] 一样，围栏内的一切都当代码，不再识别标题/列表——
 * 题库里大量答案就是「一段话 + 一段代码」，代码里的 `#` `-` `1.` 都不能被误读。
 */
fun parseInterviewBlocks(markdown: String): List<InterviewBlock> {
    val blocks = mutableListOf<InterviewBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var fence: String? = null
    var codeLanguage: String? = null

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += InterviewBlock.Paragraph(text)
        paragraph.setLength(0)
    }

    for (line in markdown.lineSequence()) {
        val fenceMatch = FENCE_REGEX.find(line)
        if (fenceMatch != null) {
            val marker = fenceMatch.groupValues[1]
            val info = fenceMatch.groupValues[2].trim()
            if (fence == null) {
                flushParagraph()
                fence = marker
                codeLanguage = info.ifBlank { null }
            } else if (marker[0] == fence[0] && marker.length >= fence.length && info.isEmpty()) {
                blocks += InterviewBlock.Code(codeLanguage, code.toString().trimEnd('\n'))
                code.setLength(0)
                fence = null
                codeLanguage = null
            } else {
                code.appendLine(line)
            }
            continue
        }
        if (fence != null) {
            code.appendLine(line)
            continue
        }

        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flushParagraph()

            IMAGE_REGEX.matches(trimmed) -> {
                flushParagraph()
                val m = IMAGE_REGEX.find(trimmed)!!
                blocks += InterviewBlock.Image(m.groupValues[1], m.groupValues[2])
            }

            HEADING_REGEX.matches(line) -> {
                flushParagraph()
                val m = HEADING_REGEX.find(line)!!
                blocks += InterviewBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += InterviewBlock.Bullet(trimmed.substring(2).trim())
            }

            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks += InterviewBlock.Quote(trimmed.removePrefix("> ").trim())
            }

            ORDERED_REGEX.matches(line) -> {
                flushParagraph()
                val m = ORDERED_REGEX.find(line)!!
                blocks += InterviewBlock.Ordered(m.groupValues[1], m.groupValues[2])
            }

            // 同一段里的连续行合并成一个段落，保持原文的软换行语义
            else -> paragraph.appendLine(trimmed)
        }
    }

    flushParagraph()
    // 围栏未闭合：已累积的内容仍当代码块输出，不丢内容
    if (fence != null && code.isNotEmpty()) {
        blocks += InterviewBlock.Code(codeLanguage, code.toString().trimEnd('\n'))
    }
    return blocks
}

/**
 * 去掉 md 的行内标记，用于卡片摘要这种只要纯文字的地方。
 *
 * 飞书导出的正文里有大量反斜杠转义（`\(`、`\+`、`\.`），直接显示很难看，
 * 一并去掉。
 */
fun plainTextPreview(markdown: String, limit: Int): String {
    val text = parseInterviewBlocks(markdown)
        .asSequence()
        .mapNotNull { block ->
            when (block) {
                is InterviewBlock.Paragraph -> block.text
                is InterviewBlock.Bullet -> block.text
                is InterviewBlock.Ordered -> block.text
                is InterviewBlock.Quote -> block.text
                is InterviewBlock.Heading -> block.text
                // 摘要里放代码和图片没有意义，跳过
                is InterviewBlock.Code, is InterviewBlock.Image -> null
            }
        }
        .joinToString(" ")
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""`(.+?)`"""), "$1")
        .replace(Regex("""\\(.)"""), "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()
    return if (text.length <= limit) text else text.take(limit).trimEnd() + "…"
}
