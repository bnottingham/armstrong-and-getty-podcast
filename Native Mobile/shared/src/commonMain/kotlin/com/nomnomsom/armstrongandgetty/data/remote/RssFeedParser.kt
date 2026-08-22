package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.RssItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

const val APP_USER_AGENT = "ArmstrongGettyPodcast/1.0"

class RssFeedParser(
    private val httpClient: HttpClient
) : FeedSource {
    companion object {
        const val FEED_URL = "https://www.omnycontent.com/d/playlist/e73c998e-6e60-432f-8610-ae210140c5b1/0516ff28-c0d6-492a-b264-ae3900375fc8/4db37684-c7ed-4964-843c-ae3900375fd7/podcast.rss"
    }

    override suspend fun fetchFeed(): Result<List<RssItem>> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(FEED_URL) {
                header(HttpHeaders.UserAgent, APP_USER_AGENT)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(Exception("HTTP ${response.status.value}"))
            }
            Result.success(parseRss(response.bodyAsText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Internal so the test harness can feed it raw XML fixtures without a network stack.
    internal fun parseRss(xml: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val reader = xmlStreaming.newReader(xml)

        var inItem = false
        var title = ""
        var description = ""
        var pubDate = ""
        var audioUrl = ""
        var durationSeconds = 0L
        var currentTag = ""
        // Captured at START_ELEMENT: the reader only exposes a prefix there, and
        // matching on localName alone makes <itunes:title> indistinguishable from
        // <title>. Omny sends both, which used to concatenate the title twice.
        var currentTagPrefixed = false

        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> {
                        currentTag = reader.localName
                        currentTagPrefixed = reader.prefix.isNotEmpty()
                        if (currentTag == "item") {
                            inItem = true
                            title = ""
                            description = ""
                            pubDate = ""
                            audioUrl = ""
                            durationSeconds = 0L
                        }
                        if (inItem && currentTag == "enclosure") {
                            for (i in 0 until reader.attributeCount) {
                                if (reader.getAttributeLocalName(i) == "url") {
                                    audioUrl = reader.getAttributeValue(i)
                                }
                            }
                        }
                    }

                    EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> {
                        if (inItem) {
                            // Accumulated raw, trimmed once at the end: a text node
                            // split around an entity ("Armstrong &amp; Getty" arrives
                            // as three events) loses its spaces if each fragment is
                            // trimmed individually.
                            val text = reader.text
                            when {
                                currentTag == "title" && !currentTagPrefixed -> title += text
                                currentTag == "description" && !currentTagPrefixed -> description += text
                                currentTag == "pubDate" && !currentTagPrefixed -> pubDate += text
                                // itunes:duration is the only field we read from a
                                // prefixed element, so it is matched by name alone.
                                currentTag == "duration" -> durationSeconds = parseDuration(text.trim())
                                else -> Unit
                            }
                        }
                    }

                    EventType.END_ELEMENT -> {
                        if (reader.localName == "item" && inItem) {
                            inItem = false
                            if (audioUrl.isNotEmpty()) {
                                items.add(
                                    RssItem(
                                        title = title.trim(),
                                        description = cleanDescription(description),
                                        pubDate = pubDate.trim(),
                                        audioUrl = audioUrl,
                                        durationSeconds = durationSeconds
                                    )
                                )
                            }
                        }
                        currentTag = ""
                    }

                    else -> Unit
                }
            }
        } finally {
            reader.close()
        }

        return items
    }

    /** itunes:duration may be "2100" (seconds), "35:00" (mm:ss), or "1:05:30" (h:mm:ss). */
    private fun parseDuration(text: String): Long {
        val parts = text.split(":")
        return when (parts.size) {
            1 -> parts[0].toLongOrNull() ?: 0L
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0
                val s = parts[1].toLongOrNull() ?: 0
                m * 60 + s
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0
                val m = parts[1].toLongOrNull() ?: 0
                val s = parts[2].toLongOrNull() ?: 0
                h * 3600 + m * 60 + s
            }
            else -> 0L
        }
    }

    private fun cleanDescription(html: String): String {
        return decodeHtmlEntities(html.replace(Regex("<[^>]*>"), ""))
            // A decoded NBSP is not matched by \s, so it would survive the collapse
            // below and show up as a stray double space.
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

private val ENTITY_PATTERN = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")

private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
    "hellip" to "\u2026",
    "mdash" to "\u2014",
    "ndash" to "\u2013",
    "lsquo" to "\u2018",
    "rsquo" to "\u2019",
    "ldquo" to "\u201C",
    "rdquo" to "\u201D",
    "bull" to "\u2022",
    "middot" to "\u00B7",
    "deg" to "\u00B0",
    "trade" to "\u2122",
    "copy" to "\u00A9",
    "reg" to "\u00AE"
)

/**
 * Decodes the HTML entities that survive inside a CDATA description, which the XML
 * reader hands back verbatim.
 *
 * One left-to-right pass, deliberately: a chain of `.replace("&amp;", "&")` calls
 * decodes `&amp;lt;` twice and turns escaped markup back into a live tag. Scanning
 * once means the text produced by a replacement is never re-examined.
 *
 * Unknown entities are left exactly as they were found rather than dropped, so a
 * feed change shows up as visible text instead of silently missing words.
 */
internal fun decodeHtmlEntities(text: String): String =
    ENTITY_PATTERN.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                codePointToString(body.drop(2).toIntOrNull(16)) ?: match.value
            body.startsWith("#") ->
                codePointToString(body.drop(1).toIntOrNull()) ?: match.value
            else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
        }
    }

private fun codePointToString(codePoint: Int?): String? = when {
    codePoint == null || codePoint <= 0 || codePoint > 0x10FFFF -> null
    // Lone surrogates are not valid scalar values; leave the entity untouched.
    codePoint in 0xD800..0xDFFF -> null
    codePoint <= 0xFFFF -> Char(codePoint).toString()
    else -> {
        val v = codePoint - 0x10000
        charArrayOf(Char(0xD800 + (v shr 10)), Char(0xDC00 + (v and 0x3FF))).concatToString()
    }
}
