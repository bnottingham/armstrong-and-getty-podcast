package com.nomnomsom.armstrongandgetty.testutil

import com.nomnomsom.armstrongandgetty.data.local.DeletionMarks
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.RssItem
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.remote.DownloadProgressCallback
import com.nomnomsom.armstrongandgetty.data.remote.FeedSource
import com.nomnomsom.armstrongandgetty.data.remote.SegmentDownloadOutcome
import com.nomnomsom.armstrongandgetty.data.remote.SegmentStore
import com.nomnomsom.armstrongandgetty.download.NetworkKind
import com.nomnomsom.armstrongandgetty.download.NetworkStatus
import com.nomnomsom.armstrongandgetty.download.WifiRequiredException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import okio.IOException

const val FAKE_SEGMENT_DURATION_MS = 3_600_000L

/** In-memory PodcastDayDao. */
class FakeDao : PodcastDayDao {
    val days = MutableStateFlow<Map<String, PodcastDay>>(emptyMap())

    private fun sorted(m: Map<String, PodcastDay>) = m.values.sortedByDescending { it.date }

    override fun getAllDays(): Flow<List<PodcastDay>> = days.map { sorted(it) }
    override suspend fun getAllDaysSnapshot(): List<PodcastDay> = sorted(days.value)
    override suspend fun getDayByDate(date: String): PodcastDay? = days.value[date]
    override fun observeDay(date: String): Flow<PodcastDay?> = days.map { it[date] }

    override suspend fun insertOrReplace(day: PodcastDay) {
        days.update { it + (day.date to day) }
    }

    private inline fun mutate(date: String, crossinline transform: (PodcastDay) -> PodcastDay) {
        days.update { m -> m[date]?.let { m + (date to transform(it)) } ?: m }
    }

    override suspend fun updateDownloadState(date: String, state: String) =
        mutate(date) { it.copy(downloadState = state) }

    override suspend fun replaceDownloadState(oldState: String, newState: String) {
        days.update { m ->
            m.mapValues { (_, d) ->
                if (d.downloadState == oldState) d.copy(downloadState = newState) else d
            }
        }
    }

    override suspend fun updateListenProgress(date: String, positionMs: Long, isListened: Boolean) =
        mutate(date) { it.copy(listenedPositionMs = positionMs, isListened = isListened) }

    override suspend fun resetProgress(date: String) =
        mutate(date) { it.copy(listenedPositionMs = 0L, isListened = false) }

    override suspend fun updateSegments(
        date: String,
        segmentsJson: String,
        totalDurationMs: Long,
        segmentCount: Int,
        summary: String,
        isComplete: Boolean,
        lastUpdated: Long
    ) = mutate(date) {
        it.copy(
            segmentsJson = segmentsJson,
            totalDurationMs = totalDurationMs,
            segmentCount = segmentCount,
            summary = summary,
            isComplete = isComplete,
            lastUpdated = lastUpdated
        )
    }

    override suspend fun deleteDay(date: String) {
        days.update { it - date }
    }
}

/** Scriptable feed: set [items] to whatever is "published" right now. */
class FakeFeed : FeedSource {
    var items: List<RssItem> = emptyList()
    var failure: Throwable? = null
    var fetchCount = 0
        private set

    override suspend fun fetchFeed(): Result<List<RssItem>> {
        fetchCount++
        failure?.let { return Result.failure(it) }
        return Result.success(items)
    }
}

/**
 * In-memory segment store. Mirrors AudioDownloader's contract: segments already "on disk"
 * are skipped with a Success outcome; others are transferred unless scripted to fail.
 */
class FakeSegmentStore : SegmentStore {
    /** (date, index) pairs currently on "disk". */
    val disk = mutableSetOf<Pair<String, Int>>()

    /** Indices whose transfer fails (any date). */
    val failIndices = mutableSetOf<Int>()

