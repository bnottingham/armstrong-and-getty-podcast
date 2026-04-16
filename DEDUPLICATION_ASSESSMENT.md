# Deduplication Assessment — Armstrong & Getty Podcast (Android)

Scope: all 25 Kotlin files under `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/`.
The Python script referenced in the task (`python-twitter/scrape_x_list.py`) is not present in this worktree.

## Findings

### HIGH confidence

**H1. Segment display-title formatting (5 callsites)**
The expression `if (seg.hour == "OMT") "OMT: ${seg.title}" else "Hr ${seg.hour}: ${seg.title}"`
appears verbatim in:
- `EpisodeListViewModel.kt:139` (loadDay)
- `EpisodeListViewModel.kt:170` (playDay)
- `EpisodeListViewModel.kt:240` (checkForNewSegments)
- `EpisodeListScreen.kt:136` (now-playing live segment label)
- `EpisodeListScreen.kt:149` (now-playing saved-progress segment label)

Drift risk is real (you have to touch 5 places to change a label convention).
Cheap to unify as a `Segment.displayLabel` extension on the model.

**H2. Effective segment duration (5 callsites)**
The expression `seg.actualDurationMs.takeIf { it > 0 } ?: seg.durationMs` (or its
`if (a > 0) a else b` equivalent) appears in:
- `EpisodeListViewModel.kt:143, 174, 244`
- `PodcastRepository.kt:139, 192`
- `EpisodeListScreen.kt:143` uses the same logic inline

Same drift risk — unify as a `Segment.effectiveDurationMs` property.

**H3. `loadDay` / `playDay` are near-duplicates**
`EpisodeListViewModel.kt:126-156` and `158-192` share ~90% of body: parse segments,
zip with file paths, filter for existing files, unzip, build titles / urls / durations,
then call `playbackController.playPlaylist`. The only substantive differences:
- `loadDay` passes `autoPlay = false` and uses saved position unconditionally.
- `playDay` defaults autoPlay=true, and if isListened resets progress + starts at 0.

Unify as a single private `preparePlaylist(day, startPositionMs, autoPlay)` helper,
leaving the two public entry points as thin wrappers. This keeps the two intents
named/callable but removes 35 lines of copy-paste.

**H4. Download-complete segment update (verbatim block)**
`PodcastRepository.kt:132-150` and `PodcastRepository.kt:185-203` have identical logic:
map actual durations onto segments, gson.toJson, sum `actualDurationMs ?: durationMs`,
call `updateDownloadComplete` then `updateSegments`. The only difference is the
source variable names (segments/updatedSegments, day/updatedDay).

Extract a private `persistDownloadedSegments(date, baseSegments, dlResult, summary, isComplete)`.

**H5. MediaItem/MediaMetadata builder duplication**
`PlaybackController.kt:117-138` (playPlaylist) and `181-202` (appendToPlaylist) build
MediaItems with identical metadata structure (artist="Armstrong & Getty",
albumTitle="Armstrong & Getty On Demand", extras bundle with remote_url, file:// URI,
AUDIO_MPEG MIME). Only differences: the trackNumber offset, segment-title fallback.

Extract a private `buildMediaItem(dayTitle, segTitle, filePath, remoteUrl, trackNumber)`.

**H6. Cumulative prefix-sum for segmentStartMs**
`PlaybackController.kt:108-114` and `171-177` both do:
```
buildList { var c = 0L; for (d in durations) { add(c); c += d } }
```
Exact same 7-line loop. Trivial to pull into one private function.

**H7. Virtual-position computation duplicated inside same class**
`PlaybackController.kt:264-272` (`computeVirtualPosition`) and lines 307-314
(inside `updateState`) compute the same thing from the same inputs. `updateState`
should just call `computeVirtualPosition()`.

