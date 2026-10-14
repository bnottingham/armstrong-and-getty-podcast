package com.nomnomsom.armstrongandgetty.review

/**
 * Play has no write-review deep link, so this opens the listing. `market://` first (Play
 * Store app); if nothing handles it (no Play), UriHandler throws and the https URL opens.
 */
actual object StoreLinks {
    actual val rateAppUrls: List<String> = listOf(
        "market://details?id=com.nomnomsom.armstrongandgetty",
        "https://play.google.com/store/apps/details?id=com.nomnomsom.armstrongandgetty",
    )
    actual val storeName: String = "Google Play"
}
