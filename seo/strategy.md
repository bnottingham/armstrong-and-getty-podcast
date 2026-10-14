# Armstrong & Getty — Search & App Store Strategy

Last updated: October 13, 2026 (Pass 1 baseline)

How the unofficial Armstrong & Getty Podcast App gets found on Google, the App Store
and Google Play without implying it is official. This doc covers the plan and the
measurement history. Companion docs:

- `seo/owner-actions.md`: the steps only Brett can do, in priority order.
- `seo/store-listing-copy.md`: the exact store text (subtitle, keywords, descriptions,
  promo text). It is the single source; this doc does not repeat it.
- `tools/seo/README.md`: the DataForSEO/OpenSEO tooling. Data snapshots live in
  `tools/seo/data/<date>/`, which is **gitignored (local only)**.

**Evidence convention.** Citations like `digest/serps.json` refer to
`tools/seo/data/2026-10-13/digest/`; `raw/…` means the raw API response in
`tools/seo/data/2026-10-13/`. Google positions are written **`rank_group (rank_absolute)`**:
the organic rank, then the absolute slot including SERP features. "–" means the page did not
appear in the results returned (depth 20). Volumes are Google Ads US monthly; **L** marks a
DataForSEO Labs estimate where Ads had none. Ads merges close variants: "armstrong and getty",
"armstrong & getty" and "armstrong getty" are one 9,900 bucket. Cluster totals are therefore
raw sums and only approximate.

**This repo is public** (`gh repo view`: `bnottingham/armstrong-and-getty-podcast`, PUBLIC).
The `seo/*.md` files, including the outreach drafts in `owner-actions.md`, become public
as soon as they're committed. See open question 1.

---

## 1. Positioning

**One line:** a free, unofficial, fan-built app that takes the show's official podcast
feed (*Armstrong & Getty On Demand*) and puts every hour of each show day, in order,
into one episode you can download, cast and resume.

### What we can claim (verified in code, read-only, 2026-10-13)

| Claim | Evidence |
|---|---|
| One episode per show day, items in posting order; One More Thing is included when it's cross-posted to On Demand (it's labeled "OMT" only if its title says so, which current titles don't) | `EpisodeAssembler.kt:113-121` (`extractHourLabel` falls back to a numbered label), `Segment.kt:13`; On Demand RSS, latest 150 items: 0 titles contain "One More Thing"/"OMT" |
| Whole-day download, offline playback | `EpisodeListViewModel.kt:143` `downloadDay`, `PodcastRepository.kt:152` |
| Chromecast/Google Cast on iPhone and Android; AirPlay on iPhone | `MediaRouteAction.ios.kt:24,44` (`AVRoutePickerView`); `PlaybackService.kt` |
| Speeds 1×, 1.25×, 1.5×, 2×; skip 10/30 s; lock-screen controls | `PlaybackController.kt:208` `listOf(1f, 1.25f, 1.5f, 2f)` |
| Background refresh; new-hour notifications; auto-download of new hours **on any network** | `NewEpisodeCheckWorker.kt:83-115` and `AGPodcastApp.kt:59` `NetworkType.CONNECTED` (Android); `BackgroundRefresh.kt` (iOS) |
| No account; no in-app purchases; the app shows no ads of its own | app code; privacy page |
| Android Auto | implemented (`automotive_app_desc.xml`, `MediaLibraryService`). **Claim it publicly only after the owner confirms that Play has approved Auto for the live 2.0.3**, because Play rejected Auto twice (v2.0, v2.0.2). |

### What we cannot claim

- **Official, endorsed, approved or "as heard on A&G"**: never, unless the show says so in writing.
- **Live radio.** The app plays the podcast hours after they post. "armstrong and getty live"
  (2,400/mo) lands on our listings anyway, so the copy must say plainly that it isn't live.
- **"No ads" / "ad-free" / ad skipping.** The episodes carry the show's own sponsor breaks.
  Use this wording everywhere: *"The app adds no ads of its own; the show's sponsor breaks
  are part of the episodes."* On the live site the bare claim appears in the homepage
  features lede, the /support FAQ (HTML and JSON-LD) and the privacy summary. It is also in
  both store descriptions (`digest/app-info.json`).
- **CarPlay or a sleep timer.** Neither exists (grep finds no `carplay`, `CPTemplate` or sleep timer).
- **"Ready every morning."** Hour 4 posts around 10:15–10:30 a.m. PT (feed observation,
  noted in `tools/promo-screenshots/buildPromoScreenshots.mjs`).
- **The show's own words.** Don't use "Smart. Funny. Independent Perspective." (the official
  tagline) or "merciless critics of mainstream media" (the official bio). Live today they
  appear in the homepage H2, both store descriptions, and the app's About screen
  (`AboutScreen.kt:177-185`, which slide 08 captures).

---

## 2. Demand map

### 2.1 Google clusters