**H8. Shared User-Agent / HTTP-request setup (3 callsites)**
`RssFeedParser.kt:22-29`, `XFeedParser.kt:27-30`, `AudioDownloader.kt:94-97` all
build `Request.Builder().url(…).header("User-Agent", "ArmstrongGettyPodcast/1.0").build()`.
The UA string literal is repeated. Small risk — one UA change needs three touches.
Extract a single helper (either an extension on `Request.Builder` or a `HttpRequests` object).

**H9. RFC-822 pubDate parsing (3 slightly different implementations)**
- `PodcastRepository.kt:245-267` (`groupItemsByDate`) — strips " GMT"/" +0000"/" -0000", parses.
- `PodcastRepository.kt:269-282` (`parsePubDate`) — same strip+parse, returns ms.
- `XFeedParser.kt:112-128` (`parsePubDate`) — tries two SimpleDateFormats instead of stripping.

All three parse RFC 822 pubDates to epoch ms (or a yyyy-MM-dd date). There's clear
repetition within `PodcastRepository` (the two helpers overlap heavily) and the
XFeedParser copy solves the same problem. Bundle into a shared util: pull the two
PodcastRepository helpers into a single source of truth; leave `XFeedParser`'s
alternative format list in a util too.

### MED confidence

**M1. "A&G" logo composable**
Appears four times with varying sizes (52dp header, 42dp now-playing, 42dp placeholder,
220dp player). Shape, background color, font weight, letter spacing all vary.
A parameterized Composable would need 4-5 knobs and wouldn't clearly beat 4 explicit blocks.
Skip.

**M2. `SectionHeader` vs PlayerScreen "SEGMENTS" label**
Similar intent, different styling (fontSize 11sp vs 12sp, different colors and paddings).
Not worth unifying.

**M3. Gold-alpha-background IconButton pattern**
PlayerScreen TransportControls has 4 nearly-identical IconButton blocks (skip -30/-10/+10/+30);
the NowPlayingCard has similar skip buttons. Tempting to abstract, but a parameterized
`CircleIconButton(icon, size, onClick)` would still need all those knobs, and the
resulting callsites would be noisier than the inline code. Skip.

**M4. XFeedViewModel "set loading state + fetch + handle success/error" pattern**
`loadFeed` (full refresh) and `pollFeed` (silent) differ meaningfully (spinners,
unread-count reset, baseline reset). Merging with a boolean flag would be an anti-pattern.
Skip.

**M5. Pull-to-refresh + loading-state boilerplate in both ViewModels**
`EpisodeListViewModel` uses `isRefreshing`; `XFeedViewModel` uses `isLoading` +
`isRefreshing`. Their error handling is entangled with domain-specific follow-up
logic. An abstraction over both would leak. Skip.

### LOW confidence

**L1. `try { … } catch (_: Exception) { 0L }` idiom for date parsers**
Appears in a few places. It's a 3-line pattern in a language with tiny
`runCatching`-style alternatives; not worth a helper.

**L2. HTTP-logging interceptor and client defaults in AppModule**
Single-use. Nothing to dedupe.

## Recommendations (what to change)

Implement H1–H9. Skip M and L.

## What NOT to dedupe (and why)

- **Composable icon buttons and logo badges.** Each differs meaningfully in size,
  shape, and padding; a 4-argument "generic" button makes the callsites less readable.
- **ViewModel feed-loading patterns.** The two screens have different loading semantics
  (pull-to-refresh vs background-poll + visible-poll). An abstraction would either
  leak the differences via a bag of booleans or force the call sites to branch anyway.
- **DAO methods.** Each is a distinct SQL query; Room intentionally surfaces them
  individually. `@Update` and the partial-column `@Query` updates serve different
  change sets; collapsing them would require constructing full `PodcastDay` objects
  for targeted writes.
- **Separate enum `DownloadState` values vs string columns.** Already minimal.
- **Error/loading UI blocks in XFeedScreen.** Different copy per state; already thin.
- **The two RSS parsers (RssFeedParser / XFeedParser).** They parse different feeds
  with different schemas. Only the HTTP and date-parsing fragments are shared; the
  XML traversal is appropriately specialized. I'm only unifying the shared fragments,
  not the parsers themselves.
