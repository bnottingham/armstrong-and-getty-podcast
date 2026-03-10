package com.nomnomsom.armstrongandgetty.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.google.gson.Gson
import com.nomnomsom.armstrongandgetty.data.model.TimedWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEPRECATED: Local transcription engine using Vosk.
 * Transcription is now handled server-side.
 * This class is kept temporarily but the Vosk dependencies have been removed.
 */
@Singleton
class TranscriptionEngine @Inject constructor(
    private val gson: Gson
) {
    /**
     * Parse JSON result to extract timed words.
     */
    fun parseWords(json: String): List<TimedWord> {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<TimedWord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
