# Type Consolidation Assessment

Scope: 25 Kotlin source files under `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/`.

## Full Type Inventory

### `data/model/` — domain / persistence types
| Type | Kind | File | Notes |
|---|---|---|---|
| `PodcastDay` | `data class` (Room `@Entity`) | `data/model/PodcastDay.kt` | Primary DB entity, keyed by date string. |
| `Segment` | `data class` | `data/model/Segment.kt` | Serialized to JSON inside `PodcastDay.segmentsJson`. |
| `RssItem` | `data class` | `data/model/RssItem.kt` | Raw parsed RSS feed item (network DTO). |
| `XFeedItem` | `data class` | `data/model/XFeedItem.kt` | Parsed X-feed RSS item; used both as DTO and UI state. |
| `DownloadState` | `enum class` | `data/model/DownloadState.kt` | Stored as string in DB; used across all layers. |

### `data/local/`
| Type | Kind | File |
|---|---|---|
| `PodcastDatabase` | `abstract class : RoomDatabase` | `data/local/PodcastDatabase.kt` |
| `PodcastDayDao` | `interface` (Room `@Dao`) | `data/local/PodcastDayDao.kt` |

### `data/remote/`
| Type | Kind | File | Notes |
|---|---|---|---|
| `DownloadResult` | `data class` | `data/remote/AudioDownloader.kt` | Internal to `AudioDownloader`; bundles paths + durations. |
| `DownloadProgressCallback` | `typealias` | `data/remote/AudioDownloader.kt` | `(segIdx, segCount, bytesDL, totalBytes) -> Unit`. |
| `AudioDownloader` | `class` (`@Singleton`) | `data/remote/AudioDownloader.kt` | |
| `RssFeedParser` | `class` (`@Singleton`) | `data/remote/RssFeedParser.kt` | |
| `XFeedParser` | `class` (`@Singleton`) | `data/remote/XFeedParser.kt` | |

### `data/repository/`
| Type | Kind | File |
|---|---|---|
| `PodcastRepository` | `class` (`@Singleton`) | `data/repository/PodcastRepository.kt` |

### `di/`
| Type | Kind | File |
|---|---|---|
| `AppModule` | `object` (Hilt `@Module`) | `di/AppModule.kt` |

### `media/`
| Type | Kind | File | Notes |
|---|---|---|---|
| `PlaybackState` | `data class` | `media/PlaybackController.kt` | Emitted by controller, consumed by VM + screens. |
| `PlaybackController` | `class` (`@Singleton`) | `media/PlaybackController.kt` | |
| `CastOptionsProvider` | `class : OptionsProvider` | `media/CastOptionsProvider.kt` | |
| `PlaybackService` | `class : MediaLibraryService` | `media/PlaybackService.kt` | |
| `MediaLibraryCallback` | private inner `class` | `media/PlaybackService.kt` | |

### `ui/screens/episodelist/`
| Type | Kind | File | Notes |
|---|---|---|---|
| `EpisodeListUiState` | `data class` | `ui/screens/episodelist/EpisodeListViewModel.kt` | Feature-local UI state. |
| `DownloadProgress` | `data class` | `ui/screens/episodelist/EpisodeListViewModel.kt` | Exposed up through UI state; duplicates callback shape. |
| `EpisodeListViewModel` | `class` (`@HiltViewModel`) | `ui/screens/episodelist/EpisodeListViewModel.kt` | |

### `ui/screens/xfeed/`
| Type | Kind | File |
|---|---|---|
| `XFeedUiState` | `data class` | `ui/screens/xfeed/XFeedViewModel.kt` |
| `XFeedViewModel` | `class` (`@HiltViewModel`) | `ui/screens/xfeed/XFeedViewModel.kt` |

