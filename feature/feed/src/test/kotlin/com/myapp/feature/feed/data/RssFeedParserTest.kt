package com.myapp.feature.feed.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RssFeedParser] 纯函数测试：RSS 2.0 / Atom 两种格式，不碰网络也不碰数据库。 */
class RssFeedParserTest {

    private fun parse(xml: String) = RssFeedParser.parse(ByteArrayInputStream(xml.toByteArray()))

    @Test
    fun rss20_parsesTitleLinkGuidAndPubDate() {
        val feed = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Hello World</title>
                  <link>https://example.com/1</link>
                  <guid>urn:uuid:abc-123</guid>
                  <description>&lt;p&gt;A &lt;b&gt;summary&lt;/b&gt;.&lt;/p&gt;</description>
                  <content:encoded>&lt;p&gt;Full body text.&lt;/p&gt;&lt;p&gt;Second para.&lt;/p&gt;</content:encoded>
                  <pubDate>Wed, 02 Oct 2024 15:00:00 GMT</pubDate>
                  <enclosure url="https://example.com/cover.jpg" type="image/jpeg" />
                </item>
              </channel>
            </rss>
            """.trimIndent(),
        )

        assertEquals("Test Feed", feed.title)
        assertEquals(1, feed.articles.size)
        val article = feed.articles.single()
        assertEquals("Hello World", article.title)
        assertEquals("https://example.com/1", article.link)
        assertEquals("urn:uuid:abc-123", article.guid)
        assertEquals("A summary.", article.summary)
        assertEquals("Full body text.\n\nSecond para.", article.content)
        assertEquals("https://example.com/cover.jpg", article.coverImageUrl)
        assertEquals(1727881200000L, article.publishedAt)
    }

    @Test
    fun rss20_missingGuid_fallsBackToLink() {
        val feed = parse(
            """
            <rss version="2.0"><channel>
              <item>
                <title>No guid</title>
                <link>https://example.com/no-guid</link>
                <description>text</description>
              </item>
            </channel></rss>
            """.trimIndent(),
        )
        assertEquals("https://example.com/no-guid", feed.articles.single().guid)
    }

    @Test
    fun rss20_malformedPubDate_publishedAtIsNull() {
        val feed = parse(
            """
            <rss version="2.0"><channel>
              <item>
                <title>Bad date</title>
                <link>https://example.com/x</link>
                <pubDate>not-a-date</pubDate>
              </item>
            </channel></rss>
            """.trimIndent(),
        )
        assertNull(feed.articles.single().publishedAt)
    }

    @Test
    fun atom_parsesIdLinkAndPublished() {
        val feed = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <entry>
                <title>Atom Entry</title>
                <id>tag:example.com,2024:1</id>
                <link rel="alternate" href="https://example.com/atom/1" />
                <published>2024-10-02T15:00:00Z</published>
                <summary>Short summary</summary>
                <content>Full atom content</content>
              </entry>
            </feed>
            """.trimIndent(),
        )

        assertEquals("Atom Feed", feed.title)
        val article = feed.articles.single()
        assertEquals("Atom Entry", article.title)
        assertEquals("tag:example.com,2024:1", article.guid)
        assertEquals("https://example.com/atom/1", article.link)
        assertEquals("Short summary", article.summary)
        assertEquals("Full atom content", article.content)
        assertEquals(1727881200000L, article.publishedAt)
    }

    @Test
    fun rss20_noItems_returnsEmptyArticleList() {
        val feed = parse("<rss version=\"2.0\"><channel><title>Empty</title></channel></rss>")
        assertTrue(feed.articles.isEmpty())
        assertEquals("Empty", feed.title)
    }
}
