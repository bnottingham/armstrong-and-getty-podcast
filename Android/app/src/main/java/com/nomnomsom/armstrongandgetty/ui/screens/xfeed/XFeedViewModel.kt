package com.nomnomsom.armstrongandgetty.ui.screens.xfeed

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomnomsom.armstrongandgetty.data.model.XFeedItem
import com.nomnomsom.armstrongandgetty.data.remote.XFeedParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class XFeedUiState(
    val items: List<XFeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class XFeedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xFeedParser: XFeedParser
) : ViewModel() {

    companion object {
        private const val TAG = "XFeedVM"
        private const val POLL_INTERVAL_BACKGROUND_MS = 5 * 60 * 1000L // 5 minutes
        private const val POLL_INTERVAL_ACTIVE_MS = 1 * 60 * 1000L     // 1 minute
        private const val PREFS_NAME = "xfeed_prefs"
        private const val KEY_LAST_SEEN_TIMESTAMP = "last_seen_timestamp_ms"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(XFeedUiState())
    val uiState: StateFlow<XFeedUiState> = _uiState.asStateFlow()

    /**
     * Number of posts newer than the last time the user viewed the X tab.
     * Drives the badge on the bottom nav tab.
     */
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /**
     * Number of new posts that arrived while the user is actively on the X tab.
     * Drives the "N new posts" banner at the top of the feed.
     */
    private val _newPostsWhileViewing = MutableStateFlow(0)
    val newPostsWhileViewing: StateFlow<Int> = _newPostsWhileViewing.asStateFlow()

    /**
     * Timestamp of the newest post the user has "seen" (i.e. was present when
     * they last visited the X tab). Posts newer than this are "unread".
     * Persisted to SharedPreferences so it survives app restarts.
     * A value of 0 means the user has never visited the X tab.
     */
    private var lastSeenTimestampMs: Long = prefs.getLong(KEY_LAST_SEEN_TIMESTAMP, 0L)

    /**
     * Timestamp of the newest post when the user last opened the tab or dismissed the banner.
     * Posts newer than this (while on the tab) drive the in-tab banner.
     */
    private var viewingBaselineTimestampMs: Long = 0L

    /** Whether the X tab is currently the active visible tab. */
    private var isTabActive: Boolean = false

    /** The polling coroutine job — cancelled and restarted when tab state changes. */
    private var pollingJob: Job? = null

    init {
        // Initial load
        loadFeed(isInitial = true)
        // Start background polling (5 min interval)
        startPolling(POLL_INTERVAL_BACKGROUND_MS)
    }

    fun refresh() {
        loadFeed(isInitial = false)
    }

    /**
     * Called by MainActivity when the user navigates TO the X tab.
     */
    fun onTabVisible() {
        if (isTabActive) return
        isTabActive = true

        // Mark everything currently loaded as "seen" — clears the badge
        markAllSeen()

        // Set the viewing baseline so only truly new posts show the banner
        viewingBaselineTimestampMs = _uiState.value.items.firstOrNull()?.timestampMs ?: 0L
        _newPostsWhileViewing.value = 0

        // Switch to faster polling
        startPolling(POLL_INTERVAL_ACTIVE_MS)

        Log.d(TAG, "Tab visible — polling every ${POLL_INTERVAL_ACTIVE_MS / 1000}s")
    }

    /**
     * Called by MainActivity when the user navigates AWAY from the X tab.
     */
    fun onTabHidden() {
        if (!isTabActive) return
        isTabActive = false

        // Mark everything as seen on departure too
        markAllSeen()
        _newPostsWhileViewing.value = 0

        // Switch to slower polling
        startPolling(POLL_INTERVAL_BACKGROUND_MS)

        Log.d(TAG, "Tab hidden — polling every ${POLL_INTERVAL_BACKGROUND_MS / 1000}s")
    }

    /**
     * Called when the user taps the "new posts" banner — refreshes the WebView
     * and resets the banner count.
     */
    fun dismissNewPostsBanner() {
        viewingBaselineTimestampMs = _uiState.value.items.firstOrNull()?.timestampMs ?: 0L
        _newPostsWhileViewing.value = 0
    }

    // ── Private ──────────────────────────────────────────

    private fun markAllSeen() {
        val newestTimestamp = _uiState.value.items.firstOrNull()?.timestampMs ?: 0L
        if (newestTimestamp > lastSeenTimestampMs) {
            lastSeenTimestampMs = newestTimestamp
            prefs.edit().putLong(KEY_LAST_SEEN_TIMESTAMP, lastSeenTimestampMs).apply()
        }
        _unreadCount.value = 0
    }

    private fun startPolling(intervalMs: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(intervalMs)
                pollFeed()
            }
        }
    }

    private fun updateUnreadCount(items: List<XFeedItem>) {
        if (lastSeenTimestampMs == 0L) {
            // User has never visited the X tab — all posts are unread
            _unreadCount.value = items.size
        } else {
            _unreadCount.value = items.count { it.timestampMs > lastSeenTimestampMs }
        }
    }

    /**
     * Lightweight poll — fetches the RSS and updates counts without showing loading spinners.
     */
    private suspend fun pollFeed() {
        val result = xFeedParser.fetchFeed()
        if (result.isFailure) return

        val freshItems = result.getOrThrow()
        if (freshItems.isEmpty()) return

        // Update the items list
        _uiState.value = _uiState.value.copy(items = freshItems)

        // Update unread count for the badge (only when not actively on the tab)
        if (!isTabActive) {
            updateUnreadCount(freshItems)
        }

        // Update "new while viewing" count (for the in-tab banner)
        if (isTabActive && viewingBaselineTimestampMs > 0L) {
            val newWhileViewing = freshItems.count { it.timestampMs > viewingBaselineTimestampMs }
            _newPostsWhileViewing.value = newWhileViewing
        }

        Log.d(TAG, "Poll complete: ${freshItems.size} items, unread=${_unreadCount.value}, newWhileViewing=${_newPostsWhileViewing.value}")
    }

    private fun loadFeed(isInitial: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = isInitial && _uiState.value.items.isEmpty(),
                isRefreshing = !isInitial,
                error = null
            )

            val result = xFeedParser.fetchFeed()
            if (result.isSuccess) {
                val items = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    items = items,
                    isLoading = false,
                    isRefreshing = false
                )

                // Compute unread count — do NOT auto-mark as seen.
                // lastSeenTimestampMs of 0 means user has never visited the tab,
                // so all items are unread. Otherwise, count items newer than last seen.
                updateUnreadCount(items)

                // If this was a manual refresh while on the tab, reset the banner baseline
                if (!isInitial && isTabActive) {
                    viewingBaselineTimestampMs = items.firstOrNull()?.timestampMs ?: 0L
                    _newPostsWhileViewing.value = 0
                    // Also mark as seen since they're actively looking at it
                    markAllSeen()
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load feed"
                )
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}