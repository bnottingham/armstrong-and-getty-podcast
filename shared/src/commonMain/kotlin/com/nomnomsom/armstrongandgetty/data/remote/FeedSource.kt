package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.RssItem

/** Source of the show's RSS items. Implemented by [RssFeedParser]; faked in tests. */
interface FeedSource {
    suspend fun fetchFeed(): Result<List<RssItem>>
}