    /**
     * Indices whose transfer AudioDownloader's Wi-Fi-only backstop holds back (any date):
     * `Failure(index, WifiRequiredException())` without a transfer.
     */
    val wifiBlockedIndices = mutableSetOf<Int>()

    /** When set, every new transfer suspends here until the gate completes. */
    var transferGate: CompletableDeferred<Unit>? = null

    /** Completed when a transfer batch is entered — lets tests sync with an in-flight download. */
    var onTransferStarted: CompletableDeferred<Unit>? = null

    /** Log of actual transfers performed (skips excluded). */
    val transferLog = mutableListOf<Pair<String, Int>>()

    private fun path(date: String, index: Int) = "/fake/ag_${date}_seg${index}.mp3"

    private suspend fun transfer(date: String, index: Int): SegmentDownloadOutcome {
        if (date to index in disk) {
            return SegmentDownloadOutcome.Success(index, path(date, index), FAKE_SEGMENT_DURATION_MS)
        }
        if (index in wifiBlockedIndices) {
            return SegmentDownloadOutcome.Failure(index, WifiRequiredException())
        }
        onTransferStarted?.complete(Unit)
        transferGate?.await()
        if (index in failIndices) {
            return SegmentDownloadOutcome.Failure(index, IOException("fake network failure"))
        }
        disk += date to index
        transferLog += date to index
        return SegmentDownloadOutcome.Success(index, path(date, index), FAKE_SEGMENT_DURATION_MS)
    }

    override suspend fun downloadSegments(
        date: String,
        segments: List<Segment>,
        onProgress: DownloadProgressCallback?
    ): List<SegmentDownloadOutcome> = segments.indices.map { transfer(date, it) }

    override suspend fun downloadSingleSegment(
        date: String,
        segment: Segment,
        index: Int,
        totalSegments: Int,
        onProgress: DownloadProgressCallback?
    ): SegmentDownloadOutcome = transfer(date, index)

    override suspend fun cancelSegment(date: String, index: Int) = Unit
    override suspend fun cancelDay(date: String) = Unit

    override fun getSegmentFiles(date: String, segmentCount: Int): List<String> =
        (0 until segmentCount).map { path(date, it) }

    override fun hasAllSegments(date: String, segmentCount: Int): Boolean =
        (0 until segmentCount).all { hasSegment(date, it) }

    override fun hasSegment(date: String, index: Int): Boolean = date to index in disk

    override fun missingSegmentIndices(date: String, segmentCount: Int): Set<Int> =
        (0 until segmentCount).filterNot { hasSegment(date, it) }.toSet()

    override fun deleteSegmentFiles(date: String) {
        disk.removeAll { it.first == date }
    }
}

class FakeDeletionMarks : DeletionMarks {
    private val deleted = mutableSetOf<String>()
    override fun isDeleted(date: String): Boolean = date in deleted
    override fun markDeleted(date: String) { deleted += date }
    override fun unmarkDeleted(date: String) { deleted -= date }
}

/**
 * Scriptable [NetworkStatus]. Set [kind] to change the reading; [changes] follows it live.
 * Counts every query so tests can prove the default (setting off) path never touches it.
 */
class FakeNetworkStatus(initial: NetworkKind = NetworkKind.UNMETERED) : NetworkStatus {
    val state = MutableStateFlow(initial)

    var kind: NetworkKind
        get() = state.value
        set(value) {
            state.value = value
        }

    var currentCalls = 0
        private set
    var awaitKnownCalls = 0
        private set
    var changesCalls = 0
        private set

    val totalCalls: Int get() = currentCalls + awaitKnownCalls + changesCalls

    override fun current(): NetworkKind {
        currentCalls++
        return state.value
    }

    override suspend fun awaitKnown(timeoutMs: Long): NetworkKind {
        awaitKnownCalls++
        return withTimeoutOrNull(timeoutMs) { state.first { it != NetworkKind.UNKNOWN } }
            ?: NetworkKind.UNKNOWN
    }

    override fun changes(): Flow<NetworkKind> {
        changesCalls++
        return state
    }
}
