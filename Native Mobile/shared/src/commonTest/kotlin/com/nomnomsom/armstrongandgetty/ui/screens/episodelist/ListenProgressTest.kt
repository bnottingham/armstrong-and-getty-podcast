package com.nomnomsom.armstrongandgetty.ui.screens.episodelist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the frontier-listened bug: finishing the LOADED playlist while
 * hours are still being published must not mark the day listened (which restarted the
 * next play from zero and wiped progress).
 */
class ListenProgressTest {

    private val hourMs = 3_600_000L

    @Test
    fun finishingACompleteFullyLoadedDayMarksListened() {
        assertTrue(
            shouldMarkListened(
                positionMs = 4 * hourMs - 1000,
                durationMs = 4 * hourMs,
                dayIsComplete = true,
                daySegmentCount = 4,
                loadedSegmentCount = 4
            )
        )
    }

    @Test
    fun finishingTheLoadedPlaylistAtTheLiveFrontierIsNotListened() {
        // User finished hour 1; hours 2-4 aren't published yet.
        assertFalse(
            shouldMarkListened(
                positionMs = hourMs,
                durationMs = hourMs,
                dayIsComplete = false,
                daySegmentCount = 1,
                loadedSegmentCount = 1
            )
        )
    }

    @Test
    fun finishingWithAKnownSegmentMissingFromPlaylistIsNotListened() {
        // Day is complete but only 4 of 5 segments made it into the playlist
        // (e.g. the interview was recorded in the row before its file downloaded).
        assertFalse(
            shouldMarkListened(
                positionMs = 4 * hourMs,
                durationMs = 4 * hourMs,
                dayIsComplete = true,
                daySegmentCount = 5,
                loadedSegmentCount = 4
            )
        )
    }

    @Test
    fun midEpisodePositionIsNotListened() {
        assertFalse(
            shouldMarkListened(
                positionMs = 2 * hourMs,
                durationMs = 4 * hourMs,
                dayIsComplete = true,
                daySegmentCount = 4,
                loadedSegmentCount = 4
            )
        )
    }

    @Test
    fun zeroDurationNeverMarksListened() {
        assertFalse(
            shouldMarkListened(
                positionMs = 0,
                durationMs = 0,
                dayIsComplete = true,
                daySegmentCount = 0,
                loadedSegmentCount = 0
            )
        )
    }
}
