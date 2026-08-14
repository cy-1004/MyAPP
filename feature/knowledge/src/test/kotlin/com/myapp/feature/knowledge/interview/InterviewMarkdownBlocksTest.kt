package com.myapp.feature.knowledge.interview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewMarkdownBlocksTest {

    @Test
    fun parsesCodeBlockWithLanguage() {
        val blocks = parseInterviewBlocks(
            """
            说明文字

            ```Python
            def f():
                return 1
            ```
            """.trimIndent(),
        )

        assertEquals(2, blocks.size)
        assertEquals(InterviewBlock.Paragraph("说明文字"), blocks[0])
        val code = blocks[1] as InterviewBlock.Code
        assertEquals("Python", code.language)
        assertEquals("def f():\n    return 1", code.content)
    }

    @Test
    fun markersInsideCodeAreNotParsed() {
        val blocks = parseInterviewBlocks(
            """
            ```
            # 注释
            - 不是列表
            1. 不是有序列表
            ```
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        val code = blocks[0] as InterviewBlock.Code
        assertTrue(code.content.contains("# 注释"))
        assertTrue(code.content.contains("- 不是列表"))
        assertTrue(code.content.contains("1. 不是有序列表"))
    }

    @Test
    fun parsesImageBlock() {
        val blocks = parseInterviewBlocks("![图](img/image 1.png)")
        assertEquals(InterviewBlock.Image("图", "img/image 1.png"), blocks.single())
    }

    @Test
    fun parsesListsHeadingsAndQuote() {
        val blocks = parseInterviewBlocks(
            """
            ### 小标题

            - 要点一
            * 要点二

            1. 第一
            2. 第二

            > 引用
            """.trimIndent(),
        )

        assertEquals(InterviewBlock.Heading(3, "小标题"), blocks[0])
        assertEquals(InterviewBlock.Bullet("要点一"), blocks[1])
        assertEquals(InterviewBlock.Bullet("要点二"), blocks[2])
        assertEquals(InterviewBlock.Ordered("1", "第一"), blocks[3])
        assertEquals(InterviewBlock.Ordered("2", "第二"), blocks[4])
        assertEquals(InterviewBlock.Quote("引用"), blocks[5])
    }

    @Test
    fun unclosedFenceStillYieldsCode() {
        val blocks = parseInterviewBlocks("```\nabc")
        val code = blocks.single() as InterviewBlock.Code
        assertEquals("abc", code.content)
    }

    @Test
    fun previewStripsMarkupAndEscapes() {
        // 飞书导出的正文里到处是 \( \+ \. 这类转义，摘要里必须去掉
        val preview = plainTextPreview(
            """
            **高阶函数**是指满足以下任一条件的函数 \(高阶\)：

            ```Python
            code_should_not_appear()
            ```

            ![图](img/a.png)
            """.trimIndent(),
            limit = 100,
        )

        assertEquals("高阶函数是指满足以下任一条件的函数 (高阶)：", preview)
    }

    @Test
    fun previewTruncatesWithEllipsis() {
        val preview = plainTextPreview("一二三四五六七八九十", limit = 5)
        assertEquals("一二三四五…", preview)
    }
}
