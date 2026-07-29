package com.nomnomsom.armstrongandgetty.media

import com.nomnomsom.armstrongandgetty.util.AppLog
import googlecast.GCKCastContext
import googlecast.GCKCastOptions
import googlecast.GCKDiscoveryCriteria
import googlecast.kGCKDefaultMediaReceiverApplicationID
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * One-time Google Cast context setup. Must run on the main thread before any cast
 * UI or session APIs are touched — called from MainViewController.
 */
@OptIn(ExperimentalForeignApi::class)
object CastManager {
    private const val TAG = "CastManager"
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        try {
            val criteria = GCKDiscoveryCriteria(applicationID = kGCKDefaultMediaReceiverApplicationID!!)
            val options = GCKCastOptions(discoveryCriteria = criteria)
            GCKCastContext.setSharedInstanceWithOptions(options)
            AppLog.d(TAG, "Cast context initialized")
        } catch (e: Exception) {
            AppLog.w(TAG, "Cast unavailable", e)
        }
    }
}
