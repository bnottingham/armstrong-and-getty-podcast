package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nomnomsom.armstrongandgetty.util.nowMs as wallClockNowMs

/**
 * In-process record of the last feed / download failure that hasn't been followed by a
 * success. [ReviewPolicy] holds the prompt for [ReviewPolicy.FAILURE_QUIET_MS] after it, and
 * any later success clears it: an offline cold start must not switch the prompt off for a
 * process that can live for days. Failures the listener can still SEE (a "Download failed"
 * row, failed segments, a refresh spinner) are gated separately and live
 * (EpisodeListViewModel.reviewBlocker).
 */
class SessionHealth(private val clock: () -> Long = { wallClockNowMs() }) {
    private val _lastFailureMs = MutableStateFlow<Long?>(null)
    val lastFailureMs: StateFlow<Long?> = _lastFailureMs.asStateFlow()

    fun reportFailure(kind: String) {
        AppLog.d(TAG, "session failure ($kind): prompt held")
        _lastFailureMs.value = clock()
    }

    fun reportSuccess(kind: String) {
        if (_lastFailureMs.value != null) AppLog.d(TAG, "session recovered ($kind)")
        _lastFailureMs.value = null
    }

    private companion object {
        const val TAG = "InAppReview"
    }
}
