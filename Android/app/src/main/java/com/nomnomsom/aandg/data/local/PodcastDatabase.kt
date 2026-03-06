package com.nomnomsom.aandg.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nomnomsom.aandg.data.model.PodcastDay
import com.nomnomsom.aandg.data.model.TranscriptEntity

@Database(
    entities = [PodcastDay::class, TranscriptEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDayDao(): PodcastDayDao
    abstract fun transcriptDao(): TranscriptDao
}
