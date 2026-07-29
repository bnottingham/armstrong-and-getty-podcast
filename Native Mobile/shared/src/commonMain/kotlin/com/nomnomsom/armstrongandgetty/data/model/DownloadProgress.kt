package com.nomnomsom.armstrongandgetty.data.model

/**
 * Aggregate progress across a day's segment downloads.
 *
 * @param totalSegments number of segments being downloaded (or retried) for the day.
 * @param segmentsInProgress 0-based indices currently streaming.
 * @param segmentsCompleted count that finished successfully (file on disk).
 * @param segmentsFailed 0-based indices that errored out on this pass; the user can retry them individually.
 * @param bytesDownloaded running total across all segments on this pass.
 * @param totalBytes sum of Content-Length across segments, or -1 if any segment didn't advertise one.
 */
data class DownloadProgress(
    val totalSegments: Int,
    val segmentsInProgress: Set<Int> = emptySet(),
    val segmentsCompleted: Int = 0,
    val segmentsFailed: Set<Int> = emptySet(),
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L
)
