package com.nomnomsom.armstrongandgetty.data.local

/**
 * Persists the set of day-dates the user has deleted.
 *
 * Auto-download paths (refreshFeed, the periodic worker) skip dates the user explicitly
 * deleted so content doesn't silently come back; a manual download clears the flag.
 *
 * expect/actual rather than a common key-value wrapper: the previous Android app stored
 * this as a SharedPreferences StringSet ("user_deletions" / "dates"), and reading that
 * shape back requires the platform API — a common string-based store would orphan (or
 * crash on) existing installs' data.
 */
expect class UserDeletionTracker {
    fun isDeleted(date: String): Boolean
    fun markDeleted(date: String)
    fun unmarkDeleted(date: String)
}
