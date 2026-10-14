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
 * Dates with content new since [daysBefore]: a day not seen before, or one with more hours.
 * Background runs log `download_waiting_wifi` only for these, so a day that stays deferred
 * isn't re-counted on every run.
 */
fun datesWithNewContent(daysBefore: Map<String, Int>, daysAfter: List<PodcastDay>): Set<String> =
    daysAfter
        .filter { day -> daysBefore[day.date].let { before -> before == null || day.segmentCount > before } }
        .mapTo(mutableSetOf()) { it.date }

/**
 * Days absent from [daysBefore] that are newly published, not just new to this install's
 * database. With an empty [daysBefore] (first run after install or cleared data) the whole
 * feed — two years of show days — is a baseline, so only [candidateDates] count. Otherwise a
 * day counts when it is a candidate or newer than every known day: `refreshFeed` stores days
 * newest first, one row at a time, so a snapshot taken during (or left by an interrupted)
 * first refresh holds only the newest days, and the older rest must not look new either.
 */
fun newlyPublishedDates(
    daysBefore: Map<String, Int>,
    daysAfter: List<PodcastDay>,
    candidateDates: List<String>,
): Set<String> {
    val newestKnown = daysBefore.keys.maxOrNull()
    return daysAfter
        .map { it.date }
        .filter { date ->
            date !in daysBefore &&
                (date in candidateDates || (newestKnown != null && date > newestKnown))
        }
        .toSet()
}

/**
 * Pure planner shared by `NewEpisodeCheckWorker` and the iOS background refresh. Rules, in
 * [daysAfter] order:
 * - a day that is newly published ([newlyPublishedDates]) and not user-deleted →
 *   [AutoDownloadAction.DownloadDay];
 * - a DOWNLOADED day whose segment count grew, or with files missing on disk →
 *   [AutoDownloadAction.AppendSegments].
 *
 * `catchUp = false` reproduces the loop both background paths ran before the Wi-Fi-only
 * setting existed (AutoDownloadPlanTest holds a reference copy), except that a day new to the
 * database but not newly published is no longer downloaded — that loop made a first run
 * download every day in the feed. `catchUp = true` (setting ON and downloads allowed right
 * now) also downloads [candidateDates] left `NONE` by an earlier Wi-Fi-only deferral — not
 * ERROR days (real failures, retried at app open as before), not user-deleted days, and never
 * a second action for the same date.
 *
 * Catch-up covers only [candidateDates] (latest + today): a day deferred and then overtaken
 * by a newer show day is not fetched automatically — the listener taps Download on Wi-Fi.
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
    val published = newlyPublishedDates(daysBefore, daysAfter, candidateDates)
    for (day in daysAfter) {
        val previousCount = daysBefore[day.date]
        when {
            previousCount == null -> {
                if (day.date in published && !isUserDeleted(day.date)) {
                    actions += AutoDownloadAction.DownloadDay(day.date)
                }
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
