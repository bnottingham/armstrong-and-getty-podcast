package com.nomnomsom.armstrongandgetty.util

actual object AppLog {
    actual fun d(tag: String, message: String) {
        println("D/$tag: $message")
    }

    actual fun i(tag: String, message: String) {
        println("I/$tag: $message")
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        println("W/$tag: $message${throwable?.let { " — $it" } ?: ""}")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        println("E/$tag: $message${throwable?.let { " — $it" } ?: ""}")
    }
}
