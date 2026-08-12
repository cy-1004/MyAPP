package com.myapp.feature.feed.data

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** [RssFeedParser] 和 [RssOpml] 共用的最小 DOM 工具，同一个模块内不重复写。 */

/** 某些 feature 名在个别 XML 解析器实现上不被识别，容忍失败而不是让整个解析崩掉。 */
internal fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
    try {
        setFeature(name, value)
    } catch (_: Exception) {
    }
}

internal fun Element.children(tag: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.substringAfter(':') == tag) {
            result += node as Element
        }
    }
    return result
}
