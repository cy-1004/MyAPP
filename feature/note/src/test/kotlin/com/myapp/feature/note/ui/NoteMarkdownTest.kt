package com.myapp.feature.note.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 解析的回归测试（PRD 3.4）。
 *
 * 解析错了 UI 照样显示一个像模像样的字符串，肉眼很难发现--
 * 比如未闭合 `**` 把后续整段都吞进 bold，或者代码块内的 `#` 被误解析为标题。
 * 用构造好的样本比对是唯一可靠的方式。
 */
class NoteMarkdownTest {

    @Test
    fun `空字符串返回空块列表`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("   ").isEmpty())
    }

    @Test
    fun `一级标题`() {
        val blocks = parseMarkdown("# 标题")
        assertEquals(1, blocks.size)
        val heading = blocks[0] as MarkdownBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("标题", heading.spans.joinToString("") { it.text })
    }

    @Test
    fun `二级与三级标题`() {
        val blocks = parseMarkdown("## 二级\n### 三级")
        assertEquals(2, blocks.size)
        assertEquals(2, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals(3, (blocks[1] as MarkdownBlock.Heading).level)
    }

    @Test
    fun `列表项支持减号与星号`() {
        val blocks = parseMarkdown("- 项一\n* 项二")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.ListItem)
        assertTrue(blocks[1] is MarkdownBlock.ListItem)
        assertEquals("项一", (blocks[0] as MarkdownBlock.ListItem).spans.joinToString("") { it.text })
    }

    @Test
    fun `引用块`() {
        val blocks = parseMarkdown("> 引用文本")
        assertEquals(1, blocks.size)
        val quote = blocks[0] as MarkdownBlock.Quote
        assertEquals("引用文本", quote.spans.joinToString("") { it.text })
    }

    @Test
    fun `代码块闭合`() {
        val blocks = parseMarkdown("```\ncode line 1\ncode line 2\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("code line 1\ncode line 2", code.content)
    }

    @Test
    fun `代码块未闭合延续到文末`() {
        val blocks = parseMarkdown("```\n未闭合的代码")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("未闭合的代码", code.content)
    }

    @Test
    fun `代码块内的井号不解析为标题`() {
        val blocks = parseMarkdown("```\n# 不是标题\n- 不是列表\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("# 不是标题\n- 不是列表", code.content)
    }

    @Test
    fun `行内加粗`() {
        val spans = parseInline("这是 **加粗** 文本")
        assertEquals(3, spans.size)
        assertEquals("这是 ", (spans[0] as InlineSpan.Text).text)
        assertEquals("加粗", (spans[1] as InlineSpan.Bold).text)
        assertEquals(" 文本", (spans[2] as InlineSpan.Text).text)
    }

    @Test
    fun `行内代码`() {
        val spans = parseInline("调用 `foo()` 函数")
        assertEquals(3, spans.size)
        assertEquals("foo()", (spans[1] as InlineSpan.Code).text)
    }

    @Test
    fun `未闭合的加粗按字面量输出`() {
        val spans = parseInline("这是 **未闭合 的文本")
        assertEquals(1, spans.size)
        assertEquals("这是 **未闭合 的文本", (spans[0] as InlineSpan.Text).text)
    }

    @Test
    fun `加粗与代码混合取最近匹配`() {
        // `code` 在前，**bold** 在后
        val spans = parseInline("`a` 然后 **b**")
        assertEquals(3, spans.size)
        assertTrue(spans[0] is InlineSpan.Code)
        assertEquals("a", (spans[0] as InlineSpan.Code).text)
        assertEquals(" 然后 ", (spans[1] as InlineSpan.Text).text)
        assertTrue(spans[2] is InlineSpan.Bold)
        assertEquals("b", (spans[2] as InlineSpan.Bold).text)
    }

    @Test
    fun `混合块级文档`() {
        val content = """
            # 项目笔记

            这是正文。

            - 任务一
            - 任务二

            ```kotlin
            val x = 1
            ```
        """.trimIndent()

        val blocks = parseMarkdown(content)
        // 标题 / 段落 / 列表 / 列表 / 代码块（空行不生成块）
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertTrue(blocks[2] is MarkdownBlock.ListItem)
        assertTrue(blocks[3] is MarkdownBlock.ListItem)
        assertTrue(blocks[4] is MarkdownBlock.CodeBlock)
    }

    @Test
    fun `firstLine 取首行非空文本`() {
        assertEquals("标题", firstLine("# 标题\n正文"))
        assertEquals("正文", firstLine("正文"))
        assertEquals("无标题", firstLine(""))
        assertEquals("无标题", firstLine("   "))
    }
}
