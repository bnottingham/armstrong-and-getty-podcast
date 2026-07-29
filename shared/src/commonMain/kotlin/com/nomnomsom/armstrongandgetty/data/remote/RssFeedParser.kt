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
) {
    companion object {
        const val FEED_URL = "https://www.omnycontent.com/d/playlist/e73c998e-6e60-432f-8610-ae210140c5b1/0516ff28-c0d6-492a-b264-ae3900375fc8/4db37684-c7ed-4964-843c-ae3900375fd7/podcast.rss"
    }

    suspend fun fetchFeed(): Result<List<RssItem>> = withContext(Dispatchers.IO) {
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

    private fun parseRss(xml: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val reader = xmlStreaming.newReader(xml)

        var inItem = false
        var title = ""
        var description = ""
        var pubDate = ""
        var audioUrl = ""
        var durationSeconds = 0L
        var currentTag = ""

        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> {
                        currentTag = reader.localName
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
                            val text = reader.text.trim()
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "title" -> title += text
                                    "description" -> description += text
                                    "pubDate" -> pubDate += text
                                    "duration" -> durationSeconds = parseDuration(text)
                                }
                            }
                        }
                    }

                    EventType.END_ELEMENT -> {
                        if (reader.localName == "item" && inItem) {
                            inItem = false
                            if (audioUrl.isNotEmpty()) {
                                items.add(
                                    RssItem(
                                        title = title,
                                        description = cleanDescription(description),
                                        pubDate = pubDate,
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
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
