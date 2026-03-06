package com.nomnomsom.starwarsshop.data.remote

import android.content.Context
import android.media.MediaMetadataRetriever
import com.nomnomsom.starwarsshop.data.model.Segment
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
     * @return DownloadResult with file paths and measured durations for ALL segments
     */
    suspend fun downloadSegments(
        date: String,
        segments: List<Segment>,
        existingSegmentCount: Int = 0
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val allPaths = mutableListOf<String>()
            val allDurations = mutableListOf<Long>()

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
                } else {
                    // Download new segment
                    downloadFile(segment.audioUrl, segFile)
                    allPaths.add(segFile.absolutePath)
                    allDurations.add(measureDuration(segFile))
                }
            }

            Result.success(DownloadResult(allPaths, allDurations))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadFile(url: String, destination: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ArmstrongGettyPodcast/1.0")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Download failed: HTTP ${response.code}")

        response.body?.byteStream()?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        } ?: throw Exception("Empty response body")
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
     * Counts from index 0 up — stops at the first missing file.
     */
    fun countExistingSegments(date: String, totalSegments: Int): Int {
        var count = 0
        for (index in 0 until totalSegments) {
            val file = File(podcastDir, "ag_${date}_seg${index}.mp3")
            if (file.exists() && file.length() > 0) {
                count++
            } else {
                break
            }
        }
        return count
    }

    /**
     * Delete all segment files for a given date.
     */
    fun deleteSegmentFiles(date: String) {
        podcastDir.listFiles()?.filter { it.name.startsWith("ag_${date}_seg") }?.forEach { it.delete() }
    }

    // Legacy: also clean up old combined files if they exist
    fun deleteCombinedFile(date: String) {
        File(podcastDir, "ag_$date.mp3").delete()
        deleteSegmentFiles(date)
    }

    fun hasCombinedFile(date: String): Boolean = false // No longer used
}