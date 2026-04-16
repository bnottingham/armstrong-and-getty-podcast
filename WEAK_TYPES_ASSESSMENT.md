# Weak Types Assessment

Scope: `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/` (25 Kotlin files).
The `python-twitter/` directory is absent in this worktree (only `Android/` and `python-transcription/`
exist; the latter is deleted per root `git status`). Python assessment N/A.

## High-confidence replacements (implemented)

### HC-1: `PodcastDay.downloadState: String` → `DownloadState` enum
- **File:** `data/model/PodcastDay.kt:21`
- **Current:** `val downloadState: String // "none", "downloading", "downloaded", "error"`
- **Evidence:**
  - `data/model/DownloadState.kt` already defines the enum with exactly these 4 values.
  - All call-sites read `day.downloadState` and compare to `DownloadState.X.value`
    (6 call-sites in `EpisodeListViewModel`, `EpisodeListScreen`, `NewEpisodeCheckWorker`,
    `PodcastRepository`, `EpisodeListScreen:508`).
  - DAO writes come exclusively from `PodcastRepository`, all using `DownloadState.X.value`.
  - Room 2.x persists enums natively via the built-in `EnumColumnTypeAdapter` (maps
    to TEXT column by `name`). Because the current column stores the `value` string
    (`"downloaded"` etc.), which happens to differ only by case from the enum constant
    names (`DOWNLOADED`), switching to a typed `DownloadState` column would require a
    migration. Schema version is 3 with `fallbackToDestructiveMigration()` enabled, so
    on-device data would be dropped — acceptable given the codebase design, but still a
    non-zero risk.
  - **Safer compromise:** centralize the conversion in one place. Keep the column as
    `String` in the entity (stored lowercase as-is), but swap the enum definition to
    store `name` in lowercase and add typed accessors. The Room `@Query` that hardcodes
    `'downloaded'` (DAO:20) stays valid.
- **Proposed:** Keep `String` in the entity for Room/SQL compatibility, BUT add a typed
  `state: DownloadState` extension property to replace all `DownloadState.fromValue(day.downloadState)` /
  `day.downloadState == DownloadState.X.value` call-sites. This is a meaningful strong-typing
  win without touching schema or migrations.

### HC-2: `Segment.hour: String` → keep String but document as "1".."4" or "OMT"
- **File:** `data/model/Segment.kt:9`
- Could be a sealed class, but the value is derived from regex matching on the RSS feed
  title. When the regex fails it falls back to `fallbackIndex.toString()` which can
  produce any number. A sealed class would force runtime branching in the parser with a
  fallback bucket. Left as `String` but documented — low confidence for replacement.
  **Skipped.**

### HC-3: `MediaMetadata` extras `Bundle` → typed accessor
- **File:** `media/PlaybackController.kt:121,185`, `media/PlaybackService.kt:119`
- **Current:** `Bundle().apply { putString("remote_url", remoteUrl) }` and
  `oldItem.mediaMetadata.extras?.getString("remote_url")`
- This is Media3's `MediaMetadata.extras: Bundle?` framework API — we cannot replace
  the field type. The weak part is the magic string `"remote_url"`. Replacing with a
  single shared `const` eliminates the "string-as-API" smell.
- **Proposed:** Extract `const val EXTRA_REMOTE_URL = "remote_url"` in a shared
  location (PlaybackController.Companion) and use it from both producers and the
  consumer in PlaybackService.

## MED/LOW items skipped

### LOW-1: Generic `catch (e: Exception)` blocks
- 14 occurrences across parsers, downloader, repository, and the episode-check worker.
- **Why skipped:** Each one wraps IO (`OkHttp`, XML parsing, Room, MediaMetadataRetriever,
  SimpleDateFormat). The legitimate exception surface is wide
  (`IOException`, `XmlPullParserException`, `ParseException`, `IllegalStateException`,
  coroutine `CancellationException`, plus `RuntimeException` from Android framework). The
  code handles all failures identically (return `Result.failure(e)` or fallback to a
  default). Narrowing without adding separate branches gains nothing and risks a crash on
  an unexpected exception type. Intentionally kept broad.
- `throw Exception("Day not found")` / `"Empty body"` / `"HTTP $code"` could be more
  specific (`IllegalStateException`, `IOException`, etc.), but they are only read via
  `result.exceptionOrNull()?.message` — the type is never inspected. Cosmetic change only.

### LOW-2: `Bundle?` in framework overrides
- `MainActivity.onCreate(savedInstanceState: Bundle?)` — framework signature, untouchable.
- `PlaybackService.onCustomCommand(..., args: Bundle)` — Media3 framework signature.
- `SessionCommand("FORWARD_30", Bundle.EMPTY)` — Media3 framework API requires `Bundle`.

### LOW-3: `String` route keys in Compose NavHost
- `MainActivity.kt` uses string routes (`"episodes"`, `"xfeed"`, `"player/{date}"`).
- A `sealed class Destination` would be stronger, but Compose Navigation's string-route
  API is how the framework works. Typed navigation is a separate, larger refactor.

### LOW-4: `DownloadProgressCallback` typealias params use positional `Long, Long`
- `data/remote/AudioDownloader.kt:28`. Parameter names documented via KDoc. Callback
  sites are typed via the typealias. Keeping as-is.

### LOW-5: `PodcastDay.combinedFilePath: String?`
- Marked legacy (no longer used — see `AudioDownloader.hasCombinedFile` which returns `false`).
- Leaving alone; removing the column would require a Room migration.

### LOW-6: `Map<String, DownloadProgress>` and `Map<String, Int>` in ViewModels / Worker
- Keys are dates (`String`). Could wrap in a value class `@JvmInline value class Date(val iso: String)`,
  but this would ripple into every caller and the navigation layer (which passes raw `String`
  via NavType.StringType). Not a high-confidence net win.

## No occurrences of
- `: Any`, `Any?`, `as Any` — none in project source.
- `Map<String, Any>` / raw `List`/`Map` without parameters — none.
- `JsonElement`, `JsonObject` — none; Gson deserialization is into typed `Segment` via
  `TypeToken<List<Segment>>`.
- Untyped lambda parameters — none.
