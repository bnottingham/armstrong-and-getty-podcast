package com.nomnomsom.armstrongandgetty.review

/**
 * Store deep links for the About screen's "Rate the App" row. Opening these is the ONLY
 * thing a button may do; no button ever calls the in-app review API.
 */
expect object StoreLinks {
    /** Tried in order; the first one the platform can open wins. */
    val rateAppUrls: List<String>

    /** Human-readable store name for the row subtitle. */
    val storeName: String
}
