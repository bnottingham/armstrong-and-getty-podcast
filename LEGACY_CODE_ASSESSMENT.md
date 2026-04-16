# Legacy Code Assessment

`minSdk` = 26 (Android O / Oreo). `compileSdk` = 36. All deprecated-API checks below are against these.

## Findings

### 1. Dead SDK version check — `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/work/NewEpisodeCheckWorker.kt:137`
- **Verdict:** REMOVE (HIGH confidence)
- **Reason:** `minSdk = 26` (O). The `if` branch is *always* taken; there's no `else`. The branch guard is pure dead code and the comment "required for API 26+" is stale in a 26-min project.

### 2. Unused DAO methods
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/data/local/PodcastDayDao.kt`
- **Methods:** `getAllDownloadedDays()` (line 20-21), `insertIfNotExists(day)` (line 32-33), `update(day)` (line 35-36), `getLatestDay()` (line 73-74)
- **Verdict:** REMOVE (HIGH confidence)
- **Reason:** Grepping the entire source tree shows zero call sites for any of them. They are schema-exposed methods from a "CRUD template" that were never wired up. Room generates code for them even though nothing calls them.

### 3. Unused import of `@AndroidEntryPoint` in `AGPodcastApp`
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/AGPodcastApp.kt:13`
- **Verdict:** REMOVE (HIGH confidence)
- **Reason:** `AGPodcastApp` is annotated `@HiltAndroidApp` (correct for `Application`). The `AndroidEntryPoint` import is unused — it's for Activities/Fragments/Services/etc.

### 4. `Room.fallbackToDestructiveMigration()` in `AppModule`
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/di/AppModule.kt:49`
- **Verdict:** KEEP
- **Reason:** This is the intended resilience strategy for a single-user app that re-fetches everything from the RSS feed on startup; losing the local DB on schema bump is an acceptable cost, not a leftover. `versionCode = 1` and Room version is 3 — migrations were never defined. Consistent with "one code path."

### 5. `extractHourLabel(title, fallbackIndex)` fallback
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/data/repository/PodcastRepository.kt:279-291`
- **Verdict:** KEEP
- **Reason:** "Fallback" here means a default numeric label when the title doesn't match "Hour N" or "OMT" — a real parsing fallback, not legacy code.

### 6. `CastContext.getSharedInstance` try/catch in `PlaybackService`
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/media/PlaybackService.kt:64-68`
- **Verdict:** KEEP
- **Reason:** Documented comment explains this is a runtime resilience path for devices without Google Play Services. That's a legitimate fallback, not legacy code.

### 7. `statusBarColor` / `navigationBarColor` in `Theme.kt`
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/ui/theme/Theme.kt:102-103`
- **Verdict:** KEEP (MED skip)
- **Reason:** Deprecated in API 35+ in favor of edge-to-edge-only, but the replacement (`enableEdgeToEdge` config) is already called from `MainActivity.onCreate`. Removing these color assignments might alter visual behavior on pre-35 devices or when Compose doesn't fully paint the inset area. Out of scope as a "mechanical migration" — keep for now.

### 8. `loadDay` vs `playDay` in `EpisodeListViewModel`
- **File:** `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/ui/screens/episodelist/EpisodeListViewModel.kt:124-190`
- **Verdict:** KEEP (LOW skip)
- **Reason:** The two methods look similar but have genuinely different semantics: `loadDay` loads without auto-play at the saved position (for PlayerScreen open), `playDay` auto-plays and resets if already listened (for the Now Playing tile tap). Not a duplicated code path in the "legacy" sense, even though a shared helper could be extracted — that's a refactor, not a legacy-cleanup task.

### 9. `python-twitter/scrape_x_list.py` references to `legacy`
- **File:** `python-twitter/scrape_x_list.py` (multiple)
- **Verdict:** KEEP
- **Reason:** `legacy` is the **literal JSON key name** from X's GraphQL API (e.g. `result["legacy"]["full_text"]`). It's a vendor schema naming decision, not our legacy code.

## Summary

- HIGH-confidence removals: 3 items (dead SDK_INT guard, 4 unused DAO methods, 1 unused import).
- MED/LOW skipped: 3 items (window color deprecation, load/play dup, X API schema naming).
