package com.nomnomsom.armstrongandgetty

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nomnomsom.armstrongandgetty.ui.icons.AppIcons
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary
import com.nomnomsom.armstrongandgetty.util.AppLog
import googlecast.GCKCastContext
import googlecast.GCKCastStateConnected
import googlecast.GCKCastStateNoDevicesAvailable
import googlecast.kGCKCastStateDidChangeNotification
import googlecast.presentCastDialog
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVRoutePickerView
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIButton
import platform.UIKit.UIControlEventTouchUpInside

/**
 * iOS media routing: a Chromecast button and an AirPlay button, both rendered as
 * themed Compose IconButtons. Cast opens the SDK's device dialog via
 * GCKCastContext.presentCastDialog(); AirPlay forwards the tap to an off-screen
 * AVRoutePickerView's internal button. No UIKit interop views in the bar — embedding
 * them drew opaque backgrounds on the dark theme.
 *
 * Per Cast UX guidelines (and matching Android's MediaRouteButton), the cast button
 * is hidden while no cast devices are discoverable — which includes the simulator,
 * where Bonjour discovery doesn't work at all.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MediaRouteAction(modifier: Modifier) {
    val routePicker = remember { AVRoutePickerView() }

    fun currentCastState(): ULong =
        runCatching { GCKCastContext.sharedInstance().castState }.getOrDefault(
            GCKCastStateNoDevicesAvailable
        )

    var castState by remember { mutableStateOf(currentCastState()) }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = kGCKCastStateDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            castState = currentCastState()
        }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (castState != GCKCastStateNoDevicesAvailable) {
            IconButton(onClick = {
                try {
                    AppLog.d("MediaRouteAction", "presenting cast dialog, castState=$castState")
                    GCKCastContext.sharedInstance().presentCastDialog()
                } catch (e: Exception) {
                    AppLog.w("MediaRouteAction", "Cast dialog unavailable", e)
                }
            }) {
                Icon(
                    imageVector = if (castState == GCKCastStateConnected) AppIcons.CastConnected else AppIcons.Cast,
                    contentDescription = "Cast",
                    tint = if (castState == GCKCastStateConnected) Gold else TextSecondary
                )
            }
        }
        IconButton(onClick = {
            val trigger = routePicker.subviews.filterIsInstance<UIButton>().firstOrNull()
            trigger?.sendActionsForControlEvents(UIControlEventTouchUpInside)
        }) {
            Icon(
                imageVector = AppIcons.Airplay,
                contentDescription = "AirPlay",
                tint = Gold
            )
        }
    }
}
