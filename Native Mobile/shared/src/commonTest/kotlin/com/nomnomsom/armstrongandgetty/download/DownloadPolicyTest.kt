package com.nomnomsom.armstrongandgetty.download

import com.nomnomsom.armstrongandgetty.testutil.FakeNetworkStatus
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadPolicyTest {

    private fun policy(wifiOnly: Boolean, kind: NetworkKind): Triple<DownloadPolicy, DownloadSettings, FakeNetworkStatus> {
        val settings = DownloadSettings(MapSettings())
        settings.setWifiOnly(wifiOnly)
        val network = FakeNetworkStatus(kind)
        return Triple(DownloadPolicy(settings, network), settings, network)
    }

    @Test
    fun decideTruthTable() {
        for (kind in NetworkKind.entries) {
            assertTrue(DownloadPolicy.decide(wifiOnly = false, network = kind), "OFF allows $kind")
            assertEquals(
                kind == NetworkKind.UNMETERED,
                DownloadPolicy.decide(wifiOnly = true, network = kind),
                "ON allows only UNMETERED (checked $kind)"
            )
        }
    }

    /** The default path (setting off) must behave exactly as before: never read the network. */
    @Test
    fun offNeverTouchesNetwork() = runTest {
        for (kind in NetworkKind.entries) {
            val (policy, _, network) = policy(wifiOnly = false, kind = kind)
            assertTrue(policy.allowedNow())

            val values = mutableListOf<Boolean>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                policy.allowedFlow.toList(values)
            }
            network.kind = NetworkKind.METERED
            network.kind = NetworkKind.OFFLINE
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf(true), values)
            assertEquals(0, network.totalCalls, "setting off must never query the network ($kind)")
        }
    }

    @Test
    fun onAllowsUnmetered() = runTest {
        assertTrue(policy(wifiOnly = true, kind = NetworkKind.UNMETERED).first.allowedNow())
    }

    @Test
    fun onBlocksMetered() = runTest {
        assertFalse(policy(wifiOnly = true, kind = NetworkKind.METERED).first.allowedNow())
    }

    @Test
    fun onBlocksOffline() = runTest {
        assertFalse(policy(wifiOnly = true, kind = NetworkKind.OFFLINE).first.allowedNow())
    }

    @Test
    fun onWaitsForAFirstReading() = runTest {
        val (policy, _, network) = policy(wifiOnly = true, kind = NetworkKind.UNKNOWN)
        launch {
            delay(1_000)
            network.kind = NetworkKind.UNMETERED
        }
        assertTrue(policy.allowedNow())
        assertTrue(currentTime < DownloadPolicy.NETWORK_WAIT_MS)
    }

    @Test
    fun onBlocksWhenTheReadingStaysUnknown() = runTest {
        val (policy, _, _) = policy(wifiOnly = true, kind = NetworkKind.UNKNOWN)
        assertFalse(policy.allowedNow())
        assertEquals(DownloadPolicy.NETWORK_WAIT_MS, currentTime)
    }

    @Test
    fun allowedFlowFollowsNetworkAndSetting() = runTest {
        val (policy, settings, network) = policy(wifiOnly = true, kind = NetworkKind.METERED)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            policy.allowedFlow.toList(values)
        }

        network.kind = NetworkKind.UNMETERED
        network.kind = NetworkKind.METERED
        settings.setWifiOnly(false)
        network.kind = NetworkKind.OFFLINE // ignored once the setting is off
        advanceUntilIdle()

        assertEquals(listOf(false, true, false, true), values)
    }
}
