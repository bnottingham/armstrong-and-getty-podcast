package com.nomnomsom.armstrongandgetty.util

import kotlin.time.Clock

/** Milliseconds → "1:23:45" or "23:45". */
fun Long.formatDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}

/** Milliseconds → "2h 22m" or "45m". */
fun Long.formatShortDuration(): String {
    val totalMinutes = (this / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** Wall-clock now in epoch millis (replaces System.currentTimeMillis()). */
fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
