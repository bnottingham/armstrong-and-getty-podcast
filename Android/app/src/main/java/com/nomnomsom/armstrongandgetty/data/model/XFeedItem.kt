package com.nomnomsom.armstrongandgetty.data.model

/**
 * A single post from the X list RSS feed.
 * We only need the tweet ID — the embedded WebView handles all rendering.
 */
data class XFeedItem(
    val tweetId: String,   // Extracted from the post URL e.g. "2030673109167272403"
    val timestampMs: Long  // For ordering
)
