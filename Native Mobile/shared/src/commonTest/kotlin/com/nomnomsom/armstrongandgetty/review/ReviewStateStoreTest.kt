package com.nomnomsom.armstrongandgetty.review

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReviewStateStoreTest {

    private val full = ReviewState(
        firstSeenMs = 1_759_000_000_000,
        listenDayKey = "2026-10-13",
        listenDayMs = 123_456,
        qualifyingDays = listOf("2026-10-09", "2026-10-11", "2026-10-13"),
        lastRequestMs = 1_760_000_000_000,
        requestCount = 2,
        lastRequestVersion = "2.1.0",
        lastCrashMs = 1_759_500_000_000,
        lastUnavailableMs = 1_759_700_000_000,
    )

    @Test
    fun roundTripsAFullyPopulatedState() {
        val store = ReviewStateStore(MapSettings())
        store.save(full)
        assertEquals(full, store.load())
    }

    /** Every ReviewState field is persisted (a field without a key would silently reset). */
    @Test
    fun everyKeyIsWrittenForAFullState() {
        val settings = MapSettings()
        ReviewStateStore(settings).save(full)
        assertEquals(ReviewKeys.all.toSet(), settings.keys)
    }

    @Test
    fun emptyStoreLoadsDefaults() {
        assertEquals(ReviewState(), ReviewStateStore(MapSettings()).load())
    }

    @Test
    fun loadIsTolerantOfMalformedDays() {
        val settings = MapSettings()
        settings.putString(ReviewKeys.QUALIFYING_DAYS, "2026-10-01,garbage,,2026-10-01,2026-09-30")
        settings.putString(ReviewKeys.LISTEN_DAY, "not-a-day")
        val loaded = ReviewStateStore(settings).load()
        assertEquals(listOf("2026-09-30", "2026-10-01"), loaded.qualifyingDays)
        assertEquals(null, loaded.listenDayKey)
    }

    @Test
    fun loadIsTolerantOfWrongTypes() {
        val settings = MapSettings()
        settings.putString(ReviewKeys.FIRST_SEEN_MS, "yesterday")
        settings.putString(ReviewKeys.REQUEST_COUNT, "two")
        val loaded = ReviewStateStore(settings).load()
        assertEquals(null, loaded.firstSeenMs)
        assertEquals(0, loaded.requestCount)
    }

    @Test
    fun loadCapsStoredDays() {
        val settings = MapSettings()
        settings.putString(ReviewKeys.QUALIFYING_DAYS, (1..20).joinToString(",") { "2026-10-${it.toString().padStart(2, '0')}" })
        val loaded = ReviewStateStore(settings).load()
        assertEquals(ReviewPolicy.MAX_STORED_DAYS, loaded.qualifyingDays.size)
        assertEquals("2026-10-05", loaded.qualifyingDays.first())
        assertEquals("2026-10-20", loaded.qualifyingDays.last())
    }

    @Test
    fun nullFieldsRemoveTheirKeys() {
        val settings = MapSettings()
        val store = ReviewStateStore(settings)
        store.save(full)
        store.save(ReviewState(firstSeenMs = full.firstSeenMs))
        assertFalse(settings.hasKey(ReviewKeys.LISTEN_DAY))
        assertFalse(settings.hasKey(ReviewKeys.QUALIFYING_DAYS))
        assertFalse(settings.hasKey(ReviewKeys.LAST_REQUEST_MS))
        assertFalse(settings.hasKey(ReviewKeys.LAST_REQUEST_VERSION))
        assertFalse(settings.hasKey(ReviewKeys.LAST_CRASH_MS))
        assertFalse(settings.hasKey(ReviewKeys.LAST_UNAVAILABLE_MS))
        assertEquals(ReviewState(firstSeenMs = full.firstSeenMs), store.load())
    }

    /** Upgrade-in-place: every review key is new and can't collide with legacy storage. */
    @Test
    fun keysAreNewAndPrefixed() {
        val legacy = setOf("dates", "user_deletions_dates", "user_deletions", "ag_podcast_db", "podcasts")
        assertEquals(ReviewKeys.all.size, ReviewKeys.all.toSet().size, "duplicate key")
        for (key in ReviewKeys.all) {
            assertTrue(key.startsWith("in_app_review."), key)
            assertFalse(key in legacy, key)
        }
        assertEquals("in_app_review", ReviewKeys.ANDROID_PREFS_FILE)
        assertNotEquals("user_deletions", ReviewKeys.ANDROID_PREFS_FILE)
    }

    /** Saving review state never touches the legacy deletion keys that share iOS defaults. */
    @Test
    fun saveTouchesOnlyReviewKeys() {
        val settings = MapSettings()
        settings.putString("user_deletions_dates", "legacy-ios")
        settings.putString("dates", "legacy-android")
        val legacyKeys = setOf("user_deletions_dates", "dates")

        val store = ReviewStateStore(settings)
        store.save(full)
        store.save(ReviewState())

        assertEquals("legacy-ios", settings.getStringOrNull("user_deletions_dates"))
        assertEquals("legacy-android", settings.getStringOrNull("dates"))
        store.save(full)
        assertTrue(ReviewKeys.all.containsAll(settings.keys - legacyKeys), "unexpected keys: ${settings.keys}")
    }
}
