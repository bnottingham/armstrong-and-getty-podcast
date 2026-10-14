package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import com.nomnomsom.armstrongandgetty.data.model.DownloadProgress
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What on the Episodes list holds the in-app rating prompt (EpisodeListViewModel.reviewBlocker). */
class ReviewBlockerTest {

    private fun day(date: String, state: DownloadState) = PodcastDay(
        date = date,
        title = "A&G $date",
        summary = "",
        segmentsJson = "[]",
        totalDurationMs = 0L,
        segmentCount = 4,
        downloadState = state.value,
        combinedFilePath = null,
        isComplete = true,
        listenedPositionMs = 0L,
        isListened = false,
        lastUpdated = 0L,
    )

    private val quietList = EpisodeListUiState(
        days = listOf(day("2026-10-13", DownloadState.DOWNLOADED), day("2026-10-12", DownloadState.NONE)),
    )

    @Test
    fun aQuietListDoesNotBlock() {
        assertFalse(hasVisibleReviewBlocker(quietList, emptyMap()))
    }

    /** A feed refresh that failed offline sets `error`, which nothing renders. */
    @Test
    fun anUnrenderedRefreshErrorDoesNotBlock() {
        assertFalse(hasVisibleReviewBlocker(quietList.copy(error = "Unable to resolve host"), emptyMap()))
    }

    @Test
    fun aDownloadFailedRowFromAnEarlierRunBlocks() {
        val withError = quietList.copy(days = quietList.days + day("2026-10-09", DownloadState.ERROR))
        assertTrue(hasVisibleReviewBlocker(withError, emptyMap()))
    }

    @Test
    fun failedSegmentsBlock() {
        val progress = mapOf("2026-10-13" to DownloadProgress(totalSegments = 4, segmentsFailed = setOf(2)))
        assertTrue(hasVisibleReviewBlocker(quietList, progress))
    }

    @Test
    fun aRefreshInProgressBlocks() {
        assertTrue(hasVisibleReviewBlocker(quietList.copy(isRefreshing = true), emptyMap()))
    }

    /** Today's hours download in the background most mornings; that alone is not a problem. */
    @Test
    fun aHealthyDownloadInProgressDoesNotBlock() {
        val progress = mapOf(
            "2026-10-13" to DownloadProgress(totalSegments = 4, segmentsInProgress = setOf(3), segmentsCompleted = 2)
        )
        val downloading = quietList.copy(days = listOf(day("2026-10-13", DownloadState.DOWNLOADING)))
        assertFalse(hasVisibleReviewBlocker(downloading, progress))
    }
}
