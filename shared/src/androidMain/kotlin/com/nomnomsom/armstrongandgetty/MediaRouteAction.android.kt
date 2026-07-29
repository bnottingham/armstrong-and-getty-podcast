package com.nomnomsom.armstrongandgetty

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
actual fun MediaRouteAction(modifier: Modifier) {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).apply {
                setBackgroundColor(0xFF0E0F13.toInt())
                CastButtonFactory.setUpMediaRouteButton(context, this)
            }
        },
        modifier = modifier
    )
}
