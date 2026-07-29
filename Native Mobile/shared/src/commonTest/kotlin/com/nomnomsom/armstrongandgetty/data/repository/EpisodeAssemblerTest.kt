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

    /** All three hour-marker styles seen in the live feed must resolve. */
    @Test
    fun hourMarkersInAllRealFeedStylesAreUnderstood() {
        assertEquals("2", EpisodeAssembler.extractHourLabel("The Best Weekend Talk Show In America (Hour Two)", 9))
        assertEquals("1", EpisodeAssembler.extractHourLabel("The Best Weekend Talk Show:  Hour One", 9))
        assertEquals("1", EpisodeAssembler.extractHourLabel("The Best Weekend Talk Show In America Hr 1", 9))
        assertEquals("4", EpisodeAssembler.extractHourLabel("The A&G Replay Monday Hour Four", 9))
        assertEquals("3", EpisodeAssembler.extractHourLabel("The A&G Replay Monday Hour 3", 9))
        // No marker → positional fallback
        assertEquals("9", EpisodeAssembler.extractHourLabel("Fauci Is Guilty of Everything!!!", 9))
    }

    /** Weekend hours with distinct pubDates (June pattern) already order correctly. */
    @Test
    fun weekendHoursWithDistinctPubDatesOrderByTime() {
        val hourOne = rssItem("The Best Weekend Talk Show In America Hour One", "Sat, 21 Jun 2026 01:58:16 +0000")
        val hourTwo = rssItem("The Best Weekend Talk Show In America Hour Two", "Sat, 21 Jun 2026 02:00:47 +0000")
        val segments = EpisodeAssembler.buildSegments(listOf(hourTwo, hourOne)) // feed order: newest first
        assertEquals(listOf("1", "2"), segments.map { it.hour })
        assertEquals(hourOne.title, segments[0].title)
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

    @Test
    fun weekendDayCompletesWithTwoHours() {
        val segments = listOf(
            Segment("1", "Hour One", "", 0, "u1", "Sat, 25 Jul 2026 07:00:00 +0000"),
            Segment("2", "Hour Two", "", 0, "u2", "Sat, 25 Jul 2026 07:00:00 +0000")
        )
        assertTrue(EpisodeAssembler.isDayComplete("2026-07-25", segments, LocalDate(2026, 7, 25)))
    }

    @Test
    fun weekendDayWithOneHourIsIncomplete() {
        val segments = listOf(
            Segment("1", "Hour One", "", 0, "u1", "Sat, 25 Jul 2026 07:00:00 +0000")
        )
        assertFalse(EpisodeAssembler.isDayComplete("2026-07-25", segments, LocalDate(2026, 7, 25)))
    }

    /** Late-evening interviews land on tomorrow's UTC key; that day is still growing. */
    @Test
    fun futureDatedStrayDayIsIncomplete() {
        val segments = EpisodeAssembler.buildSegments(StrayEveningItems.items)
        assertFalse(EpisodeAssembler.isDayComplete("2026-07-08", segments, LocalDate(2026, 7, 7)))
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
