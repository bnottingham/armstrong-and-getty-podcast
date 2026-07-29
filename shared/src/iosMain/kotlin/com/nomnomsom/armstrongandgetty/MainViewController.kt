package com.nomnomsom.armstrongandgetty

import androidx.compose.ui.window.ComposeUIViewController
import com.nomnomsom.armstrongandgetty.di.initKoin
import platform.UIKit.UIViewController

private var koinStarted = false

fun MainViewController(): UIViewController {
    if (!koinStarted) {
        koinStarted = true
        initKoin()
    }
    return ComposeUIViewController { App() }
}
