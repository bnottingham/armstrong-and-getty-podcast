package com.nomnomsom.armstrongandgetty.download

import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.state
import com.nomnomsom.armstrongandgetty.download.AutoDownloadAction.AppendSegments
import com.nomnomsom.armstrongandgetty.download.AutoDownloadAction.DownloadDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The background auto-download planner (Android worker + iOS BG task). With the Wi-Fi-only
 * setting off (`catchUp = false`) it must reproduce the pre-setting loop, except that a first
 * run (empty `daysBefore`) takes the feed as a baseline instead of downloading all of it.
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

    /**
     * The legacy loop minus the first-run flood: a day new to the database is downloaded only
     * when it is a candidate or newer than every known day.
     */
    private fun legacyWithBaseline(
        daysBefore: Map<String, Int>,
        daysAfter: List<PodcastDay>,
        candidates: List<String>,
        isUserDeleted: (String) -> Boolean,
        hasAllOnDisk: (String, Int) -> Boolean,
    ): List<AutoDownloadAction> {
        val newestKnown = daysBefore.keys.maxOrNull()
        return legacyLoop(daysBefore, daysAfter, isUserDeleted, hasAllOnDisk).filter { action ->
            action !is DownloadDay || action.date in candidates ||
                (newestKnown != null && action.date > newestKnown)
        }
    }

    /** Every day from [from] through [to], newest first — the order the DAO snapshot returns. */
    private fun feed(from: String, to: String, state: DownloadState = DownloadState.NONE): List<PodcastDay> {
        val days = mutableListOf<PodcastDay>()
        var date = LocalDate.parse(from)
        val last = LocalDate.parse(to)
        while (date <= last) {
            days += day(date.toString(), state)
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return days.asReversed()
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
                    // Every day a candidate: the baseline rule never applies, so legacy exactly.
                    candidateDates = cases.map { it.date },
                    catchUp = false,
                    isUserDeleted = deleted,
                    hasAllOnDisk = onDisk,
                ),
                "cases=$cases"
            )
        }
    }

    /** With fewer candidates, only the not-newly-published DownloadDays drop out of legacy. */
    @Test
    fun catchUpOffDiffersFromLegacyOnlyByTheBaseline() {
        for (cases in matrix.map { listOf(it) } + listOf(matrix, matrix.reversed())) {
            val (before, after, byDate) = scenario(cases)
            val deleted = { d: String -> byDate.getValue(d).deleted }
            val onDisk = { d: String, _: Int -> byDate.getValue(d).onDisk }
            val dates = cases.map { it.date }
            for (candidates in listOf(emptyList(), dates.filterIndexed { i, _ -> i % 3 == 0 }, listOf(dates.max()))) {
                assertEquals(
                    legacyWithBaseline(before, after, candidates, deleted, onDisk),
                    planAutoDownloads(before, after, candidates, false, deleted, onDisk),
                    "cases=$cases candidates=$candidates"
                )
            }
        }
    }

    // First run after install / cleared data (Oct 14, 2026: the worker queued every day in the
    // feed, 2024-10-01 through 2026-10-14, ~40 MB per hour, on any network).

    @Test
    fun firstRunDownloadsOnlyTheLatestDay() {
        val daysAfter = feed("2024-10-01", "2026-10-14")
        val candidates = autoDownloadCandidates("2026-10-14", "2026-10-14")
        for (catchUp in listOf(false, true)) {
            assertEquals(
                listOf(DownloadDay("2026-10-14")),
                planAutoDownloads(emptyMap(), daysAfter, candidates, catchUp, { false }, { _, _ -> false }),
                "catchUp=$catchUp"
            )
        }
    }

    @Test
    fun firstRunDownloadsLatestAndTodayWhenTheyDiffer() {
        // A late-evening interview past UTC midnight formed a later-dated card.
        val daysAfter = feed("2024-10-01", "2026-10-15")
        val candidates = autoDownloadCandidates("2026-10-15", "2026-10-14")
        assertEquals(
            listOf(DownloadDay("2026-10-15"), DownloadDay("2026-10-14")),
            planAutoDownloads(emptyMap(), daysAfter, candidates, false, { false }, { _, _ -> false })
        )
        // A deleted candidate stays deleted; nothing older stands in for it.
        assertEquals(
            listOf(DownloadDay("2026-10-14")),
            planAutoDownloads(emptyMap(), daysAfter, candidates, false, { it == "2026-10-15" }, { _, _ -> false })
        )
    }

    @Test
    fun firstRunBeforeTodaysShowDownloadsOnlyTheLatestDay() {
        // Today (UTC) has no hours in the feed yet, so its candidate matches no day.
        val daysAfter = feed("2024-10-01", "2026-10-13")
        assertEquals(
            listOf(DownloadDay("2026-10-13")),
            planAutoDownloads(
                emptyMap(), daysAfter, autoDownloadCandidates("2026-10-13", "2026-10-14"), true, { false }, { _, _ -> false }
            )
        )
    }

    @Test
    fun aSnapshotLeftByAnInterruptedFirstRefreshDownloadsNoOldDays() {
        // refreshFeed stores days newest first, one row at a time; it stopped after three.
        val daysAfter = feed("2024-10-01", "2026-10-14")
        val daysBefore = daysAfter.take(3).associate { it.date to it.segmentCount }
        assertEquals(
            emptyList(),
            planAutoDownloads(daysBefore, daysAfter, listOf("2026-10-14"), false, { false }, { _, _ -> false })
        )
        // A show day published since still downloads.
        val withNewDay = listOf(day("2026-10-15", DownloadState.NONE)) + daysAfter
        assertEquals(
            listOf(DownloadDay("2026-10-15")),
            planAutoDownloads(daysBefore, withNewDay, listOf("2026-10-15"), false, { false }, { _, _ -> false })
        )
    }

    @Test
    fun everyDayPublishedSinceTheLastRunStillDownloads() {
        // Days without a run (phone off): each newer day downloads, as before.
        val daysAfter = feed("2026-10-01", "2026-10-14")
        val daysBefore = daysAfter.filter { it.date <= "2026-10-09" }.associate { it.date to it.segmentCount }
        assertEquals(
            listOf("2026-10-14", "2026-10-13", "2026-10-12", "2026-10-11", "2026-10-10").map(::DownloadDay),
            planAutoDownloads(daysBefore, daysAfter, listOf("2026-10-14"), false, { false }, { _, _ -> true })
        )
    }

    @Test
    fun newlyPublishedDatesAreCandidatesOrNewerThanEveryKnownDay() {
        val days = feed("2026-10-12", "2026-10-16")
        // First run: candidates only, and only those in the feed.
        assertEquals(setOf("2026-10-16"), newlyPublishedDates(emptyMap(), days, listOf("2026-10-16", "2026-10-17")))
        assertEquals(emptySet(), newlyPublishedDates(emptyMap(), days, emptyList()))
        // Known through 10-14 (10-12 missing): newer days count; the older gap only as a candidate.
        val before = mapOf("2026-10-14" to 4, "2026-10-13" to 4)
        assertEquals(setOf("2026-10-16", "2026-10-15"), newlyPublishedDates(before, days, listOf("2026-10-16")))
        assertEquals(
            setOf("2026-10-16", "2026-10-15", "2026-10-12"),
            newlyPublishedDates(before, days, listOf("2026-10-16", "2026-10-12"))
        )
        // A known day is never newly published, candidate or not.
        assertEquals(emptySet(), newlyPublishedDates(days.associate { it.date to 4 }, days, days.map { it.date }))
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
