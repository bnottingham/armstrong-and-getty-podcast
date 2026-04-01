package com.nomnomsom.armstrongandgetty.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay

@Database(
    entities = [PodcastDay::class],
    version = 3,
    exportSchema = false
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDayDao(): PodcastDayDao
}
