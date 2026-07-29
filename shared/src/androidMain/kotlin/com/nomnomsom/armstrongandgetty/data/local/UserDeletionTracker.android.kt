package com.nomnomsom.armstrongandgetty.data.local

import android.content.Context
import android.content.SharedPreferences

actual class UserDeletionTracker(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun isDeleted(date: String): Boolean = deletedDates().contains(date)

    actual fun markDeleted(date: String) {
        prefs.edit().putStringSet(KEY_DATES, deletedDates() + date).apply()
    }

    actual fun unmarkDeleted(date: String) {
        prefs.edit().putStringSet(KEY_DATES, deletedDates() - date).apply()
    }

    private fun deletedDates(): Set<String> =
        prefs.getStringSet(KEY_DATES, emptySet()) ?: emptySet()

    private companion object {
        // Same prefs file/key as the previous Android-only app — existing flags survive.
        const val PREFS_NAME = "user_deletions"
        const val KEY_DATES = "dates"
    }
}
