# Store listing copy: App Store and Google Play

Last updated: October 13, 2026 · Research data: 2026-10-13, US/English

This is the copy-paste source for the App Store Connect and Play Console
listings of the Armstrong & Getty Podcast App. **Everything here is a draft.
Brett applies it by hand.** Each character count was computed with Python
`len()` on the exact text inside the code block. Line breaks count as 1.
All recommended text-field copy is plain ASCII; the one non-ASCII character
is the "·" separator on the feature-graphic artwork. Evidence files live under
`tools/seo/data/2026-10-13/` (`digest/...` unless another path is given).

> **Status and plan (read first)**
>
> - **APPLIED 2026-10-13, 9:04 PM PT:** 1.0.2 (61) was removed from review,
>   updated with the approved package below (subtitle, News/Entertainment,
>   keywords, promotional text, description, slides 06 and 08 replaced in the
>   iPhone 6.9" and iPad 13" sets; 02 and 09 kept; build 61, What's New and
>   release settings unchanged) and resubmitted; status Waiting for Review.
>   The pre-change fields are recorded in
>   `tools/seo/data/2026-10-13/asc-1.0.2-before.json`.
> - **Approved (Brett, 2026-10-13):** the recommended App Store package in the
>   §0 paste sheet as written: Alt A subtitle, the recommended keyword string,
>   News primary / Entertainment secondary, the recommended promotional text
>   and description, and re-captioned 06 and 08.
> - **iOS 1.0.2 (61) plan (Brett, 2026-10-13).** 1.0.2 (61), the launch-crash
>   fix, went to App Review at about 6:58 PM PT on 2026-10-13 with the old
>   listing fields. Once this copy is final, choose **Remove from review** on
>   1.0.2. Apply the subtitle (§1.2), keyword field (§1.3), category if you
>   are switching it (§1.4), promotional text (§1.5), description (§1.6) and
>   screenshots (§1.8: replace 06 and 08 only; 02 and 09 stay). Confirm build
>   61 is still attached, keep the existing What's New unless you reword it on
>   purpose (§1.7), keep the release settings, and resubmit as 1.0.2. No new
>   build and no version bump. Step-by-step: `owner-actions.md` #6.
> - **Apple promotional text** is the one Apple field that can also change
>   later without review. Set it during the 1.0.2 edit so the resubmitted
>   version carries it.
> - **Google Play** text, screenshots and the feature graphic all change
>   through a store-listing edit, with no app release. Play still reviews
>   those edits.
> - **App Store screenshots:** only 06 and 08 change (re-captioned
>   2026-10-13). Slides 02 and 09 stay by Brett's decision of 2026-10-13.
>   See §1.8. The hero slide fix (§1.8a) ships when it's built.

---

## 0. Paste sheet

| Store | Field | Recommended text | Chars / limit | When |
|---|---|---|---|---|
| Apple | Name | `Armstrong & Getty Podcast App` (keep) | 29/30 | n/a |
| Apple | Subtitle | `Unofficial A&G Talk Radio Show` (Alt A; §1.2 says when `Every Hour of A&G Talk Radio` fits instead) | 30/30 | 1.0.2 resubmission |
| Apple | Keywords | `every,hour,daily,demand,conservative,politics,political,commentary,morning,offline,download,cast` | 96/100 | 1.0.2 resubmission |
| Apple | Category | Primary **News**, secondary **Entertainment** (a separate decision, §1.4) | n/a | 1.0.2 resubmission, if you switch |
| Apple | Promotional text | §1.5 | 166/170 | Set during the 1.0.2 edit |
| Apple | Description | §1.6 | 2,418/4000 | 1.0.2 resubmission |
| Apple | What's New | Keep the submitted text (§1.7) | n/a | 1.0.2 resubmission |
| Apple | Screenshots | Replace 06 and 08 only; 02 and 09 stay (§1.8). Hero fix (§1.8a) once it's built | n/a | 1.0.2 resubmission |
| Play | Title | `Armstrong & Getty Podcast App` (keep) | 29/30 | n/a |
| Play | Short description | `Unofficial A&G podcast app: every hour in one daily episode to download or cast.` | 80/80 | Now |
| Play | Full description | §2.3 | 2,814/4000 | Now |
| Play | Screenshots + feature graphic | §2.4: re-captioned 06 and 08 (caption decision in force); §2.4a: hero and feature-graphic fixes once built | n/a | Next listing edit |
| Play | Category | Music & Audio (keep) | n/a | n/a |

---

## 1. Apple App Store (id6796129205)

### 1.1 Name: keep

```
Armstrong & Getty Podcast App
```
(29/30)

**Why:** the name is already #1 in App Store search for every spelled-out
version of the show's name. That covers 'armstrong and getty', '...podcast',
'...show', '...on demand' and 'armstrong getty podcast'
(`app-searches-apple.json`). It is also #1 for 'armstrong & getty' when the
`&` is URL-encoded (iTunes Search API, curl 2026-10-13; DataForSEO's #64 for
that query is an encoding artifact). Making the name more prominent or more
official-sounding would add Apple 4.1(c)/5.2.1 exposure and gain nothing.
Never add "Official", station call letters, iHeart or host names.

### 1.2 Subtitle

| | Text | Chars |
|---|---|---|
| Current | `Every Hour. On Demand.` | 22/30 |
| **Recommended for the 1.0.2 resubmission (Alt A)** | `Unofficial A&G Talk Radio Show` | 30/30 |
| Only in a submission that also ships the hero fix (§1.8a) | `Every Hour of A&G Talk Radio` | 28/30 |
| Alt B (keeps "On Demand") | `A&G Talk Radio Show On Demand` | 29/30 |

**Why:**
- The subtitle is indexed and today leaves 8 characters unused.
- **"radio" is not in the name or the subtitle** (the keyword field is private
  and its contents are unknown). 'armstrong and getty radio' ranks #6 and
  'armstrong and getty talk radio' ranks #8, behind generic radio apps
  (iTunes Search API, re-checked by curl 2026-10-13; an approximation of App
  Store search, not the App Store app itself).
