package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.XFeedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XFeedParser @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val FEED_URL = "https://rss.app/feeds/Y9O2qvbTulQw2Bvl.xml"
    }

    suspend fun fetchFeed(): Result<List<XFeedItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(FEED_URL)
                .header("User-Agent", "ArmstrongGettyPodcast/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            val items = parseRss(body)
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRss(xml: String): List<XFeedItem> {
        val items = mutableListOf<XFeedItem>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var inItem = false
        var link = ""
        var pubDate = ""
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        inItem = true
                        link = ""
                        pubDate = ""
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "link" -> link = text
                            "pubDate" -> pubDate = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        inItem = false

                        val tweetId = extractTweetId(link)
                        if (tweetId != null) {
                            items.add(
                                XFeedItem(
                                    tweetId = tweetId,
                                    postUrl = link,
                                    timestampMs = parsePubDate(pubDate)
                                )
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            parser.next()
        }

        return items.sortedByDescending { it.timestampMs }
    }

    private fun extractTweetId(url: String): String? {
        val regex = Regex("/status/(\\d+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun parsePubDate(pubDate: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                return sdf.parse(pubDate)?.time ?: continue
            } catch (_: ParseException) {
                continue
            }
        }
        return 0L
    }
}
