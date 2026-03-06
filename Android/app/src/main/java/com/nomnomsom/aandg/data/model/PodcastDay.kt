package com.nomnomsom.aandg.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single day's combined podcast episode.
 * Multiple RSS items from the same day get merged into one PodcastDay.
 */
@Entity(tableName = "podcast_days")
data class PodcastDay(
    @PrimaryKey
    val date: String, // "2026-03-05" format — unique per day

    val title: String, // "A&G — Mar 5, 2026"
    val summary: String, // Combined description from all segments
    val segmentsJson: String, // JSON array of Segment objects
    val totalDurationMs: Long, // Combined duration of all segments in ms
    val segmentCount: Int, // Number of segments (hours) in this day

    val downloadState: String, // "none", "downloading", "downloaded", "error"
    val combinedFilePath: String?, // Path to combined audio file on disk
    val isComplete: Boolean, // Whether the show is done for the day (all hours posted)

    val listenedPositionMs: Long, // Where the user left off
    val isListened: Boolean, // Whether user finished the whole episode

    val lastUpdated: Long // Timestamp for when we last checked/updated this day
)
