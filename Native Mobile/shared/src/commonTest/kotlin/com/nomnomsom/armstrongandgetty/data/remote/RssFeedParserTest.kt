package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.testutil.REAL_FEED_XML_SNIPPET
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // --- HTML entities in CDATA descriptions -------------------------------
    //
    // Omny wraps descriptions in CDATA, so the XML reader hands the HTML back
    // verbatim and every entity in it is ours to decode. `&nbsp;` used to reach
    // the episode list as literal text and was visible in the store screenshots.

    private fun itemXml(title: String, description: String) = """
        <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <item>
              <title>$title</title>
              <itunes:title>$title</itunes:title>
              <description><![CDATA[$description]]></description>
              <pubDate>Tue, 28 Jul 2026 14:43:09 +0000</pubDate>
              <itunes:duration>2144</itunes:duration>
              <enclosure url="https://traffic.omny.fm/x.mp3" type="audio/mpeg"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun decodesNonBreakingSpaceInDescription() {
        val items = parser.parseRss(
            itemXml("Hour One", "<p>The Best Weekend Talk Show In America&nbsp;for the weekend</p>")
        )
        assertFalse(items[0].description.contains("&nbsp;"))
        assertEquals("The Best Weekend Talk Show In America for the weekend", items[0].description)
    }

    @Test
    fun decodesNamedAndNumericEntities() {
        val items = parser.parseRss(
            itemXml("Hour One", "national debt &amp; more Katie Green&#39;s Headlines&hellip; &#x201C;quoted&#x201D;")
        )
        assertEquals(
            "national debt & more Katie Green's Headlines\u2026 \u201Cquoted\u201D",
            items[0].description
        )
    }

    @Test
    fun decodesEntitiesInASinglePassSoEscapedMarkupStaysEscaped() {
        // `&amp;lt;` is an escaped "&lt;" — it must decode to the literal text
        // "&lt;", not all the way to "<". A chained .replace("&amp;", "&") would
        // decode it twice and resurrect a tag.
        val items = parser.parseRss(itemXml("Hour One", "a &amp;lt;b&amp;gt; c"))
        assertEquals("a &lt;b&gt; c", items[0].description)
    }

    @Test
    fun leavesUnknownEntitiesUntouched() {
        val items = parser.parseRss(itemXml("Hour One", "keep &notarealentity; visible"))
        assertEquals("keep &notarealentity; visible", items[0].description)
    }

    // --- namespace-aware tag matching --------------------------------------

    @Test
    fun doesNotConcatenateItunesTitleOntoTitle() {
        // <title> and <itunes:title> share a localName. Matching on localName
        // alone appended both, producing "Hour OneHour One" in the player.
        val items = parser.parseRss(itemXml("The Best Weekend Talk Show In America Hour One", "<p>x</p>"))
        assertEquals("The Best Weekend Talk Show In America Hour One", items[0].title)
    }

    @Test
    fun stillReadsDurationFromThePrefixedItunesElement() {
        // itunes:duration is the one field that legitimately comes from a
        // prefixed element, so the namespace guard must not exclude it.
        val items = parser.parseRss(itemXml("Hour One", "<p>x</p>"))
        assertEquals(2144, items[0].durationSeconds)
    }

    @Test
    fun keepsSpacesAroundAnEntityThatSplitsATextNode() {
        // "Armstrong & Getty" arrives as three TEXT events; trimming each
        // fragment individually used to collapse it to "Armstrong&Getty".
        val items = parser.parseRss(itemXml("Armstrong &amp; Getty Hour One", "<p>x</p>"))
        assertEquals("Armstrong & Getty Hour One", items[0].title)
    }
}
