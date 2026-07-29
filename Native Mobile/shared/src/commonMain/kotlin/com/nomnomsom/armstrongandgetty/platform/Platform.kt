package com.nomnomsom.armstrongandgetty.platform

/**
 * Absolute path of the directory segment audio files live in. Created if missing.
 * Android: <filesDir>/podcasts (same location as the previous app — downloads survive
 * the upgrade). iOS: <Application Support>/podcasts.
 */
expect fun podcastsDirPath(): String

/**
 * Measure the duration of an audio file on disk, in milliseconds. Returns 0 when the
 * file can't be probed — callers treat that as "corrupt, re-download".
 */
expect fun probeAudioDurationMs(filePath: String): Long
