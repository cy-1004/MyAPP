package com.myapp.feature.feed.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RssOpml] 纯函数测试：导出/导入不碰网络也不碰数据库，PRD 3.9。 */
class RssOpmlTest {

    private fun parse(xml: String) = RssOpml.parse(ByteArrayInputStream(xml.toByteArray()))

    @Test
    fun parse_groupedAndUngroupedOutlines() {
        val entries = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Subs</title></head>
              <body>
                <outline text="科技">
                  <outline text="IT之家" title="IT之家" type="rss" xmlUrl="https://www.ithome.com/rss/" />
                </outline>
                <outline text="Hacker News" title="Hacker News" type="rss" xmlUrl="https://hnrss.org/frontpage" />
              </body>
            </opml>
            """.trimIndent(),
        )

        assertEquals(2, entries.size)
        val grouped = entries.single { it.url == "https://www.ithome.com/rss/" }
        assertEquals("IT之家", grouped.title)
        assertEquals("科技", grouped.groupName)
        val ungrouped = entries.single { it.url == "https://hnrss.org/frontpage" }
        assertEquals("", ungrouped.groupName)
    }

    @Test
    fun parse_missingTitleFallsBackToText() {
        val entries = parse(
            """
            <opml version="2.0"><body>
              <outline text="No Title Attr" type="rss" xmlUrl="https://example.com/feed.xml" />
            </body></opml>
            """.trimIndent(),
        )

        assertEquals("No Title Attr", entries.single().title)
    }

    @Test
    fun parse_emptyBody_returnsEmptyList() {
        val entries = parse("<opml version=\"2.0\"><body></body></opml>")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun export_thenParse_roundTripsUrlTitleAndGroup() {
        val sources = listOf(
            RssSourceUi(id = 1, url = "https://a.example/feed.xml", title = "Feed A", groupName = "科技", enabled = true, lastFetchAt = null),
            RssSourceUi(id = 2, url = "https://b.example/feed.xml", title = "Feed B", groupName = "", enabled = true, lastFetchAt = null),
        )

        val opml = RssOpml.export(sources)
        val entries = parse(opml)

        assertEquals(2, entries.size)
        val a = entries.single { it.url == "https://a.example/feed.xml" }
        assertEquals("Feed A", a.title)
        assertEquals("科技", a.groupName)
        val b = entries.single { it.url == "https://b.example/feed.xml" }
        assertEquals("Feed B", b.title)
        assertEquals("", b.groupName)
    }

    @Test
    fun export_escapesSpecialCharsInTitle() {
        val sources = listOf(
            RssSourceUi(id = 1, url = "https://a.example/feed.xml", title = "R&D <News>", groupName = "", enabled = true, lastFetchAt = null),
        )

        val opml = RssOpml.export(sources)
        val entries = parse(opml)

        assertEquals("R&D <News>", entries.single().title)
    }
}
