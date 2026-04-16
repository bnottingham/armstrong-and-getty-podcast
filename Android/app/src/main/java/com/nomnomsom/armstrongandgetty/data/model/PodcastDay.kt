package com.nomnomsom.armstrongandgetty.data.model

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
    val title: String,
    val summary: String,
    val segmentsJson: String,
    val totalDurationMs: Long,
    val segmentCount: Int,
    val downloadState: String, // one of DownloadState.value
    val combinedFilePath: String?,
    val isComplete: Boolean, // false while the show is still live and more hours can still post
    val listenedPositionMs: Long,
    val isListened: Boolean,
    val lastUpdated: Long
)
