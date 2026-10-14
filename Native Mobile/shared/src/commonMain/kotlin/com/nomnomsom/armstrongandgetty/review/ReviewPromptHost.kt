package com.nomnomsom.armstrongandgetty.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.compose.koinInject

/**
 * Composable glue between App() and [ReviewCoordinator]: attaches the platform launcher,
 * starts the coordinator, and reports the current screen plus whether the host is RESUMED.
 *
 * Must sit OUTSIDE the NavHost so [LocalLifecycleOwner] is the Activity / root view
 * controller lifecycle (on iOS, CMP maps willResignActive → ON_PAUSE and
 * didEnterBackground → ON_STOP, so "resumed" means foreground-active on both platforms).
 */
@Composable
fun ReviewPromptHost(currentRoute: String?) {
    val coordinator: ReviewCoordinator = koinInject()
    val launcher = rememberReviewLauncher()
    val lifecycleOwner = LocalLifecycleOwner.current
    val screen = reviewScreenForRoute(currentRoute)
    val latestScreen by rememberUpdatedState(screen)

    DisposableEffect(coordinator, launcher) {
        coordinator.attach(launcher)
        onDispose { coordinator.detach(launcher) }
    }

    LaunchedEffect(coordinator) { coordinator.start() }

    // Lifecycle changes are pushed straight from the observer instead of via recomposition:
    // the frame clock can stop once the host is stopped, which would leave a stale "resumed".
    DisposableEffect(coordinator, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            coordinator.onScreen(latestScreen, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // OTHER, not the last screen: a host recreated later (new Activity in the same
            // process) that opens on Episodes must not look like "left the player".
            coordinator.onScreen(ReviewScreen.OTHER, resumed = false)
        }
    }

    LaunchedEffect(coordinator, screen) {
        coordinator.onScreen(screen, lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
}

/** "episodes" → EPISODES, "player/…" → PLAYER, "about" → ABOUT, anything else → OTHER. */
internal fun reviewScreenForRoute(route: String?): ReviewScreen = when {
    route == "episodes" -> ReviewScreen.EPISODES
    route?.startsWith("player/") == true -> ReviewScreen.PLAYER
    route == "about" -> ReviewScreen.ABOUT
    else -> ReviewScreen.OTHER
}
