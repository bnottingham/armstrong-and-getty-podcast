package com.nomnomsom.armstrongandgetty.util

/** Minimal multiplatform logger — android.util.Log on Android, println on iOS. */
expect object AppLog {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
