package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.RssItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssFeedParser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val FEED_URL = "https://www.omnycontent.com/d/playlist/e73c998e-6e60-432f-8610-ae210140c5b1/0516ff28-c0d6-492a-b264-ae3900375fc8/4db37684-c7ed-4964-843c-ae3900375fd7/podcast.rss"
    }

    suspend fun fetchFeed(): Result<List<RssItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(FEED_URL)
                .header("User-Agent", "ArmstrongGettyPodcast/1.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val items = parseRss(body)
                Result.success(items)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRss(xml: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var inItem = false
        var title = ""
        var description = ""
        var pubDate = ""
        var audioUrl = ""
        var durationSeconds = 0L
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        inItem = true
                        title = ""
                        description = ""
                        pubDate = ""
                        audioUrl = ""
                        durationSeconds = 0L
                    }
                    if (inItem && currentTag == "enclosure") {
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null) audioUrl = url
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title" -> title = text
                            "description" -> description = text
                            "pubDate" -> pubDate = text
                            "duration" -> durationSeconds = parseDuration(text)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
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
            }
            parser.next()
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
