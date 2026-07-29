package com.nomnomsom.armstrongandgetty.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Parse an RFC-822 RSS pubDate (e.g. "Wed, 05 Mar 2026 14:00:00 GMT" or
 * "... +0000") to an Instant, or null if it doesn't parse.
 */
fun parseRssPubDate(pubDate: String): Instant? {
    // RFC_1123 covers "EEE, dd MMM yyyy HH:mm:ss GMT" and numeric zone offsets,
    // which is exactly the RFC-822 shape podcast feeds emit.
    return try {
        DateTimeComponents.Formats.RFC_1123.parse(pubDate.trim()).toInstantUsingOffset()
    } catch (_: Exception) {
        null
    }
}

/**
 * Parse an RFC-822 RSS pubDate to epoch millis, or 0L if unparseable.
 */
fun parseRssPubDateMs(pubDate: String): Long = parseRssPubDate(pubDate)?.toEpochMilliseconds() ?: 0L

/**
 * Format an Instant as a yyyy-MM-dd day key in GMT.
 */
fun formatAsDayKey(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.UTC).date.toString()

/** Parse a yyyy-MM-dd day key back to a LocalDate, or null. */
fun parseDayKey(date: String): LocalDate? = try {
    LocalDate.parse(date)
} catch (_: Exception) {
    null
}
