package com.nomnomsom.armstrongandgetty.download

import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.download.AutoDownloadAction.AppendSegments
import com.nomnomsom.armstrongandgetty.download.AutoDownloadAction.DownloadDay
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The background auto-download planner (Android worker + iOS BG task). With the Wi-Fi-only
 * setting off (`catchUp = false`) it must reproduce the pre-setting loop exactly.
 */
class AutoDownloadPlanTest {

    private fun day(date: String, state: DownloadState, segmentCount: Int = 4) = PodcastDay(
        date = date,
        title = "A&G $date",
        summary = "",
        segmentsJson = "[]",
        totalDurationMs = 0L,
        segmentCount = segmentCount,
        downloadState = state.value,
        combinedFilePath = null,
        isComplete = true,
        listenedPositionMs = 0L,
        isListened = false,
        lastUpdated = 0L,
    )

    /**
     * Reference copy of the loop NewEpisodeCheckWorker and the iOS BackgroundRefresh ran
     * before the Wi-Fi-only setting (f064684), reduced to the actions it took.
     */
    private fun legacyLoop(
        daysBefore: Map<String, Int>,
        daysAfter: List<PodcastDay>,
        isUserDeleted: (String) -> Boolean,
        hasAllOnDisk: (String, Int) -> Boolean,
    ): List<AutoDownloadAction> {
        val actions = mutableListOf<AutoDownloadAction>()
        for (day in daysAfter) {
            val previousCount = daysBefore[day.date]
            val isNew = previousCount == null
            val hasNewSegments = previousCount != null && day.segmentCount > previousCount
            if (isNew) {
                if (!isUserDeleted(day.date)) actions += DownloadDay(day.date)
            } else if (day.state == DownloadState.DOWNLOADED &&
                (hasNewSegments || !hasAllOnDisk(day.date, day.segmentCount))
            ) {
                actions += AppendSegments(day.date)
            }
        }
        return actions
    }

    private data class Case(
        val date: String,
        val isNew: Boolean,
        val deleted: Boolean,
        val state: DownloadState,
        val grew: Boolean,
        val onDisk: Boolean,
    )

    private val matrix: List<Case> = buildList {
        var i = 0
        for (isNew in listOf(true, false))
            for (deleted in listOf(true, false))
                for (state in DownloadState.entries)
                    for (grew in listOf(true, false))
                        for (onDisk in listOf(true, false)) {
                            val date = "2026-${(1 + i / 28).toString().padStart(2, '0')}-${(1 + i % 28).toString().padStart(2, '0')}"
                            add(Case(date, isNew, deleted, state, grew, onDisk))
                            i++
                        }
    }

    private fun scenario(cases: List<Case>): Triple<Map<String, Int>, List<PodcastDay>, Map<String, Case>> {
        val byDate = cases.associateBy { it.date }
        val daysAfter = cases.map { day(it.date, it.state) }
        val daysBefore = cases.filterNot { it.isNew }.associate { it.date to if (it.grew) 3 else 4 }
        return Triple(daysBefore, daysAfter, byDate)
    }

    @Test
    fun catchUpOffMatchesLegacyLoop() {
        // Each combination alone, then all of them together (order matters).
        for (cases in matrix.map { listOf(it) } + listOf(matrix, matrix.reversed())) {
            val (before, after, byDate) = scenario(cases)
            val deleted = { d: String -> byDate.getValue(d).deleted }
            val onDisk = { d: String, _: Int -> byDate.getValue(d).onDisk }
            assertEquals(
                legacyLoop(before, after, deleted, onDisk),
                planAutoDownloads(
                    daysBefore = before,
                    daysAfter = after,
                    // Candidates must not matter with catch-up off.
                    candidateDates = cases.map { it.date },
                    catchUp = false,
                    isUserDeleted = deleted,
                    hasAllOnDisk = onDisk,
                ),
                "cases=$cases"
            )
        }
    }

