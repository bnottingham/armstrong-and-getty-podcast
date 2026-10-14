package com.nomnomsom.armstrongandgetty.download

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent keys for the "Download over Wi-Fi only" setting. All NEW: Android writes them
 * to its own SharedPreferences file ([ANDROID_PREFS_FILE]); iOS writes them to standard
 * NSUserDefaults under the `download_settings.` prefix. Upgrade-in-place storage (prefs file
 * `user_deletions` / key `dates`, NSUserDefaults `user_deletions_dates`, Room `ag_podcast_db`,
 * `podcasts/`) and the in-app review keys are never read or written. DownloadSettingsTest
 * enforces this.
 */
object DownloadSettingsKeys {
    /** Android SharedPreferences file. Never `user_deletions` or `in_app_review`. */
    const val ANDROID_PREFS_FILE = "download_settings"

    const val PREFIX = "download_settings."

    const val WIFI_ONLY = "download_settings.wifi_only"

    val all: List<String> = listOf(WIFI_ONLY)
}

/**
 * The listener's download preferences. "Download over Wi-Fi only" is **OFF by default** for
 * everyone (new installs and upgrades alike — an absent key means off, and there is no
 * migration). Off = every download uses Wi-Fi or mobile data, exactly as before the setting
 * existed. Changing the default needs an owner decision.
 */
class DownloadSettings(private val settings: Settings) {

    // Absent key = OFF. A broken store must never crash startup, the worker or the BG task.
    // The constructor only reads; it never writes a key.
    private val _wifiOnly = MutableStateFlow(
        runCatching { settings.getBoolean(DownloadSettingsKeys.WIFI_ONLY, false) }.getOrDefault(false)
    )

    /** Live value of "Download over Wi-Fi only". */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setWifiOnly(enabled: Boolean) {
        runCatching { settings.putBoolean(DownloadSettingsKeys.WIFI_ONLY, enabled) }
        _wifiOnly.value = enabled
    }
}
