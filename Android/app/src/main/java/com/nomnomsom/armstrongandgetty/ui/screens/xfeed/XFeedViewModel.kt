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
    @param:ApplicationContext private val context: Context,
    private val xFeedParser: XFeedParser
) : ViewModel() {

    companion object {
        private const val TAG = "XFeedVM"
        private const val POLL_INTERVAL_BACKGROUND_MS = 5 * 60 * 1000L
        private const val POLL_INTERVAL_ACTIVE_MS = 1 * 60 * 1000L
        private const val PREFS_NAME = "xfeed_prefs"
        private const val KEY_LAST_SEEN_TIMESTAMP = "last_seen_timestamp_ms"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(XFeedUiState())
    val uiState: StateFlow<XFeedUiState> = _uiState.asStateFlow()

    /** Drives the badge on the bottom nav tab. */
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /** Drives the "N new posts" banner shown while the user is on the X tab. */
    private val _newPostsWhileViewing = MutableStateFlow(0)
    val newPostsWhileViewing: StateFlow<Int> = _newPostsWhileViewing.asStateFlow()

    /**
     * Timestamp of the newest post seen the last time the user visited the X tab.
     * Persisted so unread counts survive restarts. 0 means never visited.
     */
    private var lastSeenTimestampMs: Long = prefs.getLong(KEY_LAST_SEEN_TIMESTAMP, 0L)

    /** Baseline for the in-tab banner — reset when the tab opens or the banner is dismissed. */
    private var viewingBaselineTimestampMs: Long = 0L

    private var isTabActive: Boolean = false
    private var pollingJob: Job? = null

    init {
        loadFeed(isInitial = true)
        startPolling(POLL_INTERVAL_BACKGROUND_MS)
    }

    fun refresh() {
        loadFeed(isInitial = false)
    }

    fun onTabVisible() {
        if (isTabActive) return
        isTabActive = true

        markAllSeen()

        viewingBaselineTimestampMs = _uiState.value.items.firstOrNull()?.timestampMs ?: 0L
        _newPostsWhileViewing.value = 0

        startPolling(POLL_INTERVAL_ACTIVE_MS)

        Log.d(TAG, "Tab visible — polling every ${POLL_INTERVAL_ACTIVE_MS / 1000}s")
    }

    fun onTabHidden() {
        if (!isTabActive) return
        isTabActive = false

        markAllSeen()
        _newPostsWhileViewing.value = 0

        startPolling(POLL_INTERVAL_BACKGROUND_MS)

        Log.d(TAG, "Tab hidden — polling every ${POLL_INTERVAL_BACKGROUND_MS / 1000}s")
    }

    fun dismissNewPostsBanner() {
        viewingBaselineTimestampMs = _uiState.value.items.firstOrNull()?.timestampMs ?: 0L
        _newPostsWhileViewing.value = 0
    }

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
            // First-ever visit: treat everything as unread.
            _unreadCount.value = items.size
        } else {
            _unreadCount.value = items.count { it.timestampMs > lastSeenTimestampMs }
        }
    }

    /** Background poll — updates counts without toggling loading state. */
    private suspend fun pollFeed() {
        val result = xFeedParser.fetchFeed()
        if (result.isFailure) return

        val freshItems = result.getOrThrow()
        if (freshItems.isEmpty()) return

        _uiState.value = _uiState.value.copy(items = freshItems)

        if (!isTabActive) {
            updateUnreadCount(freshItems)
        }

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

                updateUnreadCount(items)

                // Manual refresh while on the tab resets both the banner and the unread badge.
                if (!isInitial && isTabActive) {
                    viewingBaselineTimestampMs = items.firstOrNull()?.timestampMs ?: 0L
                    _newPostsWhileViewing.value = 0
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
