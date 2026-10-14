package com.nomnomsom.armstrongandgetty.download

import com.nomnomsom.armstrongandgetty.review.ReviewKeys
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * "Download over Wi-Fi only" storage. The setting is OFF by default for everyone (owner
 * decision 2026-10-14): an absent key reads off, on new installs and upgrades alike.
 */
class DownloadSettingsTest {

    @Test
    fun defaultIsOff() {
        val settings = MapSettings()
        val download = DownloadSettings(settings)
        assertFalse(download.wifiOnly.value, "Wi-Fi only must default to OFF")
        assertTrue(settings.keys.isEmpty(), "the constructor must not write any key")
    }

    /** An upgrade from a build without the setting has no key at all — still off. */
    @Test
    fun upgradeWithOtherStoredStateStillReadsOff() {
        val settings = MapSettings()
        settings.putString("dates", "2026-10-01")
        settings.putLong(ReviewKeys.FIRST_SEEN_MS, 1_759_000_000_000)
        assertFalse(DownloadSettings(settings).wifiOnly.value)
    }

    @Test
    fun persistsUnderNewKey() {
        val settings = MapSettings()
        val download = DownloadSettings(settings)

        download.setWifiOnly(true)
        assertTrue(download.wifiOnly.value)
        assertTrue(settings.getBoolean("download_settings.wifi_only", false))
        assertTrue(DownloadSettings(settings).wifiOnly.value, "a new instance reads the stored value")

        download.setWifiOnly(false)
        assertFalse(download.wifiOnly.value)
        assertFalse(DownloadSettings(settings).wifiOnly.value)
    }

    @Test
    fun keysAreNewAndDisjoint() {
        val legacy = setOf("dates", "user_deletions_dates", "user_deletions", "ag_podcast_db", "podcasts")
        for (key in DownloadSettingsKeys.all) {
            assertTrue(key.startsWith(DownloadSettingsKeys.PREFIX), "$key must carry the download_settings. prefix")
            assertFalse(key in legacy, "$key reuses upgrade-in-place storage")
            assertFalse(key in ReviewKeys.all, "$key collides with an in-app review key")
            assertFalse(key.startsWith(ReviewKeys.PREFIX), "$key sits in the in-app review namespace")
        }
        assertEquals("download_settings", DownloadSettingsKeys.ANDROID_PREFS_FILE)
        assertNotEquals("user_deletions", DownloadSettingsKeys.ANDROID_PREFS_FILE)
        assertNotEquals(ReviewKeys.ANDROID_PREFS_FILE, DownloadSettingsKeys.ANDROID_PREFS_FILE)
    }

    @Test
    fun writesTouchOnlyDownloadKeys() {
        val settings = MapSettings()
        settings.putString("dates", "2026-10-01,2026-10-02")
        settings.putString("user_deletions_dates", "2026-10-03")
        settings.putLong(ReviewKeys.FIRST_SEEN_MS, 1_759_000_000_000)
        settings.putInt(ReviewKeys.REQUEST_COUNT, 2)
        val seeded = settings.keys.associateWith { settings.getStringOrNull(it) ?: settings.getLongOrNull(it)?.toString() ?: settings.getIntOrNull(it)?.toString() }

        val download = DownloadSettings(settings)
        download.setWifiOnly(true)
        download.setWifiOnly(false)
        download.setWifiOnly(true)

        for ((key, value) in seeded) {
            val now = settings.getStringOrNull(key) ?: settings.getLongOrNull(key)?.toString() ?: settings.getIntOrNull(key)?.toString()
            assertEquals(value, now, "$key must be untouched")
        }
        val added = settings.keys - seeded.keys
        assertTrue(DownloadSettingsKeys.all.containsAll(added), "unexpected keys written: $added")
    }

    @Test
    fun brokenStoreReadsOff() {
        val download = DownloadSettings(ThrowingSettings())
        assertFalse(download.wifiOnly.value)
        // A failing write must not crash either; the in-memory value still follows the toggle.
        download.setWifiOnly(true)
        assertTrue(download.wifiOnly.value)
    }

    private class ThrowingSettings(private val delegate: Settings = MapSettings()) : Settings by delegate {
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = error("store unavailable")
        override fun getBooleanOrNull(key: String): Boolean? = error("store unavailable")
        override fun putBoolean(key: String, value: Boolean) = error("store unavailable")
    }
}
