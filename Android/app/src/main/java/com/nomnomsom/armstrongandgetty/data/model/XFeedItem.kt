package com.nomnomsom.armstrongandgetty.data.model

/**
 * A single post from the Firebase-backed X list cache.
 */
data class XFeedItem(
    val tweetId: String,
    val text: String,
    val tweetUrl: String,
    val timestampMs: Long,
    val author: XFeedAuthor,
    val metrics: XFeedMetrics = XFeedMetrics(),
    val media: List<XFeedMedia> = emptyList(),
    val urls: List<XFeedUrl> = emptyList()
)

data class XFeedAuthor(
    val name: String,
    val username: String,
    val profileImageUrl: String?,
    val verified: Boolean
)

data class XFeedMetrics(
    val replies: Int = 0,
    val reposts: Int = 0,
    val likes: Int = 0,
    val quotes: Int = 0
)

data class XFeedMedia(
    val type: String,
    val url: String?,
    val previewImageUrl: String?,
    val width: Int?,
    val height: Int?
)

data class XFeedUrl(
    val expandedUrl: String?,
    val displayUrl: String?,
    val title: String?
)
