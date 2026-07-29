package com.nomnomsom.armstrongandgetty

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Entry point for the androidApp module. Lives here so the app module needs no Compose
 * compiler of its own — its MainActivity just calls this.
 */
fun ComponentActivity.setAppContent() {
    setContent { App() }
}
