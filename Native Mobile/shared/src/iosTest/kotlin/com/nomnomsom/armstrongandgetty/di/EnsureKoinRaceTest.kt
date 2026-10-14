package com.nomnomsom.armstrongandgetty.di

import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.analytics.NoopAnalyticsTracker
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * Regression test for the top iOS 1.0.1 crash (Crashlytics issue 98f22a15…, symbolicated as
 * "-[KotlinNumber initWithBool:]"). On a background-refresh launch the BGTaskScheduler handler
 * (background queue) and MainViewController (main thread) both call ensureKoinStarted() at the
 * same moment. An unguarded check-then-start let the loser either throw
 * KoinApplicationAlreadyStartedException or resolve from a container whose modules weren't
 * loaded yet (NoDefinitionFoundException) — both fatal across the Objective-C boundary.
 *
 * Drives [startKoinOnce] with a stand-in module: the real platformModule() references the
 * Google Cast SDK, which this bare test executable doesn't link.
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
class EnsureKoinRaceTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun concurrentCallersAllGetAFullyStartedKoin() {
        val testModule = module { single<AnalyticsTracker> { NoopAnalyticsTracker } }
        val pool = newFixedThreadPoolContext(THREADS, "ensure-koin-race")
        try {
            repeat(ROUNDS) { round ->
                stopKoin()
                val arrived = AtomicInt(0)
                val failures = runBlocking {
                    List(THREADS) {
                        async(pool) {
                            // Hold every thread at a gate so they all reach startKoinOnce() together.
                            arrived.addAndFetch(1)
                            val gate = TimeSource.Monotonic.markNow()
                            while (arrived.load() < THREADS && gate.elapsedNow() < 1.seconds) { }
                            runCatching {
                                startKoinOnce { startKoin { modules(testModule) } }
                                KoinPlatform.getKoin().get<AnalyticsTracker>()
                            }.exceptionOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }
                assertEquals(emptyList<String>(), failures.map { it.toString() }, "round $round")
            }
        } finally {
            pool.close()
        }
    }

    private companion object {
        const val THREADS = 4
        const val ROUNDS = 200
    }
}
