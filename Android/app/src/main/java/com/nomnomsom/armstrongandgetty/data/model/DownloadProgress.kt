package com.nomnomsom.armstrongandgetty.data.model

/**
 * Per-segment byte-level progress for an in-flight download.
 *
 * Shared across the data layer (emitted by `AudioDownloader`) and the UI layer
 * (consumed by `EpisodeListViewModel` / `EpisodeListScreen`). Lives in
 * `data/model/` so both layers depend on a single canonical shape instead of
 * re-packing the same fields at the seam.
 *
 * @param currentSegment 0-based index of the segment currently downloading.
 * @param totalSegments total number of segments for the day being downloaded.
 * @param segmentBytesDownloaded bytes downloaded so far for the current segment.
 * @param segmentTotalBytes total bytes for the current segment (-1 if unknown).
 */
data class DownloadProgress(
    val currentSegment: Int,
    val totalSegments: Int,
    val segmentBytesDownloaded: Long,
    val segmentTotalBytes: Long
)
