package com.nomnomsom.armstrongandgetty.platform

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File

/** Wired from Koin at startup — androidContext(). Internal to keep the surface minimal. */
internal lateinit var appContext: Context

fun initPlatformContext(context: Context) {
    appContext = context.applicationContext
}

actual fun podcastsDirPath(): String =
    File(appContext.filesDir, "podcasts").also { it.mkdirs() }.absolutePath

actual fun probeAudioDurationMs(filePath: String): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(filePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        retriever.release()
    }
}
