package com.nomnomsom.armstrongandgetty.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.nomnomsom.armstrongandgetty.data.local.TranscriptDao
import com.nomnomsom.armstrongandgetty.data.model.TimedWord
import com.nomnomsom.armstrongandgetty.data.model.TranscriptEntity
import com.nomnomsom.armstrongandgetty.data.model.TranscriptState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches segment transcriptions (SRT files) from Firestore.
 * No auth required — transcriptions are a shared public resource.
 *
 * Firestore structure (written by the Python transcriber):
 *   transcripts/{date}/segments/{segIndex}
 *     - srtContent: String (full SRT content)
 *     - segmentIndex: Int
 *     - title: String
 *     - uploadedAt: Timestamp
 */
@Singleton
class TranscriptSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val transcriptDao: TranscriptDao,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "TranscriptSync"
        private const val COLLECTION_TRANSCRIPTS = "transcripts"
        private const val COLLECTION_SEGMENTS = "segments"
    }

    /**
     * Try to fetch a single segment's transcription from Firestore.
     * Returns true if the transcription was found and saved locally.
     */
    suspend fun fetchSegmentTranscript(
        date: String,
        segmentIndex: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val transcriptId = "${date}_seg${segmentIndex}"

        // Skip if already done locally
        val existing = transcriptDao.getTranscript(transcriptId)
        if (existing != null && existing.state == TranscriptState.DONE.value) {
            return@withContext true
        }

        try {
            val doc = firestore
                .collection(COLLECTION_TRANSCRIPTS)
                .document(date)
                .collection(COLLECTION_SEGMENTS)
                .document(segmentIndex.toString())
                .get()
                .await()

            if (!doc.exists()) {
                Log.d(TAG, "No transcription available for $date seg$segmentIndex")
                return@withContext false
            }

            val srtContent = doc.getString("srtContent")
            if (srtContent.isNullOrBlank()) {
                Log.w(TAG, "Empty SRT content for $date seg$segmentIndex")
                return@withContext false
            }

            // Parse SRT into TimedWords
            val words = parseSrtToTimedWords(srtContent)
            val wordsJson = gson.toJson(words)
            val fullText = words.joinToString(" ") { it.word }

            transcriptDao.insertOrReplace(
                TranscriptEntity(
                    id = transcriptId,
                    date = date,
                    segmentIndex = segmentIndex,
                    wordsJson = wordsJson,
                    fullText = fullText,
                    state = TranscriptState.DONE.value,
                    lastUpdated = System.currentTimeMillis()
                )
            )

            Log.d(TAG, "Fetched transcript for $date seg$segmentIndex (${words.size} words)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch transcript for $date seg$segmentIndex", e)
            false
        }
    }

    /**
     * Fetch all available segment transcriptions for a given date.
     * Returns the number of segments successfully fetched.
     */
    suspend fun fetchAllSegmentsForDay(
        date: String,
        segmentCount: Int
    ): Int = withContext(Dispatchers.IO) {
        var fetchedCount = 0

        try {
            // Batch-fetch all segments for the day in one query
            val snapshot = firestore
                .collection(COLLECTION_TRANSCRIPTS)
                .document(date)
                .collection(COLLECTION_SEGMENTS)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "No transcriptions available for $date")
                return@withContext 0
            }

            for (doc in snapshot.documents) {
                val segmentIndex = doc.id.toIntOrNull() ?: continue
                if (segmentIndex >= segmentCount) continue

                val transcriptId = "${date}_seg${segmentIndex}"

                // Skip if already done locally
                val existing = transcriptDao.getTranscript(transcriptId)
                if (existing != null && existing.state == TranscriptState.DONE.value) {
                    fetchedCount++
                    continue
                }

                val srtContent = doc.getString("srtContent")
                if (srtContent.isNullOrBlank()) continue

                val words = parseSrtToTimedWords(srtContent)
                val wordsJson = gson.toJson(words)
                val fullText = words.joinToString(" ") { it.word }

                transcriptDao.insertOrReplace(
                    TranscriptEntity(
                        id = transcriptId,
                        date = date,
                        segmentIndex = segmentIndex,
                        wordsJson = wordsJson,
                        fullText = fullText,
                        state = TranscriptState.DONE.value,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                fetchedCount++
            }

            Log.d(TAG, "Fetched $fetchedCount/${segmentCount} transcripts for $date")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch-fetch transcripts for $date", e)
        }

        fetchedCount
    }

    /**
     * Check how many segments for a date already have local transcriptions.
     */
    suspend fun getLocalTranscriptCount(date: String): Int {
        return transcriptDao.getCompletedCountForDay(date)
    }

    /**
     * Parse SRT format into a list of TimedWords.
     *
     * SRT format:
     * 1
     * 00:00:00,000 --> 00:00:05,230
     * Hello world this is a test
     *
     * Each SRT entry becomes individual words with interpolated timestamps
     * spanning the entry's time range.
     */
    private fun parseSrtToTimedWords(srtContent: String): List<TimedWord> {
        val words = mutableListOf<TimedWord>()
        val entries = parseSrtEntries(srtContent)

        for (entry in entries) {
            val entryWords = entry.text.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (entryWords.isEmpty()) continue

            val durationMs = entry.endMs - entry.startMs
            val wordDurationMs = if (entryWords.size > 1) {
                durationMs / entryWords.size
            } else {
                durationMs
            }

            for ((i, word) in entryWords.withIndex()) {
                val wordStartMs = entry.startMs + (i * wordDurationMs)
                val wordEndMs = if (i == entryWords.size - 1) {
                    entry.endMs
                } else {
                    wordStartMs + wordDurationMs
                }

                words.add(
                    TimedWord(
                        word = word,
                        startMs = wordStartMs,
                        endMs = wordEndMs,
                        confidence = 1f
                    )
                )
            }
        }

        return words
    }

    private data class SrtEntry(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    private fun parseSrtEntries(srtContent: String): List<SrtEntry> {
        val entries = mutableListOf<SrtEntry>()
        val blocks = srtContent.trim().split("\n\n")

        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 3) continue

            // Line 0 = index number (skip)
            // Line 1 = timestamps
            // Lines 2+ = text
            val timeLine = lines[1]
            val textLines = lines.drop(2).joinToString(" ")

            val timestamps = parseTimestampLine(timeLine) ?: continue
            entries.add(SrtEntry(timestamps.first, timestamps.second, textLines))
        }

        return entries
    }

    /**
     * Parse "00:01:23,456 --> 00:01:28,789" into (startMs, endMs)
     */
    private fun parseTimestampLine(line: String): Pair<Long, Long>? {
        val pattern = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
        val match = pattern.find(line) ?: return null

        val (h1, m1, s1, ms1, h2, m2, s2, ms2) = match.destructured

        val startMs = h1.toLong() * 3_600_000 + m1.toLong() * 60_000 + s1.toLong() * 1_000 + ms1.toLong()
        val endMs = h2.toLong() * 3_600_000 + m2.toLong() * 60_000 + s2.toLong() * 1_000 + ms2.toLong()

        return startMs to endMs
    }
}
