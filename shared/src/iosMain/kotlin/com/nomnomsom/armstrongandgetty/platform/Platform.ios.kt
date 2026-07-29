package com.nomnomsom.armstrongandgetty.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun podcastsDirPath(): String {
    val supportDir = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: error("No Application Support directory")
    val dir = "$supportDir/podcasts"
    NSFileManager.defaultManager.createDirectoryAtPath(
        dir, withIntermediateDirectories = true, attributes = null, error = null
    )
    return dir
}

@OptIn(ExperimentalForeignApi::class)
actual fun probeAudioDurationMs(filePath: String): Long {
    val url = NSURL.fileURLWithPath(filePath)
    val asset = AVURLAsset(uRL = url, options = null)
    val seconds = CMTimeGetSeconds(asset.duration)
    if (seconds.isNaN() || seconds <= 0.0) return 0L
    return (seconds * 1000).toLong()
}
