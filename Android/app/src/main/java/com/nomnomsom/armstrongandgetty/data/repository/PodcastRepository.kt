package com.nomnomsom.armstrongandgetty.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import com.nomnomsom.armstrongandgetty.data.local.UserDeletionTracker
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.RssItem
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.effectiveDurationMs
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.data.remote.AudioDownloader
import com.nomnomsom.armstrongandgetty.data.remote.DownloadProgressCallback
import com.nomnomsom.armstrongandgetty.data.remote.RssFeedParser
import com.nomnomsom.armstrongandgetty.data.remote.SegmentDownloadOutcome
import com.nomnomsom.armstrongandgetty.util.formatAsDayKey
import com.nomnomsom.armstrongandgetty.util.parseRssPubDate
import com.nomnomsom.armstrongandgetty.util.parseRssPubDateMs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class PodcastRepository @Inject constructor(
    private val dao: PodcastDayDao,
    private val rssFeedParser: RssFeedParser,
    private val audioDownloader: AudioDownloader,
    private val deletionTracker: UserDeletionTracker,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "PodcastRepository"
    }

    // Guards against the worker + viewmodel both firing `downloadDay` for the same date on launch.
    // Without this, two parallel download loops race on the same files and the progress tracker.
    private val activeDownloadsLock = Any()
    private val activeDownloads = mutableSetOf<String>()

    fun isUserDeleted(date: String): Boolean = deletionTracker.isDeleted(date)

    fun clearUserDeletion(date: String) = deletionTracker.unmarkDeleted(date)

    fun observeAllDays(): Flow<List<PodcastDay>> = dao.getAllDays()

    suspend fun getAllDaysSnapshot(): List<PodcastDay> = dao.getAllDaysSnapshot()

    fun observeDay(date: String): Flow<PodcastDay?> = dao.observeDay(date)

    suspend fun getDayByDate(date: String): PodcastDay? = dao.getDayByDate(date)

    suspend fun refreshFeed(): Result<String> {
        val result = rssFeedParser.fetchFeed()
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)

        val items = result.getOrThrow()
        val groupedByDate = groupItemsByDate(items)

        var latestDate = ""

        for ((date, dayItems) in groupedByDate) {
            if (latestDate.isEmpty() || date > latestDate) latestDate = date

            val existingDay = dao.getDayByDate(date)
            val segments = dayItems.mapIndexed { index, item ->
                val hourLabel = extractHourLabel(item.title, index + 1)
                Segment(
                    hour = hourLabel,
                    title = item.title,
                    description = item.description,
                    durationMs = item.durationSeconds * 1000,
                    audioUrl = item.audioUrl,
                    pubDate = item.pubDate
                )
            }.sortedBy { seg ->
                parsePubDate(seg.pubDate)
            }.mapIndexed { index, seg ->
                val hourLabel = if (seg.hour == "OMT") "OMT"
                else extractHourLabel(seg.title, index + 1)
                seg.copy(hour = hourLabel)
            }

            val segmentsJson = gson.toJson(segments)
            val totalDuration = segments.sumOf { it.durationMs }
            val summary = buildSummary(segments)
            val isComplete = isDayComplete(date, segments)

            if (existingDay == null) {
                val day = PodcastDay(
                    date = date,
                    title = formatDayTitle(date),
                    summary = summary,
                    segmentsJson = segmentsJson,
                    totalDurationMs = totalDuration,
                    segmentCount = segments.size,
                    downloadState = DownloadState.NONE.value,
                    combinedFilePath = null,
                    isComplete = isComplete,
                    listenedPositionMs = 0L,
                    isListened = false,
                    lastUpdated = System.currentTimeMillis()
                )
                dao.insertOrReplace(day)
            } else {
                val existingSegments = parseSegments(existingDay.segmentsJson)
                if (segments.size > existingSegments.size || !isComplete) {
                    dao.updateSegments(
                        date = date,
                        segmentsJson = segmentsJson,
                        totalDurationMs = totalDuration,
                        segmentCount = segments.size,
                        summary = summary,
                        isComplete = isComplete,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
        }

        return Result.success(latestDate)
    }

    suspend fun downloadDay(
        date: String,
        onProgress: DownloadProgressCallback? = null
    ): Result<String> {
        val claimed = synchronized(activeDownloadsLock) {
            if (date in activeDownloads) false else activeDownloads.add(date)
        }
        if (!claimed) {
            Log.d(TAG, "downloadDay $date skipped: another call is already downloading this date")
            return Result.success(date)
        }

        var segmentsForCleanup: List<Segment> = emptyList()
        var summaryForCleanup = ""
        var isCompleteForCleanup = false

        try {
            val day = dao.getDayByDate(date) ?: return Result.failure(Exception("Day not found"))
            val segments = parseSegments(day.segmentsJson)
            if (segments.isEmpty()) return Result.failure(Exception("No segments"))
            segmentsForCleanup = segments
            summaryForCleanup = day.summary
            isCompleteForCleanup = day.isComplete

            if (audioDownloader.hasAllSegments(date, segments.size)) {
                dao.updateDownloadState(date, DownloadState.DOWNLOADED.value)
                return Result.success(date)
            }

            dao.updateDownloadState(date, DownloadState.DOWNLOADING.value)

            val outcomes = audioDownloader.downloadSegments(date, segments, onProgress)
            return finalizeDownload(date, segments, outcomes, day.summary, day.isComplete)
        } catch (e: CancellationException) {
            // The viewModelScope job was cancelled (user hit Cancel, or screen was torn down).
            // Without this NonCancellable cleanup the row would be left at DOWNLOADING forever.
            withContext(NonCancellable) {
                finalizeFromDisk(date, segmentsForCleanup, summaryForCleanup, isCompleteForCleanup)
            }
            throw e
        } finally {
            synchronized(activeDownloadsLock) { activeDownloads.remove(date) }
        }
    }

    /** Cancel the in-progress download for [date]. The active OkHttp call is interrupted; the
     *  caller's coroutine still owns the Job and is responsible for cancelling it if desired. */
    fun cancelDownload(date: String) {
        audioDownloader.cancelDay(date)
    }

    /** Cancel a single in-flight segment. The retry loop continues to the next segment. */
    fun cancelSegment(date: String, index: Int) {
        audioDownloader.cancelSegment(date, index)
    }

    /**
     * Recover from a previous run that was killed mid-download (process crash, force-close, OOM).
     * Anything left in DOWNLOADING is by definition stale by the time we boot — flip it so the UI
     * surfaces a retry path instead of staying frozen at "Downloading segment 1 of 4 — 0%".
     */
    suspend fun resetStaleDownloadingStates() {
        dao.replaceDownloadState(DownloadState.DOWNLOADING.value, DownloadState.ERROR.value)
        Log.d(TAG, "resetStaleDownloadingStates: any DOWNLOADING rows reset to ERROR")
    }

    private suspend fun finalizeFromDisk(
        date: String,
        segments: List<Segment>,
        summary: String,
        isComplete: Boolean
    ) {
        if (segments.isEmpty()) {
            dao.updateDownloadState(date, DownloadState.ERROR.value)
            return
        }
        // Treat each on-disk file as a "success" so we keep the actual durations consistent.
        val outcomes = segments.indices.map { i ->
            val file = java.io.File(audioDownloader.getSegmentFiles(date, segments.size)[i])
            if (file.exists() && file.length() > 0) {
                SegmentDownloadOutcome.Success(i, file.absolutePath, 0L)
            } else {
                SegmentDownloadOutcome.Failure(i, Exception("not on disk"))
            }
        }
        finalizeDownload(date, segments, outcomes, summary, isComplete)
    }

    suspend fun appendNewSegments(date: String): Result<String> {
        val day = dao.getDayByDate(date) ?: return Result.failure(Exception("Day not found"))
        if (day.state != DownloadState.DOWNLOADED) {
            return Result.failure(Exception("Day not downloaded"))
        }

        val previousSegmentCount = parseSegments(day.segmentsJson).size

        val refreshResult = refreshFeed()
        if (refreshResult.isFailure) return Result.failure(refreshResult.exceptionOrNull()!!)

        val updatedDay = dao.getDayByDate(date) ?: return Result.failure(Exception("Day not found after refresh"))
        val updatedSegments = parseSegments(updatedDay.segmentsJson)

        if (updatedSegments.size <= previousSegmentCount) {
            return Result.success(date)
        }

        val outcomes = audioDownloader.downloadSegments(date, updatedSegments)
        return finalizeDownload(date, updatedSegments, outcomes, updatedDay.summary, updatedDay.isComplete)
    }

    suspend fun retrySegment(
        date: String,
        index: Int,
        onProgress: DownloadProgressCallback? = null
    ): Result<String> {
        val day = dao.getDayByDate(date) ?: return Result.failure(Exception("Day not found"))
        val segments = parseSegments(day.segmentsJson)
        val segment = segments.getOrNull(index)
            ?: return Result.failure(Exception("Segment $index out of range"))

        val outcome = audioDownloader.downloadSingleSegment(
            date = date,
            segment = segment,
            index = index,
            totalSegments = segments.size,
            onProgress = onProgress
        )

        return when (outcome) {
            is SegmentDownloadOutcome.Success -> {
                val merged = segments.toMutableList().apply {
                    this[index] = this[index].copy(actualDurationMs = outcome.actualDurationMs)
                }
                persistMergedSegments(date, merged, day.summary, day.isComplete)
                // Any-on-disk = playable. Matches finalizeDownload: a partial download is still
                // DOWNLOADED — preparePlaylist filters out the missing files at playback time.
                if (day.state != DownloadState.DOWNLOADED) {
                    dao.updateDownloadState(date, DownloadState.DOWNLOADED.value)
                }
                Result.success(date)
            }
            is SegmentDownloadOutcome.Failure -> Result.failure(outcome.error)
        }
    }

    private suspend fun finalizeDownload(
        date: String,
        segments: List<Segment>,
        outcomes: List<SegmentDownloadOutcome>,
        summary: String,
        isComplete: Boolean
    ): Result<String> {
        val successDurations = outcomes.filterIsInstance<SegmentDownloadOutcome.Success>()
            .associate { it.index to it.actualDurationMs }

        val merged = segments.mapIndexed { index, seg ->
            val actualDur = successDurations[index] ?: 0L
            if (actualDur > 0) seg.copy(actualDurationMs = actualDur) else seg
        }

        persistMergedSegments(date, merged, summary, isComplete)

        val anyOnDisk = audioDownloader.missingSegmentIndices(date, merged.size).size < merged.size
        return if (anyOnDisk) {
            dao.updateDownloadState(date, DownloadState.DOWNLOADED.value)
            Result.success(date)
        } else {
            dao.updateDownloadState(date, DownloadState.ERROR.value)
            val firstFailure = outcomes.filterIsInstance<SegmentDownloadOutcome.Failure>().firstOrNull()
            Result.failure(firstFailure?.error ?: Exception("Download failed"))
        }
    }

    private suspend fun persistMergedSegments(
        date: String,
        merged: List<Segment>,
        summary: String,
        isComplete: Boolean
    ) {
        dao.updateSegments(
            date = date,
            segmentsJson = gson.toJson(merged),
            totalDurationMs = merged.sumOf { it.effectiveDurationMs },
            segmentCount = merged.size,
            summary = summary,
            isComplete = isComplete,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun getSegmentFilePaths(date: String, segmentCount: Int): List<String> {
        return audioDownloader.getSegmentFiles(date, segmentCount)
    }

    fun hasAllSegmentsOnDisk(date: String, segmentCount: Int): Boolean {
        return audioDownloader.hasAllSegments(date, segmentCount)
    }

    fun missingSegmentIndices(date: String, segmentCount: Int): Set<Int> {
        return audioDownloader.missingSegmentIndices(date, segmentCount)
    }

    suspend fun updateListenProgress(date: String, positionMs: Long, isListened: Boolean) {
        dao.updateListenProgress(date, positionMs, isListened)
    }

    suspend fun resetProgress(date: String) {
        dao.resetProgress(date)
    }

    /**
     * "Delete" means: remove local audio, reset listen progress, and flip state back to NONE so the
     * card re-offers a Download button. Row stays in the list. [deletionTracker] is marked so the
     * worker/refreshFeed don't silently re-download the day before the user asks for it.
     */
    suspend fun deleteDay(date: String) {
        Log.d(TAG, "deleteDay $date — files removed, state reset to NONE")
        audioDownloader.deleteSegmentFiles(date)
        dao.resetProgress(date)
        dao.updateDownloadState(date, DownloadState.NONE.value)
        deletionTracker.markDeleted(date)
    }

    fun parseSegments(json: String): List<Segment> {
        val type = object : TypeToken<List<Segment>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun groupItemsByDate(items: List<RssItem>): Map<String, List<RssItem>> {
        return items.mapNotNull { item ->
            parseRssPubDate(item.pubDate)?.let { formatAsDayKey(it) to item }
        }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareByDescending { it })
    }

    private fun parsePubDate(pubDate: String): Long = parseRssPubDateMs(pubDate)

    private fun extractHourLabel(title: String, fallbackIndex: Int): String {
        val hourPattern = Regex("Hour\\s+(\\d+)", RegexOption.IGNORE_CASE)
        val match = hourPattern.find(title)
        if (match != null) return match.groupValues[1]

        if (title.contains("One More Thing", ignoreCase = true) ||
            title.contains("OMT", ignoreCase = true)
        ) {
            return "OMT"
        }

        return fallbackIndex.toString()
    }

    private fun isDayComplete(date: String, segments: List<Segment>): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        if (date != today) return true

        val hourSegments = segments.count { it.hour.toIntOrNull() != null }
        return hourSegments >= 4
    }

    private fun formatDayTitle(date: String): String {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        val formatted = SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed!!)
        return "A&G — $formatted"
    }

    private fun buildSummary(segments: List<Segment>): String {
        val combined = segments
            .map { it.description }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (combined.length > 400) combined.take(400) + "…" else combined
    }
}