- **"A&G" barely registers.** The app is not among 192 results for 'a&g' and
  is #91 of 165 for 'a&g podcast' (iTunes Search API, limit=200, curl
  2026-10-13; an approximation of App Store search, and its order changes
  with the limit parameter). Ampersand brands do rank when the abbreviation
  is in their indexed text: `h&m` returns H&M at #1 at limit=100 (at
  limit=200 'H&M MENA' comes first, so treat the proxy loosely), and the
  app-store analysis found the same for A&W, AT&T and B&H.
- "talk radio" has App Store search volume of 212
  (`store-apple-keywords.json`), but its top 2 apps have 29,154 and 2,522
  ratings and we're not in its top 100 (`app-searches-apple.json`), so it's
  a long shot until the app has ratings. Google volume for the A&G+radio
  queries is small: 'armstrong and getty radio' 110, 'a&g podcast' 10,
  'a&g show' 10 (`google-keywords.json`).
- **Why Alt A for 1.0.2.** The name stays the show's name, and the current
  hero slide has laurels, a "PODCAST · ON DEMAND" lockup and no unofficial
  marker (§1.8a). With "Every Hour of A&G Talk Radio", nothing in the App
  Store search card would say unofficial, and the subtitle would extend the
  show's "A&G" mark into another indexed field (Apple 4.1(c), 5.2.1;
  strategy open question 5). Alt A puts "Unofficial" in the first text
  people see under the name, the strongest impersonation hedge the search
  card allows. It gives up the "every hour" promise in the subtitle; the
  keyword field picks up `every,hour` instead. The hero fix needs a fresh
  capture batch, so it is unlikely to be ready for 1.0.2. **Use "Every Hour
  of A&G Talk Radio" only in a submission that also ships the hero fix.**
- Dropping "On Demand" could weaken 'armstrong and getty on demand', which is
  #1 today and has 1,900/mo Google volume. Every keyword row except Alt B's
  keeps `demand` to cover it. **Check on a device after release, and switch
  to Alt B if that rank drops.** Alt B keeps the match to the official
  podcast's name, "Armstrong & Getty On Demand", which is slightly more
  impersonation risk.
- "Talk Radio" describes the show, not a live stream. The description says
  plainly that the app is "not live" (§1.6).

### 1.3 Keyword field (100 max, comma-separated, no spaces)

**Before replacing it, copy the current field** (it is private) into
`tools/seo/data/` so there is a record. One hint about what's in it now:
DataForSEO Labs shows the app ranking #30 for 'download jack' and #29 for
'download ondemand' (`store-apple-keywords.json`, rows ranked for app
6796129205). That *suggests* the field may contain `jack`, `download` or
`ondemand`, but this is an inference. `jack` must go, because Apple bans
celebrity names in keywords.

| Pair with | Keyword string | Chars |
|---|---|---|
| **Recommended**: Alt A subtitle (`Unofficial A&G Talk Radio Show`), News primary | `every,hour,daily,demand,conservative,politics,political,commentary,morning,offline,download,cast` | 96/100 |
| Alt A, if "conservative" is unwanted | `every,hour,daily,demand,politics,political,commentary,current,events,morning,offline,download,cast` | 98/100 |
| Alt A, if Entertainment stays primary | `every,hour,daily,demand,news,conservative,politics,political,commentary,morning,download,cast` | 93/100 |
| Alt A, no "conservative", Entertainment primary | `every,hour,daily,demand,news,politics,political,commentary,current,events,morning,download,cast` | 95/100 |
| `Every Hour of A&G Talk Radio` subtitle (with the hero fix only), News primary | `show,daily,demand,conservative,politics,political,commentary,morning,offline,download,cast,player` | 97/100 |
| Same, if "conservative" is unwanted | `show,daily,demand,politics,political,commentary,current,events,morning,offline,download,cast` | 92/100 |
| Same, if Entertainment stays primary | `show,daily,demand,news,conservative,politics,political,commentary,morning,offline,download,cast` | 95/100 |
| Alt B subtitle (`A&G Talk Radio Show On Demand`) | `every,hour,daily,conservative,politics,political,commentary,morning,offline,download,cast,player` | 96/100 |

All eight strings were checked by script against these rules:
- 12 or 13 tokens each, all within 100 characters.
- No spaces and no duplicate tokens.
- No token repeats a word from the name (armstrong, getty, podcast, app), the
  paired subtitle, or the primary category.
- No token is on the excluded list below.

