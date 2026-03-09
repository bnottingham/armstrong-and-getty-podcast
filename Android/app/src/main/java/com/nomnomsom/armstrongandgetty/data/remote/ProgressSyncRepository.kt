package com.nomnomsom.armstrongandgetty.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs listen progress to Firestore for authenticated users.
 *
 * Firestore structure:
 *   users/{uid}/progress/{date}
 *     - listenedPositionMs: Long
 *     - isListened: Boolean
 *     - lastUpdated: Long
 */
@Singleton
class ProgressSyncRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "ProgressSync"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_PROGRESS = "progress"
    }

    private val uid: String?
        get() = firebaseAuth.currentUser?.uid

    /**
     * Upload a single day's progress to Firestore.
     * No-op if not authenticated.
     */
    suspend fun pushProgress(
        date: String,
        listenedPositionMs: Long,
        isListened: Boolean
    ) = withContext(Dispatchers.IO) {
        val currentUid = uid ?: return@withContext // Not signed in — do nothing

        try {
            val data = mapOf(
                "listenedPositionMs" to listenedPositionMs,
                "isListened" to isListened,
                "lastUpdated" to System.currentTimeMillis()
            )
            firestore
                .collection(COLLECTION_USERS)
                .document(currentUid)
                .collection(COLLECTION_PROGRESS)
                .document(date)
                .set(data, SetOptions.merge())
                .await()

            Log.d(TAG, "Pushed progress for $date: ${listenedPositionMs}ms, listened=$isListened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push progress for $date", e)
        }
    }

    /**
     * Pull all progress records from Firestore.
     * Returns a map of date → RemoteProgress, or empty if not authenticated.
     */
    suspend fun pullAllProgress(): Map<String, RemoteProgress> = withContext(Dispatchers.IO) {
        val currentUid = uid ?: return@withContext emptyMap()

        try {
            val snapshot = firestore
                .collection(COLLECTION_USERS)
                .document(currentUid)
                .collection(COLLECTION_PROGRESS)
                .get()
                .await()

            val result = mutableMapOf<String, RemoteProgress>()
            for (doc in snapshot.documents) {
                val date = doc.id
                val positionMs = doc.getLong("listenedPositionMs") ?: 0L
                val isListened = doc.getBoolean("isListened") ?: false
                val lastUpdated = doc.getLong("lastUpdated") ?: 0L
                result[date] = RemoteProgress(
                    listenedPositionMs = positionMs,
                    isListened = isListened,
                    lastUpdated = lastUpdated
                )
            }

            Log.d(TAG, "Pulled ${result.size} progress records from Firestore")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull progress", e)
            emptyMap()
        }
    }

    /**
     * Pull progress for a single date from Firestore.
     * Returns null if not authenticated or no remote record exists.
     */
    suspend fun pullProgress(date: String): RemoteProgress? = withContext(Dispatchers.IO) {
        val currentUid = uid ?: return@withContext null

        try {
            val doc = firestore
                .collection(COLLECTION_USERS)
                .document(currentUid)
                .collection(COLLECTION_PROGRESS)
                .document(date)
                .get()
                .await()

            if (!doc.exists()) return@withContext null

            RemoteProgress(
                listenedPositionMs = doc.getLong("listenedPositionMs") ?: 0L,
                isListened = doc.getBoolean("isListened") ?: false,
                lastUpdated = doc.getLong("lastUpdated") ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull progress for $date", e)
            null
        }
    }
}

data class RemoteProgress(
    val listenedPositionMs: Long,
    val isListened: Boolean,
    val lastUpdated: Long
)
