package com.nomnomsom.armstrongandgetty.data.remote

import com.nomnomsom.armstrongandgetty.data.model.DownloadProgress
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.download.WifiRequiredException
import com.nomnomsom.armstrongandgetty.testutil.ScriptedEngine
import com.nomnomsom.armstrongandgetty.testutil.ToggleGate
import io.ktor.client.HttpClient
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * AudioDownloader's Wi-Fi-only backstop, against a scripted HTTP engine (real time: the
 * downloader runs on Dispatchers.IO). A block is `Failure(WifiRequiredException)`, never a
 * failed segment, and spends no retries. Nothing here writes audio: every request fails or
 * hangs before a byte arrives.
 */
class AudioDownloaderGateTest {

    private fun segment(n: Int) = Segment(
        hour = "$n",
        title = "Hour $n",
        description = "",
        durationMs = 60_000L,
        audioUrl = "https://cdn.example/gate-test-$n.mp3",
        pubDate = "Thu, 01 Jan 2099 1$n:00:00 +0000"
    )

    private class Run(val outcome: SegmentDownloadOutcome, val progress: List<DownloadProgress>)

    private suspend fun downloadOne(
        gate: ToggleGate,
        engine: ScriptedEngine,
        date: String,
    ): Run {
        val progress = mutableListOf<DownloadProgress>()
        val downloader = AudioDownloader(HttpClient(engine), gate)
        val outcome = downloader.downloadSingleSegment(date, segment(1), index = 0, totalSegments = 1) {
            progress += it
        }
        return Run(outcome, progress)
    }

    private fun assertBlockedNotFailed(run: Run) {
        assertIs<SegmentDownloadOutcome.Failure>(run.outcome)
        assertIs<WifiRequiredException>((run.outcome as SegmentDownloadOutcome.Failure).error)
        assertTrue(run.progress.none { it.segmentsFailed.isNotEmpty() }, "never in segmentsFailed: ${run.progress}")
        assertTrue(run.progress.last().segmentsInProgress.isEmpty(), "no longer in flight")
    }

    @Test
    fun closedGateNeverStartsATransfer() = runTest {
        val gate = ToggleGate(open = false)
        val engine = ScriptedEngine { error("no request expected") }

        val run = downloadOne(gate, engine, "2099-01-01")

        assertBlockedNotFailed(run)
        assertEquals(0, engine.requests)
    }

    /** The transfer fails because Wi-Fi went away (iOS refuses the expensive route): blocked, no retries. */
    @Test
    fun failureWhileTheGateClosedIsABlockAndSpendsNoRetries() = runTest {
        val gate = ToggleGate(open = true)
        val engine = ScriptedEngine {
            gate.open = false
            throw IOException("The network connection was lost.")
        }

        val run = downloadOne(gate, engine, "2099-01-02")

        assertBlockedNotFailed(run)
        assertEquals(1, engine.requests, "a block must not be retried")
    }

    /** Wi-Fi drops mid-transfer and the connection just hangs: the watchdog's gate check stops it. */
    @Test
    fun watchdogCancelsAHangingTransferWhenTheGateCloses() = runTest(timeout = 30.seconds) {
        val gate = ToggleGate(open = true)
        val engine = ScriptedEngine {
            gate.open = false
            awaitCancellation()
        }

        val run = downloadOne(gate, engine, "2099-01-03")

        assertBlockedNotFailed(run)
        assertEquals(1, engine.requests, "the watchdog's cancel is a block, not a stall to retry")
    }

    /** Control: the gate stays open (the setting-off path), so a real failure is retried and reported. */
    @Test
    fun realFailureWithAnOpenGateIsRetriedAndFailed() = runTest(timeout = 30.seconds) {
        val gate = ToggleGate(open = true)
        val engine = ScriptedEngine { throw IOException("HTTP 503") }

        val run = downloadOne(gate, engine, "2099-01-04")

        val failure = assertIs<SegmentDownloadOutcome.Failure>(run.outcome)
        assertFalse(failure.error is WifiRequiredException)
        assertEquals(3, engine.requests, "MAX_ATTEMPTS_PER_SEGMENT")
        assertEquals(setOf(0), run.progress.last().segmentsFailed)
    }

    /** A whole day held back: every segment blocked, nothing failed, nothing requested. */
    @Test
    fun aBlockedDayReportsNoFailedSegments() = runTest {
        val gate = ToggleGate(open = false)
        val engine = ScriptedEngine { error("no request expected") }
        val progress = mutableListOf<DownloadProgress>()

        val outcomes = AudioDownloader(HttpClient(engine), gate)
            .downloadSegments("2099-01-05", listOf(segment(1), segment(2))) { progress += it }

        assertTrue(outcomes.all { it is SegmentDownloadOutcome.Failure && it.error is WifiRequiredException })
        assertTrue(progress.none { it.segmentsFailed.isNotEmpty() })
        assertEquals(0, engine.requests)
    }
}