### `ui/theme/`, `util/`, top-level
| Type | Kind | File |
|---|---|---|
| `AGPodcastTheme` | `@Composable` fn | `ui/theme/Theme.kt` |
| (brand `Color` `val`s) | top-level vals | `ui/theme/Theme.kt` |
| `formatDuration`, `formatShortDuration` | extension `fun`s on `Long` | `util/TimeUtils.kt` |
| `AGPodcastApp` | `class : Application` | `AGPodcastApp.kt` |
| `MainActivity` | `class : FragmentActivity` | `MainActivity.kt` |
| `BottomTab` (+ `Podcast`, `XFeed`) | private `sealed class` + `data object`s | `MainActivity.kt` |

### `work/`
| Type | Kind | File |
|---|---|---|
| `NewEpisodeCheckWorker` | `class : CoroutineWorker` | `work/NewEpisodeCheckWorker.kt` |

---

## Consolidation Recommendations

### HIGH confidence

1. **Unify `DownloadProgress` (UI) with `DownloadProgressCallback` (data)**
   - `DownloadProgressCallback` in `AudioDownloader.kt` is `(Int, Int, Long, Long) -> Unit`.
   - `DownloadProgress` in `EpisodeListViewModel.kt` is a `data class` with the same four fields.
   - The ViewModel's only job at that seam is to rebuild the struct from the four positional args.
   - **Move `DownloadProgress` → `data/model/DownloadProgress.kt`**, change the callback typealias to `(DownloadProgress) -> Unit`, and have `AudioDownloader` emit the struct directly. Kills a redundant shape and flattens the seam. No Room/Gson/Hilt coupling involved.

### MED confidence

2. **`PlaybackState` could move to `data/model/`** — it's emitted by `media/PlaybackController` and consumed by `EpisodeListViewModel` and `EpisodeListScreen`. But keeping it in `media/` is defensible: it's a direct product of Media3 state and the `media/` package owns it. No obvious cross-feature reuse today. **Skipped.**

3. **`XFeedItem` double-duty** — serves as both network DTO and UI item. Today that's fine because X-feed items are never persisted. If a DB cache is ever added, split DTO from entity. **Skipped — not needed yet.**

### LOW confidence / explicitly do not merge

4. **`RssItem` vs `Segment`** — overlap on `title`, `description`, `pubDate`, `audioUrl`. **Keep separate.** `RssItem` is the raw RSS parse output (transport). `Segment` is the refined domain object with `hour` label and measured `actualDurationMs`. Repository refines RSS → Segment; merging would corrupt that boundary.

5. **`PodcastDay` vs `Segment`** — related by composition (`PodcastDay.segmentsJson` is a JSON array of `Segment`). **Keep separate.** Room entity + JSON-embedded substructure is intentional.

6. **`EpisodeListUiState`, `XFeedUiState`** — surface-level similarity (both have `error: String?` etc.) but each feature's state is specific (lists of different item types, loading/refreshing booleans differ in intent). **Keep per-feature.** A generic `UiState<T>` wrapper would be premature abstraction in a 2-screen app.

7. **`DownloadState` enum** — already in `data/model/`; used everywhere by design. **No change.**

8. **`BottomTab` sealed class in `MainActivity.kt`** — private, scoped to nav shell. **Leave.**

9. **`DownloadResult` in `AudioDownloader.kt`** — only used internally to that class. **Leave.**

10. **`MediaLibraryCallback` inner class in `PlaybackService.kt`** — Media3 session plumbing. **Leave.**

---

## Implementation plan (HIGH only)

1. Create `data/model/DownloadProgress.kt`.
2. Redefine `DownloadProgressCallback` as `(DownloadProgress) -> Unit` in `AudioDownloader.kt`.
3. `AudioDownloader.downloadSegments` builds a `DownloadProgress` and invokes the callback with it.
4. Remove the inline `DownloadProgress` in `EpisodeListViewModel.kt`; import from `data/model`.
5. Simplify the call site in `EpisodeListViewModel.downloadDay` to stash the struct directly.
6. Update `EpisodeListScreen` import path.
7. Verify Hilt/Room/Gson untouched.
