package com.nomnomsom.armstrongandgetty.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val GMT = TimeZone.getTimeZone("GMT")

private fun gmtFormatter(pattern: String): SimpleDateFormat =
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = GMT }

/**
 * Parse an RFC-822 RSS pubDate (e.g. "Wed, 05 Mar 2026 14:00:00 GMT" or
 * "... +0000") to a Date, or null if none of the known formats match.
 */
fun parseRssPubDate(pubDate: String): Date? {
    // Fast path: strip a trailing GMT / +0000 / -0000 and parse.
    val cleaned = pubDate
        .replace(" GMT", "")
        .replace(" +0000", "")
        .replace(" -0000", "")
    gmtFormatter("EEE, dd MMM yyyy HH:mm:ss").let { fmt ->
        try {
            fmt.parse(cleaned)?.let { return it }
        } catch (_: Exception) {
            // fall through
        }
    }
    // Fallback: let SimpleDateFormat handle zone tokens directly.
    for (pattern in listOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z"
    )) {
        try {
            gmtFormatter(pattern).parse(pubDate)?.let { return it }
        } catch (_: Exception) {
            // try next
        }
    }
    return null
}

/**
 * Parse an RFC-822 RSS pubDate to epoch millis, or 0L if unparseable.
 */
fun parseRssPubDateMs(pubDate: String): Long = parseRssPubDate(pubDate)?.time ?: 0L

/**
 * Format a Date as a yyyy-MM-dd day key in GMT.
 */
fun formatAsDayKey(date: Date): String = gmtFormatter("yyyy-MM-dd").format(date)
