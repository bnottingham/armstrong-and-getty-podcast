# Owner actions: the manual half of the search & store plan

Last updated: October 13, 2026

These are the steps only Brett can do: they need his accounts, judgment or name. Everything here
is drafted and ready, and **none of it has been done on his behalf.** The strategy and evidence
are in `seo/strategy.md`, and the exact store text is in `seo/store-listing-copy.md`. This
checklist links to that copy instead of repeating it.

When you finish a step, add `DONE YYYY-MM-DD` under it so the next session doesn't redo it.

> This repo is public. Once committed, this file (including the outreach drafts below) is public
> too. See strategy §8, question 1.

## Priority order at a glance

| # | Action | Impact | Your time | When |
|---|---|---|---|---|
| 1 | Search Console: verify the property, submit the sitemap, save a baseline | High | 20 min | Now |
| 2 | Answer the decisions that unblock copy (Android Auto first) | High (unblocks 3–6) | 10 min | Now |
| 3 | Check station rows, review the site changes, deploy, request indexing | High | 45 min | This week |
| 4 | Play Console listing edit (text and 06/08 screenshots; no release) | High | 30 min | This week |
| 5 | Bing Webmaster Tools import | Medium | 10 min | After #1 |
| 6 | App Store: remove 1.0.2 (61) from review, apply the listing changes, resubmit the same build; device search check | Medium–High | ~45 min + 15 min | Once the store copy is final (after #2) |
| 7 | Store website fields | n/a (already set) | 2 min check | When editing listings |
| 8 | Ratings: decide on the in-app review prompt | High | 5 min decision | Before 1.0.3 is built |
| 9 | Permission note to the show (Mailbag) | High upside, high stakes | 10 min | After #3, #4, #6 and the About-screen fix ship |
| 10 | Community and directory outreach (Reddit, AlternativeTo, GitHub) | Low–Medium | 30 min, spread out | 2+ weeks after #9 |
| 11 | What not to do | — | read once | — |

---

## 1. Google Search Console: Impact HIGH · ~20 min · now

The site is already in Google: it appears in 9 of 27 desktop and 5 of 26 mobile tracked SERPs
(`strategy.md` §3.1). Search Console is how you'll measure everything from here on. The site
promises no analytics, so this is the only web measurement you'll have.

**1a. Verify the URL-prefix property.** Don't use a Domain property: that needs DNS, and a
web.app host doesn't give you DNS.
1. Open https://search.google.com/search-console and sign in with the Google account that
   created the site's verification tag (`docs/index.html:9`, `google-site-verification`).
   If `https://armstrong-and-getty-podcast.web.app/` is already in the property list, skip to 1b.
2. Property menu → **Add property** → **URL prefix** → paste:
   ```
   https://armstrong-and-getty-podcast.web.app/
   ```
3. Choose **HTML tag** → **Verify**. The tag is already live.
   If verification fails, the tag belongs to a different Google account. Copy the new tag
   Search Console shows you, ask an agent to replace line 9 of `docs/index.html`, deploy, and
   click Verify again. **The tag must survive every homepage rewrite.**

**1b. Submit the sitemap** (once now, and once more after the deploy in #3):
Sitemaps → "Add a new sitemap" → type `sitemap.xml` → **Submit**. Today it lists 3 URLs. After
the website deploy it should list 5: `/`, `/how-to-listen`, `/podcast-feeds`, `/support`,
`/privacy`. Don't resubmit it weekly.

**1c. Save a baseline before the site changes go live.** If the property is new, data can take a
few days to appear. Export it as soon as it does, ideally before the deploy in #3.
1. Performance → Search results → Date: **Last 3 months** → **Export** (CSV).
2. Save the file as `tools/seo/data/2026-10-13/gsc/performance-baseline.csv`. That folder is
   gitignored.
3. Note the clicks, impressions and average position for queries containing `live`, `demand`,
   `podcast` and `listen` (+ New → Query → "Queries containing").

**1d. After the deploy (#3): request indexing, once per URL.**
URL Inspection (the search bar at the top) → paste a URL → wait → **Request indexing**. Do these
in order:
```
https://armstrong-and-getty-podcast.web.app/how-to-listen
https://armstrong-and-getty-podcast.web.app/podcast-feeds
https://armstrong-and-getty-podcast.web.app/
https://armstrong-and-getty-podcast.web.app/support
```
Check that each URL loads in a browser first. Don't request again later: it doesn't move a page
up the queue.

**1e. How to check indexing (don't use `site:`).** `site:` gave false negatives in Renew.
Use these instead:
- **URL Inspection** should say "URL is on Google".
- **Indexing → Pages**: watch "Crawled / Discovered – currently not indexed" for the new pages.
- **Exact-phrase Google search** for a sentence that appears only on that page, in quotes. For
  example, for /support:
  `"Why is the show one big daily episode instead of separate hours?"`
  For the new pages, use a sentence from their first paragraph once they're deployed.

**1f. Optional (~30 min): connect Search Console to the local OpenSEO.** This lets agents
read queries and inspect URLs for free (`inspect_urls`, `get_search_console_performance`). It
needs a Google OAuth client in `../open-seo/.env.local`; see
`open-seo/docs/SELF_HOSTING_GOOGLE_SEARCH_CONSOLE.md`.

---

## 2. Decisions that unblock the copy: Impact HIGH · ~10 min · now

Answer in chat or write them into `strategy.md` §8. The first one gates text on the site, on Play
and in outreach.

1. **Android Auto.** Is Auto approved for the live Play version 2.0.3? Check Play Console →
   Advanced settings → **Form factors** → Android Auto status, and look for an Auto review
   message in the Inbox. Better still, run a quick Desktop Head Unit check. Until it's confirmed,
   use the no-Auto alternates in `store-listing-copy.md` §2.3 and keep Auto off the website.
2. **"conservative" in the App Store keyword field:** yes or no (`store-listing-copy.md` §1.3).
3. **App Store subtitle and category:** Alt A, `Unofficial A&G Talk Radio Show` (recommended
   for the 1.0.2 resubmission), `Every Hour of A&G Talk Radio` (only in a submission that also
   ships the hero fix, §1.8a) or Alt B; and News or Entertainment as primary
   (`store-listing-copy.md` §1.2, §1.4). Questions 2 and 3 are needed before #6.
4. **Station table:** will you verify the rows now and re-check them each quarter (#3)? If not,
   the hub ships with the flagship and the official finder link only.
5. **Public repo:** commit `seo/`, or keep it local?

---

## 3. Website: check station rows, review, deploy, request indexing · Impact HIGH · ~45 min · this week

The agent-built changes are in `docs/`: the homepage rewrite, `/how-to-listen`, `/podcast-feeds`,
the copy fixes, the favicon and the sitemap. **No agent deploys.** That step is yours.

**3a. Verify station rows before `/how-to-listen` goes live (~20 min).** Publish only rows you
confirmed on the station's **own** site that day. Fix the time or drop the row if it doesn't match.

| Station | Check here | What the agent saw on 2026-10-13 |
|---|---|---|
| Talk 650 KSTE, Sacramento (flagship) | https://kste.iheart.com/schedule/ | 6:00–10:00 AM |
| 790 KABC, Los Angeles | https://www.kabc.com/armstrongandgetty/ | Mon–Fri 6:00–9:00 AM |
| 1190 KEX, Portland | https://1190kex.iheart.com/schedule/ | 6:00–9:00 AM |
| 810 KSFO, Bay Area | https://www.ksfo.com/armstronggetty/ | weekday mornings, no time listed |
| WMBD, Peoria | https://wmbdradio.com/armstrong-getty | weekdays 9–11 AM |
| KPRC 950, Houston | https://kprcradio.iheart.com/schedule/ | 2:00–5:00 AM |
| 770 KTTH, Seattle | the station's own schedule page | **not on the page** (unverified). Add it only if KTTH's own site confirms it |

Any other station: add it only if its own site lists the show. Always link the official finder
(https://www.armstrongandgetty.com/howtolisten/). Put the date you checked in the page's
"Last checked" line.

**3b. Review the diff, then deploy (~15 min).**
1. Read `git diff docs/` and open `docs/how-to-listen.html` and `docs/podcast-feeds.html` locally.
2. Check:
   - the footer disclaimer is on every page;
   - no bare "no ads" or "ad-free" is left: `grep -rin "no ads\|ad-free" docs/` should only find
     the shared wording "adds no ads of its own" (on 2026-10-13 every hit was that phrase); any
     other hit is a bare "no ads" claim to fix;
   - `node --test 'tools/seo/*.test.mjs'` passes (it also checks the ads wording, the
     next-segment and "every morning" claims, links, JSON-LD and the sitemap);
   - the `google-site-verification` tag is still on line 9 of `docs/index.html`;
   - nothing mentions Android Auto unless you confirmed it in #2.
3. Deploy:
   ```bash
   firebase deploy --only hosting
   ```
4. Then do 1b (resubmit the sitemap) and 1d (request indexing).

**3c. Official store badges (~10 min, before or after the deploy).** The site's App Store and
Google Play badges are hand-drawn (an Apple logo path and a redrawn Play triangle). Apple's and
Google's badge guidelines ask for the official, unaltered artwork. Download the official black
"Download on the App Store" badge (SVG) from Apple's Marketing Tools and the official "Get it on
Google Play" badge from Google Play's badge page, and save both in `docs/assets/img/`. An agent
then swaps them into the three `.badges` blocks (`index.html`, `how-to-listen.html`,
`podcast-feeds.html`) as `<img>` inside the existing store links. Until then, don't copy the
hand-drawn badges onto any further pages.

**3d. Already handled in the draft (no action):** the homepage no longer shows `about.png` (it
printed the show's tagline and bio), and the social image `og-image.png` now has the episode
title and description lines masked. The homepage's `player.png` and `library.png` still show
feed descriptions that name real people and one superlative title; reshoot them in the next
capture batch (with the hero and About recaptures) on days with neutral descriptions, or mask
those lines.

---

## 4. Play Console listing edit (no app release): Impact HIGH · ~30 min · this week

Google shows the opening of your full description as the snippet in the mobile app pack. You're
#1 in that pack for "armstrong and getty" (9,900/mo). Today that opening has no "podcast" and
nothing saying unofficial.

1. Play Console → Armstrong & Getty Podcast App → **Store presence → Main store listing**.
2. **Short description:** paste from `store-listing-copy.md` §2.2.
3. **Full description:** paste from `store-listing-copy.md` §2.3. Use the no-Auto alternate if
   #2.1 isn't confirmed.
4. **Screenshots:** in the Phone set, replace `06-catalog` and `08-about` with
   `screenshots/play-store/phone/06-catalog.png` and `08-about.png`. In the tablet set(s), replace
   them with `screenshots/play-store/tablet/06-catalog.png` and `08-about.png`. These are the
   re-captions you approved on 2026-10-13. Everything else stays per the caption decision in
   force (`store-listing-copy.md` §2.4). If the hero fix (§2.4a) has been built, replace
   `01-hero` in the same edit.
5. Optional: the same 7 tablet images sit in both the 7-inch and 10-inch slots, so the web
   carousel shows 21 images. If the Console allows it without a warning, keep one tablet set.
6. **Save → Publishing overview → Send changes for review.**
7. Keep the title and the Music & Audio category as they are.

Later: the rebuilt hero and feature graphic once an agent builds them (§2.4a, P2), and a custom
store listing aimed at "live" searches (`store-listing-copy.md` §2.5, P3).

---

## 5. Bing Webmaster Tools: Impact MEDIUM · ~10 min · after #1

Bing's index feeds Microsoft Copilot and other search and AI products. Today none of the 45
Google AI Overviews in our data cites the site, so every crawler that can find the pages helps.
1. Go to https://www.bing.com/webmasters and sign in.
2. Choose **Import from Google Search Console**, allow read access, select
   `https://armstrong-and-getty-podcast.web.app/`, and click **Import**. The sitemap comes with it.
3. After the deploy in #3: URL Submission → submit `/how-to-listen` and `/podcast-feeds`.

---

## 6. App Store Connect: pull, edit and resubmit 1.0.2 (61) · Impact MEDIUM–HIGH · ~45 min + 15 min

**Status and plan (yours, 2026-10-13):** 1.0.2 (61), the launch-crash fix, went to App Review
around 6:58 PM PT on 2026-10-13. Keep it in review until the store copy is final, then remove it
from review, apply the listing-only changes and resubmit the **same build 61**. No rebuild, and
the version stays 1.0.2. If you've already done this, add `DONE YYYY-MM-DD` here rather than
doing it twice. Needs your answers to #2.2 and #2.3 first.

**6a. Save the current keyword field (2 min, first).** It's private. Paste it into
`tools/seo/data/2026-10-13/apple-keywords-before.txt` (gitignored) so the change is on record.

**6b. Remove from review.** App Store Connect → Apps → Armstrong & Getty Podcast App → iOS App
1.0.2 → **Remove from review** → confirm. The version becomes editable again.

**6c. Apply the listing-only changes (~30 min).** Paste each field from `store-listing-copy.md`:
- Subtitle (§1.2) and keyword field (§1.3), as a matched pair from the §1.3 table.
- Promotional text (§1.5) and description (§1.6).
- Category, only if you're switching it (§1.4, on the App Information page).
- Screenshots: replace only `06-catalog` and `08-about` in both device sizes, using the
  re-captioned files in `screenshots/app-store/iphone-6.9/` and `ipad-13/` (§1.8). **Slides 02/09
  stay** (your decision of 2026-10-13). If the hero fix (§1.8a) is built by then, replace
  `01-hero` too. The built-in browser can't upload files: drag the PNGs in yourself, or have an
  agent use Claude in Chrome's file upload.

**6d. Resubmit checklist.**
- The Build section still shows **61**. If it detached, re-add it with Add Build.
- What's New: keep the submitted text (`store-listing-copy.md` §1.7) unless you reword it on
  purpose.
- Release settings: Automatically release; release to all users immediately (no phased
  release); keep the existing rating.
- Save → **Add for Review** → **Submit for Review**.

**6e. Device search check (15 min, twice): before the resubmission, and about 7 days after 1.0.2
is live.**
On a US iPhone in the App Store app, search each term exactly as written:
```
armstrong & getty
armstrong and getty
a&g
a&g podcast
armstrong and getty radio
armstrong and getty on demand
joe getty
jack armstrong
talk radio
conservative talk radio
```
For each term, write down our position and whether an **Ad** card sits above us (and whose it
is), and take a screenshot. Save everything to `tools/seo/data/<date>/device-checks/`. This is
the ground truth. DataForSEO's "&" results are invalid (`strategy.md` §3.2), and no tool sees the
Ads slot. Then compare **App Analytics → Sources → App Store Search** impressions and conversion
for 28 days before and after.

---

## 7. Store website fields: already set · 2 min check

Confirmed on 2026-10-13: Play's **Website** field shows `https://armstrong-and-getty-podcast.web.app`.
The App Store's **Developer Website** points at the site root, and its support and privacy links
point at `/support` and `/privacy`. Nothing to do except keep these values when you edit the
listings. `DONE 2026-10-13 (verified)`

---

## 8. Ratings: the in-app review prompt · Impact HIGH · 5 min decision

You have 0 ratings on both stores. On your own #1 App Store results you sit next to iHeart
(650,272 ratings) and Apple Podcasts (2,074,722). Single-show peers have 8–145 iOS ratings
(iTunes lookup, 2026-10-13: Mark Levin Show Podcast 8, Bob and Sheri 32, Walton and Johnson 51,
Clay and Buck 145). A realistic goal is **30–50 per store**.

**Decide one of these:**
- **(A) Recommended: build it.**
  - **Where:** iOS 1.0.3 at the earliest, never the crash-fix build. Android ships it after a
    Wi-Fi-only download setting, because new hours download on any network today.
  - **APIs:** `AppStore.requestReview` and the Play In-App Review API.
  - **When it can appear:** at least 3 distinct listening days with 10–15+ minutes each, and at
    least 5 days since install. Foreground only, at a natural pause. Never at launch,
    mid-playback, while casting, on Android Auto, or after a crash or a feed or download failure.
  - **Limits:** 120-day cooldown and a lifetime cap of 3. No "do you like the app?" question
    first, no button that triggers it, no incentives.
  - **Also:** a "Rate the app" row on the About screen that only deep-links to the store.
  - **Build:** an agent session in `Native Mobile/`, reusing `renew/docs/in-app-reviews.md`.
- **(B)** The "Rate the app" About row only.
- **(C)** Not yet.

Whatever you choose, reply to every review in App Store Connect and Play Console. Keep replies
short and factual. Point "doesn't play live" complaints to `/how-to-listen`.

Decision: ____ (date ____)

---

## 9. Permission note to the show (Mailbag): high upside, high stakes · ~10 min

This is the only route to an on-air mention or a link from armstrongandgetty.com (1,499
referring domains, against none known for us). It also asks the question Apple 4.1(c) and Play's
impersonation policy care about. It will put the app in front of the show and iHeartMedia, so:

**Send it only after:**
- the website deploy (#3);
- the Play listing edit (#4);
- the resubmitted 1.0.2 App Store description (#6);
- the in-app About screen rewrite (it currently shows the show's tagline and bio,
  `AboutScreen.kt:177-185`).

The show will look at all four. **Before sending,** decide whether to offer "or take it down",
and have a fallback name and icon in mind.

**To:** mailbag@armstrongandgetty.com (listed on https://www.armstrongandgetty.com/contact/)
**Subject:** A listener-built app for the A&G podcast. Are you OK with it?

```
Hi Jack, Joe, and the A&G crew,

I'm [a listener since ___] and an independent app developer. I built a free, unofficial app, the Armstrong & Getty Podcast App for iPhone and Android, and I want to make sure you're OK with it.

It takes your official "Armstrong & Getty On Demand" feed and puts each show day's hours together, in order, as one episode, with downloads and casting to speakers. Episodes play exactly as published, sponsor breaks included, and the app fetches each episode from your feed's own episode links, unchanged, the way any podcast app does. It's labeled as unofficial and not affiliated with you or iHeartMedia, it adds no ads of its own, and it links back to armstrongandgetty.com.

Two questions:
1. Are you OK with it using the show's name? If anything bothers you (the name, the icon, the wording), tell me and I'll change it[ or take it down].
2. If you like it, a mention would be very welcome, but no pressure at all.

https://armstrong-and-getty-podcast.web.app

Thanks for the show,
Brett Nottingham, Renew Studios
```

- Write it so it would be fine if read on air. Follow up once at most.
- Save any reply as written evidence of permission.
- Never call the app "approved" or "endorsed" unless they say so in writing.
- If they ask for a change, make it before anything else on this list.

Sent: ____ · Reply: ____

---

## 10. Community and directory outreach: Impact LOW–MEDIUM · spread out

**10a. Reddit: about 2 weeks after #9.** Message the moderators first. Post at most once per
community, never with identical text, and only if the mods say yes. r/ArmstrongandGettyShow
(180+ followers) ranks 10th organic on desktop for "armstrong and getty", above our site.
r/ArmstrongGetty has 160+.

Modmail:
```
Hi mods. I'm the developer of a free, unofficial app that puts each day's A&G podcast hours together into one episode (iPhone/Android). Before posting anything I wanted to ask whether one post introducing it is OK here, clearly labeled as mine and asking for feedback. Happy to do it however you prefer, or skip it. Thanks!
```

Post (only after a yes). Title: *I built a free, unofficial app that plays each A&G show day as
one episode. Looking for feedback*
```
Disclosure: I'm the developer, and the mods OK'd this post.

The On Demand feed posts every hour as its own episode, plus One More Thing and other extras, and the titles are lines from the show rather than dates. I wanted to just press play on "Tuesday's show", so I built an app that reads the official feed and puts each show day's hours together in order.

What it does: one episode per show day, downloads for offline, casting to Chromecast and Google Cast speakers (AirPlay on iPhone), [Android Auto, ]remembers your place, 10/30-second skips and speed control. No account.
What it doesn't do: it doesn't remove ads (the sponsor breaks are in the episodes as published) and it isn't a live stream.

It's unofficial and not affiliated with A&G or iHeartMedia. Details and store links: https://armstrong-and-getty-podcast.web.app

Bug reports and feature ideas welcome. I'll be in the comments.
```
- Keep "[Android Auto, ]" only if #2.1 is confirmed.
- For the second subreddit, rewrite the first two paragraphs.
- Don't ask for upvotes or ratings, and reply to every comment.
- Later, answer "how do I listen?" threads with `/how-to-listen` and say you're the developer.

**10b. AlternativeTo (~15 min, any time after #3).** Go to alternativeto.net → Suggest new
application, and disclose that you're the developer.
- **Name:** Armstrong & Getty Podcast App
- **Tagline:** Unofficial, free companion app that plays each Armstrong & Getty show day as one episode.
- **Description:**
  ```
  A free, unofficial companion app for listeners of The Armstrong & Getty Show podcast. It reads the show's public podcast feed and puts each show day's hours together, in order, as a single episode, with offline downloads, casting to Chromecast and Google Cast devices, AirPlay on iPhone, and resume. Episodes play as published, sponsor breaks included. No account required. Not affiliated with Armstrong & Getty or iHeartMedia. Submitted by the developer.
  ```
- **Platforms:** iPhone, Android · **License:** Free (proprietary) · **Tags:** podcast player,
  talk radio, offline listening.

**10c. GitHub repo profile (~5 min).** The repo is public, with no description and no homepage
(`gh repo view`). First decide question 5 in #2, then:
```bash
gh repo edit bnottingham/armstrong-and-getty-podcast \
  --description "Unofficial, fan-built companion app for The Armstrong & Getty Show podcast feed (not affiliated with Armstrong & Getty or iHeartMedia)" \
  --homepage "https://armstrong-and-getty-podcast.web.app"
```
Keep `tools/seo/data/` out of the repo (it's gitignored).

**Only after a positive reply from the show (optional):** a Show HN or dev write-up about the
episode assembly and cast handoff; a 60–90 second walkthrough video with your own narration and
**no show audio**; a trade-press pitch (Barrett Media first).

---

## 11. What not to do

- Don't call the app official, endorsed or approved, or "as heard on A&G", anywhere, without
  written permission.
- Don't use show logos, artwork or host photos. Don't upload show audio anywhere, and don't
  publish transcripts or copied episode titles or descriptions.
- Don't say "no ads" or "ad-free" on its own. Use "the app adds no ads of its own; the show's
  sponsor breaks are part of the episodes."
- Don't claim live radio, CarPlay or a sleep timer. Claim Android Auto only once you've confirmed it.
- No celebrity names (jack, joe), iHeart, Spotify, Omny or station call letters in the App Store
  keyword field. No "best", "#1", ratings or testimonials in Play text.
- Don't create per-station, per-city, per-day or per-episode pages, and don't generate pages from
  the feed.
- Don't put `aggregateRating` in structured data, and don't copy store ratings onto the site.
- No incentivized, gated or button-triggered review requests, and never ask for 5 stars.
- No bought or traded links, link services or paid reviews. Don't add our link to Wikipedia.
  Don't comment-promote on the show's Facebook, X or Instagram.
- Don't post to Reddit without moderator approval, and never post identical text twice.
- Don't register a domain containing the show's name. Stay on `armstrong-and-getty-podcast.web.app`.
- Don't test indexing with `site:`.
- Don't promise the show anything about their podcast measurement (downloads, listens) unless
  Omny or iHeart has confirmed it.
- Don't approve a DataForSEO pass over ~$3 without asking why. A full pass costs about $2.

## What agents handle (not you)

- Website pages, copy fixes and technical SEO in `docs/` (you review and deploy).
- The feature graphic, the hero fix (`store-listing-copy.md` §1.8a / §2.4a) and other
  screenshot-builder changes; the official badge swap once you've downloaded the artwork (#3c);
  the About-screen rewrite, the header-lockup change (`App.kt:124`) and review-prompt code, once
  approved.
- Free checks: the iTunes Search API and Play web search at +7 and +14 days.
- Pass 2 (≈2 weeks after deploy) and Pass 3 (≈4 weeks): `tools/seo/dataforseo_pass.py` stage1 and
  stage2, plus `digest_pass.py`, about $2 each with your OK. Results are appended to
  `strategy.md` §3.
