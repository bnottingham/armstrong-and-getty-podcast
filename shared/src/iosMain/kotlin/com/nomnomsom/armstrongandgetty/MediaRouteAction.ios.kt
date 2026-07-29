package com.nomnomsom.armstrongandgetty

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVRoutePickerView
import platform.UIKit.UIColor

/** AirPlay route picker — the iOS counterpart of the Android Cast button. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MediaRouteAction(modifier: Modifier) {
    UIKitView(
        factory = {
            AVRoutePickerView().apply {
                activeTintColor = UIColor(red = 0.910, green = 0.702, blue = 0.294, alpha = 1.0)
                tintColor = UIColor(red = 0.910, green = 0.702, blue = 0.294, alpha = 1.0)
                backgroundColor = UIColor.clearColor
            }
        },
        modifier = modifier
    )
}
