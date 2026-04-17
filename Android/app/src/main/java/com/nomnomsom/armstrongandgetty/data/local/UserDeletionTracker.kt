package com.nomnomsom.armstrongandgetty.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the set of day-dates the user has deleted.
 *
 * The DB row for a deleted day is fully removed, so a later RSS refresh would otherwise
 * look like a "new episode" and auto-download the content back. This flag lets auto-download
 * paths (refreshFeed, NewEpisodeCheckWorker) skip dates the user explicitly deleted.
 * A manual download clears the flag.
 */
@Singleton
class UserDeletionTracker @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDeleted(date: String): Boolean = deletedDates().contains(date)

    fun markDeleted(date: String) {
        prefs.edit().putStringSet(KEY_DATES, deletedDates() + date).apply()
    }

    fun unmarkDeleted(date: String) {
        prefs.edit().putStringSet(KEY_DATES, deletedDates() - date).apply()
    }

    private fun deletedDates(): Set<String> =
        prefs.getStringSet(KEY_DATES, emptySet()) ?: emptySet()

    private companion object {
        const val PREFS_NAME = "user_deletions"
        const val KEY_DATES = "dates"
    }
}
