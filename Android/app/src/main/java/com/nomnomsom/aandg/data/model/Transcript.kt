package com.nomnomsom.aandg.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single word with its timing within a segment.
 * Serialized to JSON and stored in TranscriptEntity.wordsJson.
 */
data class TimedWord(
    val word: String,
    val startMs: Long, // Start time relative to the segment start
    val endMs: Long,   // End time relative to the segment start
    val confidence: Float = 1f
)

/**
 * Stores the transcript for a single segment of a podcast day.
 */
@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey
    val id: String, // "2026-03-05_seg0", "2026-03-05_seg1", etc.

    val date: String, // "2026-03-05"
    val segmentIndex: Int,
    val wordsJson: String, // JSON array of TimedWord
    val fullText: String, // Plain text version for display fallback
    val state: String, // "none", "transcribing", "done", "error"
    val lastUpdated: Long
)

enum class TranscriptState(val value: String) {
    NONE("none"),
    TRANSCRIBING("transcribing"),
    DONE("done"),
    ERROR("error");

    companion object {
        fun fromValue(value: String): TranscriptState =
            entries.find { it.value == value } ?: NONE
    }
}