**Left out on purpose:**
- `jack`, `joe`, `katie`: celebrity names (Apple's search guidance).
- `iheart`, `omny`, `spotify` and other apps' names, plus station call letters
  (kste, ksfo, kabc, kex and so on): other companies' names and marks
  (2.3.7).
- `chromecast` and `airplay`: trademarks. `cast` covers the intent, and
  AirPlay goes in the description.
- `android`: another platform (2.3.10).
- `live` and `stream`: the app doesn't play the live broadcast.
- `free`: pricing (2.3.7).
- `carplay` and `sleep`: neither feature exists (grep finds no CarPlay code
  and no sleep timer).
- `news`: matched through the category when News is primary.

**Why these words:**
- Apple combines words across the name, subtitle and keyword field. With the
  Alt A subtitle, the recommended field builds "conservative talk radio",
  "conservative radio", "talk radio show", "political podcast", "morning talk
  radio", "every hour" and "armstrong and getty on demand". The "Every Hour"
  subtitle rows add "podcast player".
- Store volumes (`store-apple-keywords.json`): conservative radio 67,
  conservative podcasts 43, conservative podcast 28, conservative talk radio
  26.
- Current ranks (`app-searches-apple.json`): conservative talk radio #72,
  conservative podcast #83, political podcast #22, politics podcast #37.
- The big generic terms are out of reach with 0 ratings: radio 21,090,
  podcast 5,943, podcasts 2,572 (`store-apple-keywords.json`), and podcast
  app is #43 for us today. No characters are spent on them beyond "player" in
  the "Every Hour" and Alt B rows.
- "conservative" is an editorial label for a show that calls itself
  independent. It's Brett's call; use the second row if unwanted.

### 1.4 Category

**Recommended:** primary **News**, secondary **Entertainment**. **Alternate:**
keep today's order (Entertainment primary, News secondary) and use the "if
Entertainment stays primary" keyword string.

**Why:**
- Apple counts the primary category toward text relevance. With News
  primary, "news" is matched without spending keyword characters. 'news talk'
  and 'news talk radio' each have store volume 17
  (`store-apple-keywords.json`).
- Most talk-radio and podcast-player peers use News as primary: Conservative
  Talk Radio, Talk Radio+, KSFO-AM, Walton and Johnson, Podcast App,
  Overcast, Pocket Casts (baseline `# LENS: app-store` §3 and Facts).
- Today's Entertainment top 100 is TikTok and short-drama apps.
- Whether News chart thresholds are lower is unverified, so this is a
  reasonable bet, not a sure win. It probably changes only with a version
  submission.

### 1.5 Promotional text (170 max; no review; not indexed)

Current (155/170):
```
Catch every minute of Armstrong & Getty on your schedule. Full-day episodes, offline downloads, Chromecast support, and real playback controls. No clutter.
```

**Recommended** (166/170). Set it during the 1.0.2 edit:
```
Every hour of the A&G show, stitched into one episode per show day. Download it for the drive, AirPlay or cast it, and pick up where you left off. Unofficial fan app.
```

Alt 1 (166/170):
```
Missed the show this morning? Every hour of A&G, in order, in one episode. Jump to any segment, download for offline, cast to a speaker. An unofficial, fan-built app.
```

Alt 2 (151/170):
```
Every hour of the A&G show in one episode per show day. Tap any segment, save it offline, AirPlay or cast it. Unofficial; not affiliated with the show.
```

**Why:** Apple says promotional text doesn't affect search ranking, so its job
is conversion. The current text never says "unofficial" and never mentions
AirPlay, though AirPlay ships on iOS (`MediaRouteAction.ios.kt:24,44`,
AVRoutePickerView). The recommended text adds both. It says "cast" rather
than "Chromecast" because the field has no room for Google's trademark line.

### 1.6 Description (4000 max; not indexed; drives conversion and the apps.apple.com page)

<details><summary>Current live description (1,582 chars)</summary>

```
Never miss a minute of The Armstrong & Getty Show.

Jack Armstrong and Joe Getty take an independent, contrarian look at news, politics, and modern life — merciless critics of mainstream media, on the air across the country every weekday morning. This app is the easiest way to catch every hour, on your own schedule.

DAILY EPISODES, ASSEMBLED FOR YOU
Every hour of the day's broadcast is stitched into one clean episode per show day, in order, so you can listen straight through — or jump to any segment with a tap.

LISTEN OFFLINE
Download a full day's show with one tap and take it anywhere: the commute, the gym, the dead zone with no signal. Delete it when you're done to free up space.

CAST TO ANY SPEAKER OR TV
Built-in Chromecast support sends the show to any Chromecast-enabled speaker or TV on your network, with your phone as the remote.

PICKS UP WHERE YOU LEFT OFF
Your position in every episode is remembered automatically — pause in the driveway, resume in the kitchen.

REAL PLAYBACK CONTROLS
Skip forward or back 10 or 30 seconds, change playback speed, or jump straight to the next segment. Full lock-screen and Control Center controls.

FRESH EVERY MORNING
The app refreshes in the background so today's show is ready before you are.

No accounts. No ads. No clutter — just the show.

This is an independent, fan-built companion app for The Armstrong & Getty Show. It is not affiliated with, endorsed by, or sponsored by Armstrong & Getty, iHeartMedia, or their affiliates. For the official show — stations, live streams, and more — visit armstrongandgetty.com.
```
</details>

**Recommended** (2,418/4000):
```
An unofficial, fan-built player for The Armstrong & Getty Show podcast: every hour of each show day, in order, in one episode.

The Armstrong & Getty Show is a weekday morning talk radio show about news, politics and everyday life. Its podcast feed posts the show an hour at a time. This app puts each show day back together in the order the hours were posted, so you can listen straight through or jump to the part you want, whenever you get to it.

EVERY HOUR, ONE EPISODE
Each show day becomes a single episode with its hours in order. New hours are added as the show posts them, and One More Thing joins the day when it is posted to the main feed. Tap any segment to jump straight to it.

LISTEN OFFLINE
Download a whole show day with one tap for the drive, the gym or anywhere without signal. Delete it when you're done to free up space.

AIRPLAY AND CHROMECAST
Send the show to AirPlay speakers and TVs, or to Chromecast and Google Cast speakers and TVs on your Wi-Fi, and keep your iPhone as the remote.

PICKS UP WHERE YOU LEFT OFF
Your place in every episode is saved as you listen. Pause in the driveway, resume in the kitchen.

CONTROLS MADE FOR TALK
Skip back or ahead 10 or 30 seconds and listen at 1x, 1.25x, 1.5x or 2x. Play, pause and skip from the Lock Screen and Control Center.

READY WHEN YOU ARE
The app checks for new hours in the background when iOS allows it, and can notify you when they arrive. Scroll back through earlier show days and download any you missed.

MORE FROM THE SHOW
Quick links to the show's own website, how to listen live, and the show's other podcasts.

ON DEMAND, NOT LIVE
This app plays the show's podcast hours after they air. It doesn't stream live radio. For live listening and station information, visit armstrongandgetty.com.

GOOD TO KNOW
- No account and no sign-up.
- The app adds no ads of its own. Episodes play exactly as the show publishes them, including the show's own sponsor messages.
- New show days can download automatically, including over cellular data. Delete old days now and then to save space.

ABOUT THIS APP
This is an independent, fan-built companion app. It is not affiliated with, endorsed by, or sponsored by Armstrong & Getty, iHeartMedia, or their affiliates. The show's name is used only to describe the content the app plays, and all show audio and names belong to their owners.

Google Cast and Chromecast are trademarks of Google LLC.
```

Alternate opening line (174 chars). Use it in place
of the first paragraph if Brett prefers a question-led hook; the rest stays
the same:
```
Missed the show this morning? This unofficial, fan-built app puts every hour of each Armstrong & Getty show day, in order, into one episode you can download, AirPlay or cast.
```

**What changed, and why:**
- **Unofficial in the first line**, plus a disclaimer paragraph. In the live
  description, the disclaimer is only the last paragraph.
- **The copied official wording is gone.** "merciless critics of mainstream
  media" is the official site's phrase (baseline `# LENS: policy-risk`, item
  4). "independent, contrarian look" echoes the show's own branding. The
  hosts' names are dropped too; a store listing doesn't need them.
