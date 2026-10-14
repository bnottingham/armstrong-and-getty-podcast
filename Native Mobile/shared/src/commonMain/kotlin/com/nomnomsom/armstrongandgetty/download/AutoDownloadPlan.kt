package com.nomnomsom.armstrongandgetty.download

import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.state

/** One automatic download the background refresh (Android worker / iOS BG task) should run. */
sealed interface AutoDownloadAction {
    val date: String

    /** `PodcastRepository.downloadDay`. */
    data class DownloadDay(override val date: String) : AutoDownloadAction

    /** `PodcastRepository.appendNewSegments`. */
    data class AppendSegments(override val date: String) : AutoDownloadAction
}

/**
 * The days automatic downloads manage: the newest day, plus today's (UTC) when it differs —
 * late-evening interviews carry pubDates past UTC midnight and form a later-dated card,
 * which would otherwise steal "latest" and orphan the day still being aired.
 */
fun autoDownloadCandidates(latestDate: String, todayKey: String): List<String> =
    if (todayKey != latestDate) listOf(latestDate, todayKey) else listOf(latestDate)

/**
 * Pure planner shared by `NewEpisodeCheckWorker` and the iOS background refresh. Rules, in
 * [daysAfter] order:
 * - a day that is new since [daysBefore] and not user-deleted → [AutoDownloadAction.DownloadDay];
 * - a DOWNLOADED day whose segment count grew, or with files missing on disk →
 *   [AutoDownloadAction.AppendSegments].
 *
 * `catchUp = false` reproduces the loop both background paths ran before the Wi-Fi-only
 * setting existed, exactly (AutoDownloadPlanTest holds a reference copy). `catchUp = true`
 * (setting ON) also downloads [candidateDates] left `NONE` by an earlier Wi-Fi-only deferral
 * — not ERROR days (real failures, retried at app open as before), not user-deleted days,
 * and never a second action for the same date.
 */
fun planAutoDownloads(
    daysBefore: Map<String, Int>,
    daysAfter: List<PodcastDay>,
    candidateDates: List<String>,
    catchUp: Boolean,
    isUserDeleted: (String) -> Boolean,
    hasAllOnDisk: (String, Int) -> Boolean,
): List<AutoDownloadAction> {
    val actions = mutableListOf<AutoDownloadAction>()
    for (day in daysAfter) {
        val previousCount = daysBefore[day.date]
        when {
            previousCount == null -> {
                if (!isUserDeleted(day.date)) actions += AutoDownloadAction.DownloadDay(day.date)
            }
            day.state == DownloadState.DOWNLOADED &&
                (day.segmentCount > previousCount || !hasAllOnDisk(day.date, day.segmentCount)) -> {
                actions += AutoDownloadAction.AppendSegments(day.date)
            }
        }
    }

    if (catchUp) {
        val byDate = daysAfter.associateBy { it.date }
        val planned = actions.mapTo(mutableSetOf()) { it.date }
        for (date in candidateDates) {
            val day = byDate[date] ?: continue
            if (day.state != DownloadState.NONE || date in planned || isUserDeleted(date)) continue
            actions += AutoDownloadAction.DownloadDay(date)
            planned += date
        }
    }
    return actions
}
