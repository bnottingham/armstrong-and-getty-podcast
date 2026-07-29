package com.nomnomsom.armstrongandgetty

import androidx.compose.ui.window.ComposeUIViewController
import com.nomnomsom.armstrongandgetty.di.ensureKoinStarted
import com.nomnomsom.armstrongandgetty.media.CastManager
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    CastManager.initialize()
    ensureKoinStarted()
    return ComposeUIViewController { App() }
}
