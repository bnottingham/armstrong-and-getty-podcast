package com.nomnomsom.armstrongandgetty.data.model

/**
 * A single post from the X list RSS feed. The WebView renders it via Twitter's
 * embed iframe, so we only need the tweet ID.
 */
data class XFeedItem(
    val tweetId: String,
    val postUrl: String,
    val timestampMs: Long
)