| Cluster | ≈ Vol/mo (raw sum) | Main terms: vol / KD | Our position today (D / M) | Who holds page 1 | Verdict |
|---|---|---|---|---|---|
| **A. Brand head** | ≈11,000 | armstrong and getty 9,900/36 (armstrong getty KD 52) · …show 590/52 · …iheart 390/24 · …radio 110/35 | head: 12 (15) / 14 (20), plus **Play app pack #1 (7)** on M · …show: 8 (10) / 9 (14), plus Play pack #1 (6) | armstrongandgetty.com, iheart.com, reddit, spotify | Defend. The mobile app pack is our real surface. Page 1 for the head term isn't realistic. |
| **B. Podcast / on demand** | ≈6,850 | …podcast 4,400/24 (yearly −33%) · …on demand 1,900/18 (−16%) · & on demand 210/20 · …show today 90/24 · …podcast apple 90/6 L · …google podcast 90/23 L · …on demand podcast 70/18 L | podcast – / 8 (10) · on demand 12 (14) / – · & on demand 9 (10) / – · show today 4 (6) / 4 (6) | podcasts.apple.com, iheart.com, armstrongandgetty.com, spotify | **Target.** The homepage takes "podcast"; a new feeds page takes "on demand". |
| **C. One More Thing** | ≈160 | …one more thing 140/20 · …one more thing podcast 20 L | – / – | iheart.com, apple, official site | Secondary target on the feeds page. |
| **D. App intent** | ≈20 | …app 10 · …download 10 · …podcast app, …android auto, …chromecast, …carplay: no Ads volume | podcast app 5 (6), Play 8 (10) / failed · app: App Store 8 (10) / Play pack #1 (2) | apple, official site | Homepage. Tiny volume; this is a conversion surface. |
| **E. Live / how to listen** | ≈3,600 | …live 2,400/**9** · …live 650 720/2 L (+85%) · …listen live 140/0 L · …live stream 110/11 · …show live 90/9 L · listen to … live 70/13 · …live today 40/0 L · how to listen 20 | live – / – · live stream 5 (6) / – · how to listen 9 (11) / – | official site, iheart, radio.net, station sites, binnews (Dec 2021 list) | **Biggest winnable gap.** New `/how-to-listen` hub. |
| **F. Stations** | ≈850 (variants likely one bucket) | …kste 210/5 · 'talk 650 armstrong and getty' 210/8 L · 'armstrong and getty talk 650' 210/2 L · …650 170/1 L · …radio stations 40/0 · …stations 10/29 | – / – | kste.iheart.com, official site, wikipedia | Inside the hub: one sourced table. Never per-station or per-city pages. |
| **G. Station brand only** | ≈5,500 | talk 650 kste 3,600/5 · kste 650 1,900/2 | – / – | kste.iheart.com holds 7–8 of the top 10 on 3 of the 4 SERPs (1 on desktop 'talk 650 kste'), plus a knowledge panel on all four | **Don't target.** |
| **H. People** | ≈4,000 (≈1,600 A&G-relevant) | jack armstrong 2,400/15 (Google means a sportscaster and a pitcher) · joe getty 1,000/19 · katie green a&g 590/0 (−56%) · …cast 50/35 L (Labs: we're #8) | – / – | wikipedia, station bios, iheart, X, LinkedIn | **No pages.** One factual "who's on the show" line in the hub. |
| **I. Official-site navigation** | ≈850 | …website 320/55 L · …homepage 320/55 L · armstrongandgetty.com 210 | Labs: 8, 10, 11 (`digest/ranked-our-site.json`) | official site | Never optimize for these. Our snippets must read clearly unofficial, with a "looking for the official site?" link. |
| **J. Generic** | ≈250 | talk radio app 90/9 · conservative talk radio podcast 90/13 · podcast app android auto 70/27 | – / – | feedspot, iheart, spotify, apple listicles | No page. A single-show app can't satisfy "best of" intent. |

Sources: `digest/google-keywords.json`, `digest/discovery-keywords.json`, `digest/serps.json`.

### 2.2 What the brand SERPs look like (`digest/serps.json`, raw SERPs)

- **AI Overviews** appear on 24 of 27 desktop and 21 of 26 mobile SERPs. **None of the 45
  cites our site or our store listings.** The most-cited sources (overviews citing each domain
  anywhere in the answer, including inline links) are iheart.com (23 of 45),
  podcasts.apple.com (22) and armstrongandgetty.com (21) (raw `serp-*.json`).
- **The overviews disagree with each other.** The mobile "armstrong and getty live" overview says
  "6:00 AM – 9:00 AM" and cites kabc.com (`raw/serp-mobile-armstrong-and-getty-live.json`).
  The flagship's own schedule says "6:00 AM - 10:00 AM" (kste.iheart.com/schedule, fetched
  2026-10-13). A dated, sourced answer that explains why is the content gap.
- **People Also Ask**, by number of SERPs: "Are Armstrong and Getty still on the radio?" 24;
  "…get paid?" 19; "…conservative or liberal?" 18; "Who is Katie from/on…?" 12 + 12;
  "…still on the air?" 9; "Who are Armstrong and Getty?" 6; "Where can I listen to the
  Armstrong & Getty Show on demand?" 4; "…live today?" 3. Answer the factual, show-level
  questions. Skip pay, politics and personal life.
- **Mobile app packs** appear on 7 SERPs. Our Play listing is the first app on all 5 A&G ones
  (app, show, head term, armstrong getty, & podcast app). The other 2 (the KSTE brand searches)
  show a third-party "Talk 650 KSTE" radio app (`com.henapp.talk650kste`).
- **Google surfaces foreign App Store pages** for "armstrong and getty app": the JP storefront
  at 8 (10) and RU at 12 (15) (`raw/serp-desktop-armstrong-and-getty-app.json`).

### 2.3 Store demand (DataForSEO Labs store volumes; `digest/store-*-keywords.json`)

| Store | Relevant generics (vol) | Out of reach (vol) | Show-name analogs (vol) |
|---|---|---|---|
| App Store | talk radio 212 · talk radio stations 165 · conservative radio 67 · conservative podcasts 43 · conservative podcast 28 · conservative talk radio 26 · news talk 17 · news talk radio 17 | radio 21,090 · podcast 5,943 · conservative news 5,912 · podcasts 2,572 | mark levin 4,085 · mark levin podcast 782 · clay and buck 236 · bob and sheri 95 · walton and johnson 86. **Labs has no row for any A&G phrase.** |
| Google Play | conservative radio 90 · conservative radio talk shows 70 (Talk Right #1 on both) · morning radio talk shows 13 · conservative talk show podcasts 13 · talk radio app 10 · conservative talk radio podcasts 6 | podcast 9,169 (iHeart #6) · podcast app 2,260 (iHeart #14) | Our only Labs keyword is "armstrong application" (vol 0). Use the Play Console search-terms report. |

None of the App Store generics is easy: we're not in the top 100 for 'talk radio', whose top 2
apps have 29,154 and 2,522 ratings, and we're #72 for 'conservative talk radio'
(`digest/app-searches-apple.json`).

Takeaway: **show-name search is the store channel** (we're already #1). The work is
converting those visitors, through ratings, accurate copy and screenshots. Generic gains will
be small, and come mostly from adding "talk radio" and "A&G" to indexed fields.

---

## 3. Scoreboard

### Pass 1 — 2026-10-13 baseline

Spend **$1.75** (`ledger.jsonl`: Labs/Ads $1.17, backlinks $0.10, SERPs $0.23, app data $0.24).

#### 3.1 Google positions (live SERPs, US, depth 20; mobile = Android)

| Keyword | Vol | Desktop: site | Desktop: stores | Mobile: site | Mobile: stores |
|---|---|---|---|---|---|
| armstrong and getty | 9900 | 12 (15) | Play 14 (17) | 14 (20) | Play pack #1 (7) |
| armstrong getty | 9900 | 13 (17) | Play 16 (20) | – | Play pack #1 (5) |
| armstrong & getty | 9900 | not captured¹ | | not captured¹ | |
| armstrong and getty podcast | 4400 | – | – | 8 (10) | – |
| armstrong getty podcast | 4400 | – | – | 9 (11) | – |
| armstrong & getty podcast | 4400 | not captured¹ | | not captured¹ | |
| talk 650 kste | 3600 | – | – | – | – |
| armstrong and getty live | 2400 | – | – | – | – |
| jack armstrong | 2400 | – | – | – | – |
| armstrong and getty on demand | 1900 | 12 (14) | – | – | – |
| kste 650 | 1900 | – | – | – | – |
| joe getty | 1000 | – | – | – | – |
| armstrong and getty show | 590 | 8 (10) | Play 9 (11) | 9 (14) | Play pack #1 (6) |
| katie green armstrong and getty | 590 | – | – | – | – |
| armstrong and getty iheart | 390 | – | – | – | – |
| armstrong and getty kste | 210 | – | – | – | – |
| armstrong & getty on demand | 210 | 9 (10) | – | – | – |
| armstrong and getty one more thing | 140 | – | – | – | – |
| armstrong and getty live stream | 110 | 5 (6) | – | – | – |
| armstrong and getty radio | 110 | – | – | – | – |
| armstrong and getty show today | 90 | 4 (6) | – | 4 (6) | – |
| conservative talk radio podcast | 90 | – | – | – | – |
| how to listen to armstrong and getty | 20 | 9 (11) | – | – | – |
| listen to armstrong and getty | 20 | – | – | – | – |
| armstrong and getty app | 10 | – | App Store 8 (10)² | – | Play pack #1 (2) |
| armstrong and getty stations | 10 | – | – | – | – |
| armstrong and getty full show | – | – | – | failed³ | failed³ |
| armstrong and getty podcast app | – | 5 (6) | Play 8 (10) | failed³ | failed³ |
| armstrong and getty replay | – | – | – | – | – |
| armstrong & getty podcast app | – | failed³ | failed³ | – | Play pack #1 (2) |

¹ Lost to a filename collision ("&" and plain variants shared a slug). This is already fixed in
`dataforseo_pass.py:84-86`. ² This result is the Japanese storefront URL. ³ DataForSEO task
error 40101.

**Summary:** the site appears on 9 of 27 desktop and 5 of 26 mobile SERPs. It is top 10
(rank_group) on 6 desktop and 4 mobile SERPs, and top 3 on none. Play holds app pack #1 on 5
mobile SERPs.

#### 3.2 Store search positions (DataForSEO live search; Apple depth 100, Play returned 8–30 results per term; approximations of on-device search; `digest/app-searches-*.json`)

| Term | App Store | Play | Note |
|---|---|---|---|
| armstrong and getty | 1 | 1 | Apple top 5 next to us: iHeart 650,272 ratings, Apple Podcasts 2,074,722 |
| armstrong and getty podcast | 1 | 1 | |
| armstrong getty podcast | 1 | 1 | |
| armstrong and getty show | 1 | 1 | |
| armstrong and getty on demand | 1 | 1 | |
| armstrong & getty | 64 **invalid**⁴ | 1 | iTunes Search API (encoded "&"): **1** |
| armstrong getty | not captured¹ | not captured¹ | |
| armstrong and getty radio | not tracked | not tracked | iTunes API: **6** |
| armstrong and getty talk radio | not tracked | not tracked | iTunes API: **8** |
| a&g | – **invalid**⁴ | – | iTunes API: not in 192 results |
| a&g podcast | – **invalid**⁴ | – | iTunes API: **91** of 165 |
| joe getty | 2 | – | |
| jack armstrong | 10 | – | |
| jack and joe | 22 | – | |
| getty | 10 | – | |
| armstrong | 64 | – | |
| political podcast | 22 | – | |
| politics podcast | 37 | – | |
| podcast app | 43 | – | |
| conservative talk radio | 72 | – | |
| conservative podcast | 83 | – | |
| conservative radio · talk radio · talk radio podcast · talk show · talk shows · news talk · news talk radio · radio shows · radio podcast · daily podcast · podcast player · one more thing · sacramento radio | – | – | |
| station and other brands: kste · ksfo · kabc · kex · kfbk · kftk · kxl · iheart | – | – | Never targeted, by design |

Play "–" = not in the returned list (8–30 items), not "outside the top 100". iTunes API figures
are another approximation of App Store search; their order shifts with the `limit` parameter.

⁴ DataForSEO cut Apple queries at the "&". The 'armstrong & getty' item list is identical to
the one for 'armstrong'. The 'a&g' and 'a&g podcast' lists are identical to each other, and
their top 3 match a search for "a". These are not real positions. iTunes API spot checks were
run by curl on 2026-10-13.

#### 3.3 Authority, trust and listing state

| Metric | Pass 1 value | Source |
|---|---|---|
| Google keywords the site ranks for (Labs, ~mid-Sept) | 13 (positions 4–10: 3 · 11–20: 7 · 21–30: 3) · est. traffic ≈38 visits/mo (Labs ETV) · est. paid-equivalent value $10.49 | `digest/domain-rank.json` |
| Referring domains / backlinks (site) | none known (DataForSEO has no record of the domain) | `digest/backlinks.json` |
| armstrongandgetty.com, for scale | 1,499 referring domains · 53,397 backlinks · rank 371 | `digest/backlinks.json` |
| AI Overviews citing the site or our listings | 0 of 45 | raw SERPs |
| App Store ratings | 0 | iTunes lookup, 2026-10-13 |
| Play rating / downloads | no rating · 10+ downloads | Play listing curl, 2026-10-13; `digest/app-info.json` |
| Peer apps (iOS rating counts) | Single-show: Mark Levin Show Podcast 8 · Bob and Sheri 32 · Walton and Johnson 51 · Clay and Buck 145; multi-show: Talk Right 467 | iTunes lookup, 2026-10-13 |
| App Store listing | v1.0.1 · subtitle "Every Hour. On Demand." · Entertainment (primary), News (secondary) · 9 screenshots (incl. 02/09 review walls) | `digest/app-info.json`; iTunes lookup, 2026-10-13 |
| Play listing | v2.0.3 · short desc "Every hour of the A&G show, on demand. Download, cast, never miss it." · Music & Audio · 21 carousel images (tablet set repeated) | `digest/app-info.json`, Play curl |
| Store → site links | set: Play "Website", App Store "Developer Website" plus /support and /privacy | page curls, 2026-10-13 |

#### 3.4 Known gaps in Pass 1 data

- Apple "&" queries are invalid (⁴). This is **not fixed yet**: skip "&" terms for Apple in
  `dataforseo_pass.py` and check them on a device or with the iTunes API instead.
- The 2 Google keywords above were lost to the slug collision (fixed). 3 SERPs failed (40101).
- Mobile SERPs are Android-only. The Labs ranked-keyword snapshot dates from about mid-September.
- App Store positions come from DataForSEO and the iTunes API, not the App Store app. Neither
  shows the Apple Ads slot. On-device checks are the ground truth (`owner-actions.md` #6e).

> **Pass 2 goes here.** Copy §3.1–3.4 as "Pass 2 — YYYY-MM-DD", keeping the same rows in the
> same order. Add a "Changes since Pass 1" table (moved up or down, newly ranking, lost) and a
> "Decisions" list. Generate the §3.1 rows with the snippet in §7.

---

## 4. Plan by surface

Priority: **P0** now · **P1** next 2 weeks · **P2** next 1–2 months · **P3** later, or only when data says so.
Owner: **A** = agent builds (Brett reviews and deploys) · **B** = Brett only (see `owner-actions.md`).

### 4.1 Website (`docs/`, Firebase Hosting)

Status 2026-10-13: an agent has drafted most P0–P2 items in `docs/`, uncommitted and not
deployed. That includes `how-to-listen.html`, `podcast-feeds.html`, a 5-URL sitemap,
`favicon.ico` and the Smart App Banner. Brett reviews and deploys them (`owner-actions.md` #3).

| Pri | Action | Why (evidence) | Who |
|---|---|---|---|
| P0 | Fix the claims: exact ads wording everywhere (HTML and FAQ JSON-LD); remove the borrowed tagline and bio; put "Unofficial" in the homepage title, meta and kicker; never use "official/website/homepage" for ourselves | §1. The homepage already ranks 8–10 for "…website/homepage" (Labs), so people who want the official site land on us | A |
| P0 | Keep the `google-site-verification` meta (`index.html:9`) through every rewrite | Search Console depends on it | A |
| P1 | **New `/how-to-listen` hub** (one page): air times by time zone; live stream via KSTE on iHeartRadio; smart-speaker phrase; one sourced, owner-verified station table with "last checked" date and the official finder linked; "still on the radio / live today"; one "who's on the show" line; catching up later; the app last | Cluster E (≈3,600, "live" KD 9) + F. Page 1 is fragmented and stale (binnews' Dec 2021 list ranks). The homepage already ranks 5 (6) for "live stream" and 9 (11) for "how to listen" without targeting them. "Still on the radio?" appears in PAA on 24 SERPs | A (rows verified by B) |
| P1 | **New `/podcast-feeds` explainer**: what On Demand and One More Thing are (our own words), the other official feeds, why hours look out of order, when hours usually post, how to hear a day in order, replays and the weekend show, ads. Official links first. No episode titles, descriptions or statistics | Cluster B "on demand" (1,900/18), where the homepage sits at 12 (14) and 9 (10) on desktop; C (One More Thing 140). The URL `/podcast-feeds` is chosen over `/podcasts` so it doesn't mirror the official `armstrongandgetty.com/podcasts/` | A |
| P1 | Re-aim the homepage at podcast-app intent: the "podcast" keyword visible on mobile; a visible "Questions before you download" Q&A (official app? live? One More Thing? ads? free?); "Looking for the official site?" and "Listen live →" links; H1 and badges above the phone mockup on mobile | Cluster B "podcast" 4,400 (M 8 (10), D –). On a 390×844 screen the H1 renders at 859px and the badges at 1178px (baseline website lens) | A |
| P1 | Write for AI Overviews: question-form H2s in PAA wording, a 40–60-word answer opening each section, HTML tables, "Last checked" plus a source link next to each fact, consistent names ("The Armstrong & Getty Show", "Talk 650 KSTE", "Armstrong & Getty On Demand") | 45 AIOs, 0 cite us; they conflict on hours (§2.2) | A |
| P2 | Technical batch: Smart App Banner `apple-itunes-app` on indexable pages; `favicon.ico` and a 48 px PNG (`/favicon.ico` returned 404 on 2026-10-13); JSON-LD `@graph` (WebSite, publisher, MobileApplication with `sameAs` both store URLs; drop the personal email; **never** `aggregateRating`); sitemap with the new URLs and real `lastmod` (`/terms` and `/foreground-service` stay out because they're noindex); "Unofficial" on `og-image.png`; nav and footer links to the new pages | Store↔site entity tie-up; the sitemap has 3 URLs with lastmod 2026-07-29 | A |
| P1 | Official store badge artwork in place of the hand-drawn badges (Brett downloads the official files, an agent swaps them in; `owner-actions.md` #3c) | Apple's and Google's badge guidelines ask for the unaltered artwork | B + A |
| P2 | Site screenshots: reshoot `player.png` and `library.png` on neutral show days (or mask the description lines) in the next capture batch | They show feed descriptions naming real people and a superlative title (guardrail: no copied episode titles or descriptions) | A |
| P3 | `tools/seo/site.test.mjs` regression test (titles, canonicals, sitemap set equals indexable pages, JSON-LD parses, no "no ads" string) | Renew's copy drifted without one (baseline renew-playbook lens) | A |
| — | **Don't build:** station, city, day or episode pages; transcripts; cast or host pages; Katie Green; "best conservative podcasts" lists; a standalone car or Chromecast page | Doorway and scaled-content rules; the people SERPs are personal-life intent (§2.1 H) | — |

### 4.2 App Store (`seo/store-listing-copy.md` has the copy)

1.0.2 (build 61, the crash fix) was submitted around 6:58 PM PT on 2026-10-13 (session note).
**Owner plan (2026-10-13):** once the store copy is final, 1.0.2 (61) is removed from review and
resubmitted (same build, version stays 1.0.2) with the subtitle, keywords, description, promo
text and the 06/08 re-captions (`owner-actions.md` #6).

| Pri | Action | Why | Who |
|---|---|---|---|
| P0 (1.0.2 resubmit) | Promotional text, set during the 1.0.2 edit: add AirPlay and an upfront "unofficial" | It can also change later without review. Apple says it doesn't affect ranking, so it's for conversion | B |
| P0 | Save the current (private) keyword field to `tools/seo/data/` (gitignored) before changing it | Its contents are unknown. Labs shows us at #30 for "download jack", which hints "jack" may be in it. Removing a celebrity name may move "jack armstrong" a little | B |
| P0 (1.0.2 resubmit) | Subtitle adds **"A&G Talk Radio"**: "Unofficial A&G Talk Radio Show" (30 chars) for 1.0.2; "Every Hour of A&G Talk Radio" (28) only in a submission that also ships the hero fix (`store-listing-copy.md` §1.2, §1.8a) | "a&g" barely registers (iTunes API: not in 192 results; a&g podcast 91 of 165). Ampersand brands rank #1 when the abbreviation is in indexed text. "…radio" ranks 6 and "…talk radio" ranks 8; App Store "talk radio" volume is 212. Without the hero fix, nothing in the search card says unofficial | B |
| P0 (1.0.2 resubmit) | Keyword field: generic and feature words only. **No** jack, joe, iheart, omny, spotify, call letters, live, free, android, chromecast, carplay, sleep, and no words already in the name or subtitle | Apple 2.3.7 and its search guidance (no celebrity names or trademarks; don't repeat words) | B |
| P0 (1.0.2 resubmit) | Description rewrite: "unofficial" in the first line; original wording instead of the official bio; AirPlay; exact speeds and skips; exact ads line; disclaimer kept | Not indexed, but it's what converts, and Google indexes apps.apple.com | B |
| P0 (1.0.2 resubmit) | Replace screenshots **06-catalog and 08-about only** (recaptioned 2026-10-13, both device sizes). Slides 02/09 stay (owner decision) | Approved caption fixes: slide 06 dropped "ready every morning"; slide 08 dropped the show's tagline | B |
| P1 | On-device search check before the 1.0.2 resubmission and about 7 days after 1.0.2 is live (term list in `owner-actions.md` #6e) | DataForSEO "&" data is invalid, and no tool sees the Ads slot | B |
| P1 | Hero slide fix, both stores: "Unofficial fan-made podcast app" kicker, no laurels, show name as a small subline, recapture on a title with no superlative (`store-listing-copy.md` §1.8a). App Store at the 1.0.2 resubmission if ready, otherwise the next version | The hero has laurels, a "PODCAST · ON DEMAND" lockup that echoes the official feed, and no unofficial marker | A + B |
| P2 | Primary category decision (News vs Entertainment) | Weak evidence either way (open question 4) | B |
| P2 (next build) | Set `CFBundleDisplayName` (e.g. "A&G Podcast"); rewrite the About screen's tagline and bio (`AboutScreen.kt:177-185`); change the header kicker "PODCAST · ON DEMAND" (`App.kt:124`) to "UNOFFICIAL PODCAST APP" | Likely shows the lowercase target name "armstrongandgetty" on the home screen (no display name in `Info.plist`/pbxproj). The About screen reproduces official copy, and the header lockup appears in every capture | A |
| P3 | Custom product pages (e.g. "Cast & AirPlay") and in-app events, only once ratings and traffic exist; events only for real launches | Too little traffic to test; Apple says daily activities aren't events | B |

### 4.3 Google Play (listing edits need no app release)

| Pri | Action | Why | Who |
|---|---|---|---|
| P1 | Short description: "unofficial", "A&G" and "podcast app" (exact text: `store-listing-copy.md` §2.2) | Indexed, and it is the Play page's meta description. Today it has no "podcast" and nothing saying unofficial | B |
| P1 | Full description: the first ~130 chars carry the benefit plus "unofficial, fan-made"; the disclaimer moves to paragraph 2; add "On demand, not live", the exact ads line, a mobile-data note, and Android Auto (once confirmed); drop the official bio | Google shows the description's opening as the snippet in the mobile app pack, where we're #1 for the 9,900/mo head term (`raw/serp-mobile-armstrong-and-getty.json`). Today it has 0 mentions of podcast, A&G, talk radio or on demand | B |
| P1 | Upload recaptioned 06-catalog and 08-about (phone and tablet), per the caption decision in force (`store-listing-copy.md` §2.4; feature-slide quotes stay, Brett 2026-08-24) | Approved 2026-10-13 for "the next listing edit" | B |
| P1 | Confirm Android Auto is approved for the live 2.0.3. That's likely: 2.0.3 has been live since 2026-08-31 (`digest/app-info.json`), and both earlier Auto failures blocked whole releases. But it's an inference | Gates every Auto claim (§1) | B |
| P2 | Feature graphic and hero rebuild: "Unofficial fan-made podcast app" kicker; remove the "PODCAST · ON DEMAND" lockup, the large wordmark and the hero's laurels. Add the feature graphic as a builder profile, since no source exists in the repo (`store-listing-copy.md` §2.4a) | Both appear where the disclaimer isn't visible | A |
| P2 | Real Android Auto (Desktop Head Unit) and Cast (physical Chromecast) captures for the 8th phone slot; Play profiles only | Neither feature is shown; emulators can't discover Cast devices; Apple 2.3.10 rules out using them on iOS | A + B |
| P2 | Tablet set: stop showing the same 7 images twice (21 carousel images) | Looks careless next to 0 ratings | B |
| P3 | Custom store listing targeted at "live" search keywords ("Not live radio: …" opener); a `&listing=website` URL listing plus `referrer=` on the site's Play badge | Expectation-setting for the 2,400/mo live intent; website install attribution without web analytics | B |
| P3 | Store listing experiments only when Play Console estimates a result in a reasonable window | At 10+ downloads, results would be noise | B |
| — | Keep the title "Armstrong & Getty Podcast App" and the Music & Audio category | #1 on every brand variant (`digest/app-searches-google.json`) | — |

### 4.4 Ratings

| Pri | Action | Why | Who |
|---|---|---|---|
| P1 decision / P2 build | Native review prompts in the release **after** 1.0.2: `AppStore.requestReview` and the Play In-App Review API. Gate on ≥3 distinct listening days with ≥10–15 min each and ≥5 days since install; never after a crash or a feed or download failure; foreground only at a natural pause; never at launch, mid-playback, while casting or on Auto; 120-day cooldown, lifetime cap 3; no "do you like it?" pre-question; never triggered from a button; no incentives. Plus a "Rate the app" About row (deep link only). Reuse `renew/docs/in-app-reviews.md` | 0 ratings next to iHeart (650,272) and Apple Podcasts (2,074,722) on our own #1 results. Peers have 8–145 ratings, so a goal of **30–50 per store** matches Bob and Sheri (32) and Walton and Johnson (51). Apple names ratings as a ranking input | A (B decides) |
| P2 | Android prerequisite: a Wi-Fi-only download setting (default on), then prompts | New hours download on any network today. Prompting first invites "ate my data" reviews | A |
| ongoing | Reply to every review, factually; point "doesn't play live" complaints to how-to-listen | Trust signal on a 0-rating listing | B |

### 4.5 Authority (links and mentions)

The site has no known referring domains, against 1,499 for the official site. Links won't beat
the official site on head terms. They can lift mid-tail listening queries and build trust.

| Pri | Action | Why | Who |
|---|---|---|---|
| P1 | Site-side entity links: Smart App Banner, `sameAs` (§4.1 P2), official badges (§4.1 P1). The store-side links already exist | Ties together the 3 results already ranking for app intent | A |
| P1 | Make `/how-to-listen` the page to link and to answer community questions with | The official how-to-listen has no crawlable station list; stale lists rank | A |
| P2 | **Permission-first note to the show's Mailbag**, sent only after the copy cleanup ships: the site, both store descriptions (Play edit; App Store 1.0.2 resubmission) and the app's About screen | The only route to an on-air mention or a link from a 1,499-domain site. It also addresses Apple 4.1(c) and Play impersonation exposure. High stakes, so prepare a fallback name and icon first | B |
| P3 | Reddit: r/ArmstrongandGettyShow (180+ followers; 10 (13) on desktop for the head term, above our site's 12 (15)), then r/ArmstrongGetty (160+; 17 (21)), with modmail first and one disclosed post each at most. AlternativeTo listing (disclosed). GitHub repo description and homepage | The only fan communities found; legitimate moderated listings | B |
| P3 | Optional, only after the Mailbag outcome: Show HN or dev write-up; a 60–90 s walkthrough video with no show audio; trade press only if the show responds positively | Low topical relevance; draws attention to brand use | B |
| — | Domain: stay on `armstrong-and-getty-podcast.web.app`. **Never** register a domain containing the show's name | UDRP exposure; links earned now point here | B |

---

## 5. Target keyword → page map

| Page | Primary targets (vol / KD) | Secondary | Must not target | Hands off to |
|---|---|---|---|---|
| `/` | armstrong and getty podcast 4,400/24 · …podcast app · …app 10 · …download 10 · …show today 90/24 | …show 590/52 · head term 9,900 (defend only) | live (→ hub) · on demand (→ feeds) · website / homepage (320 L each) · official website (10) | `/how-to-listen`, `/podcast-feeds`, stores |
| `/how-to-listen` (new) | …live 2,400/9 · …live 650 720/2 L · …listen live 140/0 L · …live stream 110/11 · …show live 90/9 L · listen to … live 70/13 · how to listen to … 20 | …kste 210/5 · 'talk 650 armstrong and getty' 210/8 L · …650 170/1 L · …radio stations 40/0 · …stations 10 · …live today 40/0 L · …cast 50/35 L (one line) | talk 650 kste 3,600 · kste 650 1,900 (station brand) · host names · per-station or per-city URLs | `/podcast-feeds` (catch up), `/` (the app, last) |
| `/podcast-feeds` (new) | …on demand 1,900/18 · & on demand 210/20 · …on demand podcast 70/18 L | …one more thing 140/20 · …podcast apple 90/6 L · …google podcast 90/23 L · …replay · …full show (no volume) | episode titles or descriptions · counts or "we analyzed" statistics | `/how-to-listen` (live), `/` (the app) |
| `/support` | (no search volume) app help · "is there an A&G app?" | "can I listen live in the app?" → hub | — | `/how-to-listen`, `/podcast-feeds` |
| `/privacy`, `/terms` | — (fix the ads wording on privacy) | — | — | `/terms` and `/foreground-service` stay noindex and out of the sitemap |

**Store fields** (copy in `seo/store-listing-copy.md`): App Store name, kept (armstrong, getty,
podcast, app) · subtitle (a&g, talk, radio) · keyword field (generic and feature words) ·
category. Play title, kept · short description (unofficial, A&G, podcast app, daily episode) ·
full description (talk radio, Google Cast, offline, on demand, not live, Android Auto once confirmed).

The drafted site uses exactly these URLs (`docs/how-to-listen.html`, `docs/podcast-feeds.html`,
`docs/sitemap.xml`). If one ever has to move, use a single 301 and update this map.

---

## 6. Guardrails

**Affiliation and IP**
- "Unofficial / not affiliated with Armstrong & Getty or iHeartMedia" goes on every page (byline
  and footer) and in the first lines of both store descriptions.
- No show logos, artwork or host photos. No hosted or re-uploaded audio, no transcripts, no copied
  episode titles or descriptions, no embedded players. Link official sources first.
- Brief factual references (show times, station names, feed names) are fine as descriptive use.
- Never say "official", "endorsed", "approved" or "as heard on A&G" without written permission.
  Keep any written reply from the show as evidence (Apple 5.2.2, Play IP "authorization upon request").

**Store metadata**
- Apple 2.3.7 keyword field: 100 chars, comma-separated, no spaces. No other apps' names,
  trademarks, celebrity names or call letters. No words already in the name, subtitle or category.
- Apple 2.3 / 2.3.1: accurate metadata. Apple 2.3.10: no Android words or imagery in the iOS listing.
- Apple 5.6.1: the system review prompt only.
- Google Play Metadata policy: no keyword stuffing or keyword lists; no testimonials, ratings or
  rank claims ("#1", "best"); nothing misleading; no ALL CAPS used to game search.
- Play Impersonation and IP policies apply to the title, icon, feature graphic and screenshots.

**Google Search spam policies**
- No doorway pages or scaled thin content: no per-city, per-station, per-day or per-episode pages,
  and nothing generated from the feed.
- The feed's `<copyright>` notice prohibits "text and data mining or computational analysis"
  without iHeartMedia's consent (fetched 2026-10-13). Feed facts are published only as rounded
  listener observations ("usually within about half an hour of airing"), never as dataset
  statistics. Per-hour clock times, hour lengths and episode counts stay off the site until open
  question 8 is answered.
- Structured data never includes `aggregateRating` or `review`. Store ratings must not be imported.

**People**
- No content about hosts' or staff's ages, pay, marriages, politics or "what happened to". Facts
  stay at the show level.

**Ratings and outreach**
- No incentives, no sentiment pre-question, no button-triggered review API, no "5 stars" asks.
- Always disclose as the developer. Ask moderators first. No vote requests, sockpuppets or
  identical cross-posts. Never add our link to Wikipedia. No bought or traded links.

**Privacy promise**
- The site runs no analytics and sets no cookies (`docs/privacy.html`). Measure with Search
  Console, the store consoles and store campaign tokens. Don't add web analytics without first
  changing the privacy page.

**Owner decisions on record (not revisited here)**
- App Store review-wall slides 02/09 stay (final, 2026-10-13).
- Feature-slide quote captions stay, on Play too, as verbatim excerpts of real reviews. To change
  one, pick another excerpt, never reword (2026-08-24). Play rejected only the 02-reviews 5-star
  review wall; the captions passed the resubmission. Don't suggest stripping them.
- Caption fixes for slides 06 and 08 approved 2026-10-13: App Store with the 1.0.2
  resubmission, Play with the next listing edit.
- iOS 1.0.2 (61) is pulled from review and resubmitted (same build) with the new listing copy
  once it's final (2026-10-13).

---

## 7. Measurement cadence

| When | What | Cost |
|---|---|---|
| Weekly (5 min) | Search Console: Performance (queries containing live / demand / podcast / listen), Pages report, new-page inspection | free |
| +7 and +14 days after each listing change | Free store checks: iTunes Search API with an encoded "&", for the §3.2 brand terms; Play web search (`play.google.com/store/search?q=…&c=apps&hl=en_US&gl=US`) for the same terms | free |
| **Pass 2: 2 weeks** after the website deploy (≈2026-10-27 if it ships this week) | `python3 tools/seo/dataforseo_pass.py stage1 --budget 2.5`, then `… stage2 --serp-keywords 34 --budget 2.5`, then `python3 tools/seo/digest_pass.py --date <date>`; append Pass 2 to §3 | ≈$2 (Pass 1: $1.75) |
| **Pass 3: 4 weeks** after deploy (≈2026-11-10) | same | ≈$2 |
| Then monthly | same; plus the store consoles (App Store Connect App Analytics → Sources; Play Console search terms) and rating counts | ≈$2/month |

**Before Pass 2 (agent, with the owner's OK on spend):**
- Add to `serp_must_include` in `tools/seo/tracking.json`: "armstrong and getty live 650",
  "armstrong and getty listen live", "armstrong and getty radio stations", "is armstrong and getty
  live today". Run with `--serp-keywords 34`, which keeps the same 30 plus 4 at about $0.04 extra.
- Add "armstrong and getty radio" and "armstrong and getty talk radio" to `store_keywords` (they
  measure the subtitle change).
- Skip Apple app searches for terms containing "&" (DataForSEO truncates them), and record
  those from the iTunes API or the device check instead.
- After the run, confirm the "&" Google SERPs now land in separate `…-amp-…` files.

**§3.1 row generator** (identical format every pass):

```bash
python3 - 2026-10-27 <<'EOF'
import json, sys
dg = f"tools/seo/data/{sys.argv[1]}/digest/"
vol = {r["keyword"]: r.get("ads_volume") or r.get("volume") for r in json.load(open(dg + "google-keywords.json"))}
rows = {}
for r in json.load(open(dg + "serps.json")):
    o, st = r["ours"], []
    site = f"{o['site']['rank_group']} ({o['site']['rank_absolute']})" if "site" in o else "–"
    for k, lab in (("play", "Play"), ("app_store", "App Store")):
        if k in o:
            p = o[k]
            st.append(f"{lab}{' pack #' if p.get('type') == 'app' else ' '}{p['rank_group']} ({p['rank_absolute']})")
    rows.setdefault(r["keyword"], {})[r["device"]] = (site, ", ".join(st) or "–")
for k in sorted(rows, key=lambda k: -(vol.get(k) or 0)):
    d, m = rows[k].get("desktop", ("failed",) * 2), rows[k].get("mobile", ("failed",) * 2)
    print(f"| {k} | {vol.get(k) or '–'} | {d[0]} | {d[1]} | {m[0]} | {m[1]} |")
EOF
```

**Decision rules** (from Renew's dated passes):
- Change one element per page at a time, and measure a full period (4 weeks) before judging.
- Keep, revise or revert. Write the decision into the pass.
- Improve a page that's already earning impressions before adding a new one.
- Add a page only when Search Console queries or listener language show the demand.
- Don't test indexing with `site:`. It gave false negatives in Renew. Use URL Inspection or
  exact-phrase probes.

**Rough goals** (estimates from one snapshot with no known backlinks; judge them against Search
Console, not this table):

| Target | By ~4 weeks | By ~12 weeks |
|---|---|---|
| `/how-to-listen` | indexed; "how to listen" top 10; "live stream" top 10 on the hub URL | "…live" top 10 on D, top 15 on M; "listen live", "live 650" top 10; cited in ≥1 AI Overview |
| `/podcast-feeds` | indexed; hold "on demand" 9–14 on D | "on demand" top 8 on D, top 12 on M; "one more thing" top 15 |
| `/` | hold "podcast" top 10 on M, "show today" top 5 | "podcast" top 8 on M, top 15 on D; "podcast app" top 3 |
| Stores | brand #1 held on both; "a&g" and "…radio" improving after the 1.0.2 resubmission | 30–50 ratings per store once prompts ship |

---

## 8. Open questions for the owner

1. **Public repo.** `seo/*.md` (strategy, outreach drafts) becomes public when committed. Commit
   it, or keep `seo/` local by gitignoring it?
2. **Android Auto.** Has Play approved Auto for the live 2.0.3? Probably yes, since it has been
   live since 2026-08-31, but confirm. Every Auto mention (site, Play description, outreach)
   waits on this.
3. **"conservative" in the App Store keyword field.** It's an editorial label for a show that
   calls itself independent. The upside is small (conservative radio 67, conservative podcasts 43,
   conservative talk radio 26). Include it or not?
4. **App Store primary category: News or Entertainment?** "News" would be matched for free, but
   that's worth little ("news talk" 17). Peers are split (iTunes lookup: Clay and Buck and Walton
   and Johnson are News; Bob and Sheri and Talk Right are Entertainment; Mark Levin is Music).
5. **"A&G" in the App Store subtitle.** It extends the show's mark into one more indexed field,
   the same kind of exposure as the name. OK? If yes, the recommendation for 1.0.2 is the
   variant that also says "Unofficial" ("Unofficial A&G Talk Radio Show"), because the hero
   slide still has no unofficial marker (`store-listing-copy.md` §1.2, §1.8a).
6. **Station table upkeep.** Will you verify the station rows before launch and re-check them
   quarterly? If not, the hub ships with only the flagship and a link to the official finder.
7. **Mailbag note.** Offer "or take it down" in the note? Prepare a fallback name and icon first?
8. **Feed copyright notice.** How much feed-derived timing should the site publish? Until you
   answer, `/podcast-feeds` keeps only the rounded "each hour usually shows up within about half
   an hour of airing, so a normal weekday's show is usually all there by late morning Pacific"
   (removed 2026-10-13: the per-hour clock times, hour length and episodes-per-day counts). The
   notice may also bear on the app's own feed parsing. That's a legal question, not an SEO one.
9. **Publisher name** in structured data: "Renew Studios" (the Play developer) or "Brett
   Nottingham" (the App Store seller)?
10. **iOS territories.** Google shows the JP and RU App Store pages for "armstrong and getty app".
    Is worldwide availability intended for a US talk show?
11. **Android data use.** Ship the Wi-Fi-only download setting before review prompts on Android?
