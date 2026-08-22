# Promotional store screenshots

The framed images uploaded to App Store Connect and Play Console are
**generated**, not hand-composed:

```bash
cd tools/promo-screenshots
npm install          # first run only
npm run build
```

Source: `tools/promo-screenshots/buildPromoScreenshots.mjs`.

| Profile | Dimensions | Slides | Output |
|---|---|---|---|
| `iphone-6.9` | 1320×2868 | 9 | `app-store/screenshots/iphone-6.9/` |
| `ipad-13` | 2064×2752 | 9 | `app-store/screenshots/ipad-13/` |
| `android-phone` | 1080×1920 | 8 | `play-store/screenshots/phone/` |
| `android-tablet` | 1440×2560 | 8 | `play-store/screenshots/tablet/` |

**Play caps every device type at 8 screenshots**, so the two Play profiles
exclude the second review wall (`09-reviews`); the builder throws rather than
silently truncating if a Play profile is ever configured past 8. It also prunes
any PNG in an output folder that is not in the current slide list, so a renamed
or dropped slide cannot leave an orphan sitting in the upload folder. Every output is RGB PNG with no alpha channel: App
Store Connect rejects alpha and Play requires "24-bit PNG (no alpha)". The
builder flattens after rendering, then re-reads each file to assert dimensions
and channel count. `contact-sheet-<profile>.jpg` is regenerated beside each set
on every full run.

Flags: `--profile=android-phone`, `--only=04`, `--batch=ag-M-D-YY`.

## Capturing the raw sources

`screenshots/<batch>/{ios,android}/<profile>/` holds the raw device captures.
They are real app screenshots, not mockups. Four states per profile:

| File | State |
|---|---|
| `01-episodes.png` | Episode list with the Now Playing card populated |
| `02-player.png` | Player screen, scrubbed ~⅓ in so the progress bar reads |
| `03-downloads.png` | An episode mid-download ("Downloading segment N of M") |
| `04-about.png` | About tab, at the top |
| `05-catalog.png` | Episode list scrolled deep into the back catalogue |
| `06-morefrom.png` | About tab scrolled to "More from A&G" |

The app has four screens, so slides 6 and 7 re-frame the episode list and the
About tab at a different scroll position rather than repeating slides 3 and 8
verbatim.

**iOS.** Build for a simulator, then capture at native resolution:

```bash
cd "Native Mobile/iOS"
xcodebuild -project armstrongandgetty.xcodeproj -scheme armstrongandgetty \
  -configuration Debug -destination "id=<SIMULATOR_UDID>" \
  -derivedDataPath build/DD build CODE_SIGNING_ALLOWED=NO
```

Use **iPhone 16 Pro Max** (natively 1320×2868) and **iPad Pro 13-inch (M4)**
(natively 2064×2752) — no scaling needed. Set a clean status bar first:

```bash
xcrun simctl status_bar <UDID> override --time "9:41" --wifiMode active \
  --wifiBars 3 --batteryState charged --batteryLevel 100
```

Capture with `xcrun simctl io <UDID> screenshot <path>`.

**Android.** Build and install, then force the exact Play dimensions on one
emulator rather than maintaining two AVDs:

```bash
cd "Native Mobile" && ./gradlew :androidApp:assembleDebug
adb install -r Android/build/outputs/apk/debug/androidApp-debug.apk
adb shell wm size 1080x1920 && adb shell wm density 420   # phone
adb shell wm size 1440x2560 && adb shell wm density 320   # tablet
adb exec-out screencap -p > <path>
adb shell wm size reset && adb shell wm density reset      # when done
```

Changing `wm size` restarts the activity, so relaunch and re-dismiss the
notification permission prompt each time.

### Why the status bar is cropped, not hidden

All four sets are status-bar-free. On iOS that is cosmetic; on Android it is
necessary. `settings put global policy_control immersive.status=*` is ignored
on modern Android, and SystemUI demo mode (`notifications -e visible false`)
does not suppress the emulator's persistent system notification icons — they
survive into the capture and look like debug clutter on a store listing.

