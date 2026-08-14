package com.myapp.feature.knowledge.interview

/** 一道题：标题是题干，正文是答案（可含代码块、列表、图片）。 */
data class ParsedQuestion(
    val title: String,
    val body: String,
)

/** 一章：`# 一、Python` 这一级，下辖若干道题。 */
data class ParsedChapter(
    val title: String,
    val questions: List<ParsedQuestion>,
)

/**
 * 面试题 md 解析器（PRD 3.7）。纯函数、无 Android 依赖，可 JVM 单测。
 *
 * 文档结构约定（两篇文档实测一致）：
 * ```
 * # 火箭🚀          <- 文档标题，下面直接跟章节，本身不含题目
 * # 一、基础篇       <- 章节
 * ## 什么是多态      <- 一道题，题干
 * 正文……            <- 答案，直到下一个 ## 或 #
 * ### 补充           <- 三级及以下标题留在答案正文里，不单独成题
 * ```
 *
 * **最容易踩的坑：代码块里的 `#`**。两篇文档里有大量
 * ```
 * # greet_decorator 就是一个高阶函数
 * # 使用线程池
 * ```
 * 这类 Python/Shell 注释写在 ``` 围栏内。按行首 `#` 裸判标题会把它们全当成章节，
 * 后端那篇会凭空多出十几个「章节」，题目也会被从中间截断。
 * 所以解析必须先跟踪围栏状态，围栏内的行一律当正文。
 *
 * 另一个细节：围栏可能带语言标记（```Python），也可能是波浪线（~~~）；
 * 结束围栏的长度可以大于开始围栏，但不能更短（CommonMark 规则）。
 */
object InterviewMarkdownParser {

    private val fenceRegex = Regex("""^(\s{0,3})(`{3,}|~{3,})(.*)$""")
    private val h1Regex = Regex("""^#\s+(.+?)\s*$""")
    private val h2Regex = Regex("""^##\s+(.+?)\s*$""")

    /**
     * 解析整篇文档。
     *
     * [imagePathPrefix] 用于把 md 里的图片相对路径（`图片和附件/image%201.png`）
     * 换成 assets 里的实际路径（`img/image 1.png`）——见 [rewriteImagePaths]。
     *
     * 返回的章节里会**过滤掉没有题目的章节**：文档第一行的 `# 火箭🚀` / `# 面试题总`
     * 是文档标题而不是章节，它下面直到第一个真正章节之间没有 `##`，自然被这条规则滤掉，
     * 不需要为「第一个 # 是标题」写特例。
     */
    fun parse(markdown: String, imagePathPrefix: String = "img/"): List<ParsedChapter> {
        val chapters = mutableListOf<ParsedChapter>()

        var chapterTitle: String? = null
        var questions = mutableListOf<ParsedQuestion>()
        var questionTitle: String? = null
        val body = StringBuilder()

        var fence: String? = null

        fun flushQuestion() {
            val title = questionTitle ?: return
            questions.add(
                ParsedQuestion(
                    title = title,
                    body = rewriteImagePaths(body.toString().trim(), imagePathPrefix),
                ),
            )
            questionTitle = null
            body.setLength(0)
        }

        fun flushChapter() {
            flushQuestion()
            val title = chapterTitle
            if (title != null && questions.isNotEmpty()) {
                chapters.add(ParsedChapter(title, questions.toList()))
            }
            questions = mutableListOf()
            chapterTitle = null
        }

        for (line in markdown.lineSequence()) {
            val fenceMatch = fenceRegex.find(line)
            if (fenceMatch != null) {
                val marker = fenceMatch.groupValues[2]
                val info = fenceMatch.groupValues[3]
                if (fence == null) {
                    // 开围栏：``` 后面可以跟语言名
                    fence = marker
                } else if (marker[0] == fence[0] && marker.length >= fence.length && info.isBlank()) {
                    // 闭围栏：同种字符、不短于开围栏、后面不能再跟信息串
                    fence = null
                }
                if (questionTitle != null) body.appendLine(line)
                continue
            }
            if (fence != null) {
                // 围栏内：一律是正文，哪怕它长得像标题
                if (questionTitle != null) body.appendLine(line)
                continue
            }

            val h2 = h2Regex.find(line)
            if (h2 != null) {
                flushQuestion()
                // 章节外出现的 ## 直接丢弃：没有归属，收进来也无从展示
                if (chapterTitle != null) questionTitle = unescapeTitle(h2.groupValues[1])
                continue
            }

            val h1 = h1Regex.find(line)
            if (h1 != null) {
                flushChapter()
                chapterTitle = unescapeTitle(h1.groupValues[1])
                continue
            }

            if (questionTitle != null) body.appendLine(line)
        }
        flushChapter()

        return chapters
    }

    /**
     * 把 md 里的图片相对路径换成 assets 里的路径。
     *
     * 源文档里长这样：`![image\.png](图片和附件/image%201.png)`——
     * 目录名是中文、空格被转义成 `%20`。导入 assets 时目录统一改成 ASCII 的 `img/`，
     * 文件名保持原样（含空格），所以这里要同时做「换目录」和「解 %20」两件事。
     * 只认这一种前缀，不做通用 URL 解码：正文里可能有别的链接，不该被动到。
     */
    private fun rewriteImagePaths(text: String, prefix: String): String =
        imageLinkRegex.replace(text) { match ->
            val alt = match.groupValues[1]
            val path = match.groupValues[2]
                .removePrefix(ATTACHMENT_DIR)
                .replace("%20", " ")
            "![$alt]($prefix$path)"
        }

    /**
     * 标题里的反斜杠转义还原。
     *
     * 飞书导出会把 `&`、`(`、`+`、`.` 这些字符转义成 `\&`、`\(`……
     * 正文渲染时会统一还原，但标题是直接当字符串存库和显示的，
     * 不在这里处理的话章节名会显示成「九、Linux \& Docker」（真机实测）。
     */
    private fun unescapeTitle(title: String): String = title.replace(escapeRegex, "$1")

    private val escapeRegex = Regex("""\\(.)""")

    /** 源文档里图片附件的目录名（飞书导出固定用这个名字）。 */
    private const val ATTACHMENT_DIR = "图片和附件/"

    private val imageLinkRegex = Regex("""!\[([^\]]*)\]\((%s[^)]+)\)""".format(Regex.escape(ATTACHMENT_DIR)))
}
