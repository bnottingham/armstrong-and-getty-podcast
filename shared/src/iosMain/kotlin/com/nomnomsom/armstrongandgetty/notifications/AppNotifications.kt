package com.nomnomsom.armstrongandgetty.notifications

import com.nomnomsom.armstrongandgetty.util.AppLog
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS counterpart of the Android worker's "new episodes" notification channel.
 */
object AppNotifications {
    private const val TAG = "AppNotifications"
    private const val REQUEST_ID = "new_episodes"

    /** Asks once at startup; iOS remembers the answer and won't re-prompt. */
    fun requestAuthorization() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, error ->
            AppLog.d(TAG, "Notification authorization granted=$granted error=${error?.localizedDescription}")
        }
    }

    /** Immediate local notification — used by the background refresh on new content. */
    fun postNewContent(title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = REQUEST_ID,
            content = content,
            trigger = null
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { error ->
                if (error != null) {
                    AppLog.w(TAG, "Failed to post notification: ${error.localizedDescription}")
                }
            }
    }
}
