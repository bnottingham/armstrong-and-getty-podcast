package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.XFeedItem
import com.nomnomsom.armstrongandgetty.util.appGetRequest
import com.nomnomsom.armstrongandgetty.util.parseRssPubDateMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
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
            okHttpClient.newCall(appGetRequest(FEED_URL)).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                Result.success(parseRss(body))
            }
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
                                    timestampMs = parseRssPubDateMs(pubDate)
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
}
