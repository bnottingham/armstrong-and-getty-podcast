package com.nomnomsom.armstrongandgetty.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.compose.koinInject

/**
 * Composable glue between App() and [ReviewCoordinator]: attaches the platform launcher,
 * starts the coordinator, and reports the current screen, whether the host is RESUMED and
 * whether its window has focus. [blocked] (a visible failure or refresh on the Episodes
 * list) holds the prompt while true.
 *
 * Must sit OUTSIDE the NavHost so [LocalLifecycleOwner] is the Activity / root view
 * controller lifecycle (on iOS, CMP maps willResignActive → ON_PAUSE and
 * didEnterBackground → ON_STOP, so "resumed" means foreground-active on both platforms).
 * Window focus adds what RESUMED misses on Android: the notification shade, dialog windows
 * and the Cast chooser (on iOS it tracks the scene's active state).
 */
@Composable
fun ReviewPromptHost(currentRoute: String?, blocked: Boolean) {
    val coordinator: ReviewCoordinator = koinInject()
    val launcher = rememberReviewLauncher()
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val screen = reviewScreenForRoute(currentRoute)
    val latestScreen by rememberUpdatedState(screen)

    fun report() = coordinator.onScreen(
        screen = latestScreen,
        resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
        windowFocused = windowInfo.isWindowFocused,
    )

    DisposableEffect(coordinator, launcher) {
        coordinator.attach(launcher)
        onDispose { coordinator.detach(launcher) }
    }

    LaunchedEffect(coordinator) { coordinator.start() }

    HoldReviewPromptWhile(blocked)

    // Lifecycle changes are pushed straight from the observer instead of via recomposition:
    // the frame clock can stop once the host is stopped, which would leave a stale "resumed".
    DisposableEffect(coordinator, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ -> report() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // OTHER + a fresh playback baseline: a host recreated later (new Activity in the
            // same process) that opens on Episodes must not look like "left the player", and
            // its reconnecting PlaybackController must not look like a stop or a finish.
            coordinator.onHostDisposed()
        }
    }

    LaunchedEffect(coordinator, windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { report() }
    }

    LaunchedEffect(coordinator, screen) { report() }
}

/**
 * Holds the in-app rating prompt while [condition] is true, e.g. while a dialog is open
 * (on iOS a Compose dialog shares the window, so window focus doesn't see it).
 */
@Composable
fun HoldReviewPromptWhile(condition: Boolean) {
    val coordinator: ReviewCoordinator = koinInject()
    val owner = remember { Any() }
    DisposableEffect(coordinator, owner, condition) {
        coordinator.setHold(owner, condition)
        onDispose { coordinator.setHold(owner, false) }
    }
}

/** "episodes" → EPISODES, "player/…" → PLAYER, "about" → ABOUT, anything else → OTHER. */
internal fun reviewScreenForRoute(route: String?): ReviewScreen = when {
    route == "episodes" -> ReviewScreen.EPISODES
    route?.startsWith("player/") == true -> ReviewScreen.PLAYER
    route == "about" -> ReviewScreen.ABOUT
    else -> ReviewScreen.OTHER
}