- **The bare "No ads." is replaced with exact wording.** "No ads" would read
  as ad-free audio, but episodes carry the show's own sponsor messages. The
  app has no ad-handling code and plays the feed audio as published (policy
  lens). Apple 2.3.1 covers misleading marketing.
- **The false "jump straight to the next segment" claim is removed.** There
  is no next-segment control: `PlaybackService.kt:260-262` removes
  `COMMAND_SEEK_TO_NEXT`, and `AvPlatformPlayer.kt:144` disables
  `nextTrackCommand`. Users jump by tapping a segment row
  (`PlayerScreen.kt:261`, `seekToSegment`). The new copy says "tap any
  segment".
- **AirPlay is added** (`MediaRouteAction.ios.kt:24,44`).
- **"In the order the hours were posted", not "broadcast order".**
  `EpisodeAssembler.kt` `buildSegments` orders by pubDate, breaks ties on the
  hour number in the title, then keeps upload order. The website says the
  same ("in the order it was posted").
- **Exact speeds are added.** `PlaybackController.kt:207-213` cycles 1x,
  1.25x, 1.5x and 2x.
- **New "On demand, not live" section.** The subtitle now says "Talk Radio",
  'armstrong and getty live' gets 2,400/mo on Google
  (`google-keywords.json`), and the app plays only the On Demand RSS feed
  (`RssFeedParser.kt:23`).
- **Honest data note.** New days auto-download in background refresh
  (`BackgroundRefresh.kt:31-34`), and no Wi-Fi-only restriction exists
  anywhere in the shared code (grep for cellular or wifi found nothing).
- **Every claim was checked in code:**
  - 10/30 s skips: `PlayerScreen.kt:338-383`.
  - Lock Screen and Control Center: `AvPlatformPlayer.kt:48-49`,
    MPRemoteCommandCenter.
  - Saved position: `PlayerScreen.kt:96-104`, `saveListenProgress`.
  - Delete a day: `EpisodeListViewModel.kt:224`.
  - New-content notification: `BackgroundRefresh.kt:140-141`.
  - One More Thing joins the day when it's cross-posted to On Demand
    (every item for the show day is assembled). It's labeled "OMT" only if
    its title says so (`EpisodeAssembler.kt:113-121`), which current titles
    don't, so the copy never promises an "OMT" label.
  - About-screen links: `AboutScreen.kt:87-130`.
- **Never claim** CarPlay, a sleep timer, live radio, "ad-free", or how far
  back the archive goes.
- **No Android mentions** (2.3.10).
- **Google's Cast trademark line is included** (Google Cast UX guidelines,
  per the policy lens).

### 1.7 What's New (1.0.2)

**Keep Brett's submitted text** at the 1.0.2 resubmission (155 chars):
```
Fixed a crash that could happen when the app refreshed episodes in the background. New-episode notifications and automatic downloads are now more reliable.
```

The lines below are optional, only if Brett rewords it on purpose.

Optional (154 chars):
```
Fixes a crash that could happen right as the app opened, especially after it had refreshed in the background. Thanks for listening, and for your patience.
```
Optional, shorter (73 chars):
```
Fixes a crash some people saw when opening the app. Thanks for listening.
```
**Why:** it is factual. The crash was a startup race between the
background-refresh handler and the UI (fix `1e0bf71`). It names no other
product and promises nothing beyond the fix.

### 1.8 Screenshots

**Decision in force (Brett, 2026-10-13):** the App Store keeps `02-reviews`
and `09-reviews`. This doc proposes no change to that.

**At the 1.0.2 resubmission, same slots:** replace `06-catalog.png` and
`08-about.png` in both device sets with the versions now in
`screenshots/app-store/iphone-6.9/` and `ipad-13/`. Brett approved these
re-captions on 2026-10-13; they were rebuilt that day and were uncommitted
when this was written.
- 08's headline changes from "Smart. Funny. Independent." (an echo of the
  show's official tagline) to "Made By A Fan, For The Fans".
- 06 drops "ready every morning". Hour 4 posts around 10:20 a.m. PT, so the
  full day isn't ready by morning (builder comment in
  `buildPromoScreenshots.mjs`).
- **The new 08 is better than the live one, but not clean.** It still shows
  the in-app About card, which renders the show's tagline and the copied bio
  (`AboutScreen.kt:177-185`). Rewrite that card in the app, then recapture 08.

**Ready only if App Review ever flags 02/09:** upload the 7 files from
`screenshots/app-store/iphone-6.9/` and `ipad-13/` in this order:
`01-hero`, `03-episodes`, `05-downloads`, `04-player`, `06-catalog`,
`07-more`, `08-about`. Delete `02-reviews` and `09-reviews`. Search results
show slots 1-3, so 05-downloads moves to slot 3. That ordering is a
judgment call, not data.

### 1.8a Hero fix (independent of the caption decisions; both stores)

`01-hero` is the listing's biggest impersonation and accolade exposure, on
both stores (viewed: `screenshots/app-store/iphone-6.9/01-hero.png`,
`screenshots/play-store/phone/01-hero.png`):
- a serif "Armstrong & Getty" wordmark above the headline "Never Miss The
  Show.", framed by a laurel wreath (accolade imagery);
- a "PODCAST · ON DEMAND" lockup that echoes the official feed, "Armstrong &
  Getty On Demand", whose own description says "Accept no substitutes!"
  (baseline `# LENS: policy-risk`);
- no unofficial marker anywhere on the slide;
- a visible episode title with a superlative, "The Best Weekend Talk Show In
  America".

**Spec (both stores):**
- Kicker: "UNOFFICIAL FAN-MADE PODCAST APP" (replaces "PODCAST · ON DEMAND").
- Remove the laurels.
- Shrink the show name to a subline: "For Armstrong & Getty listeners".
- Headline: keep "Never Miss The Show." or use "Every Hour. / One Episode."
  (the §2.4b headline).
- Recapture the device area on a weekday episode whose title has no
  superlative. The raw captures are gone (`docs/promo-screenshots.md`), so
  this needs a fresh capture batch.

