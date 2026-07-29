package com.nomnomsom.armstrongandgetty.data.repository

import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.testutil.BurstDay
import com.nomnomsom.armstrongandgetty.testutil.StrayEveningItems
import com.nomnomsom.armstrongandgetty.testutil.rssItem
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodeAssemblerTest {

    private val today = LocalDate(2026, 7, 23)

    // ---- grouping ----

    @Test
    fun groupsWeekdayItemsIntoOneUtcDay() {
        val grouped = EpisodeAssembler.groupItemsByDate(BurstDay.feedItems(*BurstDay.all.toTypedArray()))
        assertEquals(setOf(BurstDay.DATE), grouped.keys)
        assertEquals(5, grouped.getValue(BurstDay.DATE).size)
    }

    @Test
    fun groupingDropsUnparseablePubDates() {
        val bad = rssItem("Broken", "not a date")
        val grouped = EpisodeAssembler.groupItemsByDate(listOf(BurstDay.hour1, bad))
        assertEquals(1, grouped.getValue(BurstDay.DATE).size)
    }

    @Test
    fun groupingAcceptsBothGmtAndNumericOffsetDates() {
        val gmt = rssItem("GMT item", "Thu, 23 Jul 2026 14:45:20 GMT")
        val offset = rssItem("Offset item", "Thu, 23 Jul 2026 15:45:20 +0000")
        val grouped = EpisodeAssembler.groupItemsByDate(listOf(gmt, offset))
        assertEquals(2, grouped.getValue("2026-07-23").size)
    }

    /**
     * Documents current behavior: interview segments recorded in the evening (PT) carry
     * pubDates past UTC midnight, so they group into the NEXT UTC day and form a separate
     * "phantom" card that becomes the newest day in the list. See the engine report —
     * this steals `latestDate` from the real current day in the ViewModel refresh path.
     */
    @Test
    fun strayEveningItemsGroupIntoNextUtcDay_documentedQuirk() {
        val grouped = EpisodeAssembler.groupItemsByDate(StrayEveningItems.items)
        assertEquals(setOf("2026-07-08"), grouped.keys)
    }

    // ---- ordering + labels ----

    @Test
    fun weekdaySegmentsAreOrderedChronologicallyFromNewestFirstFeed() {
        val segments = EpisodeAssembler.buildSegments(BurstDay.feedItems(*BurstDay.all.toTypedArray()))
        assertEquals(
            listOf(
                BurstDay.hour1.title, BurstDay.hour2.title, BurstDay.hour3.title,
                BurstDay.hour4.title, BurstDay.bonus.title
            ),
            segments.map { it.title }
        )
        assertEquals(listOf("1", "2", "3", "4", "5"), segments.map { it.hour })
    }

    @Test
    fun partialDayKeepsStableOrderAsSegmentsArrive() {
        // Hour 1 alone…
        val early = EpisodeAssembler.buildSegments(BurstDay.feedItems(BurstDay.hour1))
        assertEquals(listOf(BurstDay.hour1.title), early.map { it.title })

        // …then the burst arrives. Existing item must keep position 0.
        val late = EpisodeAssembler.buildSegments(BurstDay.feedItems(*BurstDay.all.toTypedArray()))
        assertEquals(BurstDay.hour1.title, late[0].title)
        assertEquals(early[0].audioUrl, late[0].audioUrl)
    }

    @Test
    fun omtTitleIsLabelledOmt() {
        val omt = rssItem("A Special Pre-Vacation One More Thing", "Thu, 23 Jul 2026 21:00:00 +0000")
        val segments = EpisodeAssembler.buildSegments(BurstDay.feedItems(BurstDay.hour1, omt))
        assertEquals(listOf("1", "OMT"), segments.map { it.hour })
    }

    @Test
    fun numericHourInTitleWins() {
        val titled = rssItem("The A&G Replay Monday Hour 3", "Thu, 23 Jul 2026 16:00:00 +0000")
        val segments = EpisodeAssembler.buildSegments(listOf(titled))
        assertEquals("3", segments[0].hour)
    }

    // ---- merge of downloaded metadata ----

    @Test
    fun mergeCarriesActualDurationByAudioUrl() {
        val fresh = EpisodeAssembler.buildSegments(BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2))
        val existing = listOf(fresh[0].copy(actualDurationMs = 1234L))
        val merged = EpisodeAssembler.mergeDownloadedMetadata(fresh, existing)
        assertEquals(1234L, merged[0].actualDurationMs)
        assertEquals(0L, merged[1].actualDurationMs)
    }

    @Test
    fun mergeFallsBackToPubDateAndTitleWhenUrlChanges() {
        val fresh = EpisodeAssembler.buildSegments(listOf(BurstDay.hour1))
        val existing = listOf(fresh[0].copy(audioUrl = "https://old.example/replaced.mp3", actualDurationMs = 999L))
        val merged = EpisodeAssembler.mergeDownloadedMetadata(fresh, existing)
        assertEquals(999L, merged[0].actualDurationMs)
    }

    // ---- completeness ----

    @Test
    fun pastDayIsAlwaysComplete() {
        val segments = EpisodeAssembler.buildSegments(BurstDay.feedItems(BurstDay.hour1))
        assertTrue(EpisodeAssembler.isDayComplete(BurstDay.DATE, segments, today = LocalDate(2026, 7, 24)))
    }

    @Test
    fun todayWithOneHourIsIncomplete() {
        val segments = EpisodeAssembler.buildSegments(BurstDay.feedItems(BurstDay.hour1))
        assertFalse(EpisodeAssembler.isDayComplete(BurstDay.DATE, segments, today))
    }

    @Test
    fun todayWithFourHoursIsComplete() {
        val segments = EpisodeAssembler.buildSegments(
            BurstDay.feedItems(BurstDay.hour1, BurstDay.hour2, BurstDay.hour3, BurstDay.hour4)
        )
        assertTrue(EpisodeAssembler.isDayComplete(BurstDay.DATE, segments, today))
    }

    /**
     * Documents current behavior: a 2-hour weekend day never reaches the 4-hour bar, so
     * it stays "incomplete" its whole calendar day and the player polls it fruitlessly
     * every 2 minutes. Harmless but noisy — see report.
     */
    @Test
    fun weekendDayNeverCompletesOnItsOwnDay_documentedQuirk() {
        val segments = listOf(
            Segment("1", "Hour One", "", 0, "u1", "Sat, 25 Jul 2026 07:00:00 +0000"),
            Segment("2", "Hour Two", "", 0, "u2", "Sat, 25 Jul 2026 07:00:00 +0000")
        )
        assertFalse(EpisodeAssembler.isDayComplete("2026-07-25", segments, LocalDate(2026, 7, 25)))
    }

    // ---- titles / summary ----

    @Test
    fun dayTitleFormatsFromDayKey() {
        assertEquals("A&G — Jul 23, 2026", EpisodeAssembler.formatDayTitle("2026-07-23"))
    }

    @Test
    fun summaryConcatenatesAndCaps() {
        val segments = List(10) {
            Segment("1", "t", "d".repeat(80), 0, "u$it", "Thu, 23 Jul 2026 14:00:00 +0000")
        }
        val summary = EpisodeAssembler.buildSummary(segments)
        assertEquals(401, summary.length) // 400 + ellipsis
        assertTrue(summary.endsWith("…"))
    }
}
