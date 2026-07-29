package com.nomnomsom.armstrongandgetty

import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
actual fun MediaRouteAction(modifier: Modifier) {
    // The Activity context is required (not just themed): the button's device chooser
    // is a DialogFragment, so the host must be a FragmentActivity with an AppCompat
    // theme — see MainActivity. No background override; the button draws its own
    // themed icon and ripple on the app's dark surface.
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).apply {
                try {
                    CastButtonFactory.setUpMediaRouteButton(context, this)
                } catch (_: Exception) {
                    // No Google Play services on this device — hide Cast entirely.
                    visibility = View.GONE
                }
            }
        },
        modifier = modifier.size(48.dp)
    )
}
