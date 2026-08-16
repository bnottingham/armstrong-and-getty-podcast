package com.nomnomsom.armstrongandgetty.media

import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import com.nomnomsom.armstrongandgetty.data.model.Segment
import com.nomnomsom.armstrongandgetty.data.model.displayLabel
import com.nomnomsom.armstrongandgetty.data.model.effectiveDurationMs
import com.nomnomsom.armstrongandgetty.data.repository.PodcastRepository
import com.nomnomsom.armstrongandgetty.util.AppLog
import com.nomnomsom.armstrongandgetty.util.formatShortDuration
import com.nomnomsom.armstrongandgetty.util.parseDayKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number

/**
 * One playable segment as seen by an external media surface (Android Auto, Assistant,
 * CarPlay). [localPath] is set only when the downloaded file is actually on disk; a segment
 * with no local file streams from [remoteUrl].
 */
data class CatalogSegment(
    val date: String,
    val index: Int,
    val title: String,
    val remoteUrl: String,
    val localPath: String?,
    val durationMs: Long
) {
    val mediaId: String get() = segmentMediaId(date, index)
    val isDownloaded: Boolean get() = localPath != null
    val isPlayable: Boolean get() = localPath != null || remoteUrl.isNotBlank()
}

/** One day's show — the unit users pick in the car — plus its playable segments in order. */
data class CatalogEpisode(
    val date: String,
    val title: String,
    val summary: String,
    val totalDurationMs: Long,
    val listenedPositionMs: Long,
    val isListened: Boolean,
    val isComplete: Boolean,
    val segments: List<CatalogSegment>
) {
    val mediaId: String get() = episodeMediaId(date)
    val isFullyDownloaded: Boolean get() = segments.isNotEmpty() && segments.all { it.isDownloaded }
    val playableSegments: List<CatalogSegment> get() = segments.filter { it.isPlayable }

    /** "1h 18m · 2 segments · Downloaded" — subtitle for car/list surfaces. */
    val subtitle: String
        get() {
            val count = segments.size
            val parts = mutableListOf(
                totalDurationMs.formatShortDuration(),
                "$count segment${if (count == 1) "" else "s"}"
            )
            when {
                isFullyDownloaded -> parts += "Downloaded"
                segments.any { it.isDownloaded } -> parts += "Partly downloaded"
                else -> parts += "Streams"
            }
            return parts.joinToString(" · ")
        }

    /** Where to start when the user picks this episode: saved position, or 0 once finished. */
    val resumePositionMs: Long
        get() = if (isListened) 0L else listenedPositionMs.coerceAtLeast(0L)

    val completionPercent: Int
        get() = when {
            isListened -> 100
            totalDurationMs <= 0 -> 0
            else -> ((listenedPositionMs * 100) / totalDurationMs).toInt().coerceIn(0, 100)
        }
}

/** A concrete playlist request: which segments to queue and where to start. */
data class CatalogPlayback(
    val episode: CatalogEpisode,
    val segments: List<CatalogSegment>,
    val startSegmentIndex: Int,
    val startPositionInSegmentMs: Long
)

const val MEDIA_ID_ROOT = "root"
const val MEDIA_ID_TAB_EPISODES = "tab_episodes"
const val MEDIA_ID_TAB_LATEST = "tab_latest"

private const val EPISODE_PREFIX = "episode:"
private const val SEGMENT_PREFIX = "segment:"

fun episodeMediaId(date: String): String = "$EPISODE_PREFIX$date"
fun segmentMediaId(date: String, index: Int): String = "$SEGMENT_PREFIX$date:$index"

/** Parsed form of a catalog media id. */
sealed class CatalogMediaId {
    data class Episode(val date: String) : CatalogMediaId()
    data class Segment(val date: String, val index: Int) : CatalogMediaId()

    companion object {
        fun parse(mediaId: String?): CatalogMediaId? {
            if (mediaId.isNullOrBlank()) return null
            return when {
                mediaId.startsWith(EPISODE_PREFIX) ->
                    Episode(mediaId.removePrefix(EPISODE_PREFIX)).takeIf { it.date.isNotBlank() }
                mediaId.startsWith(SEGMENT_PREFIX) -> {
                    val body = mediaId.removePrefix(SEGMENT_PREFIX)
                    val sep = body.lastIndexOf(':')
                    if (sep <= 0) return null
                    val index = body.substring(sep + 1).toIntOrNull() ?: return null
                    Segment(body.substring(0, sep), index)
                }
                else -> null
            }
        }
    }
}

/**
 * Read-only view of the episode library for external media surfaces (Android Auto browse
 * tree, Assistant voice search, media-button playback resumption; CarPlay later).
 *
 * Everything here is derived from [PodcastRepository]; the only side effect is an
 * on-demand feed refresh when the library is empty, so a user who opens the app in the
 * car before ever launching it on the phone still gets content.
 */
