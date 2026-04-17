package com.nomnomsom.armstrongandgetty.data.remote

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import com.nomnomsom.armstrongandgetty.data.model.DownloadProgress
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.util.appGetRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class AudioDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AudioDownloader"
        private const val PER_ATTEMPT_READ_TIMEOUT_SECONDS = 30L
        // No call timeout — readTimeout is the stall detector. A hard call cap would kill big downloads on slow networks.
        private const val MAX_ATTEMPTS_PER_SEGMENT = 3
        private const val RETRY_BACKOFF_MS = 1000L

        // Rough bitrate estimate for mp3 podcast audio (~128 kbps). Used to seed a progress-bar total
        // when the CDN doesn't send Content-Length, so the bar has something to scale against.
        private const val ESTIMATED_BYTES_PER_SECOND = 16_000L

        // Coalesce in-segment byte updates to ~10/sec so we don't flood the main thread.
        private const val PROGRESS_EMIT_INTERVAL_MS = 100L
    }

    private val podcastDir: File
        get() = File(context.filesDir, "podcasts").also { it.mkdirs() }

    private val tightClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(PER_ATTEMPT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Download any segments that aren't already on disk, one at a time.
     *
     * Returns one outcome per segment so callers can persist successes even when some segments fail.
     * Progress is emitted as an aggregate — segmentsInProgress (single-element set), bytes downloaded,
     * completed/failed counts. Per-segment byte updates are throttled to ~10/sec.
     */
    suspend fun downloadSegments(
        date: String,
        segments: List<Segment>,
        onProgress: DownloadProgressCallback? = null
    ): List<SegmentDownloadOutcome> = withContext(Dispatchers.IO) {
        val estimates = LongArray(segments.size) { estimateBytes(segments[it]) }
        val tracker = ProgressTracker(segments.size, estimates, onProgress)
        tracker.emitInitial()

        segments.mapIndexed { index, segment ->
            downloadWithRetry(date, segment, index, tracker)
        }
    }

    /** Download one segment — used by the per-segment retry action from the UI. */
    suspend fun downloadSingleSegment(
        date: String,
        segment: Segment,
        index: Int,
        totalSegments: Int,
        onProgress: DownloadProgressCallback? = null
    ): SegmentDownloadOutcome = withContext(Dispatchers.IO) {
        val estimates = LongArray(totalSegments) { if (it == index) estimateBytes(segment) else 0L }
        val tracker = ProgressTracker(totalSegments, estimates, onProgress)
        tracker.emitInitial()
        downloadWithRetry(date, segment, index, tracker)
    }

    private fun estimateBytes(segment: Segment): Long {
        val seconds = (segment.durationMs / 1000).coerceAtLeast(1)
        return seconds * ESTIMATED_BYTES_PER_SECOND
    }

    private suspend fun downloadWithRetry(
        date: String,
        segment: Segment,
        index: Int,
        tracker: ProgressTracker
    ): SegmentDownloadOutcome {
        val destFile = segmentFile(date, index)
        if (destFile.exists() && destFile.length() > 0) {
            Log.d(TAG, "seg $index already on disk (${destFile.length()} bytes), skipping")
            val duration = measureDuration(destFile)
            tracker.markCompleted(index, destFile.length(), destFile.length())
            return SegmentDownloadOutcome.Success(index, destFile.absolutePath, duration)
        }

        tracker.markStarted(index)
        Log.d(TAG, "seg $index start: ${segment.audioUrl}")

        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS_PER_SEGMENT) { attempt ->
            try {
                val written = downloadFile(segment.audioUrl, destFile) { bytesDelta, totalBytes ->
                    tracker.addBytes(index, bytesDelta, totalBytes)
                }
                Log.d(TAG, "seg $index complete, wrote $written bytes (attempt ${attempt + 1})")
                val duration = measureDuration(destFile)
                tracker.markCompleted(index, destFile.length(), destFile.length())
                return SegmentDownloadOutcome.Success(index, destFile.absolutePath, duration)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                Log.w(TAG, "seg $index attempt ${attempt + 1}/$MAX_ATTEMPTS_PER_SEGMENT failed: ${e.javaClass.simpleName}: ${e.message}")
                destFile.delete()
                if (attempt < MAX_ATTEMPTS_PER_SEGMENT - 1) {
                    delay(RETRY_BACKOFF_MS * (attempt + 1))
                }
            }
        }

        Log.e(TAG, "seg $index giving up after $MAX_ATTEMPTS_PER_SEGMENT attempts: ${lastError?.message}")
        tracker.markFailed(index)
        return SegmentDownloadOutcome.Failure(index, lastError ?: Exception("Download failed"))
    }

    private fun downloadFile(
        url: String,
        destination: File,
        onBytes: (bytesDelta: Long, totalBytes: Long) -> Unit
    ): Long {
        val response = tightClient.newCall(appGetRequest(url)).execute()
        response.use {
            if (!it.isSuccessful) throw Exception("HTTP ${it.code}")
            val body = it.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()

            var written = 0L
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        onBytes(read.toLong(), totalBytes)
                    }
                }
            }
            return written
        }
    }

    private fun measureDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    private fun segmentFile(date: String, index: Int): File =
        File(podcastDir, "ag_${date}_seg${index}.mp3")

    fun getSegmentFiles(date: String, segmentCount: Int): List<String> =
        (0 until segmentCount).map { segmentFile(date, it).absolutePath }

    fun hasAllSegments(date: String, segmentCount: Int): Boolean =
        (0 until segmentCount).all { segmentFile(date, it).exists() }

    fun hasSegment(date: String, index: Int): Boolean =
        segmentFile(date, index).let { it.exists() && it.length() > 0 }

    fun missingSegmentIndices(date: String, segmentCount: Int): Set<Int> =
        (0 until segmentCount).filterNot { hasSegment(date, it) }.toSet()

    fun deleteSegmentFiles(date: String) {
        podcastDir.listFiles()?.filter { it.name.startsWith("ag_${date}_seg") }?.forEach { it.delete() }
    }

    /**
     * Accumulates download state and forwards updates to the user callback. Since downloads run
     * sequentially in the current design, `segmentsInProgress` holds at most one index at a time.
     * Synchronization is still here because `addBytes` is invoked from the IO thread reading bytes
     * while the caller may query state from elsewhere.
     *
     * `segmentTotals` is seeded with rough size estimates so aggregate `totalBytes` stays positive
     * even when the CDN doesn't send Content-Length. Real sizes override estimates as they arrive.
     */
    private class ProgressTracker(
        totalSegments: Int,
        estimatedSegmentBytes: LongArray,
        private val onProgress: DownloadProgressCallback?
    ) {
        private val lock = Any()
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
            synchronized(lock) {
                bytesPerSegment[index] = bytes
                if (totalBytes > 0) segmentTotals[index] = totalBytes
            }
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
            synchronized(lock) {
                bytesPerSegment[index] = (bytesPerSegment[index] ?: 0L) + delta
                if (totalForThisSegment > 0) segmentTotals[index] = totalForThisSegment
            }
            maybeEmit { it.copy(bytesDownloaded = aggregateBytes(), totalBytes = aggregateTotal()) }
        }

        private fun aggregateBytes(): Long = synchronized(lock) { bytesPerSegment.values.sum() }

        private fun aggregateTotal(): Long = synchronized(lock) {
            val sum = segmentTotals.values.sum()
            if (sum > 0) sum else -1L
        }

        private inline fun forceEmit(transform: (DownloadProgress) -> DownloadProgress) {
            val snapshot = synchronized(lock) {
                state = transform(state)
                lastEmitMs = SystemClock.elapsedRealtime()
                state
            }
            onProgress?.invoke(snapshot)
        }

        /** Coalesces bursty in-stream updates: commits state every call, emits at most once per interval. */
        private inline fun maybeEmit(transform: (DownloadProgress) -> DownloadProgress) {
            val snapshot = synchronized(lock) {
                state = transform(state)
                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitMs < PROGRESS_EMIT_INTERVAL_MS) return
                lastEmitMs = now
                state
            }
            onProgress?.invoke(snapshot)
        }
    }
}
