package com.nomnomsom.armstrongandgetty.util

import okhttp3.Request

private const val USER_AGENT = "ArmstrongGettyPodcast/1.0"

/**
 * Build a GET request with the app's standard User-Agent header.
 */
fun appGetRequest(url: String): Request =
    Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .build()