    /** With catch-up on, the legacy actions still come first and unchanged. */
    @Test
    fun catchUpOnKeepsTheLegacyActionsFirst() {
        val (before, after, byDate) = scenario(matrix)
        val deleted = { d: String -> byDate.getValue(d).deleted }
        val onDisk = { d: String, _: Int -> byDate.getValue(d).onDisk }
        val legacy = legacyLoop(before, after, deleted, onDisk)
        val plan = planAutoDownloads(before, after, matrix.map { it.date }, true, deleted, onDisk)
        assertEquals(legacy, plan.take(legacy.size))
        // Extras: only NONE, not deleted, not already planned.
        val extras = plan.drop(legacy.size)
        val planned = legacy.map { it.date }.toSet()
        for (extra in extras) {
            val case = byDate.getValue(extra.date)
            assertEquals(DownloadDay(extra.date), extra)
            assertEquals(DownloadState.NONE, case.state)
            assertEquals(false, case.deleted)
            assertEquals(false, extra.date in planned)
        }
        assertEquals(extras.size, extras.map { it.date }.toSet().size)
    }

    @Test
    fun catchUpOnAddsNoneCandidatesOnly() {
        val latest = day("2026-10-15", DownloadState.NONE)
        val today = day("2026-10-14", DownloadState.NONE)
        val olderNone = day("2026-10-13", DownloadState.NONE)
        val daysAfter = listOf(latest, today, olderNone)
        val daysBefore = daysAfter.associate { it.date to it.segmentCount }

        // Latest and today, left NONE by a deferral, are caught up; an older NONE day isn't.
        assertEquals(
            listOf(DownloadDay("2026-10-15"), DownloadDay("2026-10-14")),
            planAutoDownloads(daysBefore, daysAfter, listOf("2026-10-15", "2026-10-14"), true, { false }, { _, _ -> true })
        )

        // Nothing extra with catch-up off.
        assertEquals(
            emptyList(),
            planAutoDownloads(daysBefore, daysAfter, listOf("2026-10-15", "2026-10-14"), false, { false }, { _, _ -> true })
        )
    }

    @Test
    fun catchUpSkipsDeletedErrorAndPlannedCandidates() {
        val deleted = day("2026-10-15", DownloadState.NONE)
        val failed = day("2026-10-14", DownloadState.ERROR)
        val downloading = day("2026-10-13", DownloadState.DOWNLOADING)
        val brandNew = day("2026-10-16", DownloadState.NONE)
        val daysAfter = listOf(brandNew, deleted, failed, downloading)
        // brandNew is new since the last snapshot; the others were known.
        val daysBefore = listOf(deleted, failed, downloading).associate { it.date to it.segmentCount }

        val plan = planAutoDownloads(
            daysBefore = daysBefore,
            daysAfter = daysAfter,
            candidateDates = listOf("2026-10-16", "2026-10-15", "2026-10-14", "2026-10-13", "2026-10-01"),
            catchUp = true,
            isUserDeleted = { it == "2026-10-15" },
            hasAllOnDisk = { _, _ -> true },
        )

        // Only the new day, once — not again as a catch-up candidate.
        assertEquals(listOf(DownloadDay("2026-10-16")), plan)
    }

    @Test
    fun aDownloadedCandidateIsNotCaughtUpAgain() {
        val done = day("2026-10-15", DownloadState.DOWNLOADED)
        val plan = planAutoDownloads(
            mapOf(done.date to 4), listOf(done), listOf(done.date), true, { false }, { _, _ -> true }
        )
        assertEquals(emptyList(), plan)
        assertEquals(DownloadState.DOWNLOADED, done.state)
    }

    @Test
    fun datesWithNewContentAreNewDaysAndGrownDays() {
        val brandNew = day("2026-10-16", DownloadState.NONE, segmentCount = 1)
        val grew = day("2026-10-15", DownloadState.DOWNLOADED, segmentCount = 3)
        val same = day("2026-10-14", DownloadState.NONE, segmentCount = 4)
        val shrank = day("2026-10-13", DownloadState.DOWNLOADED, segmentCount = 2)
        val before = mapOf("2026-10-15" to 2, "2026-10-14" to 4, "2026-10-13" to 3)

        assertEquals(
            setOf("2026-10-16", "2026-10-15"),
            datesWithNewContent(before, listOf(brandNew, grew, same, shrank))
        )
        assertEquals(emptySet(), datesWithNewContent(before, listOf(same, shrank)))
    }

    @Test
    fun candidates() {
        assertEquals(listOf("2026-10-14"), autoDownloadCandidates("2026-10-14", "2026-10-14"))
        assertEquals(listOf("2026-10-15", "2026-10-14"), autoDownloadCandidates("2026-10-15", "2026-10-14"))
    }
}
