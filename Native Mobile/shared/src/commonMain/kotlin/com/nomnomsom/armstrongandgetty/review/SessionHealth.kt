package com.nomnomsom.armstrongandgetty.review

import com.nomnomsom.armstrongandgetty.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process flag: did this session hit a user-visible failure (feed refresh, day download,
 * segment retry)? If so, no rating prompt until the process restarts.
 */
class SessionHealth {
    private val _hadFailure = MutableStateFlow(false)
    val hadFailure: StateFlow<Boolean> = _hadFailure.asStateFlow()

    fun reportFailure(kind: String) {
        if (!_hadFailure.value) AppLog.d("InAppReview", "session failure ($kind): prompt suppressed this process")
        _hadFailure.value = true
    }
}
