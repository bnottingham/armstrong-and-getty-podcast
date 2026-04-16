package com.nomnomsom.armstrongandgetty.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nomnomsom.armstrongandgetty.data.local.PodcastDayDao
import com.nomnomsom.armstrongandgetty.data.model.DownloadState
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.RssItem
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.remote.AudioDownloader
import com.nomnomsom.armstrongandgetty.data.remote.DownloadProgressCallback
import com.nomnomsom.armstrongandgetty.data.remote.RssFeedParser
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val dao: PodcastDayDao,
    private val rssFeedParser: RssFeedParser,
    private val audioDownloader: AudioDownloader,
    private val gson: Gson
) {
    fun observeAllDays(): Flow<List<PodcastDay>> = dao.getAllDays()

    suspend fun getAllDaysSnapshot(): List<PodcastDay> = dao.getAllDaysSnapshot()

    fun observeDay(date: String): Flow<PodcastDay?> = dao.observeDay(date)

    suspend fun getDayByDate(date: String): PodcastDay? = dao.getDayByDate(date)

    suspend fun refreshFeed(): Result<String> {
        val result = rssFeedParser.fetchFeed()
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)

        val items = result.getOrThrow()
        val groupedByDate = groupItemsByDate(items)

        var latestDate = ""

        for ((date, dayItems) in groupedByDate) {
            if (latestDate.isEmpty() || date > latestDate) latestDate = date

            val existingDay = dao.getDayByDate(date)
            val segments = dayItems.mapIndexed { index, item ->
                val hourLabel = extractHourLabel(item.title, index + 1)
                Segment(
                    hour = hourLabel,
                    title = item.title,
                    description = item.description,
                    durationMs = item.durationSeconds * 1000,
                    audioUrl = item.audioUrl,
                    pubDate = item.pubDate
                )
            }.sortedBy { seg ->
                parsePubDate(seg.pubDate)
            }.mapIndexed { index, seg ->
                val hourLabel = if (seg.hour == "OMT") "OMT"
                else extractHourLabel(seg.title, index + 1)
                seg.copy(hour = hourLabel)
            }

            val segmentsJson = gson.toJson(segments)
            val totalDuration = segments.sumOf { it.durationMs }
            val summary = buildSummary(segments)
            val isComplete = isDayComplete(date, segments)

            if (existingDay == null) {
                val day = PodcastDay(
                    date = date,
                    title = formatDayTitle(date),
                    summary = summary,
                    segmentsJson = segmentsJson,
                    totalDurationMs = totalDuration,
                    segmentCount = segments.size,
                    downloadState = DownloadState.NONE.value,
                    combinedFilePath = null,
                    isComplete = isComplete,
                    listenedPositionMs = 0L,
                    isListened = false,
                    lastUpdated = System.currentTimeMillis()
                )
                dao.insertOrReplace(day)
            } else {
                val existingSegments = parseSegments(existingDay.segmentsJson)
                if (segments.size > existingSegments.size || !isComplete) {
                    dao.updateSegments(
                        date = date,
                        segmentsJson = segmentsJson,
                        totalDurationMs = totalDuration,
                        segmentCount = segments.size,
                        summary = summary,
                        isComplete = isComplete,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
        }

        return Result.success(latestDate)
    }

    suspend fun downloadDay(
        date: String,
        onProgress: DownloadProgressCallback? = null
    ): Result<String> {
        val day = dao.getDayByDate(date) ?: return Result.failure(Exception("Day not found"))
        val segments = parseSegments(day.segmentsJson)
        if (segments.isEmpty()) return Result.failure(Exception("No segments"))

        val existingOnDisk = audioDownloader.countExistingSegments(date, segments.size)

        if (existingOnDisk == segments.size) {
            dao.updateDownloadState(date, DownloadState.DOWNLOADED.value)
            return Result.success(date)
        }

        dao.updateDownloadState(date, DownloadState.DOWNLOADING.value)

        val downloadResult = audioDownloader.downloadSegments(
            date = date,
            segments = segments,
            existingSegmentCount = existingOnDisk,
            onProgress = onProgress
        )

        return if (downloadResult.isSuccess) {
            val result = downloadResult.getOrThrow()

            val updatedSegments = segments.mapIndexed { index, seg ->
                val actualDur = result.segmentActualDurationsMs.getOrElse(index) { 0L }
                if (actualDur > 0) seg.copy(actualDurationMs = actualDur) else seg
            }
            val updatedJson = gson.toJson(updatedSegments)
            val actualTotal = updatedSegments.sumOf { it.actualDurationMs.takeIf { d -> d > 0 } ?: it.durationMs }

            dao.updateDownloadComplete(date, DownloadState.DOWNLOADED.value, date)
            dao.updateSegments(
                date = date,
                segmentsJson = updatedJson,
                totalDurationMs = actualTotal,
                segmentCount = updatedSegments.size,
                summary = day.summary,
                isComplete = day.isComplete,
                lastUpdated = System.currentTimeMillis()
            )
            Result.success(date)
        } else {
            dao.updateDownloadState(date, DownloadState.ERROR.value)
            Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
        }
    }

    suspend fun appendNewSegments(date: String): Result<String> {
        val day = dao.getDayByDate(date) ?: return Result.failure(Exception("Day not found"))
        if (day.downloadState != DownloadState.DOWNLOADED.value) {
            return Result.failure(Exception("Day not downloaded"))
        }

        val previousSegmentCount = parseSegments(day.segmentsJson).size

        val refreshResult = refreshFeed()
        if (refreshResult.isFailure) return Result.failure(refreshResult.exceptionOrNull()!!)

        val updatedDay = dao.getDayByDate(date) ?: return Result.failure(Exception("Day not found after refresh"))
        val updatedSegments = parseSegments(updatedDay.segmentsJson)

        if (updatedSegments.size <= previousSegmentCount) {
            return Result.success(date)
        }

        val existingOnDisk = audioDownloader.countExistingSegments(date, updatedSegments.size)

        val result = audioDownloader.downloadSegments(
            date = date,
            segments = updatedSegments,
            existingSegmentCount = existingOnDisk
        )

        return if (result.isSuccess) {
            val dlResult = result.getOrThrow()

            val finalSegments = updatedSegments.mapIndexed { index, seg ->
                val actualDur = dlResult.segmentActualDurationsMs.getOrElse(index) { 0L }
                if (actualDur > 0) seg.copy(actualDurationMs = actualDur) else seg
            }
            val finalJson = gson.toJson(finalSegments)
            val actualTotal = finalSegments.sumOf { it.actualDurationMs.takeIf { d -> d > 0 } ?: it.durationMs }

            dao.updateDownloadComplete(date, DownloadState.DOWNLOADED.value, date)
            dao.updateSegments(
                date = date,
                segmentsJson = finalJson,
                totalDurationMs = actualTotal,
                segmentCount = finalSegments.size,
                summary = updatedDay.summary,
                isComplete = updatedDay.isComplete,
                lastUpdated = System.currentTimeMillis()
            )
            Result.success(date)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Download failed"))
        }
    }

    fun getSegmentFilePaths(date: String, segmentCount: Int): List<String> {
        return audioDownloader.getSegmentFiles(date, segmentCount)
    }

    fun hasAllSegmentsOnDisk(date: String, segmentCount: Int): Boolean {
        return audioDownloader.hasAllSegments(date, segmentCount)
    }

    suspend fun updateListenProgress(date: String, positionMs: Long, isListened: Boolean) {
        dao.updateListenProgress(date, positionMs, isListened)
    }

    suspend fun resetProgress(date: String) {
        dao.resetProgress(date)
    }

    suspend fun deleteDay(date: String) {
        audioDownloader.deleteSegmentFiles(date)
        dao.deleteDay(date)
    }

    fun parseSegments(json: String): List<Segment> {
        return try {
            val type = object : TypeToken<List<Segment>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun groupItemsByDate(items: List<RssItem>): Map<String, List<RssItem>> {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return items.mapNotNull { item ->
            try {
                val cleanDate = item.pubDate
                    .replace(" GMT", "")
                    .replace(" +0000", "")
                    .replace(" -0000", "")
                val parsed = dateFormat.parse(cleanDate)
                if (parsed != null) {
                    dayFormat.format(parsed) to item
                } else null
            } catch (_: Exception) {
                null
            }
        }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareByDescending { it })
    }

    private fun parsePubDate(pubDate: String): Long {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        return try {
            val cleanDate = pubDate
                .replace(" GMT", "")
                .replace(" +0000", "")
                .replace(" -0000", "")
            dateFormat.parse(cleanDate)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractHourLabel(title: String, fallbackIndex: Int): String {
        val hourPattern = Regex("Hour\\s+(\\d+)", RegexOption.IGNORE_CASE)
        val match = hourPattern.find(title)
        if (match != null) return match.groupValues[1]

        if (title.contains("One More Thing", ignoreCase = true) ||
            title.contains("OMT", ignoreCase = true)
        ) {
            return "OMT"
        }

        return fallbackIndex.toString()
    }

    private fun isDayComplete(date: String, segments: List<Segment>): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        if (date != today) return true

        val hourSegments = segments.count { it.hour.toIntOrNull() != null }
        return hourSegments >= 4
    }

    private fun formatDayTitle(date: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
            val formatted = SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed!!)
            "A&G — $formatted"
        } catch (_: Exception) {
            "A&G — $date"
        }
    }

    private fun buildSummary(segments: List<Segment>): String {
        val combined = segments
            .map { it.description }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (combined.length > 400) combined.take(400) + "…" else combined
    }
}
