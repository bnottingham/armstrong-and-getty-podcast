package com.nomnomsom.armstrongandgetty.media

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Drives [PlaybackService] the way Android Auto (and Google Assistant / Bluetooth car
 * decks) do: through the legacy MediaBrowserCompat + MediaControllerCompat surface that
 * Media3 bridges via MediaSessionLegacyStub. Play's Auto reviewers exercise exactly these
 * calls — browse the root, tap an item (playFromMediaId), say "play <app>"
 * (playFromSearch), press play with nothing queued (playback resumption).
 *
 * Every test also asserts the process is still alive: an unhandled exception in the
 * service kills the whole app process (and this test with it).
 */
@RunWith(AndroidJUnit4::class)
class AndroidAutoClientTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var browser: MediaBrowserCompat
    private lateinit var controller: MediaControllerCompat

    @Before
    fun connect() {
        val connected = CountDownLatch(1)
        val failure = AtomicReference<String?>()
        mainHandler.post {
            browser = MediaBrowserCompat(
                context,
                ComponentName(context, PlaybackService::class.java),
                object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() {
                        controller = MediaControllerCompat(context, browser.sessionToken)
                        connected.countDown()
                    }

                    override fun onConnectionFailed() {
                        failure.set("connection failed")
                        connected.countDown()
                    }

                    override fun onConnectionSuspended() {
                        failure.set("connection suspended")
                    }
                },
                null
            )
            browser.connect()
        }
        assertTrue("browser did not connect", connected.await(20, TimeUnit.SECONDS))
        assertEquals(null, failure.get())
    }

    @After
    fun disconnect() {
        runOnMain {
            try {
                controller.transportControls.stop()
            } catch (_: Exception) {
            }
            browser.disconnect()
        }
    }

    // ---- browse -------------------------------------------------------------------

    @Test
    fun rootIsBrowsableAndHasContent() {
        val root = browser.root
        assertNotNull(root)
        val children = loadChildren(root)
        assertTrue("Android Auto root must offer browsable content, got none", children.isNotEmpty())
        // Walk one level down: tabs → episodes. Every leaf must be playable or browsable.
        for (tab in children) {
            val leaves = if (tab.isBrowsable) loadChildren(tab.mediaId!!) else listOf(tab)
            assertTrue("tab ${tab.mediaId} is empty", leaves.isNotEmpty())
            for (leaf in leaves) {
                assertTrue(
                    "item ${leaf.mediaId} is neither playable nor browsable",
                    leaf.isPlayable || leaf.isBrowsable
                )
                assertNotNull("item ${leaf.mediaId} has no title", leaf.description.title)
            }
        }
    }

    @Test
    fun getItemReturnsBrowsableAndPlayableItems() {
        val root = browser.root
        val tab = loadChildren(root).first()
        val fetched = getItem(tab.mediaId!!)
        assertNotNull("getItem(${tab.mediaId}) returned null", fetched)
        val leaf = (if (tab.isBrowsable) loadChildren(tab.mediaId!!) else listOf(tab)).first()
        assertNotNull("getItem(${leaf.mediaId}) returned null", getItem(leaf.mediaId!!))
    }

    // ---- play requests --------------------------------------------------------------

    @Test
    fun playFromSearchStartsPlayback() {
        runOnMain { controller.transportControls.playFromSearch("Armstrong and Getty", Bundle()) }
        awaitPlaybackStarted("playFromSearch(\"Armstrong and Getty\")")
    }

    @Test
    fun playFromSearchWithUnknownQueryStillPlaysSomething() {
        runOnMain { controller.transportControls.playFromSearch("some random show that does not exist", null) }
        awaitPlaybackStarted("playFromSearch(unknown query)")
    }

    @Test
    fun playFromEmptySearchResumes() {
        // Assistant sends an empty query for a generic "play" — treated as resumption.
        runOnMain { controller.transportControls.playFromSearch("", null) }
        awaitPlaybackStarted("playFromSearch(\"\")")
    }

    @Test
    fun playFromMediaIdPlaysBrowsedItem() {
        val leaf = firstPlayableLeaf()
        runOnMain { controller.transportControls.playFromMediaId(leaf.mediaId, null) }
        awaitPlaybackStarted("playFromMediaId(${leaf.mediaId})")
        val nowPlaying = controller.metadata
        assertNotNull("session metadata missing after play", nowPlaying)
    }

    @Test
    fun playFromUnknownMediaIdDoesNotCrash() {
        runOnMain { controller.transportControls.playFromMediaId("definitely-not-a-real-id", null) }
        Thread.sleep(3000)
        assertProcessAlive()
    }

    @Test
    fun playFromUriDoesNotCrash() {
        runOnMain { controller.transportControls.playFromUri(android.net.Uri.parse("content://nothing/here"), null) }
        Thread.sleep(3000)
        assertProcessAlive()
    }

    @Test
    fun prepareThenPlayFromEmptyQueueResumesLastEpisode() {
        runOnMain { controller.transportControls.prepare() }
        Thread.sleep(1500)
        runOnMain { controller.transportControls.play() }
        awaitPlaybackStarted("prepare()+play() on empty queue")
    }

    @Test
    fun transportControlsAndCustomActionsDoNotCrash() {
        val leaf = firstPlayableLeaf()
        runOnMain { controller.transportControls.playFromMediaId(leaf.mediaId, null) }
        awaitPlaybackStarted("playFromMediaId")

        runOnMain {
            val tc = controller.transportControls
            tc.pause()
            tc.play()
            tc.seekTo(15_000)
            tc.fastForward()
            tc.rewind()
            tc.skipToNext()
            tc.skipToPrevious()
            tc.setPlaybackSpeed(1.5f)
            tc.setPlaybackSpeed(1f)
            for (action in listOf("BACKWARD_30", "BACKWARD_10", "FORWARD_10", "FORWARD_30", "NOT_A_REAL_ACTION")) {
                tc.sendCustomAction(action, Bundle())
            }
            tc.skipToQueueItem(0)
            tc.skipToQueueItem(9999)
            tc.seekTo(-5)
        }
        Thread.sleep(3000)
        assertProcessAlive()
        assertTrue(
            "player should still be active after control storm, state=${controller.playbackState?.state}",
            controller.playbackState?.state != PlaybackStateCompat.STATE_ERROR
        )
    }

    // ---- helpers --------------------------------------------------------------------

    private fun firstPlayableLeaf(): MediaBrowserCompat.MediaItem {
        val root = browser.root
        for (tab in loadChildren(root)) {
            val leaves = if (tab.isBrowsable) loadChildren(tab.mediaId!!) else listOf(tab)
            leaves.firstOrNull { it.isPlayable }?.let { return it }
        }
        throw AssertionError("no playable item anywhere in the browse tree")
    }

    private fun loadChildren(parentId: String): List<MediaBrowserCompat.MediaItem> {
        val latch = CountDownLatch(1)
        val result = AtomicReference<List<MediaBrowserCompat.MediaItem>>(emptyList())
        val error = AtomicReference<String?>()
        runOnMain {
            browser.subscribe(parentId, object : MediaBrowserCompat.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                    result.set(children.toList())
                    latch.countDown()
                }

                override fun onError(parentId: String) {
                    error.set("onError($parentId)")
                    latch.countDown()
                }
            })
        }
        assertTrue("subscribe($parentId) timed out", latch.await(30, TimeUnit.SECONDS))
        runOnMain { browser.unsubscribe(parentId) }
        assertEquals(null, error.get())
        return result.get()
    }

    private fun getItem(mediaId: String): MediaBrowserCompat.MediaItem? {
        val latch = CountDownLatch(1)
        val result = AtomicReference<MediaBrowserCompat.MediaItem?>()
        runOnMain {
            browser.getItem(mediaId, object : MediaBrowserCompat.ItemCallback() {
                override fun onItemLoaded(item: MediaBrowserCompat.MediaItem?) {
                    result.set(item)
                    latch.countDown()
                }

                override fun onError(itemId: String) {
                    latch.countDown()
                }
            })
        }
        assertTrue("getItem($mediaId) timed out", latch.await(30, TimeUnit.SECONDS))
        return result.get()
    }

    /** Waits for the session to report playing/buffering; fails if it hits STATE_ERROR or stays idle. */
    private fun awaitPlaybackStarted(what: String) {
        val deadline = System.currentTimeMillis() + 45_000
        var lastState = -1
        while (System.currentTimeMillis() < deadline) {
            assertProcessAlive()
            val state = controller.playbackState
            lastState = state?.state ?: -1
            when (lastState) {
                PlaybackStateCompat.STATE_PLAYING,
                PlaybackStateCompat.STATE_BUFFERING -> return
                PlaybackStateCompat.STATE_ERROR ->
                    throw AssertionError("$what: session entered STATE_ERROR: ${state?.errorMessage}")
            }
            Thread.sleep(250)
        }
        throw AssertionError("$what: playback never started (last state=$lastState)")
    }

    private fun assertProcessAlive() {
        // If PlaybackService threw on the main thread, the process (and this test) is gone.
        // Reaching here means it survived; assert the session is still reachable.
        assertFalse("browser lost its connection to PlaybackService", !browser.isConnected)
    }

    private fun runOnMain(block: () -> Unit) {
        val latch = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        mainHandler.post {
            try {
                block()
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        assertTrue("main-thread block timed out", latch.await(20, TimeUnit.SECONDS))
        error.get()?.let { throw it }
    }
}
