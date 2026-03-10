package com.nomnomsom.armstrongandgetty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDayDao {

    @Query("SELECT * FROM podcast_days ORDER BY date DESC")
    fun getAllDays(): Flow<List<PodcastDay>>

    @Query("SELECT * FROM podcast_days ORDER BY date DESC")
    suspend fun getAllDaysSnapshot(): List<PodcastDay>

    @Query("SELECT * FROM podcast_days WHERE downloadState = 'downloaded' ORDER BY date DESC")
    suspend fun getAllDownloadedDays(): List<PodcastDay>

    @Query("SELECT * FROM podcast_days WHERE date = :date")
    suspend fun getDayByDate(date: String): PodcastDay?

    @Query("SELECT * FROM podcast_days WHERE date = :date")
    fun observeDay(date: String): Flow<PodcastDay?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(day: PodcastDay)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(day: PodcastDay)

    @Update
    suspend fun update(day: PodcastDay)

    @Query("UPDATE podcast_days SET downloadState = :state WHERE date = :date")
    suspend fun updateDownloadState(date: String, state: String)

    @Query("UPDATE podcast_days SET downloadState = :state, combinedFilePath = :path WHERE date = :date")
    suspend fun updateDownloadComplete(date: String, state: String, path: String)

    @Query("UPDATE podcast_days SET listenedPositionMs = :positionMs, isListened = :isListened WHERE date = :date")
    suspend fun updateListenProgress(date: String, positionMs: Long, isListened: Boolean)

    @Query("UPDATE podcast_days SET listenedPositionMs = 0, isListened = 0 WHERE date = :date")
    suspend fun resetProgress(date: String)

    @Query("""
        UPDATE podcast_days 
        SET segmentsJson = :segmentsJson, 
            totalDurationMs = :totalDurationMs, 
            segmentCount = :segmentCount,
            summary = :summary,
            isComplete = :isComplete,
            lastUpdated = :lastUpdated
        WHERE date = :date
    """)
    suspend fun updateSegments(
        date: String,
        segmentsJson: String,
        totalDurationMs: Long,
        segmentCount: Int,
        summary: String,
        isComplete: Boolean,
        lastUpdated: Long
    )

    @Query("DELETE FROM podcast_days WHERE date = :date")
    suspend fun deleteDay(date: String)

    @Query("SELECT * FROM podcast_days ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDay(): PodcastDay?
}