So the builder crops the status bar at framing time, per profile
(`statusBarCrop`: 186 / 48 / 137 / 140 px). The raw captures stay untouched as
evidence. Android's inset comes from:

```bash
adb shell dumpsys window displays | grep ROTATION_0   # overrideConfigInsets top
```

Re-measure it if the emulator density changes.

## Layout

| # | File | Slot |
|---|------|------|
| 1 | `01-hero.png` | Fly-in hero — wordmark, laurel-framed line, tilted device |
| 2 | `02-reviews.png` | Five-star review wall |
| 3 | `03-episodes.png` | Episode list |
| 4 | `04-player.png` | Player and skip controls |
| 5 | `05-downloads.png` | Offline downloads |
| 6 | `06-catalog.png` | Back catalogue |
| 7 | `07-more.png` | More from A&G |
| 8 | `08-about.png` | About the show |
| 9 | `09-reviews.png` | Five-star review wall — **iOS only** |

Each feature slide (3–8) pairs a two-line serif headline with a short quoted
review fragment, exactly as the review walls do — the fragment is always a real
excerpt, never invented copy.

Measurements are stated per profile in the builder's `PROFILES` map. The two
Play profiles share a shape (both 9:16), so the tablet is
`scaleLayout(ANDROID_PHONE_LAYOUT, 4/3)` with two hero overrides; the two iOS
profiles are 19.5:9 and 3:4 and get their own numbers.

The template requires every feature device to run off the bottom edge. The
builder computes the rendered device height from the *cropped* source and
throws if it would overshoot by less than 100px — a device that fits inside the
canvas reads as a floating card, and the status-bar crop shortens the source
enough to cause exactly that if `deviceWidth` is not retuned.

Palette comes from the app's own Compose theme
(`Native Mobile/shared/.../ui/theme/Theme.kt`): `#0E0F13` ground, `#E8B34B`
gold, `#F0EDE6` text.

Type is **Playfair Display** for headlines and quotations and **Inter** for the
uppercase review-wall eyebrows and the hero kicker. The app itself declares no
custom typeface (Compose renders SF on iOS, Roboto on Android), so the promo
face is a deliberate marketing choice: a high-contrast news serif that suits a
talk-radio show and gives the set the same editorial weight as the sibling
Renew listing. Both faces are inlined as data URIs (cached in
`tools/promo-screenshots/.cache/`) and the builder throws rather than silently
falling back to a system face and reflowing the art-directed line breaks.

## Copy rules

- **Review text is quotation, trimmed.** Every quote on slides 2 and 7 is a
  shortened excerpt of a real supplied review, cut to 10–13 words so it stays
  legible at store-thumbnail size. Do not paraphrase a review into something
  the reviewer did not say, and never fabricate one: invented testimonials
  deceive the people reading the listing and are grounds for removal under
  both Apple's and Google's policies.
- **No ranking or award claims** without a citable source.
- **This is an unofficial app.** The About screen carries "Unofficial companion
  app. Not affiliated with Armstrong & Getty, iHeartMedia, or their
  affiliates." The promo art uses the show's name descriptively, as the app's
  own header does. Do not add official-sounding endorsement copy, and consider
  carrying the disclaimer in the listing description — Apple 4.1 and Play's
  impersonation policy both look at whether promotional art implies an
  affiliation that does not exist.

## Known content bugs visible in these captures

Both are in the RSS/episode text pipeline, not the screenshot tooling:

1. Episode descriptions render raw `&nbsp;` instead of a decoded space.
2. Segment titles are duplicated
   ("…Hour One**The Best Weekend Talk Show In America Hour One**").

Both appear in slides 3–5. Fix them, then regenerate the batch and rebuild
before uploading.

## Before uploading

**iOS.** Both profiles go up together — the target ships iPhone and iPad, so
App Store Connect requires its own iPad set.

**Android.** The `tablet/` set serves both the 7-inch and 10-inch Play slots
(Play's large-screen rule is a 9:16 portrait between 1080 and 7680px). If Play
warns that submission would restart an existing review, save the screenshot
changes and do not restart the review.
