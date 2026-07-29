package com.nomnomsom.armstrongandgetty.data.repository

import com.nomnomsom.armstrongandgetty.data.model.RssItem
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.util.formatAsDayKey
import com.nomnomsom.armstrongandgetty.util.parseDayKey
import com.nomnomsom.armstrongandgetty.util.parseRssPubDate
import com.nomnomsom.armstrongandgetty.util.parseRssPubDateMs
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

/**
 * The episode-creation engine: pure functions that turn the raw RSS item list into the
 * per-day segment model. Extracted from PodcastRepository so the grouping / ordering /
 * labeling / completeness rules are directly unit-testable — this is the logic that has
 * to be right when segments trickle in over the morning while the user is listening.
 */
object EpisodeAssembler {

    private val dayTitleFormat = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        char(',')
        char(' ')
        year()
    }

    /**
     * Group feed items into day buckets keyed yyyy-MM-dd (UTC date of pubDate), newest day
     * first. Items with unparseable pubDates are dropped. Within a day, items keep feed
     * order (feeds are newest-first).
     */
    fun groupItemsByDate(items: List<RssItem>): Map<String, List<RssItem>> {
        return items.mapNotNull { item ->
            parseRssPubDate(item.pubDate)?.let { formatAsDayKey(it) to item }
        }
            .groupBy({ it.first }, { it.second })
            .toList()
            .sortedByDescending { it.first }
            .toMap()
    }

    /**
     * Order a day's items chronologically and label each with its hour number
     * ("1".."4", "OMT", or a positional fallback).
     *
     * Ordering: pubDate first; ties (weekend shows publish both hours with one
     * timestamp) break on the hour number in the title; remaining ties keep upload
     * order (the feed is newest-first, so reversed feed order is chronological).
     */
    fun buildSegments(dayItems: List<RssItem>): List<Segment> {
        return dayItems.asReversed()
            .sortedWith(
                compareBy(
                    { item -> parseRssPubDateMs(item.pubDate) },
                    { item -> explicitHourNumber(item.title) ?: Int.MAX_VALUE }
                )
            )
            .mapIndexed { index, item ->
                Segment(
                    hour = extractHourLabel(item.title, index + 1),
                    title = item.title,
                    description = item.description,
                    durationMs = item.durationSeconds * 1000,
                    audioUrl = item.audioUrl,
                    pubDate = item.pubDate
                )
            }
    }

    /** Carry measured (on-disk) durations from previously-stored segments onto fresh ones. */
    fun mergeDownloadedMetadata(
        freshSegments: List<Segment>,
        existingSegments: List<Segment>
    ): List<Segment> {
        if (existingSegments.isEmpty()) return freshSegments

        val existingByUrl = existingSegments.associateBy { it.audioUrl }
        return freshSegments.map { fresh ->
            val existing = existingByUrl[fresh.audioUrl]
                ?: existingSegments.firstOrNull {
                    it.pubDate == fresh.pubDate && it.title == fresh.title
                }
            val actualDurationMs = existing?.actualDurationMs ?: 0L
            if (actualDurationMs > 0) {
                fresh.copy(actualDurationMs = actualDurationMs)
            } else {
                fresh
            }
        }
    }

    // The live feed writes hour markers three ways: "Hour 3", "Hr 1", and spelled out
    // ("The Best Weekend Talk Show In America (Hour Two)" — the dominant weekend form).
    private val digitHourPattern = Regex("""\b(?:hour|hr)\.?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val spelledHourPattern =
        Regex("""\b(?:hour|hr)\s+(one|two|three|four|five|six)\b""", RegexOption.IGNORE_CASE)
    private val spelledNumbers =
        mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6)

    /** The hour number explicitly present in [title], or null if the title carries none. */
    fun explicitHourNumber(title: String): Int? {
        digitHourPattern.find(title)?.let { return it.groupValues[1].toIntOrNull() }
        spelledHourPattern.find(title)?.let { return spelledNumbers[it.groupValues[1].lowercase()] }
        return null
    }

    fun extractHourLabel(title: String, fallbackIndex: Int): String {
        explicitHourNumber(title)?.let { return it.toString() }

        if (title.contains("One More Thing", ignoreCase = true) ||
            title.contains("OMT", ignoreCase = true)
        ) {
            return "OMT"
        }

        return fallbackIndex.toString()
    }

    /**
     * A day is "complete" when no more segments are expected. Incomplete days are the
     * ones the live-append machinery keeps polling.
     *
     * Past days are done. Future-dated days are still growing — late-evening interviews
     * carry pubDates past UTC midnight, landing on a key the next morning's hours will
     * join. Today needs four numbered hours on weekdays; the weekend show is only two.
     * [today] must be the UTC date, matching the UTC-derived day keys.
     */
    fun isDayComplete(date: String, segments: List<Segment>, today: LocalDate): Boolean {
        val dayDate = parseDayKey(date) ?: return true
        if (dayDate < today) return true
        if (dayDate > today) return false

        val requiredHours = when (dayDate.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2
            else -> 4
        }
        val hourSegments = segments.count { it.hour.toIntOrNull() != null }
        return hourSegments >= requiredHours
    }

    fun formatDayTitle(date: String): String {
        val parsed = parseDayKey(date) ?: return "A&G — $date"
        return "A&G — ${dayTitleFormat.format(parsed)}"
    }

    fun buildSummary(segments: List<Segment>): String {
        val combined = segments
            .map { it.description }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (combined.length > 400) combined.take(400) + "…" else combined
    }
}