class MediaCatalog(
    private val repository: PodcastRepository
) {
    companion object {
        private const val TAG = "MediaCatalog"

        /** Cap what we hand to car surfaces; Auto lists get unwieldy past this. */
        private const val MAX_EPISODES = 30
    }

    /** All known days, newest first, each with its segments and on-disk status. */
    suspend fun episodes(refreshIfEmpty: Boolean = true): List<CatalogEpisode> {
        var days = repository.getAllDaysSnapshot()
        if (days.isEmpty() && refreshIfEmpty) {
            AppLog.d(TAG, "Library empty — refreshing feed for external browser")
            val result = repository.refreshFeed()
            if (result.isFailure) {
                AppLog.w(TAG, "Feed refresh failed: ${result.exceptionOrNull()?.message}")
            }
            days = repository.getAllDaysSnapshot()
        }
        return days.asSequence()
            .map { toEpisode(it) }
            .filter { it.playableSegments.isNotEmpty() }
            .take(MAX_EPISODES)
            .toList()
    }

    suspend fun episode(date: String): CatalogEpisode? =
        repository.getDayByDate(date)?.let { toEpisode(it) }?.takeIf { it.playableSegments.isNotEmpty() }

    /** Newest episode that has anything playable. */
    suspend fun latest(): CatalogEpisode? = episodes().firstOrNull()

    /**
     * What "just play" should resume: the newest episode the user is partway through,
     * else the newest episode. Null only when the library is empty and can't be fetched.
     */
    suspend fun resumeCandidate(): CatalogEpisode? {
        val all = episodes()
        return all.firstOrNull { !it.isListened && it.listenedPositionMs > 0 } ?: all.firstOrNull()
    }

    /**
     * Resolve a media id from the browse tree to a concrete playlist. Episodes resume from
     * their saved position; segments start the same day's playlist at that segment.
     */
    suspend fun playbackFor(mediaId: String?): CatalogPlayback? {
        return when (val parsed = CatalogMediaId.parse(mediaId)) {
            is CatalogMediaId.Episode -> episode(parsed.date)?.let { playbackForEpisode(it) }
            is CatalogMediaId.Segment -> {
                val ep = episode(parsed.date) ?: return null
                val playable = ep.playableSegments
                val start = playable.indexOfFirst { it.index == parsed.index }
                if (start < 0) return null
                CatalogPlayback(ep, playable, start, 0L)
            }
            null -> null
        }
    }

    /** Playlist for an episode starting at its saved position (mapped into segment/offset). */
    fun playbackForEpisode(episode: CatalogEpisode, positionMs: Long = episode.resumePositionMs): CatalogPlayback? {
        val playable = episode.playableSegments
        if (playable.isEmpty()) return null
        var remaining = positionMs.coerceAtLeast(0L)
        var index = 0
        while (index < playable.size - 1 && playable[index].durationMs in 1..remaining) {
            remaining -= playable[index].durationMs
            index++
        }
        // A position that runs past every known duration lands on the last segment at 0 rather
        // than an offset the file can't satisfy.
        if (index == playable.size - 1 && playable[index].durationMs in 1..remaining) remaining = 0L
        return CatalogPlayback(episode, playable, index, remaining)
    }

    /**
     * Voice / text search ("play Armstrong and Getty", "play the August 15th show", "play
     * Friday's episode"). Empty query means "just play" → resume. Anything we can't map to a
     * specific day falls back to the newest episode, so a voice request never dead-ends.
     */
    suspend fun search(query: String?): CatalogEpisode? {
        val all = episodes()
        if (all.isEmpty()) return null
        val q = query.orEmpty().trim().lowercase()
        if (q.isEmpty()) return resumeCandidate()

        matchDateWords(q, all)?.let { return it }

        if (Regex("\\b(latest|newest|today|todays|today's|new|recent|current)\\b").containsMatchIn(q)) {
            return all.first()
        }
        if (Regex("\\b(yesterday|yesterdays|yesterday's|last)\\b").containsMatchIn(q)) {
            return all.getOrNull(1) ?: all.first()
        }
        // Weekday name → the most recent episode on that weekday.
        weekdayIn(q)?.let { wanted ->
            all.firstOrNull { parseDayKey(it.date)?.dayOfWeek == wanted }?.let { return it }
        }
        // Words that appear in an episode's segment titles (topic search).
        val terms = q.split(Regex("[^a-z0-9']+")).filter { it.length >= 4 && it !in STOP_WORDS }
        if (terms.isNotEmpty()) {
            all.firstOrNull { ep ->
                val hay = (ep.title + " " + ep.segments.joinToString(" ") { it.title }).lowercase()
                terms.all { hay.contains(it) }
            }?.let { return it }
        }
        return resumeCandidate()
    }

    /** Episodes matching [query] for a browse-search results list (empty query → everything). */
    suspend fun searchResults(query: String?): List<CatalogEpisode> {
        val all = episodes()
        val q = query.orEmpty().trim().lowercase()
        if (q.isEmpty()) return all
        val terms = q.split(Regex("[^a-z0-9']+")).filter { it.isNotBlank() && it !in STOP_WORDS }
        val byDate = matchDateWords(q, all)
        val byTerms = all.filter { ep ->
            val hay = (ep.title + " " + ep.date + " " + ep.segments.joinToString(" ") { it.title }).lowercase()
            terms.any { hay.contains(it) }
        }
        val combined = listOfNotNull(byDate) + byTerms.filter { it.date != byDate?.date }
        return combined.ifEmpty { all }
    }

    // ---- internals ------------------------------------------------------------------

    private fun toEpisode(day: PodcastDay): CatalogEpisode {
        val segments = repository.parseSegments(day.segmentsJson)
        val paths = repository.getSegmentFilePaths(day.date, segments.size)
        val missing = repository.missingSegmentIndices(day.date, segments.size)
        val catalogSegments = segments.mapIndexed { index, seg ->
            CatalogSegment(
                date = day.date,
                index = index,
                title = seg.displayLabel,
                remoteUrl = seg.audioUrl,
                localPath = paths.getOrNull(index)?.takeIf { index !in missing },
                durationMs = seg.effectiveDurationMs
            )
        }
        return CatalogEpisode(
            date = day.date,
            title = day.title,
            summary = day.summary,
            totalDurationMs = segments.sumOf(Segment::effectiveDurationMs),
            listenedPositionMs = day.listenedPositionMs,
            isListened = day.isListened,
            isComplete = day.isComplete,
            segments = catalogSegments
        )
    }

    private fun matchDateWords(q: String, all: List<CatalogEpisode>): CatalogEpisode? {
        // ISO date anywhere in the query.
        Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b").find(q)?.groupValues?.get(1)?.let { iso ->
            all.firstOrNull { it.date == iso }?.let { return it }
        }
        // "8/15" or "8/15/2026"
        Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b").find(q)?.let { m ->
            val month = m.groupValues[1].toIntOrNull()
            val day = m.groupValues[2].toIntOrNull()
            if (month != null && day != null) {
                matchMonthDay(month, day, all)?.let { return it }
            }
        }
        // "august 15", "aug 15th", "15 august"
        val monthIdx = MONTH_NAMES.indexOfFirst { names -> names.any { Regex("\\b$it\\b").containsMatchIn(q) } }
        if (monthIdx >= 0) {
            val dayNum = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\b").find(q)?.groupValues?.get(1)?.toIntOrNull()
            if (dayNum != null) matchMonthDay(monthIdx + 1, dayNum, all)?.let { return it }
        }
        return null
    }

    private fun matchMonthDay(month: Int, day: Int, all: List<CatalogEpisode>): CatalogEpisode? {
        if (month !in 1..12 || day !in 1..31) return null
        return all.firstOrNull { ep ->
            val d: LocalDate = parseDayKey(ep.date) ?: return@firstOrNull false
            d.month.number == month && d.day == day
        }
    }

    private fun weekdayIn(q: String): kotlinx.datetime.DayOfWeek? = WEEKDAYS.entries
        .firstOrNull { (name, _) -> Regex("\\b$name('s)?\\b").containsMatchIn(q) }
        ?.value

    private val MONTH_NAMES: List<List<String>> = listOf(
        listOf("january", "jan"), listOf("february", "feb"), listOf("march", "mar"),
        listOf("april", "apr"), listOf("may"), listOf("june", "jun"), listOf("july", "jul"),
        listOf("august", "aug"), listOf("september", "sept", "sep"), listOf("october", "oct"),
        listOf("november", "nov"), listOf("december", "dec")
    )

    private val WEEKDAYS: Map<String, kotlinx.datetime.DayOfWeek> = mapOf(
        "monday" to kotlinx.datetime.DayOfWeek.MONDAY,
        "tuesday" to kotlinx.datetime.DayOfWeek.TUESDAY,
        "wednesday" to kotlinx.datetime.DayOfWeek.WEDNESDAY,
        "thursday" to kotlinx.datetime.DayOfWeek.THURSDAY,
        "friday" to kotlinx.datetime.DayOfWeek.FRIDAY,
        "saturday" to kotlinx.datetime.DayOfWeek.SATURDAY,
        "sunday" to kotlinx.datetime.DayOfWeek.SUNDAY
    )

    private val STOP_WORDS = setOf(
        "play", "the", "and", "armstrong", "getty", "podcast", "show", "episode", "episodes",
        "listen", "start", "resume", "please", "from", "with", "some", "music", "radio",
        "on", "demand", "a&g", "app", "that", "this", "hour"
    )
}
