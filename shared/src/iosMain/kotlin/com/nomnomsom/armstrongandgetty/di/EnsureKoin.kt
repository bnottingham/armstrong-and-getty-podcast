package com.nomnomsom.armstrongandgetty.di

import org.koin.mp.KoinPlatformTools

/**
 * Idempotent Koin bootstrap. Called from the UI entry point and from background-task
 * launches, whichever comes first — a BGAppRefreshTask relaunch never builds the UI.
 */
fun ensureKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
