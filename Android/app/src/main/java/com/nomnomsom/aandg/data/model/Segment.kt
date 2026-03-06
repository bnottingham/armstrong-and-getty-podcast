package com.nomnomsom.aandg.data.model

/**
 * Represents a single segment (hour) of the podcast.
 * Serialized to JSON and stored in PodcastDay.segmentsJson.
 */
data class Segment(
    val hour: String, // "1", "2", "3", "4", or "OMT"
    val title: String,
    val description: String,
    val durationMs: Long, // From RSS metadata (approximate)
    val audioUrl: String,
    val pubDate: String, // Original pub date string from RSS
    val actualDurationMs: Long = 0L // Measured after download (exact)
)
