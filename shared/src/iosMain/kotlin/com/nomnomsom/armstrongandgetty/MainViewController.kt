package com.nomnomsom.armstrongandgetty

import androidx.compose.ui.window.ComposeUIViewController
import com.nomnomsom.armstrongandgetty.di.ensureKoinStarted
import com.nomnomsom.armstrongandgetty.media.CastManager
import com.nomnomsom.armstrongandgetty.notifications.AppNotifications
import com.nomnomsom.armstrongandgetty.util.AppLog
import googlecast.GCKCastContext
import googlecast.createCastContainerControllerForViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
fun MainViewController(): UIViewController {
    CastManager.initialize()
    ensureKoinStarted()
    AppNotifications.requestAuthorization()

    val composeController = ComposeUIViewController { App() }

    // Wrap the Compose UI in the Cast container so the SDK's mini media controls bar
    // slides in at the bottom during an active cast session (invisible otherwise).
    return runCatching {
        val container = GCKCastContext.sharedInstance()
            .createCastContainerControllerForViewController(composeController)
        container.miniMediaControlsItemEnabled = true
        container as UIViewController
    }.getOrElse { e ->
        AppLog.w("MainViewController", "Cast container unavailable", e as? Exception)
        composeController
    }
}
