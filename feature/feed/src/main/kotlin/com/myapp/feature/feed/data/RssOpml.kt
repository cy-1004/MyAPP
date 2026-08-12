package com.myapp.feature.feed.data

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** OPML 里解析出的一条订阅源（PRD 3.9：导入导出）。 */
data class OpmlEntry(val url: String, val title: String, val groupName: String)

/**
 * OPML 2.0 读写，纯函数（不碰网络/数据库/Uri），与 [RssFeedParser] 同一套约定，方便单测。
 *
 * 分组用 `<outline>` 嵌套表达：有 `xmlUrl` 属性的是一条订阅源，没有 `xmlUrl` 但有子
 * `<outline>` 的是一个分组容器，子项 groupName 取父节点的 `text`/`title`。顶层直接挂着
 * 的订阅源（没有分组）groupName 为空串，与 `RssSourceEntity.groupName` 默认值一致。
 */
object RssOpml {

    fun export(sources: List<RssSourceUi>): String {
        val body = buildString {
            sources.groupBy { it.groupName }.forEach { (group, items) ->
                if (group.isBlank()) {
                    items.forEach { append(outlineTag(it, indent = "")) }
                } else {
                    append("<outline text=\"${escape(group)}\">\n")
                    items.forEach { append(outlineTag(it, indent = "  ")) }
                    append("</outline>\n")
                }
            }
        }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<opml version=\"2.0\">\n")
            append("<head><title>MyAPP RSS 订阅</title></head>\n")
            append("<body>\n")
            append(body)
            append("</body>\n")
            append("</opml>\n")
        }
    }

    private fun outlineTag(source: RssSourceUi, indent: String): String {
        val title = source.title.ifBlank { source.url }
        return "$indent<outline text=\"${escape(title)}\" title=\"${escape(title)}\" " +
            "type=\"rss\" xmlUrl=\"${escape(source.url)}\" />\n"
    }

    private fun escape(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** 解析失败（不是合法 XML/OPML）时直接抛异常，由调用方 `runCatching` 兜底。 */
    fun parse(input: InputStream): List<OpmlEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(input)
        val body = document.documentElement.children("body").firstOrNull() ?: return emptyList()
        return body.children("outline").flatMap { outline -> collectEntries(outline, groupName = "") }
    }

    private fun collectEntries(outline: Element, groupName: String): List<OpmlEntry> {
        val xmlUrl = outline.getAttribute("xmlUrl").trim()
        if (xmlUrl.isNotBlank()) {
            val title = outline.getAttribute("title").ifBlank { outline.getAttribute("text") }.trim()
            return listOf(OpmlEntry(url = xmlUrl, title = title, groupName = groupName))
        }
        val childGroupName = outline.getAttribute("title").ifBlank { outline.getAttribute("text") }.trim()
        return outline.children("outline").flatMap { child -> collectEntries(child, groupName = childGroupName) }
    }
}
