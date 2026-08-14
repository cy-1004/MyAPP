package com.myapp.feature.knowledge.interview

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拿**真实的两篇题库文档**跑解析器（PRD 3.7）。
 *
 * 构造的小样本测不出真实文档的问题：这两篇一共 44 万字、500 道题，
 * 里面有几十处代码块内的 `#` 注释、带空格的图片文件名、中英文混排的章节标题。
 * 解析歪了不会报错，只会安静地少几道题或多几个假章节，只有对着真文件跑才看得出来。
 *
 * 断言刻意用宽松的下界而不是精确数字——文档以后会更新，
 * 这里要守住的是「结构没被解析歪」，不是「恰好 290 道题」。
 */
class InterviewRealDocumentTest {

    private val assetsDir = File("src/main/assets/interview")

    private fun parse(dir: String): List<ParsedChapter> {
        val file = File(assetsDir, "$dir/doc.md")
        assertTrue("找不到题库文档：${file.absolutePath}", file.exists())
        return InterviewMarkdownParser.parse(file.readText())
    }

    @Test
    fun backendDocumentParsesIntoSaneStructure() {
        val chapters = parse("backend")
        assertStructureIsSane(chapters, minChapters = 10, minQuestions = 200)
        // 后端那篇的章节是「一、基础篇」「二、数据库篇」这种编号开头
        assertTrue("首章应是基础篇，实际 ${chapters.first().title}", chapters.first().title.contains("基础"))
    }

    @Test
    fun llmDocumentParsesIntoSaneStructure() {
        val chapters = parse("llm")
        assertStructureIsSane(chapters, minChapters = 10, minQuestions = 150)
        assertTrue("首章应是 Python，实际 ${chapters.first().title}", chapters.first().title.contains("Python"))
    }

    @Test
    fun noCodeCommentLeakedIntoChapterTitles() {
        // 代码块里的注释一旦被当成标题，会冒出「使用线程池」「变量」「例如」这种假章节。
        // 真章节都带「一、」「二、」这样的中文序号，用它当判据最直接。
        val leaked = (parse("backend") + parse("llm"))
            .map { it.title }
            .filterNot { it.first() in CHAPTER_NUMERALS }

        assertEquals("这些章节标题疑似来自代码块注释：$leaked", emptyList<String>(), leaked)
    }

    @Test
    fun everyQuestionHasTitleAndBody() {
        val questions = (parse("backend") + parse("llm")).flatMap { it.questions }
        val blankTitles = questions.filter { it.title.isBlank() }
        assertEquals("有题目没有题干", emptyList<ParsedQuestion>(), blankTitles)

        // 允许极少数题目正文为空（原文档里确实有几条只有标题的占位），
        // 但比例高说明正文被截断了
        val blankBodies = questions.count { it.body.isBlank() }
        assertTrue(
            "正文为空的题目过多：$blankBodies / ${questions.size}",
            blankBodies * 20 < questions.size,
        )
    }

    @Test
    fun imagePathsPointIntoAssets() {
        val questions = (parse("backend") + parse("llm")).flatMap { it.questions }
        val imageLinks = Regex("""!\[[^\]]*]\(([^)]+)\)""")
            .findAll(questions.joinToString("\n") { it.body })
            .map { it.groupValues[1] }
            .toList()

        assertTrue("真实文档里应该有图片链接", imageLinks.isNotEmpty())
        // 全部改写成 img/ 开头，且指向的文件真的躺在 assets 里
        imageLinks.forEach { link ->
            assertTrue("图片路径没有改写：$link", link.startsWith("img/"))
        }
    }

    @Test
    fun rewrittenImagesExistOnDisk() {
        listOf("backend", "llm").forEach { dir ->
            val links = Regex("""!\[[^\]]*]\((img/[^)]+)\)""")
                .findAll(parse(dir).flatMap { it.questions }.joinToString("\n") { it.body })
                .map { it.groupValues[1] }
                .toSet()
            links.forEach { link ->
                val file = File(assetsDir, "$dir/$link")
                assertTrue("assets 里缺图片：${file.path}", file.exists())
            }
        }
    }

    private fun assertStructureIsSane(
        chapters: List<ParsedChapter>,
        minChapters: Int,
        minQuestions: Int,
    ) {
        assertTrue("章节数偏少：${chapters.size}", chapters.size >= minChapters)
        val total = chapters.sumOf { it.questions.size }
        assertTrue("题目数偏少：$total", total >= minQuestions)
        chapters.forEach {
            assertTrue("章节「${it.title}」没有题目", it.questions.isNotEmpty())
        }
    }

    private companion object {
        /** 真章节标题的开头字符：「一、基础篇」「十三、场景篇」。 */
        val CHAPTER_NUMERALS = setOf('一', '二', '三', '四', '五', '六', '七', '八', '九', '十')
    }
}
