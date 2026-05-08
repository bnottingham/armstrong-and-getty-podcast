package com.nomnomsom.armstrongandgetty.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.nomnomsom.armstrongandgetty.data.model.XFeedAuthor
import com.nomnomsom.armstrongandgetty.data.model.XFeedItem
import com.nomnomsom.armstrongandgetty.data.model.XFeedMedia
import com.nomnomsom.armstrongandgetty.data.model.XFeedMetrics
import com.nomnomsom.armstrongandgetty.data.model.XFeedUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class XFeedCursor(
    val createdAtMs: Long,
    val id: String
)

data class XFeedPage(
    val items: List<XFeedItem>,
    val nextCursor: XFeedCursor?,
    val hasMore: Boolean
)

@Singleton
class XFeedRepository @Inject constructor(
    private val functions: FirebaseFunctions
) {
    suspend fun fetchPage(
        limit: Int = PAGE_SIZE,
        cursor: XFeedCursor? = null
    ): Result<XFeedPage> = withContext(Dispatchers.IO) {
        try {
            val payload = mutableMapOf<String, Any>("limit" to limit)
            if (cursor != null) {
                payload["cursor"] = mapOf(
                    "createdAtMs" to cursor.createdAtMs,
                    "id" to cursor.id
                )
            }

            val result = functions
                .getHttpsCallable("getXListPosts")
                .call(payload)
                .await()

            Result.success(parsePage(result.data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePage(raw: Any?): XFeedPage {
        val map = raw.asMap()
        val items = map["posts"].asMapList().map(::parseItem)
        val cursor = map["nextCursor"].asMap().takeIf { it.isNotEmpty() }?.let {
            XFeedCursor(
                createdAtMs = it["createdAtMs"].asLong() ?: 0L,
                id = it["id"].asString()
            )
        }

        return XFeedPage(
            items = items,
            nextCursor = cursor,
            hasMore = map["hasMore"].asBoolean()
        )
    }

    private fun parseItem(map: Map<String, Any?>): XFeedItem {
        val author = map["author"].asMap()
        val metrics = map["metrics"].asMap()

        return XFeedItem(
            tweetId = map["tweetId"].asString().ifBlank { map["id"].asString() },
            text = map["text"].asString(),
            tweetUrl = map["tweetUrl"].asString(),
            timestampMs = map["timestampMs"].asLong() ?: 0L,
            author = XFeedAuthor(
                name = author["name"].asString(),
                username = author["username"].asString(),
                profileImageUrl = author["profileImageUrl"].asNullableString(),
                verified = author["verified"].asBoolean()
            ),
            metrics = XFeedMetrics(
                replies = metrics["replies"].asInt(),
                reposts = metrics["reposts"].asInt(),
                likes = metrics["likes"].asInt(),
                quotes = metrics["quotes"].asInt()
            ),
            media = map["media"].asMapList().map { media ->
                XFeedMedia(
                    type = media["type"].asString(),
                    url = media["url"].asNullableString(),
                    previewImageUrl = media["previewImageUrl"].asNullableString(),
                    width = media["width"].asNullableInt(),
                    height = media["height"].asNullableInt()
                )
            },
            urls = map["urls"].asMapList().map { url ->
                XFeedUrl(
                    expandedUrl = url["expandedUrl"].asNullableString(),
                    displayUrl = url["displayUrl"].asNullableString(),
                    title = url["title"].asNullableString()
                )
            }
        )
    }

    private fun Any?.asMap(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any?> ?: emptyMap()
    }

    private fun Any?.asMapList(): List<Map<String, Any?>> {
        return (this as? List<*>)
            ?.mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it as? Map<String, Any?>
            }
            ?: emptyList()
    }

    private fun Any?.asString(): String = this as? String ?: ""

    private fun Any?.asNullableString(): String? = (this as? String)?.takeIf { it.isNotBlank() }

    private fun Any?.asBoolean(): Boolean = this as? Boolean ?: false

    private fun Any?.asLong(): Long? = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

    private fun Any?.asInt(): Int = asLong()?.toInt() ?: 0

    private fun Any?.asNullableInt(): Int? = asLong()?.toInt()

    companion object {
        const val PAGE_SIZE = 25
    }
}
