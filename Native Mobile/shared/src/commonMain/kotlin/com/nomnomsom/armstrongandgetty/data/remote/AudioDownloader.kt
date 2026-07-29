package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.DownloadProgress
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.platform.podcastsDirPath
import com.nomnomsom.armstrongandgetty.platform.probeAudioDurationMs
import com.nomnomsom.armstrongandgetty.util.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

sealed class SegmentDownloadOutcome {
    abstract val index: Int
    data class Success(
        override val index: Int,
        val filePath: String,
        val actualDurationMs: Long
    ) : SegmentDownloadOutcome()

    data class Failure(
        override val index: Int,
        val error: Throwable
    ) : SegmentDownloadOutcome()
}

typealias DownloadProgressCallback = (DownloadProgress) -> Unit

class CancelledByUserException : IOException("Download cancelled by user")

@OptIn(ExperimentalAtomicApi::class)
class AudioDownloader(
    private val httpClient: HttpClient
) : SegmentStore {
    companion object {
        private const val TAG = "AudioDownloader"
        private const val PER_ATTEMPT_READ_TIMEOUT_MS = 30_000L
        // Ktor's socket timeout only fires when no bytes arrive on a single read. The stall
        // watchdog below catches the case where reads keep returning a trickle of bytes (or
        // none) without the engine raising a timeout. STALL_TIMEOUT_MS is a no-progress budget
        // across the whole attempt.
        private const val STALL_TIMEOUT_MS = 45_000L
        private const val STALL_CHECK_INTERVAL_MS = 5_000L
        private const val MAX_ATTEMPTS_PER_SEGMENT = 3
        private const val RETRY_BACKOFF_MS = 1000L

        // Rough bitrate estimate for mp3 podcast audio (~128 kbps). Used to seed a progress-bar total
        // when the CDN doesn't send Content-Length, so the bar has something to scale against.
        private const val ESTIMATED_BYTES_PER_SECOND = 16_000L

        // Coalesce in-segment byte updates to ~10/sec so we don't flood the main thread.
        private const val PROGRESS_EMIT_INTERVAL_MS = 100L
    }

    private val fs = FileSystem.SYSTEM
    private val timeOrigin = TimeSource.Monotonic.markNow()
    private fun elapsedMs(): Long = timeOrigin.elapsedNow().inWholeMilliseconds

    // Tracks the active attempt Job for each in-flight (date, segmentIndex) so cancellation from
    // outside can abort the transfer. Sequential downloads mean at most one entry per date, but we
    // still key by index so per-segment retries (which can run on top of an unrelated active
    // download from the worker) don't stomp each other.
    private val stateMutex = Mutex()
    private val activeAttempts = HashMap<Pair<String, Int>, Job>()
    private val cancelledSegments = HashSet<Pair<String, Int>>()

    private val podcastDir: Path
        get() = podcastsDirPath().toPath()

    /**
     * Download any segments that aren't already on disk, one at a time.
     *
     * Returns one outcome per segment so callers can persist successes even when some segments fail.
     * Progress is emitted as an aggregate — segmentsInProgress (single-element set), bytes downloaded,
     * completed/failed counts. Per-segment byte updates are throttled to ~10/sec.
     */
    override suspend fun downloadSegments(
        date: String,
        segments: List<Segment>,
        onProgress: DownloadProgressCallback?
    ): List<SegmentDownloadOutcome> = withContext(Dispatchers.IO) {
        val estimates = LongArray(segments.size) { estimateBytes(segments[it]) }
        val tracker = ProgressTracker(segments.size, estimates, ::elapsedMs, onProgress)
        tracker.emitInitial()

        segments.mapIndexed { index, segment ->
            downloadWithRetry(date, segment, index, tracker)
        }
    }

    /** Download one segment — used by the per-segment retry action from the UI. */
    override suspend fun downloadSingleSegment(
        date: String,
        segment: Segment,
        index: Int,
        totalSegments: Int,
        onProgress: DownloadProgressCallback?
    ): SegmentDownloadOutcome = withContext(Dispatchers.IO) {
        val estimates = LongArray(totalSegments) { if (it == index) estimateBytes(segment) else 0L }
        val tracker = ProgressTracker(totalSegments, estimates, ::elapsedMs, onProgress)
        tracker.emitInitial()
        downloadWithRetry(date, segment, index, tracker)
    }

    private fun estimateBytes(segment: Segment): Long {
        val seconds = (segment.durationMs / 1000).coerceAtLeast(1)
        return seconds * ESTIMATED_BYTES_PER_SECOND
    }

    /**
     * Cancel the currently-active transfer for one segment. Safe to call when nothing is in
     * flight — it just records the intent so the next attempt aborts before starting. The
     * cancellation flag clears on the next [downloadWithRetry] entry for that segment.
     */
    override suspend fun cancelSegment(date: String, index: Int) {
        val jobToCancel: Job? = stateMutex.withLock {
            cancelledSegments.add(date to index)
            activeAttempts[date to index]
        }
        jobToCancel?.cancel()
        AppLog.d(TAG, "cancelSegment $date#$index (attempt active=${jobToCancel != null})")
    }

    /** Cancel every active segment for a date. Sequential downloads mean usually one. */
    override suspend fun cancelDay(date: String) {
        val toCancel: List<Job> = stateMutex.withLock {
            val keys = activeAttempts.keys.filter { it.first == date }
            keys.forEach { cancelledSegments.add(it) }
            keys.mapNotNull { activeAttempts[it] }
        }
        toCancel.forEach { it.cancel() }
        AppLog.d(TAG, "cancelDay $date (canceled ${toCancel.size} active attempt(s))")
    }

    private suspend fun isCancelled(date: String, index: Int): Boolean = stateMutex.withLock {
        date to index in cancelledSegments
    }

    private suspend fun clearCancelled(date: String, index: Int) {
        stateMutex.withLock { cancelledSegments.remove(date to index) }
    }

    private suspend fun downloadWithRetry(
        date: String,
        segment: Segment,
        index: Int,
        tracker: ProgressTracker
    ): SegmentDownloadOutcome {
        val destFile = segmentFile(date, index)
        val existingSize = fs.metadataOrNull(destFile)?.size ?: 0L
        if (existingSize > 0) {
            val duration = probeAudioDurationMs(destFile.toString())
            if (duration > 0) {
                AppLog.d(TAG, "seg $index already on disk ($existingSize bytes), skipping")
                tracker.markCompleted(index, existingSize, existingSize)
                return SegmentDownloadOutcome.Success(index, destFile.toString(), duration)
            }

            AppLog.w(TAG, "seg $index existing file has no duration; deleting and redownloading")
            fs.delete(destFile, mustExist = false)
        }

        // Fresh entry into this segment's retry loop — drop any stale cancel-intent left over from
        // a prior aborted attempt so a brand-new attempt isn't poisoned.
        clearCancelled(date, index)

        tracker.markStarted(index)
        AppLog.d(TAG, "seg $index start: ${segment.audioUrl}")

        var lastError: Throwable? = null
        var attempt = 0
        while (attempt < MAX_ATTEMPTS_PER_SEGMENT) {
            if (isCancelled(date, index)) {
                AppLog.i(TAG, "seg $index cancelled by user before attempt ${attempt + 1}")
                lastError = CancelledByUserException()
                break
            }
            try {
                val written = downloadFile(date, index, segment.audioUrl, destFile) { bytesDelta, totalBytes ->
                    tracker.addBytes(index, bytesDelta, totalBytes)
                }
                AppLog.d(TAG, "seg $index complete, wrote $written bytes (attempt ${attempt + 1})")
                val duration = probeAudioDurationMs(destFile.toString())
                if (duration <= 0) {
                    throw IOException("Downloaded audio has no measurable duration")
                }
                val finalSize = fs.metadataOrNull(destFile)?.size ?: written
                tracker.markCompleted(index, finalSize, finalSize)
                return SegmentDownloadOutcome.Success(index, destFile.toString(), duration)
            } catch (e: CancellationException) {
                // Whole-download cancellation (caller's coroutine was cancelled): clean up the
                // partial file and propagate so the caller can finalize state. An attempt-level
                // cancel (user cancel or stall watchdog) leaves the outer scope active — treat
                // it as this attempt's failure instead.
                currentCoroutineContext().ensureActive()
                fs.delete(destFile, mustExist = false)
                if (isCancelled(date, index)) {
                    AppLog.i(TAG, "seg $index cancelled by user during attempt ${attempt + 1}")
                    lastError = CancelledByUserException()
                    break
                }
                lastError = IOException("Transfer aborted (stalled)")
                AppLog.w(TAG, "seg $index attempt ${attempt + 1}/$MAX_ATTEMPTS_PER_SEGMENT stalled")
            } catch (e: Throwable) {
                lastError = e
                fs.delete(destFile, mustExist = false)
                if (isCancelled(date, index)) {
                    AppLog.i(TAG, "seg $index cancelled by user during attempt ${attempt + 1}")
                    break
                }
                AppLog.w(TAG, "seg $index attempt ${attempt + 1}/$MAX_ATTEMPTS_PER_SEGMENT failed: ${e.message}")
            }
            if (attempt < MAX_ATTEMPTS_PER_SEGMENT - 1) {
                delay(RETRY_BACKOFF_MS * (attempt + 1))
            }
            attempt++
        }

        if (isCancelled(date, index)) {
            AppLog.i(TAG, "seg $index user-cancelled, giving up")
        } else {
            AppLog.e(TAG, "seg $index giving up after $MAX_ATTEMPTS_PER_SEGMENT attempts: ${lastError?.message}")
        }
        tracker.markFailed(index)
        return SegmentDownloadOutcome.Failure(index, lastError ?: Exception("Download failed"))
    }

    /**
     * Performs one download attempt with a no-progress watchdog. The watchdog cancels the attempt
     * scope if no bytes arrive for [STALL_TIMEOUT_MS], turning a hang into a retryable failure.
     * Also registers the attempt Job for external cancellation.
     */
    private suspend fun downloadFile(
        date: String,
        index: Int,
        url: String,
        destination: Path,
        onBytes: (bytesDelta: Long, totalBytes: Long) -> Unit
    ): Long = coroutineScope {
        fs.createDirectories(podcastDir)
        val partial = partialSegmentFile(date, index)
        fs.delete(partial, mustExist = false)

        val attemptJob = currentCoroutineContext()[Job]!!
        stateMutex.withLock {
            // Late-cancel race: if cancel() raced ahead of the attempt being registered, honor it now.
            if (date to index in cancelledSegments) {
                attemptJob.cancel()
            }
            activeAttempts[date to index] = attemptJob
        }

        val lastBytesAtMs = AtomicLong(elapsedMs())
        var promotedPartial = false
        val watchdog: Job = launch {
            while (isActive) {
                delay(STALL_CHECK_INTERVAL_MS)
                val sinceProgressMs = elapsedMs() - lastBytesAtMs.load()
                if (sinceProgressMs > STALL_TIMEOUT_MS) {
                    AppLog.w(TAG, "seg $index stalled (${sinceProgressMs}ms without bytes) — cancelling attempt")
                    attemptJob.cancel()
                    break
                }
            }
        }

        try {
            httpClient.prepareGet(url) {
                header(HttpHeaders.UserAgent, APP_USER_AGENT)
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = PER_ATTEMPT_READ_TIMEOUT_MS
                    connectTimeoutMillis = PER_ATTEMPT_READ_TIMEOUT_MS
                }
            }.execute { response ->
                if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
                val totalBytes = response.contentLength() ?: -1L
                lastBytesAtMs.store(elapsedMs())

                var written = 0L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(16_384)
                fs.sink(partial).buffer().use { output ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        written += read
                        lastBytesAtMs.store(elapsedMs())
                        onBytes(read.toLong(), totalBytes)
                    }
                }
                if (written <= 0L) {
                    throw IOException("Downloaded empty audio file")
                }
                if (totalBytes >= 0 && written != totalBytes) {
                    throw IOException("Incomplete download: wrote $written of $totalBytes bytes")
                }
                fs.delete(destination, mustExist = false)
                fs.atomicMove(partial, destination)
                promotedPartial = true
                written
            }
        } finally {
            watchdog.cancel()
            if (!promotedPartial) {
                fs.delete(partial, mustExist = false)
            }
            stateMutex.withLock {
                activeAttempts.remove(date to index)
            }
        }
    }

    private fun segmentFile(date: String, index: Int): Path =
        podcastDir / "ag_${date}_seg${index}.mp3"

    private fun partialSegmentFile(date: String, index: Int): Path =
        podcastDir / "ag_${date}_seg${index}.mp3.part"

    override fun getSegmentFiles(date: String, segmentCount: Int): List<String> =
        (0 until segmentCount).map { segmentFile(date, it).toString() }

    override fun hasAllSegments(date: String, segmentCount: Int): Boolean =
        (0 until segmentCount).all { hasSegment(date, it) }

    override fun hasSegment(date: String, index: Int): Boolean =
        (fs.metadataOrNull(segmentFile(date, index))?.size ?: 0L) > 0L

    override fun missingSegmentIndices(date: String, segmentCount: Int): Set<Int> =
        (0 until segmentCount).filterNot { hasSegment(date, it) }.toSet()

    override fun deleteSegmentFiles(date: String) {
        val prefix = "ag_${date}_seg"
        fs.listOrNull(podcastDir)
            ?.filter { it.name.startsWith(prefix) }
            ?.forEach { fs.delete(it, mustExist = false) }
    }

    /**
     * Accumulates download state and forwards updates to the user callback. Downloads run
     * sequentially and every tracker call happens on the download coroutine, so no locking
     * is needed — distinct days/retries get distinct trackers.
     *
     * `segmentTotals` is seeded with rough size estimates so aggregate `totalBytes` stays positive
     * even when the CDN doesn't send Content-Length. Real sizes override estimates as they arrive.
     */
    private class ProgressTracker(
        totalSegments: Int,
        estimatedSegmentBytes: LongArray,
        private val elapsedMs: () -> Long,
        private val onProgress: DownloadProgressCallback?
    ) {
        private var state = DownloadProgress(totalSegments = totalSegments)
        private val bytesPerSegment = HashMap<Int, Long>()
        private val segmentTotals = HashMap<Int, Long>().apply {
            estimatedSegmentBytes.forEachIndexed { i, est -> if (est > 0) put(i, est) }
        }
        private var lastEmitMs = 0L

        init {
            state = state.copy(totalBytes = segmentTotals.values.sum().takeIf { it > 0 } ?: -1L)
        }

        fun emitInitial() {
            forceEmit { it }
        }

        fun markStarted(index: Int) {
            forceEmit { it.copy(segmentsInProgress = it.segmentsInProgress + index) }
        }

        fun markCompleted(index: Int, bytes: Long, totalBytes: Long) {
            bytesPerSegment[index] = bytes
            if (totalBytes > 0) segmentTotals[index] = totalBytes
            forceEmit {
                it.copy(
                    segmentsInProgress = it.segmentsInProgress - index,
                    segmentsFailed = it.segmentsFailed - index,
                    segmentsCompleted = it.segmentsCompleted + 1,
                    bytesDownloaded = aggregateBytes(),
                    totalBytes = aggregateTotal()
                )
            }
        }

        fun markFailed(index: Int) {
            forceEmit {
                it.copy(
                    segmentsInProgress = it.segmentsInProgress - index,
                    segmentsFailed = it.segmentsFailed + index
                )
            }
        }

        fun addBytes(index: Int, delta: Long, totalForThisSegment: Long) {
            bytesPerSegment[index] = (bytesPerSegment[index] ?: 0L) + delta
            if (totalForThisSegment > 0) segmentTotals[index] = totalForThisSegment
            maybeEmit { it.copy(bytesDownloaded = aggregateBytes(), totalBytes = aggregateTotal()) }
        }

        private fun aggregateBytes(): Long = bytesPerSegment.values.sum()

        private fun aggregateTotal(): Long {
            val sum = segmentTotals.values.sum()
            return if (sum > 0) sum else -1L
        }

        private inline fun forceEmit(transform: (DownloadProgress) -> DownloadProgress) {
            state = transform(state)
            lastEmitMs = elapsedMs()
            onProgress?.invoke(state)
        }

        /** Coalesces bursty in-stream updates: commits state every call, emits at most once per interval. */
        private inline fun maybeEmit(transform: (DownloadProgress) -> DownloadProgress) {
            state = transform(state)
            val now = elapsedMs()
            if (now - lastEmitMs < PROGRESS_EMIT_INTERVAL_MS) return
            lastEmitMs = now
            onProgress?.invoke(state)
        }
    }
}
