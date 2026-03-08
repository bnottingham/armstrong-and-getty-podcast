package com.nomnomsom.armstrongandgetty.transcription

import com.google.gson.Gson
import com.nomnomsom.armstrongandgetty.data.local.TranscriptDao
import com.nomnomsom.armstrongandgetty.data.model.TranscriptEntity
import com.nomnomsom.armstrongandgetty.data.model.TranscriptState
import com.nomnomsom.armstrongandgetty.data.model.TimedWord
import com.nomnomsom.armstrongandgetty.data.remote.AudioDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionManager @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val transcriptionEngine: TranscriptionEngine,
    private val modelManager: VoskModelManager,
    private val audioDownloader: AudioDownloader,
    private val gson: Gson
) {
    fun observeTranscripts(date: String): Flow<List<TranscriptEntity>> =
        transcriptDao.observeTranscriptsForDay(date)

    suspend fun resetStuckTranscripts(date: String) {
        transcriptDao.resetStuckTranscripts(date)
    }

    suspend fun getTranscripts(date: String): List<TranscriptEntity> =
        transcriptDao.getTranscriptsForDay(date)

    /**
     * Transcribe all segments for a day. Skips already-completed segments.
     * Downloads the Vosk model if not present.
     */
    suspend fun transcribeDay(date: String, segmentCount: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Ensure model is downloaded
            if (!modelManager.isModelReady()) {
                val dlResult = modelManager.downloadModel()
                if (dlResult.isFailure) return@withContext Result.failure(dlResult.exceptionOrNull()!!)
            }

            // Ensure model is loaded
            val loadResult = transcriptionEngine.ensureModelLoaded()
            if (loadResult.isFailure) return@withContext Result.failure(loadResult.exceptionOrNull()!!)

            val segmentFiles = audioDownloader.getSegmentFiles(date, segmentCount)

            for (index in 0 until segmentCount) {
                val transcriptId = "${date}_seg${index}"

                // Skip if already done
                val existing = transcriptDao.getTranscript(transcriptId)
                if (existing != null && existing.state == TranscriptState.DONE.value) continue

                // Create or update entry as "transcribing"
                transcriptDao.insertOrReplace(
                    TranscriptEntity(
                        id = transcriptId,
                        date = date,
                        segmentIndex = index,
                        wordsJson = "[]",
                        fullText = "",
                        state = TranscriptState.TRANSCRIBING.value,
                        lastUpdated = System.currentTimeMillis()
                    )
                )

                val filePath = segmentFiles.getOrNull(index)
                if (filePath == null || !java.io.File(filePath).exists()) {
                    transcriptDao.updateState(transcriptId, TranscriptState.ERROR.value)
                    continue
                }

                // Run transcription
                val result = transcriptionEngine.transcribe(filePath)

                if (result.isSuccess) {
                    val words = result.getOrThrow()
                    val wordsJson = gson.toJson(words)
                    val fullText = words.joinToString(" ") { it.word }

                    transcriptDao.updateComplete(
                        id = transcriptId,
                        state = TranscriptState.DONE.value,
                        wordsJson = wordsJson,
                        fullText = fullText,
                        lastUpdated = System.currentTimeMillis()
                    )
                } else {
                    transcriptDao.updateState(transcriptId, TranscriptState.ERROR.value)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Transcribe a single segment on demand.
     * Downloads the Vosk model if not present.
     */
    suspend fun transcribeSegment(date: String, segmentIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Ensure model is downloaded
            if (!modelManager.isModelReady()) {
                val dlResult = modelManager.downloadModel()
                if (dlResult.isFailure) return@withContext Result.failure(dlResult.exceptionOrNull()!!)
            }

            // Ensure model is loaded
            val loadResult = transcriptionEngine.ensureModelLoaded()
            if (loadResult.isFailure) return@withContext Result.failure(loadResult.exceptionOrNull()!!)

            val transcriptId = "${date}_seg${segmentIndex}"

            // Skip if already done
            val existing = transcriptDao.getTranscript(transcriptId)
            if (existing != null && existing.state == TranscriptState.DONE.value) {
                return@withContext Result.success(Unit)
            }

            // Create or update entry as "transcribing"
            transcriptDao.insertOrReplace(
                TranscriptEntity(
                    id = transcriptId,
                    date = date,
                    segmentIndex = segmentIndex,
                    wordsJson = "[]",
                    fullText = "",
                    state = TranscriptState.TRANSCRIBING.value,
                    lastUpdated = System.currentTimeMillis()
                )
            )

            // Find the file — we need to figure out total segment count from what's on disk
            val segmentFiles = audioDownloader.getSegmentFiles(date, segmentIndex + 1)
            val filePath = segmentFiles.getOrNull(segmentIndex)
            if (filePath == null || !java.io.File(filePath).exists()) {
                transcriptDao.updateState(transcriptId, TranscriptState.ERROR.value)
                return@withContext Result.failure(Exception("Segment file not found"))
            }

            // Run transcription
            val result = transcriptionEngine.transcribe(filePath)

            if (result.isSuccess) {
                val words = result.getOrThrow()
                val wordsJson = gson.toJson(words)
                val fullText = words.joinToString(" ") { it.word }

                transcriptDao.updateComplete(
                    id = transcriptId,
                    state = TranscriptState.DONE.value,
                    wordsJson = wordsJson,
                    fullText = fullText,
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                transcriptDao.updateState(transcriptId, TranscriptState.ERROR.value)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun isTranscribing(date: String): Boolean {
        return transcriptDao.getTranscribingCountForDay(date) > 0
    }
}
