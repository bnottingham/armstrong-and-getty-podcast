package com.nomnomsom.starwarsshop.data.model

/**
 * Raw parsed RSS feed item before grouping into days.
 */
data class RssItem(
    val title: String,
    val description: String,
    val pubDate: String, // Raw RSS date string e.g. "Wed, 05 Mar 2026 14:00:00 GMT"
    val audioUrl: String,
    val durationSeconds: Long // From <itunes:duration> tag
)
