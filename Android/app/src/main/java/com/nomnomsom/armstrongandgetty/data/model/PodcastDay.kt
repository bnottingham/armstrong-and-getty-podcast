package com.nomnomsom.armstrongandgetty.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcast_days")
data class PodcastDay(
    @PrimaryKey
    val date: String,
    val title: String,
    val summary: String,
    val segmentsJson: String,
    val totalDurationMs: Long,
    val segmentCount: Int,
    // Persisted as one of [DownloadState]'s `value` strings — read via [state]. DAO SQL filters on literal 'downloaded'.
    val downloadState: String,
    val combinedFilePath: String?,
    val isComplete: Boolean,
    val listenedPositionMs: Long,
    val isListened: Boolean,
    val lastUpdated: Long
)

val PodcastDay.state: DownloadState
    get() = DownloadState.fromValue(downloadState)
