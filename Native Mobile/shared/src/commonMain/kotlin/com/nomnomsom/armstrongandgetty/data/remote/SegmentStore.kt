package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.Segment

/**
 * Storage + transfer of per-day segment audio files, addressed by (date, segment index).
 * Implemented by [AudioDownloader]; faked in tests so the episode-engine scenarios can
 * run without network or disk.
 */
interface SegmentStore {
    suspend fun downloadSegments(
        date: String,
        segments: List<Segment>,
        onProgress: DownloadProgressCallback? = null
    ): List<SegmentDownloadOutcome>

    suspend fun downloadSingleSegment(
        date: String,
        segment: Segment,
        index: Int,
        totalSegments: Int,
        onProgress: DownloadProgressCallback? = null
    ): SegmentDownloadOutcome

    suspend fun cancelSegment(date: String, index: Int)
    suspend fun cancelDay(date: String)

    fun getSegmentFiles(date: String, segmentCount: Int): List<String>
    fun hasAllSegments(date: String, segmentCount: Int): Boolean
    fun hasSegment(date: String, index: Int): Boolean
    fun missingSegmentIndices(date: String, segmentCount: Int): Set<Int>
    fun deleteSegmentFiles(date: String)
}
