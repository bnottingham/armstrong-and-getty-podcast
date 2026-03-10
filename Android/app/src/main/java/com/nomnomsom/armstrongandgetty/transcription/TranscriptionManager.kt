package com.nomnomsom.armstrongandgetty.transcription

import android.util.Log
import com.google.gson.Gson
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import com.nomnomsom.armstrongandgetty.data.local.TranscriptDao
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.TimedWord
import com.nomnomsom.armstrongandgetty.data.model.TranscriptEntity
import com.nomnomsom.armstrongandgetty.data.remote.TranscriptSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages transcription state by fetching SRT files from Firebase Firestore.
 *
 * No local transcription — the Python server does the heavy lifting and uploads
 * SRT files to Firestore. This manager:
 * 1. Automatically fetches transcriptions for downloaded days that don't have them
 * 2. Periodically re-checks every 30 minutes for missing segment transcriptions
 * 3. Provides a manual retry for individual segments
 *
 * No auth required — transcriptions are a shared public resource.
 */
@Singleton
class TranscriptionManager @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val podcastDayDao: PodcastDayDao,
    private val transcriptSyncRepository: TranscriptSyncRepository,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "TranscriptionMgr"
        private const val POLL_INTERVAL_MS = 30L * 60 * 1000 // 30 minutes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    /**
     * Start background polling for missing transcriptions.
     * Checks all downloaded days that are missing transcripts every 30 minutes.
     */
    fun startBackgroundPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            while (true) {
                try {
                    fetchMissingTranscriptsForAllDays()
                } catch (e: Exception) {
                    Log.e(TAG, "Background polling error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        Log.d(TAG, "Background transcript polling started (every 30 min)")
    }

    fun stopBackgroundPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun observeTranscripts(date: String): Flow<List<TranscriptEntity>> =
        transcriptDao.observeTranscriptsForDay(date)

    suspend fun getTranscripts(date: String): List<TranscriptEntity> =
        transcriptDao.getTranscriptsForDay(date)

    /**
     * Fetch all available transcriptions for a specific day.
     * Called when the user enters the player screen.
     */
    suspend fun fetchTranscriptsForDay(date: String, segmentCount: Int): Int =
        withContext(Dispatchers.IO) {
            transcriptSyncRepository.fetchAllSegmentsForDay(date, segmentCount)
        }

    /**
     * Retry fetching a single segment's transcription.
     * Called when the user taps the "Retry" button.
     */
    suspend fun fetchSegmentTranscript(date: String, segmentIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            transcriptSyncRepository.fetchSegmentTranscript(date, segmentIndex)
        }

    /**
     * Fetch missing transcripts for ALL downloaded days.
     * This is called periodically by the background polling job.
     */
    private suspend fun fetchMissingTranscriptsForAllDays() {
        try {
            val allDays = podcastDayDao.getAllDownloadedDays()

            for (day in allDays) {
                val localCount = transcriptSyncRepository.getLocalTranscriptCount(day.date)
                if (localCount < day.segmentCount) {
                    Log.d(TAG, "Fetching missing transcripts for ${day.date} " +
                            "(have $localCount/${day.segmentCount})")
                    transcriptSyncRepository.fetchAllSegmentsForDay(day.date, day.segmentCount)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching missing transcripts", e)
        }
    }

    /**
     * Parse stored words JSON back into TimedWord list.
     */
    fun parseWords(wordsJson: String): List<TimedWord> {
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<TimedWord>>() {}.type
            gson.fromJson(wordsJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Check if transcripts are available for a day.
     */
    suspend fun hasTranscripts(date: String): Boolean {
        return transcriptDao.getCompletedCountForDay(date) > 0
    }

    /**
     * Reset stuck transcripts (shouldn't happen with remote fetching,
     * but kept for safety).
     */
    suspend fun resetStuckTranscripts(date: String) {
        transcriptDao.resetStuckTranscripts(date)
    }
}
