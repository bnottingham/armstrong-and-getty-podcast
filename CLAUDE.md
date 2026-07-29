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

## Upgrade-in-place invariants (Android)

The app replaced an earlier native app. These names must never change: applicationId,
Room db `ag_podcast_db`, files dir `podcasts/`, SharedPreferences `user_deletions`,
and `Segment` JSON field names (Gson-compatible).
