# Unused Code Assessment

Branch: `worktree-agent-a426bd50` (based on `main`)
Worktree path: `/Users/brett/Documents/projects/github/armstrong-and-getty-podcast/.claude/worktrees/agent-a426bd50`

Python scope note: this worktree does not contain `python-twitter/scrape_x_list.py`; the deleted `python-transcription/*.py` files are part of the working-tree delete listed in the main repo status. Python analysis is therefore out of scope for this worktree.

## HIGH-confidence (deleting)

| Item | Location | Rationale |
|---|---|---|
| Resource `R.string.app_name` | `res/values/strings.xml` | Manifest hardcodes `android:label`; lint confirms unused. |
| Resource `R.color.card_bg` | `res/values/colors.xml` | Not referenced from Kotlin or XML; lint confirms. |
| Resource `R.xml.network_security_config` | `res/xml/network_security_config.xml` | Manifest never references it via `android:networkSecurityConfig`. |
| DAO `insertIfNotExists` | `data/local/PodcastDayDao.kt` | No callers anywhere. |
| DAO `update` | `data/local/PodcastDayDao.kt` | No `dao.update(` callers; only `updateSegments`/`updateDownload*`/`updateListenProgress` are used. |
| DAO `getLatestDay` | `data/local/PodcastDayDao.kt` | No callers. |
| DAO `getAllDownloadedDays` | `data/local/PodcastDayDao.kt` | No callers. |
| `AudioDownloader.hasCombinedFile` | `data/remote/AudioDownloader.kt` | Marked "No longer used"; zero callers. |
| `AudioDownloader.deleteCombinedFile` | `data/remote/AudioDownloader.kt` | Legacy cleanup; zero callers. |
| `PlaybackController.resume()` | `media/PlaybackController.kt` | `togglePlayPause`/`pause` are used; `resume` is not. |
| `PlaybackState.positionInSegmentMs` | `media/PlaybackController.kt` | Written in `updateState` but never read outside. `currentSegmentIndex` and `currentPositionMs` cover what UI needs. |
| `XFeedItem.postUrl` | `data/model/XFeedItem.kt` | Only `tweetId` (iframe embed) and `timestampMs` (ordering) are read. |
| Local var `newDayDate` | `work/NewEpisodeCheckWorker.kt` | Assigned in both branches of the update loop but never read. |
| Import `dagger.hilt.android.AndroidEntryPoint` | `AGPodcastApp.kt` | File uses `@HiltAndroidApp`, not `@AndroidEntryPoint`. |
| Import `androidx.compose.animation.AnimatedVisibility` | `ui/screens/xfeed/XFeedScreen.kt` | Only call site uses the fully-qualified name. The FQN call is intentional: inside a `Box` scope, the unqualified import resolves to `ColumnScope.AnimatedVisibility` (imported transitively via a sibling import) and fails to compile. Kept the FQN call site; removed the unused top-level import. |
| Dep `libs.retrofit` + `libs.retrofit.converter.gson` | `app/build.gradle.kts` + `libs.versions.toml` | No `retrofit2` imports anywhere. OkHttp + Gson are used directly. |

## MED / LOW (keeping, with reason)

- **`PodcastDay.combinedFilePath`** (entity column): Removing it would break Room schema compat (entity is at version 3, `fallbackToDestructiveMigration` wipes data on change, but leaving a benign nullable column is safer than a forced reset). Keep.
- **`PodcastDayDao.updateDownloadComplete` `path` parameter**: Currently called with `date` as the path. Arguably stale, but touches the ignored `combinedFilePath` column — safe to leave with the column.
- **`ui-tooling`, `ui-tooling-preview`, `ui-test-manifest` (`debugImplementation`)**: No `@Preview`s exist today, but these are IDE-only tooling deps routinely kept to unblock adding previews/tests. Keeping.
- **`testInstrumentationRunner` declaration**: No `androidTest/` folder exists, but the runner declaration is scaffolding for future tests; single harmless line. Keeping.
- **`TAG` companion constants, `CHANNEL_ID`, `NOTIFICATION_ID`, `WORK_NAME`, `PREFS_NAME`, `KEY_LAST_SEEN_TIMESTAMP`**: All referenced within the defining file (`Log.d(TAG,…)`, etc). Keeping.
- **`CastOptionsProvider`**: Declared in `AndroidManifest.xml` via `OPTIONS_PROVIDER_CLASS_NAME` meta-data → loaded by reflection. Keeping.
- **`AGPodcastApp`, `MainActivity`, `PlaybackService`, `NewEpisodeCheckWorker`, DAOs, ViewModels, Room entity**: All loaded by Hilt/WorkManager/manifest. Keeping.
- **Sealed `BottomTab` variants (`Podcast`, `XFeed`)**: Both referenced in `bottomTabs` list and branch logic. Keeping.
- **Theme colors `GoldDark`, `SurfaceVariant`**: Referenced inside `DarkColorScheme`. Keeping.
- **`play-services-cast-framework`, `media3-cast`, `mediarouter`**: Cast wiring (`CastContext`, `CastPlayer`, `MediaRouteButton`, `CastOptionsProvider`) depends on all three. Keeping.
- **`@Assisted`/`@AssistedInject` in worker, `@HiltWorker`, `@HiltAndroidApp`, `@HiltViewModel`, `@AndroidEntryPoint`**: DI annotations; code-generator consumers. Keeping.
