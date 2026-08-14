package com.myapp.feature.knowledge.interview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 面试题 md 解析（PRD 3.7）。
 *
 * 重点覆盖代码块里的 `#`——两篇真实文档里有几十处，是最容易把整份题库解析歪的地方。
 */
class InterviewMarkdownParserTest {

    @Test
    fun parsesChaptersAndQuestions() {
        val md = """
            # 面试题总

            # 一、Python

            ## 什么是魔术方法

            以双下划线开头结尾的方法。

            ## 装饰器

            本质是语法糖。

            # 二、数据库

            ## 什么是索引

            加速查询的数据结构。
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md)

        assertEquals(listOf("一、Python", "二、数据库"), chapters.map { it.title })
        assertEquals(listOf("什么是魔术方法", "装饰器"), chapters[0].questions.map { it.title })
        assertEquals("以双下划线开头结尾的方法。", chapters[0].questions[0].body)
        assertEquals(listOf("什么是索引"), chapters[1].questions.map { it.title })
    }

    @Test
    fun documentTitleIsNotAChapter() {
        // 文档第一行的 `# 面试题总` 下面没有题目，应该被滤掉而不是变成一个空章节
        val md = """
            # 面试题总

            # 一、Python

            ## 装饰器

            答案
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md)

        assertEquals(1, chapters.size)
        assertEquals("一、Python", chapters[0].title)
    }

    @Test
    fun hashInsideCodeFenceIsNotAHeading() {
        // 这是真实文档里的形态：代码块里全是 # 注释
        val md = """
            # 一、Python

            ## 高阶函数

            示例代码：

            ```Python
            def greet_decorator(func):
                return func

            # greet_decorator 就是一个高阶函数
            # 使用线程池
            ## 这行也不是题目
            ```

            以上就是高阶函数。
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md)

        assertEquals(1, chapters.size)
        // 只有一道题：代码块里的 # / ## 全部留在正文里
        assertEquals(listOf("高阶函数"), chapters[0].questions.map { it.title })
        val body = chapters[0].questions[0].body
        assertTrue("代码块内容应保留", body.contains("# greet_decorator 就是一个高阶函数"))
        assertTrue("代码块内容应保留", body.contains("## 这行也不是题目"))
        assertTrue("围栏应保留", body.contains("```Python"))
        assertTrue("围栏后的正文应保留", body.contains("以上就是高阶函数。"))
    }

    @Test
    fun tildeFenceAlsoRecognized() {
        val md = """
            # 章节

            ## 题目

            ~~~
            # 不是标题
            ~~~

            结尾
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md)

        assertEquals(listOf("题目"), chapters[0].questions.map { it.title })
        assertTrue(chapters[0].questions[0].body.contains("# 不是标题"))
    }

    @Test
    fun h3StaysInBody() {
        val md = """
            # 章节

            ## 题目

            ### 小标题

            内容
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md)

        assertEquals(1, chapters[0].questions.size)
        assertTrue(chapters[0].questions[0].body.contains("### 小标题"))
    }

    @Test
    fun rewritesImagePathsToAssets() {
        val md = """
            # 章节

            ## 带图的题

            ![image\.png](图片和附件/image%201.png)
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md, imagePathPrefix = "img/")

        // 目录换成 img/，%20 还原成空格（assets 里的文件名带空格）
        assertTrue(
            chapters[0].questions[0].body,
            chapters[0].questions[0].body.contains("](img/image 1.png)"),
        )
    }

    @Test
    fun questionsBeforeAnyChapterAreDropped() {
        // 没有归属章节的 ## 收进来也没法展示，直接丢弃
        val md = """
            ## 野题目

            内容

            # 章节

            ## 正常题目

            内容
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md)

        assertEquals(1, chapters.size)
        assertEquals(listOf("正常题目"), chapters[0].questions.map { it.title })
    }

    @Test
    fun unescapesTitles() {
        // 真机上章节名显示成了「九、Linux \& Docker」——飞书导出的转义没还原
        val md = """
            # 九、Linux \& Docker

            ## 什么是 Docker \(容器\)

            答案
        """.trimIndent()

        val chapters = InterviewMarkdownParser.parse(md)

        assertEquals("九、Linux & Docker", chapters[0].title)
        assertEquals("什么是 Docker (容器)", chapters[0].questions[0].title)
    }

    @Test
    fun emptyInputYieldsNoChapters() {
        assertEquals(emptyList<ParsedChapter>(), InterviewMarkdownParser.parse(""))
    }
}
