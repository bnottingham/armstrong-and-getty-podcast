package com.nomnomsom.armstrongandgetty.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.nomnomsom.armstrongandgetty.data.model.PodcastDay

@Database(
    entities = [PodcastDay::class],
    version = 3,
    exportSchema = false
)
@ConstructedBy(PodcastDatabaseConstructor::class)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun podcastDayDao(): PodcastDayDao

    companion object {
        /** Same file name the previous Android-only app used — existing installs keep their data. */
        const val DB_NAME = "ag_podcast_db"
    }
}

// The Room compiler generates the `actual` implementations per platform.
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object PodcastDatabaseConstructor : RoomDatabaseConstructor<PodcastDatabase> {
    override fun initialize(): PodcastDatabase
}
