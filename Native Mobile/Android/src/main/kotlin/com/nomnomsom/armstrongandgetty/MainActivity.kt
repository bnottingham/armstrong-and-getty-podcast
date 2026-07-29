package com.nomnomsom.armstrongandgetty

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.cast.framework.CastContext

/**
 * AppCompatActivity (a FragmentActivity), not ComponentActivity: the Cast
 * MediaRouteButton presents its device chooser as a DialogFragment and crashes the
 * moment it's tapped inside a plain ComponentActivity. The Cast sender guide also
 * expects an AppCompat theme, which Theme.AGPodcast already provides.
 */
class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initCastContext()
        setAppContent()
        requestNotificationPermissionIfNeeded()
    }

    /**
     * Initialize the Cast framework at activity launch, per the sender-app spec, so
     * device discovery starts early and the cast button reflects route availability.
     * Devices without Google Play services simply skip Cast.
     */
    private fun initCastContext() {
        try {
            CastContext.getSharedInstance(applicationContext)
        } catch (_: Exception) {
        }
    }

    /**
     * Android 13+ requires a runtime grant for POST_NOTIFICATIONS — without it the
     * new-episode notifications from NewEpisodeCheckWorker are silently dropped.
     * Mirrors the iOS UNUserNotificationCenter authorization request at first launch;
     * the system only ever shows this prompt once (plus one re-ask if declined).
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
