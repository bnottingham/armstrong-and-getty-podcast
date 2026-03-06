package com.nomnomsom.aandg.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nomnomsom.aandg.data.model.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {

    @Query("SELECT * FROM transcripts WHERE date = :date ORDER BY segmentIndex ASC")
    fun observeTranscriptsForDay(date: String): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE date = :date ORDER BY segmentIndex ASC")
    suspend fun getTranscriptsForDay(date: String): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    suspend fun getTranscript(id: String): TranscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(transcript: TranscriptEntity)

    @Query("UPDATE transcripts SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("UPDATE transcripts SET state = :state, wordsJson = :wordsJson, fullText = :fullText, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateComplete(id: String, state: String, wordsJson: String, fullText: String, lastUpdated: Long)

    @Query("SELECT COUNT(*) FROM transcripts WHERE date = :date AND state = 'done'")
    suspend fun getCompletedCountForDay(date: String): Int

    @Query("SELECT COUNT(*) FROM transcripts WHERE date = :date AND state = 'transcribing'")
    suspend fun getTranscribingCountForDay(date: String): Int

    @Query("UPDATE transcripts SET state = 'none' WHERE date = :date AND state = 'transcribing'")
    suspend fun resetStuckTranscripts(date: String)
}
