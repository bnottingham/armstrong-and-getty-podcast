package com.nomnomsom.armstrongandgetty.data.repository

import com.nomnomsom.armstrongandgetty.data.local.DeletionMarks
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.effectiveDurationMs
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.data.remote.DownloadProgressCallback
import com.nomnomsom.armstrongandgetty.data.remote.FeedSource
import com.nomnomsom.armstrongandgetty.data.remote.SegmentDownloadOutcome
import com.nomnomsom.armstrongandgetty.data.remote.SegmentStore
import com.nomnomsom.armstrongandgetty.util.AppLog
import com.nomnomsom.armstrongandgetty.util.nowMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.time.Clock

data class LiveSegmentAppendResult(
    val date: String,
    val previousSegmentCount: Int,
    val appendedSegmentIndices: List<Int>
)

class PodcastRepository(
    private val dao: PodcastDayDao,
    private val rssFeedParser: FeedSource,
    private val audioDownloader: SegmentStore,
    private val deletionTracker: DeletionMarks,
    private val todayProvider: () -> LocalDate = {
        Clock.System.todayIn(TimeZone.currentSystemDefault())
    }
) {
    companion object {
        private const val TAG = "PodcastRepository"
    }

    // Reads old Gson-written rows (same field names) and writes the same shape back.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val fs = FileSystem.SYSTEM

    // Serializes all file-writing operations per day. The worker, refresh path, player screen, and
    // per-segment retry actions can all discover live content; only one of them may touch a day's
    // segment files at a time.
    private val dayDownloadLocksGuard = Mutex()
    private val dayDownloadLocks = mutableMapOf<String, Mutex>()

    fun isUserDeleted(date: String): Boolean = deletionTracker.isDeleted(date)

    fun clearUserDeletion(date: String) = deletionTracker.unmarkDeleted(date)

    fun observeAllDays(): Flow<List<PodcastDay>> = dao.getAllDays()

    suspend fun getAllDaysSnapshot(): List<PodcastDay> = dao.getAllDaysSnapshot()

    fun observeDay(date: String): Flow<PodcastDay?> = dao.observeDay(date)

    suspend fun getDayByDate(date: String): PodcastDay? = dao.getDayByDate(date)

    private suspend fun <T> withDayDownloadLock(
        date: String,
        operation: String,
        block: suspend () -> T
    ): T {
        val lock = dayDownloadLocksGuard.withLock {
            dayDownloadLocks.getOrPut(date) { Mutex() }
        }
        if (lock.isLocked) {
            AppLog.d(TAG, "$operation $date waiting for active segment file operation")
        }
        return lock.withLock { block() }
    }

    suspend fun refreshFeed(): Result<String> {
        val result = rssFeedParser.fetchFeed()
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)

        val items = result.getOrThrow()
        val groupedByDate = EpisodeAssembler.groupItemsByDate(items)

        var latestDate = ""

        for ((date, dayItems) in groupedByDate) {
            if (latestDate.isEmpty() || date > latestDate) latestDate = date

            val existingDay = dao.getDayByDate(date)
            val existingSegments = existingDay?.let { parseSegments(it.segmentsJson) } ?: emptyList()
            val rssSegments = EpisodeAssembler.buildSegments(dayItems)
            val segments = EpisodeAssembler.mergeDownloadedMetadata(rssSegments, existingSegments)

            val segmentsJson = json.encodeToString(segments)
            val totalDuration = segments.sumOf { it.effectiveDurationMs }
            val summary = EpisodeAssembler.buildSummary(segments)
            val isComplete = EpisodeAssembler.isDayComplete(date, segments, todayProvider())

            if (existingDay == null) {
                val day = PodcastDay(
                    date = date,
                    title = EpisodeAssembler.formatDayTitle(date),
                    summary = summary,
                    segmentsJson = segmentsJson,
                    totalDurationMs = totalDuration,
                    segmentCount = segments.size,
                    downloadState = DownloadState.NONE.value,
                    combinedFilePath = null,
                    isComplete = isComplete,
                    listenedPositionMs = 0L,
                    isListened = false,
                    lastUpdated = nowMs()
                )
                dao.insertOrReplace(day)
            } else {
                if (segments.size > existingSegments.size || !isComplete) {
                    dao.updateSegments(
                        date = date,
                        segmentsJson = segmentsJson,
                        totalDurationMs = totalDuration,
                        segmentCount = segments.size,
                        summary = summary,
                        isComplete = isComplete,
                        lastUpdated = nowMs()
                    )
                }
            }
        }

        return Result.success(latestDate)
    }

    suspend fun downloadDay(
        date: String,
        onProgress: DownloadProgressCallback? = null
    ): Result<String> = withDayDownloadLock(date, "downloadDay") {
        var segmentsForCleanup: List<Segment> = emptyList()
        var summaryForCleanup = ""
        var isCompleteForCleanup = false

        try {
            val day = dao.getDayByDate(date)
                ?: return@withDayDownloadLock Result.failure(Exception("Day not found"))
            val segments = parseSegments(day.segmentsJson)
            if (segments.isEmpty()) {
                return@withDayDownloadLock Result.failure(Exception("No segments"))
            }
            segmentsForCleanup = segments
            summaryForCleanup = day.summary
            isCompleteForCleanup = day.isComplete

            if (audioDownloader.hasAllSegments(date, segments.size)) {
                dao.updateDownloadState(date, DownloadState.DOWNLOADED.value)
                return@withDayDownloadLock Result.success(date)
            }

            dao.updateDownloadState(date, DownloadState.DOWNLOADING.value)

            val outcomes = audioDownloader.downloadSegments(date, segments, onProgress)
            finalizeDownload(date, segments, outcomes, day.summary, day.isComplete)
        } catch (e: CancellationException) {
            // The viewModelScope job was cancelled (user hit Cancel, or screen was torn down).
            // Without this NonCancellable cleanup the row would be left at DOWNLOADING forever.
            withContext(NonCancellable) {
                finalizeFromDisk(date, segmentsForCleanup, summaryForCleanup, isCompleteForCleanup)
            }
            throw e
        }
    }

    /** Cancel the in-progress download for [date]. The active transfer is interrupted; the
     *  caller's coroutine still owns the Job and is responsible for cancelling it if desired. */
    suspend fun cancelDownload(date: String) {
        audioDownloader.cancelDay(date)
    }

    /** Cancel a single in-flight segment. The retry loop continues to the next segment. */
    suspend fun cancelSegment(date: String, index: Int) {
        audioDownloader.cancelSegment(date, index)
    }

    /**
     * Recover from a previous run that was killed mid-download (process crash, force-close, OOM).
     * Anything left in DOWNLOADING is by definition stale by the time we boot — flip it so the UI
     * surfaces a retry path instead of staying frozen at "Downloading segment 1 of 4 — 0%".
     */
    suspend fun resetStaleDownloadingStates() {
        dao.replaceDownloadState(DownloadState.DOWNLOADING.value, DownloadState.ERROR.value)
        AppLog.d(TAG, "resetStaleDownloadingStates: any DOWNLOADING rows reset to ERROR")
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
        val filePaths = audioDownloader.getSegmentFiles(date, segments.size)
        val outcomes = segments.indices.map { i ->
            val path = filePaths[i].toPath()
            val size = fs.metadataOrNull(path)?.size ?: 0L
            if (size > 0) {
                SegmentDownloadOutcome.Success(i, path.toString(), 0L)
            } else {
                SegmentDownloadOutcome.Failure(i, Exception("not on disk"))
            }
        }
        finalizeDownload(date, segments, outcomes, summary, isComplete)
    }

    suspend fun appendNewSegments(
        date: String,
        onProgress: DownloadProgressCallback? = null
    ): Result<LiveSegmentAppendResult> = withDayDownloadLock(date, "appendNewSegments") {
        val day = dao.getDayByDate(date)
            ?: return@withDayDownloadLock Result.failure(Exception("Day not found"))
        if (day.state != DownloadState.DOWNLOADED) {
            return@withDayDownloadLock Result.failure(Exception("Day not downloaded"))
        }

        val previousSegmentCount = parseSegments(day.segmentsJson).size

        val refreshResult = refreshFeed()
        if (refreshResult.isFailure) {
            return@withDayDownloadLock Result.failure(refreshResult.exceptionOrNull()!!)
        }

        val updatedDay = dao.getDayByDate(date)
            ?: return@withDayDownloadLock Result.failure(Exception("Day not found after refresh"))
        val updatedSegments = parseSegments(updatedDay.segmentsJson)

        if (updatedSegments.size <= previousSegmentCount) {
            return@withDayDownloadLock Result.success(
                LiveSegmentAppendResult(date, previousSegmentCount, emptyList())
            )
        }

        val outcomes = audioDownloader.downloadSegments(date, updatedSegments, onProgress)
        val finalizeResult = finalizeDownload(
            date = date,
            segments = updatedSegments,
            outcomes = outcomes,
            summary = updatedDay.summary,
            isComplete = updatedDay.isComplete
        )

        val newRange = previousSegmentCount until updatedSegments.size
        val appendedIndices = newRange.filter { audioDownloader.hasSegment(date, it) }
        if (appendedIndices.isNotEmpty()) {
            return@withDayDownloadLock Result.success(
                LiveSegmentAppendResult(date, previousSegmentCount, appendedIndices)
            )
        }

        val firstNewFailure = outcomes
            .filterIsInstance<SegmentDownloadOutcome.Failure>()
            .firstOrNull { it.index in newRange }

        if (firstNewFailure != null) {
            Result.failure(firstNewFailure.error)
        } else {
            finalizeResult.map {
                LiveSegmentAppendResult(date, previousSegmentCount, emptyList())
            }
        }
    }

    suspend fun retrySegment(
        date: String,
        index: Int,
        onProgress: DownloadProgressCallback? = null
    ): Result<String> = withDayDownloadLock(date, "retrySegment") {
        val day = dao.getDayByDate(date)
            ?: return@withDayDownloadLock Result.failure(Exception("Day not found"))
        val segments = parseSegments(day.segmentsJson)
        val segment = segments.getOrNull(index)
            ?: return@withDayDownloadLock Result.failure(Exception("Segment $index out of range"))

        val outcome = audioDownloader.downloadSingleSegment(
            date = date,
            segment = segment,
            index = index,
            totalSegments = segments.size,
            onProgress = onProgress
        )

        when (outcome) {
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
            segmentsJson = json.encodeToString(merged),
            totalDurationMs = merged.sumOf { it.effectiveDurationMs },
            segmentCount = merged.size,
            summary = summary,
            isComplete = isComplete,
            lastUpdated = nowMs()
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
        AppLog.d(TAG, "deleteDay $date — files removed, state reset to NONE")
        audioDownloader.deleteSegmentFiles(date)
        dao.resetProgress(date)
        dao.updateDownloadState(date, DownloadState.NONE.value)
        deletionTracker.markDeleted(date)
    }

    fun parseSegments(jsonString: String): List<Segment> = try {
        json.decodeFromString<List<Segment>>(jsonString)
    } catch (_: Exception) {
        emptyList()
    }

}