**When:** Play with the next listing edit if it's built by then, otherwise
the one after. App Store at the 1.0.2 resubmission if it's ready, otherwise
the next version (it most likely won't be ready, which is why §1.2 recommends
Alt A). None of this touches the quote captions or slides 02 and 09.

**App side (next build, agent work with Brett's OK):** the in-app header
renders the same "PODCAST · ON DEMAND" lockup in every capture
(`App.kt:124`). Change it to "UNOFFICIAL PODCAST APP" alongside the About-card
rewrite (`AboutScreen.kt:177-185`).

---

## 2. Google Play (com.nomnomsom.armstrongandgetty)

### 2.1 Title: keep

```
Armstrong & Getty Podcast App
```
(29/30)

**Considered and rejected:** `A&G Podcast: Armstrong & Getty` (30). It would
add the "A&G" token, worth about 10 Google searches/mo for 'a&g podcast'
(`google-keywords.json`). But it leads with the show's monogram, which looks
more official, and it would risk the current #1 ranks.

**Why keep:** the title is #1 on Play for 'armstrong and getty',
'armstrong & getty', 'armstrong and getty podcast', '...show', '...on demand'
and 'armstrong getty podcast' (`app-searches-google.json`). The baseline
re-check also found it #1 for 'armstrong and getty live', the misspelling
'armstong and getty', and 'jack armstrong joe getty' (baseline
`# LENS: play-store` §2). Never add "Official", emoji, ALL CAPS, call
letters, iHeart, "Free" or "Best".

### 2.2 Short description (80 max; indexed; also Play's meta and og description)

| | Text | Chars |
|---|---|---|
| Current | `Every hour of the A&G show, on demand. Download, cast, never miss it.` | 69/80 |
| **Recommended** | `Unofficial A&G podcast app: every hour in one daily episode to download or cast.` | 80/80 (exactly at the limit) |
| Alt 1 (softer label) | `Fan-made A&G podcast app: every hour in one daily episode to download or cast.` | 78/80 |
| Alt 2 (Auto-forward) | `Unofficial A&G podcast app: every hour in one episode, ready for Android Auto.` | 78/80 |

**Why:**
- The current text has no "podcast" and no unofficial signal. The Play page
  serves it as `<meta name="description">` and og:description (curl of the
  listing, 2026-10-13).
- 'a&g podcast' doesn't rank on Play: our_rank null among 14 results
  (`app-searches-google.json`).
- Putting "Unofficial" first lowers impersonation risk without touching the
  #1-ranked title.
- "to download or cast" describes features rather than issuing a
  call-to-action.
- "on demand" moves to the full description, which uses it twice. Re-check
  the 'armstrong and getty on demand' Play rank at +7 and +14 days.
- Use Alt 2 only after the Android Auto check in §5.

### 2.3 Full description (4000 max; indexed by Play; its opening is Google's app-pack snippet)

<details><summary>Current live description (1,580 chars)</summary>

```
Never miss a minute of The Armstrong & Getty Show.

Jack Armstrong and Joe Getty take an independent, contrarian look at news, politics, and modern life — merciless critics of mainstream media, on the air across the country every weekday morning. This app is the easiest way to catch every hour, on your own schedule.

DAILY EPISODES, ASSEMBLED FOR YOU
Every hour of the day's broadcast is stitched into one clean episode per show day, in order, so you can listen straight through — or jump to any segment with a tap.

LISTEN OFFLINE
Download a full day's show with one tap and take it anywhere: the commute, the gym, the dead zone with no signal. Delete it when you're done to free up space.

CAST TO ANY SPEAKER OR TV
Built-in Chromecast support sends the show to any Chromecast-enabled speaker or TV on your network, with your phone as the remote.

PICKS UP WHERE YOU LEFT OFF
Your position in every episode is remembered automatically — pause in the driveway, resume in the kitchen.

REAL PLAYBACK CONTROLS
Skip forward or back 10 or 30 seconds, change playback speed, or jump straight to the next segment. Full lock-screen and notification controls.

FRESH EVERY MORNING
The app refreshes in the background so today's show is ready before you are.

No accounts. No ads. No clutter — just the show.

This is an independent, fan-built companion app for The Armstrong & Getty Show. It is not affiliated with, endorsed by, or sponsored by Armstrong & Getty, iHeartMedia, or their affiliates. For the official show — stations, live streams, and more — visit armstrongandgetty.com.
```
</details>

**Recommended** (2,814/4000). The first line is
129 characters:
```
Every hour of The Armstrong & Getty Show, stitched into one daily podcast episode. An unofficial, fan-made app for A&G listeners.

This app is not affiliated with, endorsed by, or sponsored by Armstrong & Getty or iHeartMedia. It plays the show's public podcast feed, Armstrong & Getty On Demand, exactly as published.

The Armstrong & Getty Show is a weekday morning talk radio show about news, politics, and everyday life. Its podcast feed posts the show an hour at a time. This app puts each show day back together, in order, so you can listen straight through or jump to the part you want.

One daily episode, in order
Every hour of the day's broadcast in a single episode, with One More Thing added when it is posted to the main feed. Tap any segment to jump to it, or scroll back to earlier show days you missed.

Download for the drive
Download a full show day with one tap and listen offline: the commute, the gym, the dead zone with no signal. Delete it when you're done to free up space.

Android Auto
Browse recent episodes and play them from your car's Android Auto screen.

Cast to speakers and TVs
Google Cast-enabled: send the show to a Chromecast or Google Cast speaker or TV on your Wi-Fi, and keep your phone as the remote.

Picks up where you left off
Your place in every episode is saved as you listen. Pause in the driveway, resume in the kitchen.

Playback controls built for talk
Skip back or forward 10 or 30 seconds and listen at 1x, 1.25x, 1.5x, or 2x speed. Controls also work from the lock screen and the media notification.

Ready when you are
The app checks the feed in the background, downloads new hours after they post, and can notify you when they arrive.

More from the show, one tap away
Quick links to the show's own website, its how-to-listen page, Hot Links, and the show's other podcasts: One More Thing, Extra Large Interviews, and Select Cuts.

On demand, not live
This is a podcast app, not a live radio stream. Hours appear after the show posts them to its podcast feed. For live radio and station listings, visit armstrongandgetty.com.

Good to know
- No account and no sign-up.
- The app adds no ads of its own. Episodes play exactly as the show publishes them, including the show's own sponsor messages.
- New hours download automatically over any connection, including mobile data. Delete old days now and then to save space.

About this app
This is an independent, fan-built companion app for listeners of The Armstrong & Getty Show. It is not affiliated with, endorsed by, or sponsored by Armstrong & Getty, iHeartMedia, or their affiliates. The show's name is used only to describe the content the app plays. For the show itself, its stations, and live streams, visit armstrongandgetty.com.

Google Cast, Chromecast, and Android Auto are trademarks of Google LLC.
```

**Alternate** (if Brett doesn't confirm Android Auto, §5): delete the
"Android Auto" heading and its line, and change the trademark line to
"Google Cast and Chromecast are trademarks of Google LLC."

**Why:**
- **The opening is a Google web result.** The Play listing is #1 in Google's
  mobile app pack for 'armstrong and getty' and 'armstrong getty' (one merged
  Google Ads bucket of 9,900/mo), 'armstrong and getty show' (590), 'armstrong and getty app' and
  'armstrong & getty podcast app' (`serps.json` mobile, type app;
  `google-keywords.json`). Google shows about the first 135 characters as the
  snippet. Today that snippet is "Never miss a minute of The Armstrong &
  Getty Show. Jack Armstrong and Joe Getty take an independent, contrarian
  look at news, politics, ..." (`serp-mobile-armstrong-and-getty.json`). The
  new first line carries the benefit, "podcast" and "unofficial".
- **The disclaimer moves to paragraph 2.** In the live text it is the last
  paragraph, below the "Read more" fold.
- **Indexed terms go from absent to natural**, counted by script, recommended
  vs current: podcast 6 vs 0, talk radio 1 vs 0, Android Auto 3 vs 0, on
  demand 2 vs 0, Google Cast 3 vs 0, A&G 1 vs 0, Armstrong & Getty 6 vs 3.
  There is no keyword list and no repetition block.
- **"On demand, not live."** We rank #1 on Play for 'armstrong and getty
  live' (baseline play lens), and that query has 2,400/mo on Google
  (`google-keywords.json`). Saying it up front avoids "doesn't play live"
  1-star reviews.
- **No hosts' names.** Play's Metadata policy says "Don't use a celebrity's
  name ... without permission". The show name alone describes the content.
  Trade-off: this may loosen today's #1 Play rank for 'jack armstrong joe
  getty' (90/mo on Google, `google-keywords.json`). Both surnames stay in the
  title, so most of it should hold; re-check at +7 and +14 days.
- **Sentence-case headers.** Play warns against all caps used to manipulate
  ranking.
- **Every claim was checked in code:**
  - Speeds: `PlaybackController.kt:207-213`.
  - 10/30 s skips in the media notification: `PlaybackService.kt:234-249`.
  - Android Auto browse of recent days: `PlaybackService.kt:403-461`.
  - Background check, auto-download and notification:
    `NewEpisodeCheckWorker.kt:83-115`.
  - Any-network downloads: `AGPodcastApp.kt:58-59`, `NetworkType.CONNECTED`.
  - About links (Hot Links, One More Thing, Extra Large Interviews, Select
    Cuts): `AboutScreen.kt:94-130`.
- **What it does not claim:** no "next segment" button (none exists, §1.6),
  no CarPlay, no sleep timer.
- **"Google Cast-enabled" wording and the trademark line** follow Google's
  Cast branding rules (policy lens).
- **Once a Wi-Fi-only setting ships**, change the third "Good to know" bullet
  to: "New hours download automatically; choose Wi-Fi only in Settings to
  save mobile data."

### 2.4 Screenshots and feature graphic

**Live today:** 7 phone and 7 tablet images. The tablet set was uploaded to
both tablet slots, so the web carousel shows 21 images: 7 phone and 7
tablet, with the 7 tablet images shown twice (`app-info.json` google.images;
baseline play lens). The set includes:
- a laurel-wreath hero with a "PODCAST · ON DEMAND" kicker (fix: §2.4a);
- quoted review fragments on 6 feature slides;
- the old 08 headline "Smart. Funny. Independent.";
- "The Best Weekend Talk Show In America" visible as an episode title.

**Decision in force (Brett, 2026-08-24): the feature-slide quote captions
stay.** Play rejected the `02-reviews` 5-star review wall on 2026-08-24. The
feature-slide quote captions passed the resubmission and are live. They are
verbatim excerpts of real reviews, not presented as reviews, with no star
imagery. At the next listing edit, upload the re-captioned `06-catalog.png`
and `08-about.png` from `screenshots/play-store/phone/` and `tablet/`
(approved 2026-10-13). That removes the official-tagline headline and the
"ready every morning" claim. Everything else stays, apart from the hero and
feature-graphic fixes in §2.4a.

Residual risk: Play's Metadata policy bars unattributed testimonials in the
description, and its feature-graphic guidance bars user testimonials. The
caption quotes passed review after 2026-08-24 and stay by owner decision.
(Play's screenshot guidance itself says "Do not include any content that
reflects or suggests Google Play performance, ranking, accolades or awards";
that bears on the hero's laurels, §2.4a, not on the captions.)

### 2.4a Hero and feature graphic (independent of the caption decision)

**Hero (`01-hero`):** same problem and spec as the App Store hero, §1.8a
(unofficial kicker, no laurels, show name as a small subline, recapture on an
episode title with no superlative). Ship it with the next Play listing edit
if it's built by then.

**Feature graphic (1024x500).** The current one
has a large "Armstrong & Getty" wordmark and a "PODCAST · ON DEMAND" lockup,
with no unofficial marker. That lockup echoes the official podcast art
(policy lens, item 5).

| Element | Text | Chars |
|---|---|---|
| Kicker | `Unofficial fan-made podcast app` | 31 |
| Headline | `Every hour of the show. One daily episode.` | 42 |
| Feature line | `Download · Cast · Android Auto` | 30 |

- Right side: a flat crop of the episodes list, with no device frame. The
  app's own gold A&G tile is fine.
- Remove the big wordmark, the lockup, laurels, stars, "Free", any
  call-to-action, and Google or Chromecast logos (use words instead).
- Keep text away from the edges.
- No source file exists in the repo, so add it as a `feature-graphic` profile
  in `tools/promo-screenshots`.
- Drop "· Android Auto" if §5 isn't confirmed.

### 2.4b Fallback only: a quote-free Play set (Option A)

Use this only if Play flags the feature-slide captions. It is not
recommended while the caption decision above is in force.

**Option A spec (android-phone 1080x1920 and android-tablet profiles only):**

Rules for every slide: no quotation marks, review text, stars, laurels,
"Free" or call-to-action; taglines at most 20% of the image; real UI only.

| Slot | Slide | Headline | Subline (plain text, no quote marks) | Source |
|---|---|---|---|---|
| 1 | 01-hero | Every Hour. / One Episode. | Per §1.8a / §2.4a (unofficial kicker, no laurels) | episodes list |
| 2 | 03-episodes | Every Show Day, / In Order | Each hour stitched into one daily episode. | 01-episodes |
| 3 | 04-player | Jump To Any / Segment | Skip 10 or 30 seconds. Play at up to 2x. | 02-player |
| 4 | 05-downloads | Download For / The Drive | A full show day, one tap. Plays offline. | 03-downloads |
| 5 | NEW 09-auto | On Your Car's / Screen | Browse and play with Android Auto. | Desktop Head Unit capture |
| 6 | NEW 10-cast | Cast It To The / Kitchen | Works with Chromecast and Google Cast speakers. | Real phone + Chromecast capture (emulators can't discover Cast devices) |
| 7 | 06-catalog | Missed A Day? / It's Still Here. | Recent show days, ready to play. | 05-catalog |
| 8 | 07-more | More A&G, / One Tap Away | Show site, Hot Links and more A&G podcasts. Footer: Unofficial. Not affiliated with Armstrong & Getty or iHeartMedia. | 06-morefrom |
| dropped | 08-about | n/a | The captured About card shows the show's tagline and bio (`AboutScreen.kt:177-185`). Bring it back only after that copy is rewritten. | n/a |

Build notes:
- The raw `ag-8-22-26` captures are gone (`docs/promo-screenshots.md`).
  `--recaption` can redraw the caption band of feature slides 03-07, but the
  hero and the new 09/10 slides need a fresh capture batch.
- Recapture on an episode whose title has no superlative.
- Gate 09-auto and 10-cast to the Play profiles; Android imagery is barred
  from the iOS set (2.3.10).
- `docs/promo-screenshots.md` currently says captions must be verbatim review
  excerpts. Option A needs that rule changed for the Play profiles.
- If the Cast capture isn't feasible, ship 7 slides. Never mock it up.
- Upload the tablet set to one tablet slot only, or build a distinct 7-inch
  set, so the carousel stops repeating the 7 tablet images. First confirm in
  Play Console that an empty 7-inch slot is allowed. (This applies under the
  caption decision in force too.)

### 2.5 Optional: custom store listing for "live" searches

Play allows keyword-targeted custom listings. 'armstrong and getty live' has
2,400/mo and '...live 650' 720/mo on Google (`google-keywords.json`,
`discovery-keywords.json`), and the default listing is #1 on Play for
'armstrong and getty live'.

Short description (79/80):
```
Not live radio: every hour of the A&G show as one daily episode, after it airs.
```
Opening paragraph (236 chars). The rest matches §2.3:
```
Looking for the live show? This app doesn't stream live radio. It plays every hour of The Armstrong & Getty Show from the show's podcast feed after it airs, stitched into one daily episode. An unofficial, fan-made app for A&G listeners.
```
If Play generates a description for the listing, discard any version that
drops the disclaimer.

---

## 3. Shared wording (use the same words everywhere)

| Topic | Say | Never say |
|---|---|---|
| Ads | "The app adds no ads of its own. Episodes play exactly as the show publishes them, including the show's own sponsor messages." | "No ads", "ad-free", "skips ads" |
| Live | "plays the show's podcast hours after they air; it doesn't stream live radio" | "live", "listen live", "live radio" as a feature |
| Affiliation | "unofficial, fan-built companion app ... not affiliated with, endorsed by, or sponsored by Armstrong & Getty, iHeartMedia, or their affiliates" | "official", "approved", "endorsed", "as heard on" |
| Casting | "AirPlay" (iOS only); "Google Cast-enabled: Chromecast and Google Cast speakers and TVs" + "Google Cast and Chromecast are trademarks of Google LLC." | "Chromecast built in" (Google's device certification name) |
| Speed and skips | "10 or 30 seconds", "1x, 1.25x, 1.5x or 2x" | "any speed", "next segment button" |
| Car | Play: "Android Auto" (after §5). iOS: nothing | "CarPlay" (doesn't exist), any Android term on iOS |
| The show | "The Armstrong & Getty Show is a weekday morning talk radio show about news, politics and everyday life." | Official tagline or bio ("Smart. Funny. Independent Perspective.", "merciless critics of mainstream media", "Accept no substitutes") |

---

## 4. Policy checklist

Every row below was checked against the recommended text. Lengths, the
keyword-field rules and the banned-term scans were run by script. The scan
looked for "no ads", "ad-free", "best", "#1", "official", "free", Android,
CarPlay, sleep, the copied tagline and bio, iHeart, host names, quote marks,
"live", "next segment" and station call letters, and every hit was reviewed.
"iHeart" appears only inside disclaimers. "live" appears only in negations
("not live", "doesn't stream live radio") or in pointers to the show's own
site for live listening. "free" appears only in "free up space".

| # | Item | Rule | Result |
|---|---|---|---|
| A1 | Name unchanged | Apple 4.1(c), 5.2.1 (brand in name) | **Unchanged risk.** Structural, not created by this copy. Never add "Official". Mitigation: written permission from the show (`owner-actions.md` #9); keep any reply as evidence for Apple 5.2.2 and Play IP "authorization upon request". |
| A2 | Subtitle (all three) | 2.3.7: no other apps, prices or unverifiable claims; ≤30; 4.1(c) | Pass. No app or company names, no "free", "best" or "#1". "Talk Radio" describes the show and §1.6 says "not live". Alt A (recommended for 1.0.2) puts "Unofficial" in the search card; the "Every Hour" option only with the hero fix (§1.2) |
| A3 | Keyword field (all eight) | 2.3.7 + Apple search guidance: ≤100, commas, no spaces, no repeats of name, subtitle or category words, no trademarks, competing apps, call letters or celebrity names | Pass (script). 12 or 13 tokens each, ≤100 chars, no repeats, none from the excluded list |
| A4 | Promotional text | 2.3 accuracy; 2.3.7 pricing | Pass. No price, no ranking, "Unofficial" included, AirPlay is a shipped feature |
| A5 | Description | 2.3 / 2.3.1 accurate metadata | Pass. Every feature claim checked in code (§1.6); "not live" stated; exact ads wording; false next-segment claim removed; "in the order the hours were posted", not "broadcast order" |
| A6 | Description | 2.3.10 other platforms | Pass. No Android or Android Auto anywhere in iOS copy |
| A7 | Description | 4.1, 5.2.1 impersonation and IP | Pass. Unofficial in line 1 plus disclaimer paragraph; no copied official tagline or bio; no host names, logos or art |
| A8 | Description | Google Cast branding | Pass. Trademark line included |
| A9 | What's New | 2.3 | Pass. Factual (the submitted text is kept) |
| A10 | Screenshots 02/06/08/09 | n/a | 06 and 08 replaced with the approved re-captions at the 1.0.2 resubmission; 02 and 09 kept by owner decision (2026-10-13) |
| A11 | Screenshot 01-hero | 4.1(c) impersonation; 2.3 | **Open** until the §1.8a fix ships: laurels, "PODCAST · ON DEMAND" lockup, no unofficial marker |
| G1 | Title | Play Metadata (≤30, no emoji, caps, ranking or "Official"); Impersonation | Pass. Unchanged, 29 chars |
| G2 | Short description | Play Metadata: no testimonials, price, ranking or CTA | Pass. "Unofficial" first; features, not commands |
| G3 | Full description | Play Metadata: no testimonials, repetitive or unrelated keywords, word lists, comparisons, ranking or price; no all caps to game rank | Pass. Natural term counts (§2.3); sentence-case headers; no other app named; iHeartMedia only in the disclaimer |
| G4 | Full description | Play Metadata "Don't use a celebrity's name … without permission" | Pass. Hosts' names removed |
| G5 | Full description | Impersonation: "Don't imply that your app is related to or authorized by someone that it isn't" | Pass. Unofficial in line 1, disclaimer in paragraph 2 and at the end, "names used only to describe the content" |
| G6 | Full description | Misleading claims | Pass. Exact ads wording; "not live"; data-use bullet; Android Auto gated on §5 |
| G7 | Screenshots, captions (decision in force) | Metadata (testimonials, in the description); preview assets (accolades, ≤20% taglines, no CTA) | **Accepted by owner (2026-08-24).** The captions passed review after the 2026-08-24 rejection |
| G7b | Screenshots, Option A (fallback only, §2.4b) | Same rules | Pass once built; used only if Play flags the captions |
| G7c | Screenshot 01-hero | Preview assets (accolades, "Best"); Impersonation | **Open** until the §2.4a fix ships (laurels, lockup, no unofficial marker) |
| G8 | Feature graphic | Preview assets; Impersonation | Pass once rebuilt. Unofficial kicker; no wordmark lockup, laurels, "Free" or logos |
| G9 | IP | Play IP policy | **Unchanged structural risk** (name and icon use the show's brand). No show logos, art, host photos or audio in any asset. Mitigation: written permission from the show (`owner-actions.md` #9); keep any reply as evidence for Apple 5.2.2 and Play IP "authorization upon request" |

---

## 5. Before publishing: Brett's checks and decisions

1. **Android Auto.** v2.0.3, the Auto fix, has been live on Play since Aug
   31, 2026 (listing "Updated on Aug 31, 2026"; What's new "Fixes an issue
   where the app didn't load properly in Android Auto"). Play rejected Auto
   twice before that. Do a quick head-unit or Desktop Head Unit check, then
   keep the Auto lines. If it fails, use the §2.3 alternate.
2. **"conservative"** in the Apple keyword field: yes or no (§1.3).
3. **Apple subtitle:** Alt A (recommended for 1.0.2), "Every Hour of A&G Talk
   Radio" (only with the hero fix) or Alt B (§1.2). **Category:** News
   primary or not (§1.4).
4. **Before replacing the Apple keyword field,** save the current one to
   `tools/seo/data/`.
5. **After each change goes live,** search on a US iPhone and in the Play
   app at +7 and +14 days for:
   - the brand set: armstrong and getty, armstrong & getty, ...podcast,
     ...on demand, ...radio, ...live;
   - the abbreviations: a&g, a&g podcast;
   - the generic terms: talk radio, conservative talk radio.

   Note our position and any ad above us. Watch for any brand-rank drop from
   the "On Demand" moves.

## Sources

- **Data:** `tools/seo/data/2026-10-13/digest/` (app-searches-apple.json,
  app-searches-google.json, store-apple-keywords.json,
  store-google-keywords.json, google-keywords.json, serps.json,
  app-info.json) and raw `serp-mobile-armstrong-and-getty.json`.
- **Baseline lenses** in `baseline-reports.md`: app-store, play-store,
  policy-risk.
- **Re-checked live on 2026-10-13 (free):**
  - iTunes Search API for: armstrong and getty radio, armstrong and getty
    talk radio, a&g, a&g podcast, armstrong & getty, h&m (limit=100 and
    limit=200; results shift with the limit).
  - iTunes lookup for id 6796129205.
  - The Play listing HTML.
- **Policies** (as fetched in the policy lens):
  - [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
    2.3, 2.3.1, 2.3.7, 2.3.10, 4.1, 5.2.
  - [Apple search guidance](https://developer.apple.com/app-store/search/).
  - [Play Metadata](https://support.google.com/googleplay/android-developer/answer/9898842).
  - [Play preview assets](https://support.google.com/googleplay/android-developer/answer/9866151).
  - [Play Impersonation](https://support.google.com/googleplay/android-developer/answer/9888374).
  - [Play IP](https://support.google.com/googleplay/android-developer/answer/9888072).
  - [Google Cast UX guidelines](https://developers.google.com/cast/docs/ux_guidelines).
- **Prior art:** `../renew/docs/store-listing-copy.md`.
