package com.nomnomsom.armstrongandgetty.data.remote

import android.content.Context
import android.media.MediaMetadataRetriever
import com.nomnomsom.armstrongandgetty.data.model.Segment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadResult(
    val segmentFilePaths: List<String>,
    val segmentActualDurationsMs: List<Long>
)

/**
 * Callback for download progress.
 * @param segmentIndex 0-based index of the segment currently downloading
 * @param segmentCount total number of segments for this day
 * @param bytesDownloaded bytes downloaded for the current segment so far
 * @param totalBytes total bytes for the current segment (-1 if unknown)
 */
typealias DownloadProgressCallback = (segmentIndex: Int, segmentCount: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit

@Singleton
class AudioDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val podcastDir: File
        get() = File(context.filesDir, "podcasts").also { it.mkdirs() }

    /**
     * Downloads segments for a day, keeping each as a separate file.
     * Only downloads segments that haven't been downloaded yet.
     *
     * @param date The date string key e.g. "2026-03-05"
     * @param segments The list of segments to download
     * @param existingSegmentCount How many segments are already downloaded for this day
     * @param onProgress Optional callback for per-segment byte-level progress
     * @return DownloadResult with file paths and measured durations for ALL segments
     */
    suspend fun downloadSegments(
        date: String,
        segments: List<Segment>,
        existingSegmentCount: Int = 0,
        onProgress: DownloadProgressCallback? = null
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val allPaths = mutableListOf<String>()
            val allDurations = mutableListOf<Long>()
            val totalSegments = segments.size

            for ((index, segment) in segments.withIndex()) {
                val segFile = File(podcastDir, "ag_${date}_seg${index}.mp3")

                if (index < existingSegmentCount && segFile.exists()) {
                    // Already downloaded — just measure if we don't have duration
                    allPaths.add(segFile.absolutePath)
                    val dur = if (segment.actualDurationMs > 0) {
                        segment.actualDurationMs
                    } else {
                        measureDuration(segFile)
                    }
                    allDurations.add(dur)
                    // Report as fully complete for this segment
                    onProgress?.invoke(index, totalSegments, segFile.length(), segFile.length())
                } else {
                    // Download new segment with progress
                    downloadFile(segment.audioUrl, segFile) { bytesDownloaded, totalBytes ->
                        onProgress?.invoke(index, totalSegments, bytesDownloaded, totalBytes)
                    }
                    allPaths.add(segFile.absolutePath)
                    allDurations.add(measureDuration(segFile))
                }
            }

            Result.success(DownloadResult(allPaths, allDurations))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadFile(
        url: String,
        destination: File,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ArmstrongGettyPodcast/1.0")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Download failed: HTTP ${response.code}")

        val body = response.body ?: throw Exception("Empty response body")
        val totalBytes = body.contentLength() // -1 if unknown

        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesDownloaded = 0L
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesDownloaded += read
                    onProgress?.invoke(bytesDownloaded, totalBytes)
                }
            }
        }
    }

    private fun measureDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Get the list of segment file paths for a day.
     */
    fun getSegmentFiles(date: String, segmentCount: Int): List<String> {
        return (0 until segmentCount).map { index ->
            File(podcastDir, "ag_${date}_seg${index}.mp3").absolutePath
        }
    }

    /**
     * Check if all segments for a day exist on disk.
     */
    fun hasAllSegments(date: String, segmentCount: Int): Boolean {
        return (0 until segmentCount).all { index ->
            File(podcastDir, "ag_${date}_seg${index}.mp3").exists()
        }
    }

    /**
     * Count how many segments for a day actually exist on disk.
     * Counts all existing files regardless of gaps (e.g. seg0-3 present, seg4 missing, seg5 present → returns 5).
     */
    fun countExistingSegments(date: String, totalSegments: Int): Int {
        return (0 until totalSegments).count { index ->
            val file = File(podcastDir, "ag_${date}_seg${index}.mp3")
            file.exists() && file.length() > 0
        }
    }

    /**
     * Delete all segment files for a given date.
     */
    fun deleteSegmentFiles(date: String) {
        podcastDir.listFiles()?.filter { it.name.startsWith("ag_${date}_seg") }?.forEach { it.delete() }
    }
}
