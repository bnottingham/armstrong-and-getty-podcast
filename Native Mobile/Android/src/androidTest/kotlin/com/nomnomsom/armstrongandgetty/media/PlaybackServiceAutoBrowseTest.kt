package com.nomnomsom.armstrongandgetty.media

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Regression test for the "Auto App Quality Guidelines: App doesn't load content" Play
 * rejection (Aug 2026). Android Auto talks to [PlaybackService] purely through the
 * MediaBrowser protocol (connect -> getLibraryRoot -> getChildren) and never through the
 * phone UI or a plain MediaController, so a manual playback smoke test cannot catch a
 * regression here — that's exactly how the underlying bug (onConnect silently granting
 * only DEFAULT_SESSION_COMMANDS instead of DEFAULT_SESSION_AND_LIBRARY_COMMANDS) shipped
 * unnoticed. This drives the same calls Auto makes and fails loudly if browsing breaks
 * again.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceAutoBrowseTest {

    @Test
    fun autoCanConnectAndBrowseRealEpisodes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // buildAsync()'s future completes via a main-thread post once the binder connection is
        // up, so blocking on .get() from inside runOnMainSync would deadlock the very thread
        // the connection needs — only *start* the connection on main, then block on the test
        // thread.
        lateinit var browserFuture: ListenableFuture<MediaBrowser>
        instrumentation.runOnMainSync {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            browserFuture = MediaBrowser.Builder(context, token).buildAsync()
        }
        val browser = browserFuture.get(20, TimeUnit.SECONDS)

        try {
            lateinit var rootFuture: ListenableFuture<LibraryResult<MediaItem>>
            instrumentation.runOnMainSync { rootFuture = browser.getLibraryRoot(null) }
            val rootResult = rootFuture.get(15, TimeUnit.SECONDS)

            assertEquals(
                "getLibraryRoot failed — this is the exact failure mode Android Auto " +
                    "surfaces as \"doesn't seem to be working right now\" when library " +
                    "commands aren't granted in onConnect",
                LibraryResult.RESULT_SUCCESS,
                rootResult.resultCode
            )
            val root = requireNotNull(rootResult.value) { "getLibraryRoot succeeded but returned no root item" }

            lateinit var childrenFuture: ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
            instrumentation.runOnMainSync {
                childrenFuture = browser.getChildren(root.mediaId, 0, 50, null)
            }
            val childrenResult = childrenFuture.get(30, TimeUnit.SECONDS)

            assertEquals(
                "getChildren failed for the root's children",
                LibraryResult.RESULT_SUCCESS,
                childrenResult.resultCode
            )
            val children = childrenResult.value.orEmpty()
            assertTrue(
                "Auto's browse tree returned zero episodes — a fresh install with an " +
                    "empty local catalog should trigger a feed refresh, not show nothing",
                children.isNotEmpty()
            )
            assertTrue(
                "Every browsed episode must be playable",
                children.all { it.mediaMetadata.isPlayable == true }
            )
        } finally {
            instrumentation.runOnMainSync { browser.release() }
        }
    }
}
