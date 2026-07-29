package com.nomnomsom.armstrongandgetty.data.model

import kotlinx.serialization.Serializable

/**
 * A single segment (hour) of the podcast. Serialized to JSON in PodcastDay.segmentsJson.
 *
 * Field names must stay exactly as they are: rows written by the previous Gson-based
 * Android app carry this same shape, and kotlinx-serialization reads them in place.
 */
@Serializable
data class Segment(
    val hour: String, // "1", "2", "3", "4", or "OMT"
    val title: String,
    val description: String,
    val durationMs: Long, // from RSS metadata (approximate)
    val audioUrl: String,
    val pubDate: String,
    val actualDurationMs: Long = 0L // measured from the downloaded file (exact)
)

/**
 * Measured duration when available, otherwise the RSS-reported duration.
 */
val Segment.effectiveDurationMs: Long
    get() = if (actualDurationMs > 0) actualDurationMs else durationMs

/**
 * Human-readable label used in playlists and UI:
 *   "OMT: <title>" for the One More Thing bonus segment,
 *   "Hr <n>: <title>" for numbered hours.
 */
val Segment.displayLabel: String
    get() = if (hour == "OMT") "OMT: $title" else "Hr $hour: $title"
