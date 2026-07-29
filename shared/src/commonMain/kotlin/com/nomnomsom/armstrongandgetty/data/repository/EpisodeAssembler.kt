package com.nomnomsom.armstrongandgetty.data.repository

import com.nomnomsom.armstrongandgetty.data.model.RssItem
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.util.formatAsDayKey
import com.nomnomsom.armstrongandgetty.util.parseDayKey
import com.nomnomsom.armstrongandgetty.util.parseRssPubDate
import com.nomnomsom.armstrongandgetty.util.parseRssPubDateMs
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
     */
    fun buildSegments(dayItems: List<RssItem>): List<Segment> {
        return dayItems.mapIndexed { index, item ->
            val hourLabel = extractHourLabel(item.title, index + 1)
            Segment(
                hour = hourLabel,
                title = item.title,
                description = item.description,
                durationMs = item.durationSeconds * 1000,
                audioUrl = item.audioUrl,
                pubDate = item.pubDate
            )
        }.sortedBy { seg ->
            parseRssPubDateMs(seg.pubDate)
        }.mapIndexed { index, seg ->
            val hourLabel = if (seg.hour == "OMT") "OMT"
            else extractHourLabel(seg.title, index + 1)
            seg.copy(hour = hourLabel)
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

    fun extractHourLabel(title: String, fallbackIndex: Int): String {
        val hourPattern = Regex("Hour\\s+(\\d+)", RegexOption.IGNORE_CASE)
        val match = hourPattern.find(title)
        if (match != null) return match.groupValues[1]

        if (title.contains("One More Thing", ignoreCase = true) ||
            title.contains("OMT", ignoreCase = true)
        ) {
            return "OMT"
        }

        return fallbackIndex.toString()
    }

    /**
     * A day is "complete" when no more segments are expected: any past day, or today once
     * four numbered hours exist. Incomplete days are the ones the live-append machinery
     * keeps polling.
     */
    fun isDayComplete(date: String, segments: List<Segment>, today: LocalDate): Boolean {
        if (date != today.toString()) return true

        val hourSegments = segments.count { it.hour.toIntOrNull() != null }
        return hourSegments >= 4
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
