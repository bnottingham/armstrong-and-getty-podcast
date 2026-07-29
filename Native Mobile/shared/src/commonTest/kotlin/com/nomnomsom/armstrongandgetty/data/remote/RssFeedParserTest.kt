package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.testutil.REAL_FEED_XML_SNIPPET
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RssFeedParserTest {

    private val parser = RssFeedParser(HttpClient())

    @Test
    fun parsesRealFeedShape() {
        val items = parser.parseRss(REAL_FEED_XML_SNIPPET)

        // Third item has no enclosure and must be dropped.
        assertEquals(2, items.size)

        val first = items[0]
        assertEquals("Fauci Is Guilty of Everything!!!", first.title)
        assertEquals("Tue, 28 Jul 2026 14:43:09 +0000", first.pubDate)
        assertEquals(2144, first.durationSeconds)
        assertTrue(first.audioUrl.startsWith("https://traffic.omny.fm/"))
        // HTML stripped from description
        assertTrue(!first.description.contains("<p>"))
    }

    @Test
    fun parsesDurationFormats() {
        val items = parser.parseRss(REAL_FEED_XML_SNIPPET)
        assertEquals(2144, items[0].durationSeconds)        // plain seconds
        assertEquals(35 * 60L, items[1].durationSeconds)    // mm:ss
    }

    @Test
    fun parsesGmtStylePubDate() {
        val items = parser.parseRss(REAL_FEED_XML_SNIPPET)
        assertEquals("Mon, 27 Jul 2026 14:23:57 GMT", items[1].pubDate)
    }
}
