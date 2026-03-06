package com.nomnomsom.aandg.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR
}

data class ModelStatus(
    val state: ModelState = ModelState.NOT_DOWNLOADED,
    val progressPercent: Int = 0,
    val errorMessage: String? = null
)

@Singleton
class VoskModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
        private const val EXPECTED_SIZE_BYTES = 50_000_000L // ~50MB
    }

    // Dedicated client with long timeouts for large model download
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No read timeout for large download
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelsDir: File
        get() = File(context.filesDir, "vosk_models").also { it.mkdirs() }

    private val modelDir: File
        get() = File(modelsDir, MODEL_DIR_NAME)

    private val _status = MutableStateFlow(ModelStatus())
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    init {
        if (isModelReady()) {
            _status.value = ModelStatus(state = ModelState.READY)
        }
    }

    fun isModelReady(): Boolean {
        val confFile = File(modelDir, "conf/model.conf")
        val amDir = File(modelDir, "am")
        return modelDir.exists() && confFile.exists() && amDir.exists()
    }

    fun getModelPath(): String = modelDir.absolutePath

    suspend fun downloadModel(): Result<String> = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _status.value = ModelStatus(state = ModelState.READY)
            return@withContext Result.success(modelDir.absolutePath)
        }

        _status.value = ModelStatus(state = ModelState.DOWNLOADING, progressPercent = 0)

        try {
            val request = Request.Builder()
                .url(MODEL_URL)
                .header("User-Agent", "ArmstrongGettyPodcast/1.0")
                .header("Accept-Encoding", "identity") // Disable gzip so Content-Length is accurate
                .build()

            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val error = "Download failed: HTTP ${response.code}"
                _status.value = ModelStatus(state = ModelState.ERROR, errorMessage = error)
                return@withContext Result.failure(Exception(error))
            }

            val contentLength = response.body?.contentLength()?.takeIf { it > 0 } ?: EXPECTED_SIZE_BYTES
            val zipFile = File(modelsDir, "model_download.zip")

            // Download with throttled progress updates
            response.body?.byteStream()?.use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(65536) // 64KB buffer for faster I/O
                    var bytesRead: Long = 0
                    var lastReportedPercent = -1
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        bytesRead += len
                        val progress = ((bytesRead * 100) / contentLength).toInt().coerceIn(0, 99)
                        // Only update UI when percentage actually changes
                        if (progress != lastReportedPercent) {
                            lastReportedPercent = progress
                            _status.value = ModelStatus(
                                state = ModelState.DOWNLOADING,
                                progressPercent = progress
                            )
                        }
                    }
                }
            }

            // Extract zip
            _status.value = ModelStatus(state = ModelState.DOWNLOADING, progressPercent = 99)
            extractZip(zipFile, modelsDir)

            // Clean up zip
            zipFile.delete()

            if (isModelReady()) {
                _status.value = ModelStatus(state = ModelState.READY)
                Result.success(modelDir.absolutePath)
            } else {
                val error = "Model extraction failed — required files not found"
                _status.value = ModelStatus(state = ModelState.ERROR, errorMessage = error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            _status.value = ModelStatus(state = ModelState.ERROR, errorMessage = e.message)
            Result.failure(e)
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos, bufferSize = 65536)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
