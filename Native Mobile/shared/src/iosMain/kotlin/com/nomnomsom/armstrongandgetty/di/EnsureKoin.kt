package com.nomnomsom.armstrongandgetty.di

import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSLock

/**
 * Idempotent, thread-safe Koin bootstrap. Called from the UI entry point (main thread) and
 * from the BGTaskScheduler handler (background queue) — and a background-refresh launch can
 * run both at the same moment, so whichever comes first starts Koin while the other waits.
 */
fun ensureKoinStarted() = startKoinOnce { initKoin() }

private val startLock = NSLock()

/**
 * Runs [start] unless a Koin context already exists, atomically. Koin's getOrNull() takes no
 * lock, and startKoin publishes the instance before loading its modules: unguarded, a
 * concurrent caller either threw KoinApplicationAlreadyStartedException or resolved from an
 * empty container (NoDefinitionFoundException) — the top iOS crash in 1.0.1. See
 * EnsureKoinRaceTest.
 */
internal fun startKoinOnce(start: () -> Unit) {
    startLock.lock()
    try {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            start()
        }
    } finally {
        startLock.unlock()
    }
}
