# Error Handling Assessment

Scope: Android Kotlin source under `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/`.
Python scope (`python-twitter/scrape_x_list.py`) not present in this worktree.
WebView-embedded JavaScript try/catch (XFeedScreen.kt lines 434, 460) is JS, not Kotlin, and out of scope.

## 1. PlaybackService.kt:64-69

**Catches:** `Exception` from `CastContext.getSharedInstance(this)` + `CastPlayer(castContext)`.
**Action:** Swallows silently, leaves `castPlayer = null`.
**Verdict: KEEP.**
Reason: `CastContext.getSharedInstance` can genuinely fail when Play Services is missing/outdated (`DynamiteModule.LoadingException`, `IllegalStateException`). This is inside `Service.onCreate`; propagating would crash the service at startup and brick the whole app for users without Play Services. castPlayer=null is the correct degraded mode — downstream code at line 93 uses `?.` so null is handled. True external-boundary catch.

## 2. AudioDownloader.kt:54-86 — `downloadSegments`

**Catches:** `Exception`, converts to `Result.failure(e)`.
**Action:** Wraps IOException + manual `throw Exception("Download failed: HTTP ${code}")` from helper into the `Result<DownloadResult>` return type.
**Verdict: TIGHTEN.**
Reason: Function signature is `Result<DownloadResult>`, and callers (`PodcastRepository.downloadDay`, `appendNewSegments`) inspect `.isSuccess`/`.isFailure`. This is a real caller contract, not hiding. However, catching `Exception` silently swallows `CancellationException`, breaking structured concurrency (download won't stop when the viewModelScope is cancelled). Rethrow `CancellationException`.

## 3. AudioDownloader.kt:122-132 — `measureDuration`

**Catches:** `Exception`, returns 0L.
**Action:** Silent error hiding; returns 0L if `MediaMetadataRetriever` fails.
**Verdict: REMOVE.**
Reason: Hides malformed-file errors. The caller path is: `downloadSegments` loop uses `measureDuration(segFile)` after a successful download. If a just-downloaded mp3 can't be probed, that's genuinely broken; the caller's `downloadSegments` already converts any exception into `Result.failure`. Let MediaMetadataRetriever's errors propagate to the outer try/catch. Keep `finally { retriever.release() }`.

## 4. XFeedParser.kt:26-42 — `fetchFeed`

**Catches:** `Exception`, returns `Result.failure(e)`.
**Action:** Same boundary-conversion pattern as AudioDownloader#2.
**Verdict: TIGHTEN.**
Reason: Same as #2 — legitimate Result<T> boundary; add CancellationException rethrow.

## 5. XFeedParser.kt:117-126 — `parsePubDate`

**Catches:** `Exception`, continues to next format.
**Action:** Tries multiple date formats.
**Verdict: TIGHTEN.**
Reason: This is the "try-parse-with-fallback" pattern — legitimate use of catch. Narrow to `java.text.ParseException`.

## 6. RssFeedParser.kt:23-40 — `fetchFeed`

**Catches:** `Exception`, returns `Result.failure(e)`.
**Verdict: TIGHTEN.**
Reason: Same as #2/#4 — boundary; add CancellationException rethrow.

## 7. RssFeedParser.kt:117-136 — `parseDuration`

**Catches:** `Exception`, returns 0L.
**Action:** Wraps a block that uses `toLongOrNull() ?: 0L` everywhere and simple index access on a `split` result whose size is matched in a `when`.
**Verdict: REMOVE.**
Reason: No statement in the try body can throw. `split`, size-check via `when`, indexed reads bounded by the size check, `toLongOrNull()` returns null instead of throwing. Dead catch.

## 8. PodcastRepository.kt:235-241 — `parseSegments`

**Catches:** `Exception`, returns `emptyList()`.
**Action:** Silent fallback on Gson parse failure.
**Verdict: REMOVE.**
Reason: The JSON was produced by this same repository via `gson.toJson(segments)` and then persisted to Room. If deserialisation fails it's a real corruption/bug, not something to hide by silently returning 0 segments (which makes the app look like the episode has no content). Let `JsonSyntaxException` propagate. Keep `?: emptyList()` for the null case (empty input string).

## 9. PodcastRepository.kt:252-263 — `groupItemsByDate` (inside `mapNotNull`)

**Catches:** `Exception`, returns `null` → drops the item.
**Action:** Guards `SimpleDateFormat.parse`.
**Verdict: TIGHTEN.**
Reason: External RSS feed could contain an oddly formatted pubDate. Silently dropping one misformed item is the reasonable degraded mode for a feed parser. But narrow to `java.text.ParseException`.

## 10. PodcastRepository.kt:273-281 — `parsePubDate`

**Catches:** `Exception`, returns 0L.
**Action:** Parses pubDate used as sort key.
**Verdict: TIGHTEN.**
Reason: Same reasoning as #9 — narrow to `ParseException`. Using 0L as sort key for malformed dates is a known degraded mode.

## 11. PodcastRepository.kt:307-313 — `formatDayTitle`

**Catches:** `Exception`, returns raw date string with prefix.
**Action:** Guards SimpleDateFormat parse of a "yyyy-MM-dd" string.
**Verdict: REMOVE.**
Reason: Input is our own `"yyyy-MM-dd"` key that we format. If the format changes, the parse failure silently shows an ugly fallback title. Remove the catch; let it crash loud if we ever break the invariant.

## 12. NewEpisodeCheckWorker.kt:46-130 — `doWork`

**Catches:** `Exception`, logs, returns `Result.retry()`.
**Verdict: TIGHTEN.**
Reason: `CoroutineWorker.doWork()` returns a `Result` — the framework boundary requires not throwing. This is framework requirement (#3 in task). KEEP the boundary, but rethrow `CancellationException` so cancellation propagates correctly.

## 13. EpisodeListScreen.kt:771-773 — `DateBadge`

**Catches:** `Exception`, returns null.
**Action:** `SimpleDateFormat("yyyy-MM-dd").parse(dateString)` — input is our DB key.
**Verdict: TIGHTEN.**
Reason: Narrow to `ParseException`. The composable already handles null via `parsed?.let`. Fine as-is otherwise.

---

## Summary of actions

| Verdict | Count | Files |
|---|---|---|
| KEEP | 1 | PlaybackService |
| TIGHTEN | 8 | AudioDownloader×1, XFeedParser×2, RssFeedParser×1, PodcastRepository×2, NewEpisodeCheckWorker×1, EpisodeListScreen×1 |
| REMOVE | 4 | AudioDownloader×1, RssFeedParser×1, PodcastRepository×2 |
