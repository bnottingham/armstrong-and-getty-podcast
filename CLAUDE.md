# Armstrong & Getty Podcast App — agent instructions

Kotlin Multiplatform + Compose Multiplatform podcast app (Android + iOS). Nearly all
code lives in `Native Mobile/shared/src/commonMain`; the platform shells are thin.

Shared agent playbook: `../ai-agent-docs/README.md` — read the matching guide before
platform-sensitive work. For casting specifically:
`../ai-agent-docs/media/android-chromecast-media3.md` (the generic pattern; this file
records how it's wired in this app).

## Layout & builds

- `Native Mobile/` is the Gradle/KMP root — **the path contains a space; always quote it**.
  - `Native Mobile/Android/` — Android shell (Gradle module `:androidApp` via projectDir mapping)
  - `Native Mobile/iOS/` — Xcode project + app sources, flattened. The pbxproj uses
    **explicit file references** (no filesystem-synchronized group): new iOS files must be
    added to the pbxproj by hand.
  - `Native Mobile/shared/` — all UI, playback, and business logic
- Android build: `cd "Native Mobile" && ./gradlew :androidApp:assembleDebug`
- iOS build: `xcodebuild` in `Native Mobile/iOS` (its build phase cds to `$SRCROOT/..` and
  runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`)
- Tests: `cd "Native Mobile" && ./gradlew :shared:iosSimulatorArm64Test` — no Android
  host-test target exists. Keep `EpisodeEngineBugTest` green; it is the regression suite
  for the episode-assembly engine (day keys and "today" are **UTC**).

## Casting — read before touching playback (Android)

The Cast integration has crashed in production twice; every rule below traces to a real
bug. Files: `PlaybackService.kt` (Android shell), `Media3PlatformPlayer.kt` (androidMain),
`MediaRouteAction.android.kt`, `CastOptionsProvider.kt`, `PlaybackController.kt` (common).

### Sender-app requirements (Google Cast spec)

- `MainActivity` **must extend `AppCompatActivity`** (a `FragmentActivity`). The
  `MediaRouteButton` device chooser is a DialogFragment; inside a plain
  `ComponentActivity` the app crashes the instant the button is tapped.
- The manifest theme must stay an AppCompat descendant (`Theme.AGPodcast` →
  `Theme.AppCompat.NoActionBar`).
- `CastOptionsProvider` is registered via `OPTIONS_PROVIDER_CLASS_NAME` manifest
  meta-data; `CastContext` is initialized at activity launch so discovery starts early.
- Everything Cast-related wraps in try/catch: devices without Google Play services must
  degrade to no-Cast, never crash (the button hides itself if setup fails).

### Media item URI resolution — the core invariant

Controllers (the app UI) always build playlists with `file://` URIs pointing at
downloaded audio. A Cast receiver cannot reach the phone's filesystem, so **every
MediaItem must carry both URLs in its MediaMetadata extras**:

- `EXTRA_REMOTE_URL` — the Omny streaming URL (what the receiver plays)
- `EXTRA_LOCAL_PATH` — the downloaded file path (what local playback prefers)

`PlaybackService` is the single place URIs get resolved, in
`onSetMediaItems` / `onAddMediaItems` (the Media3-documented hook): Cast active → remote
https URL; local → downloaded file when it exists. `switchToPlayer` re-resolves in BOTH
directions on session handoff (connect → remote URLs; disconnect → back to local files).
If you add any new path that feeds MediaItems to the session, it must carry both extras —
otherwise casting silently no-ops (receiver queue load fails, play() does nothing).
Always set `MimeTypes.AUDIO_MPEG`; the Cast `DefaultMediaItemConverter` requires a
non-null mimeType.

### Seek safety — CastPlayer's async timeline

`CastPlayer`'s timeline is **empty until the receiver reports its queue back** (an async
round trip). `RemoteCastPlayer.seekTo` indexes into that timeline without bounds checks →
`ArrayIndexOutOfBoundsException` (`CastTimeline.getPeriod`). Two rules:

1. Never call `setMediaItems(...)` followed by a separate `seekTo(index, pos)` — use the
   atomic `setMediaItems(items, startIndex, startPositionMs)` overload.
2. The Cast player handed to the MediaSession is wrapped in `BoundsCheckedPlayer`
   (a ForwardingPlayer that drops out-of-range index seeks) because stale commands can
   still arrive via controller command-queue flushes mid-handoff. Keep the wrapper; keep
   identity checks against `castSessionPlayer` (the wrapper), not the raw `CastPlayer` —
   `player is CastPlayer` is false for the wrapper.

### Testing constraints

- Emulators/simulators **cannot discover Cast devices** (no mDNS). Cast handoff can only
  be verified on a physical phone + Chromecast speaker. Emulator verification is limited
  to: cast dialog opens without crashing, and local playback works through the URI
  resolution path.
- iOS Cast uses the Google Cast SDK via cinterop (`CastManager`,
  `CastAwarePlatformPlayer` in iosMain); the xcframework is auto-fetched by
  `Native Mobile/scripts/fetch_cast_sdk.sh` from the Xcode build phase. Same
  physical-hardware-only testing constraint applies.

## Notification controls (Android)

Media button preferences use Media3's **built-in icons**
(`CommandButton.ICON_SKIP_BACK_30` etc.) with slot hints — never hand-vendored drawables
(theme-attr tints render malformed in notification shells). ±30s sits in the primary
back/forward slots, ±10s in the secondary slots (surfaces on Auto/Wear).

## In-app review (rating prompt)

Code: `shared/.../review/` (commonMain policy + coordinator, platform launchers), the
Swift `AppStoreReviewBridge` in `iOS/FirebaseBridge.swift`, tests in `commonTest/.../review/`.

- **Store policy (Apple + Google):** system sheet only (`AppStore.requestReview(in:)`,
  Play In-App Review). No "Enjoying the app?" pre-question, no sentiment gating, no
  incentives, nothing gated behind a rating. **No button may call the review API** — the
  About "Rate the App" row only deep-links to the store (`StoreLinks`). Analytics say
  `in_app_review_requested`, never "shown"/"rated": neither store reports either.
- **Only `ReviewCoordinator` asks**, only from `App()` composition (via `ReviewPromptHost`),
  and only at the END of a listen the listener made in this foreground visit, with the
  Episodes list on screen: `LEFT_PLAYER` (back from the player after pausing or finishing
  that day during this player visit) or `FINISHED_ON_LIST` (the day played to its natural
  end on the list). **Never on a pause tap** (Apple: not "in response to a button tap or
  other user action"), never for a stop made from the lock screen / notification / Auto,
  for audio held back by a call or another app (`PlaybackState.playWhenReady` stays true),
  or for a switch to another day. A 2 s settle then re-checks every gate live. Never call
  it from `PlaybackService`, Android Auto, `NewEpisodeCheckWorker`, the iOS BG task, or
  within 30 s of the app coming to the foreground. Its Koin definitions stay lazy
  `single`s (no `createdAtStart`) — see the iOS Koin startup race (`EnsureKoin.kt`).
- **Gates** live in `ReviewPolicy` (pure, exhaustively tested): ≥3 distinct device-local
  days with ≥10 min credited listening (today one of them), ≥5 days since first seen,
  120-day cooldown, one per app version, lifetime cap 3, 7 days quiet after a crash; never
  while playing or held back, casting, without window focus (shade, dialog window), while
  a dialog / refresh / "Download failed" row / failed segment is on screen
  (`HoldReviewPromptWhile`, `EpisodeListViewModel.reviewBlocker`), or within 30 min of a
  feed/download failure not yet followed by a success (`SessionHealth`). Its day key is
  **device-local**, not the engine's UTC key — don't unify them.
- **Attempts:** persisted and read back BEFORE the platform call (a broken store never
  asks). `canPresent()` is checked before anything is spent. A result of `context_gone`
  is given back; `unavailable` is given back with a 14-day retry gap; never a second ask
  in one process. Analytics: `in_app_review_requested` (with `result`), plus once per
  process `in_app_review_skipped` (engagement gates) and, with the switch off,
  `in_app_review_would_request`.
- **Storage is new-keys-only:** Android prefs file `in_app_review`, iOS NSUserDefaults keys
  prefixed `in_app_review.`. Never reuse `user_deletions` / Room / `podcasts/`
  (`ReviewStateStoreTest` enforces it).
- **Android auto-prompt is ON** (`ANDROID_AUTO_PROMPT_ENABLED` in
  `androidMain/.../review/AndroidReviewEnvironment.kt`, flipped 2026-10-14 by Brett's call;
  `false` makes every moment a logged dry run). Downloads use Wi-Fi and mobile data by
  default; listeners can restrict them with the About tab's "Download over Wi-Fi only"
  setting (off by default — see below). Listening days
  accumulate only while the phone UI is running: Android Auto-only listening earns none
  until `PlaybackService` also feeds the tracker (an undercount, never an over-ask).
- **Verify:** `./gradlew :shared:iosSimulatorArm64Test`; logcat / Xcode console tag
  `InAppReview` prints `skip <moment>: <reason>` or `requested …`. The iOS sheet never
  appears in TestFlight (development builds only); the Play card only on a Play-installed
  build (internal test track). Seed state rather than lowering thresholds. iOS simulator:
  with the app terminated, `defaults write` the **container** plist (a plain bundle-id
  domain does not reach the app): `P="$(xcrun simctl get_app_container <udid>
  nomnomsom.armstrongandgetty data)/Library/Preferences/nomnomsom.armstrongandgetty"`,
  then `xcrun simctl spawn <udid> defaults write "$P" in_app_review.first_seen_ms -int <ms>`.
  Android: `adb shell run-as com.nomnomsom.armstrongandgetty cat shared_prefs/in_app_review.xml`.
  A generic-simulator `xcodebuild` needs `ARCHS=arm64` (there is no iosX64 target).

## Wi-Fi-only downloads (setting)

Code: `shared/.../download/` (`DownloadSettings`, `DownloadPolicy`, `NetworkStatus` + platform
actuals, `AutoDownloadPlan`); tests in `commonTest/.../download/` and `WifiGateTest`.

- **Default OFF for everyone** (absent key = off; no migration). Off = the old behaviour
  exactly: every download uses Wi-Fi or mobile data and `DownloadPolicy` never consults the
  network. Don't change the default without an owner decision.
- **On = unmetered only** (Android `NET_CAPABILITY_NOT_METERED`; iOS path satisfied and not
  `isExpensive`). Metered, offline and unknown all block. It governs every transfer
  `AudioDownloader` makes: app-open/pull-to-refresh auto-download, the player's live poll,
  `NewEpisodeCheckWorker`, the iOS BG task, and manual Download/Retry taps. Enforced in
  `PodcastRepository` (`downloadDay`, `appendNewSegments`, `retrySegment`) before any state
  change, plus a per-attempt/watchdog backstop in `AudioDownloader`; the iOS audio-only Darwin
  client sets `allowsExpensiveNetworkAccess = false` per request while on.
- **Never gated:** feed refresh, notifications, playback (incl. Android Auto's remote-URL
  streaming), Cast and AirPlay. Keep `RssFeedParser` on the unqualified HttpClient.
- **Blocked ≠ failed:** `WifiRequiredException` leaves the day `NONE` (never `ERROR`), never
  enters `segmentsFailed`, and skips `SessionHealth.reportFailure`, `download_failed` and
  Crashlytics — so it never holds the review prompt.
- **Manual tap while blocked:** `EpisodeListViewModel.wifiRequiredPrompt` → `WifiRequiredDialog`
  in `App()` ("Turn Off & Download" / "Not Now"; holds the review prompt). No per-tap queue.
- **Catch-up (setting on):** worker and BG task plan via `planAutoDownloads(catchUp = true)`
  (also today's/latest day left `NONE`); the VM reruns its auto-download pass when
  `allowedFlow` goes false→true. A deferring Android run enqueues one-time unique work
  `new_episode_check_wifi` (UNMETERED). The periodic `new_episode_check` stays `CONNECTED` +
  `KEEP` — never re-enqueue it for this setting.
- **Storage is new-keys-only:** Android prefs file `download_settings`, key
  `download_settings.wifi_only` (iOS: standard NSUserDefaults, same key). `DownloadSettingsTest`
  enforces default-off and no reuse of `user_deletions`/`dates`/`user_deletions_dates`/`in_app_review.*`.
- New Koin definitions are lazy `single`s; the iOS BG task resolves them inside its try/catch.
- **Testing:** an Android emulator with Wi-Fi off is metered mobile data. iOS simulators report
  the Mac's network (never expensive) — cellular behaviour needs a physical iPhone.

## Release CI & signing

- Website: `docs/` → Firebase Hosting (`firebase deploy --only hosting`), live at
  https://armstrong-and-getty-podcast.web.app (cleanUrls; store-listing URLs
  `/privacy` and `/support`).
- Android release signing: the Play **upload keystore is never in the repo** — it
  lives in `~/keystores/armstrong-and-getty-podcast/` (Brett's machine) and in GitHub
  Actions secrets (`ANDROID_KEYSTORE_BASE64/_PASSWORD`, `ANDROID_KEY_ALIAS/_PASSWORD`).
  `Android/build.gradle.kts` signs release builds only when `ANDROID_KEYSTORE_PATH`
  is set in the env; local release builds are unsigned.
- `.github/workflows/android-release.yml` (tag `v*` or manual dispatch): runs the
  shared engine test suite on macOS, then builds a signed AAB + APK on ubuntu.
  `versionCode`/`versionName` accept `-P` overrides / dispatch inputs. On a manual
  dispatch it also **uploads to Google Play** (`playTrack`: production/internal/none,
  `playStatus`: draft/completed; default production + **draft**, so a person still sends it
  for review in Play Console → Publishing overview) with the R8 mapping file and release
  notes from `Native Mobile/Android/whatsnew/whatsnew-en-US` (≤500 chars — update it every
  release). Uses the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64` secret (service account
  `ag-podcast-play-ci@armstrong-and-getty-app-d67a4.iam.gserviceaccount.com`, set up Jul 2026, with
  release + store-presence permissions on this app in Play Console); without it the step logs a
  warning and skips. `play-api-check.yml` verifies the credential without uploading. Bump the versionCode default in
  `Android/build.gradle.kts` for each release (Play rejects a reused code).
- `.github/workflows/ios-release.yml` is a dispatch-only scaffold that fails fast
  until the Apple signing secrets listed in its header are configured.

## SEO research tooling

Keyword/SERP/backlink/rank data comes from DataForSEO through two MCP servers: `openseo`
(project `.mcp.json` → self-hosted OpenSEO at `localhost:3001`, sibling checkout
`../open-seo`, shared with Renew; skills vendored in `.claude/skills/`) and `dataforseo`
(user-scoped, hosted, OAuth — use it for App Store / Google Play data OpenSEO lacks).
Credentials live in the Keychain (`dataforseo-api`), never in this repo. Every call costs
money: confirm before batches over ~2,000 credits. Setup, recovery, and `.ai` response
caveats: `tools/seo/README.md`.

## Upgrade-in-place invariants (Android)

The app replaced an earlier native app. These names must never change: applicationId,
Room db `ag_podcast_db`, files dir `podcasts/`, SharedPreferences `user_deletions`,
and `Segment` JSON field names (Gson-compatible).
