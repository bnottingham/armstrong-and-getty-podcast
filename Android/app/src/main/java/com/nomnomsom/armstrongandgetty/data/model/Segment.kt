package com.nomnomsom.armstrongandgetty.data.model

/**
 * A single segment (hour) of the podcast. Serialized to JSON in PodcastDay.segmentsJson.
 */
data class Segment(
    val hour: String, // "1", "2", "3", "4", or "OMT"
    val title: String,
    val description: String,
    val durationMs: Long, // from RSS metadata (approximate)
    val audioUrl: String,
    val pubDate: String,
    val actualDurationMs: Long = 0L // measured from the downloaded file (exact)
)
