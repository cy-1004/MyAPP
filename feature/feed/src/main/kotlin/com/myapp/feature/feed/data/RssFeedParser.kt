package com.myapp.feature.feed.data

import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** 一篇解析出来的文章，去重键、落库前的领域模型（PRD 3.9）。 */
data class ParsedArticle(
    val guid: String,
    val link: String,
    val title: String,
    val summary: String,
    /** 全文，`<content:encoded>`/Atom `<content>`，缺失时为 null。 */
    val content: String?,
    val coverImageUrl: String?,
    /** 解析失败或字段缺失时为 null，落库时由调用方兜底成抓取时间。 */
    val publishedAt: Long?,
)

data class ParsedFeed(
    val title: String?,
    val articles: List<ParsedArticle>,
)

/**
 * RSS 2.0 / Atom 解析器，纯函数（不做网络请求，不碰数据库），方便单测。
 *
 * 用 `javax.xml.parsers.DocumentBuilderFactory`（纯 JVM，不依赖 Android 框架）而不是
 * `android.util.Xml`——同样的 DOM API，但单测不需要 Robolectric 就能跑，更快。
 * 两种源格式用根标签区分：`<rss><channel><item>` 是 RSS 2.0，`<feed><entry>` 是 Atom。
 */
object RssFeedParser {

    fun parse(input: InputStream): ParsedFeed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // XXE 防护：这是解析不可信的外部订阅源，禁止加载外部实体/外部 DTD。
            // 不用 "disallow-doctype-decl"——那是 Xerces 专属 feature，真机上 Android 自带的
            // Harmony DocumentBuilderFactory 不认识它，setFeature 直接抛
            // ParserConfigurationException（单测用桌面 JDK 的 Xerces 实现，这个坑测不出来，
            // 真机验证时才炸——见交接文档记录的同类真机专属坑）。改用这三个更通用、
            // Android/JDK 都支持的 feature，只挡外部实体，不影响内部 DOCTYPE 里定义的实体。
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(input)
        val root = document.documentElement
        return when (root.tagName.substringAfter(':').lowercase()) {
            "feed" -> parseAtom(root)
            else -> parseRss(root)
        }
    }

    private fun parseRss(root: Element): ParsedFeed {
        val channel = root.children("channel").firstOrNull() ?: return ParsedFeed(null, emptyList())
        val feedTitle = channel.children("title").firstOrNull()?.textContent?.trim()
        val articles = channel.children("item").map { item ->
            val link = item.children("link").firstOrNull()?.textContent?.trim().orEmpty()
            val guid = item.children("guid").firstOrNull()?.textContent?.trim()?.ifBlank { null } ?: link
            ParsedArticle(
                guid = guid,
                link = link,
                title = item.children("title").firstOrNull()?.textContent?.trim().orEmpty(),
                summary = stripHtml(
                    item.children("description").firstOrNull()?.textContent.orEmpty(),
                ),
                content = item.children("encoded").firstOrNull()?.textContent?.let(::stripHtml)?.ifBlank { null },
                coverImageUrl = item.children("enclosure").firstOrNull()
                    ?.takeIf { it.getAttribute("type").startsWith("image") }
                    ?.getAttribute("url")?.ifBlank { null },
                publishedAt = item.children("pubDate").firstOrNull()?.textContent?.trim()
                    ?.let(::parseRfc822Date),
            )
        }
        return ParsedFeed(feedTitle, articles)
    }

    private fun parseAtom(root: Element): ParsedFeed {
        val feedTitle = root.children("title").firstOrNull()?.textContent?.trim()
        val articles = root.children("entry").map { entry ->
            val link = entry.children("link").firstOrNull { it.getAttribute("rel").let { rel -> rel.isBlank() || rel == "alternate" } }
                ?.getAttribute("href")?.trim().orEmpty()
            val guid = entry.children("id").firstOrNull()?.textContent?.trim()?.ifBlank { null } ?: link
            val published = entry.children("published").firstOrNull()?.textContent?.trim()
                ?: entry.children("updated").firstOrNull()?.textContent?.trim()
            val rawContent = entry.children("content").firstOrNull()?.textContent
            ParsedArticle(
                guid = guid,
                link = link,
                title = entry.children("title").firstOrNull()?.textContent?.trim().orEmpty(),
                summary = stripHtml(entry.children("summary").firstOrNull()?.textContent ?: rawContent.orEmpty()),
                content = rawContent?.let(::stripHtml)?.ifBlank { null },
                coverImageUrl = null,
                publishedAt = published?.let(::parseIso8601Date),
            )
        }
        return ParsedFeed(feedTitle, articles)
    }

    private fun parseRfc822Date(raw: String): Long? = try {
        OffsetDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }

    private fun parseIso8601Date(raw: String): Long? = try {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }

    /**
     * 纯文本展示用，不做真 HTML 渲染（Compose Text 展示，不接 WebView）。
     * 块级标签换成换行保留段落感，其余标签直接去掉。
     */
    private fun stripHtml(html: String): String {
        val withBreaks = html.replace(Regex("(?i)</p>"), "\n\n").replace(Regex("(?i)<br\\s*/?>"), "\n")
        val stripped = withBreaks.replace(Regex("<[^>]*>"), "")
        return stripped.lines().joinToString("\n") { it.replace(Regex("\\s+"), " ").trim() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
