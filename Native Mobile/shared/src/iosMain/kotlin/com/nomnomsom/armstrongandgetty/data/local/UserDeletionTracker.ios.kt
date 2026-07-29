package com.nomnomsom.armstrongandgetty.data.local

import platform.Foundation.NSUserDefaults

actual class UserDeletionTracker : DeletionMarks {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual override fun isDeleted(date: String): Boolean = deletedDates().contains(date)

    actual override fun markDeleted(date: String) {
        defaults.setObject((deletedDates() + date).toList(), KEY_DATES)
    }

    actual override fun unmarkDeleted(date: String) {
        defaults.setObject((deletedDates() - date).toList(), KEY_DATES)
    }

    private fun deletedDates(): Set<String> {
        val stored = defaults.arrayForKey(KEY_DATES) ?: return emptySet()
        return stored.filterIsInstance<String>().toSet()
    }

    private companion object {
        const val KEY_DATES = "user_deletions_dates"
    }
}
