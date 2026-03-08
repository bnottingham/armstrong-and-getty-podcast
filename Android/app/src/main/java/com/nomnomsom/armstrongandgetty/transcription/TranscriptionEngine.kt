package com.nomnomsom.armstrongandgetty.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.google.gson.Gson
import com.nomnomsom.armstrongandgetty.data.model.TimedWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionEngine @Inject constructor(
    private val modelManager: VoskModelManager,
    private val gson: Gson
) {
    private var model: Model? = null

    /**
     * Ensure the Vosk model is loaded into memory.
     */
    suspend fun ensureModelLoaded(): Result<Unit> = withContext(Dispatchers.IO) {
        if (model != null) return@withContext Result.success(Unit)

        if (!modelManager.isModelReady()) {
            return@withContext Result.failure(Exception("Model not downloaded"))
        }

        try {
            model = Model(modelManager.getModelPath())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Transcribe an audio file and return word-level timestamps.
     * The audio file is decoded to 16kHz mono PCM, then fed to Vosk.
     */
    suspend fun transcribe(audioFilePath: String): Result<List<TimedWord>> = withContext(Dispatchers.IO) {
        try {
            val loadResult = ensureModelLoaded()
            if (loadResult.isFailure) return@withContext Result.failure(loadResult.exceptionOrNull()!!)

            val voskModel = model ?: return@withContext Result.failure(Exception("Model not loaded"))
            val sampleRate = 16000f

            val recognizer = Recognizer(voskModel, sampleRate).apply {
                setWords(true) // Enable word-level timestamps
                setPartialWords(false)
            }

            val words = mutableListOf<TimedWord>()

            // Decode MP3 to raw PCM using MediaCodec
            decodeToPcm(audioFilePath, sampleRate.toInt()) { pcmChunk ->
                if (recognizer.acceptWaveForm(pcmChunk, pcmChunk.size)) {
                    val result = recognizer.result
                    words.addAll(parseVoskResult(result))
                }
            }

            // Get final result
            val finalResult = recognizer.finalResult
            words.addAll(parseVoskResult(finalResult))

            recognizer.close()

            Result.success(words)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decode an audio file to 16kHz mono 16-bit PCM and pass chunks to the consumer.
     */
    private fun decodeToPcm(
        filePath: String,
        targetSampleRate: Int,
        consumer: (ByteArray) -> Unit
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)

        // Find audio track
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }

        if (audioTrackIndex == -1) {
            extractor.release()
            throw Exception("No audio track found in file")
        }

        extractor.selectTrack(audioTrackIndex)
        val inputFormat = extractor.getTrackFormat(audioTrackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
        val inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        val chunkSize = 4000 // ~250ms at 16kHz mono 16-bit

        while (true) {
            // Feed input
            if (!inputDone) {
                val inputBufferId = codec.dequeueInputBuffer(10_000)
                if (inputBufferId >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferId, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }
            }

            // Read output
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputBufferId >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferId)!!
                val pcmData = ByteArray(bufferInfo.size)
                outputBuffer.get(pcmData)
                codec.releaseOutputBuffer(outputBufferId, false)

                // Resample & convert to mono if needed
                val resampled = resampleToMono16k(
                    pcmData, inputSampleRate, inputChannels, targetSampleRate
                )

                // Feed to Vosk in chunks
                var offset = 0
                while (offset < resampled.size) {
                    val end = minOf(offset + chunkSize, resampled.size)
                    val chunk = resampled.copyOfRange(offset, end)
                    consumer(chunk)
                    offset = end
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (inputDone) break
            }
        }

        codec.stop()
        codec.release()
        extractor.release()
    }

    /**
     * Simple resampling from input rate/channels to 16kHz mono 16-bit PCM.
     */
    private fun resampleToMono16k(
        pcmData: ByteArray,
        inputRate: Int,
        inputChannels: Int,
        targetRate: Int
    ): ByteArray {
        // PCM data is 16-bit little-endian
        val shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val sampleCount = shortBuffer.remaining()
        val samples = ShortArray(sampleCount)
        shortBuffer.get(samples)

        // Convert to mono by averaging channels
        val monoSamples = if (inputChannels > 1) {
            val monoCount = sampleCount / inputChannels
            ShortArray(monoCount) { i ->
                var sum = 0L
                for (ch in 0 until inputChannels) {
                    sum += samples[i * inputChannels + ch]
                }
                (sum / inputChannels).toInt().toShort()
            }
        } else {
            samples
        }

        // Resample if rates differ
        val resampled = if (inputRate != targetRate) {
            val ratio = inputRate.toDouble() / targetRate
            val outputCount = (monoSamples.size / ratio).toInt()
            ShortArray(outputCount) { i ->
                val srcIdx = (i * ratio).toInt().coerceIn(0, monoSamples.size - 1)
                monoSamples[srcIdx]
            }
        } else {
            monoSamples
        }

        // Convert back to bytes
        val output = ByteArray(resampled.size * 2)
        val outBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        for (s in resampled) {
            outBuffer.putShort(s)
        }
        return output
    }

    /**
     * Parse Vosk JSON result to extract timed words.
     */
    private fun parseVoskResult(json: String): List<TimedWord> {
        return try {
            val parsed = gson.fromJson(json, VoskResult::class.java)
            parsed?.result?.map { w ->
                TimedWord(
                    word = w.word,
                    startMs = (w.start * 1000).toLong(),
                    endMs = (w.end * 1000).toLong(),
                    confidence = w.conf
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Vosk JSON response structure
    private data class VoskResult(val result: List<VoskWord>? = null, val text: String? = null)
    private data class VoskWord(val word: String, val start: Float, val end: Float, val conf: Float)
}
