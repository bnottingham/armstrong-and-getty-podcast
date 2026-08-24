#!/usr/bin/env node
/**
 * Armstrong & Getty — store promotional screenshot builder.
 *
 * Composes the marketing-framed store sets from the raw device captures in
 * `screenshots/<batch>/{ios,android}/<profile>/`, which are captured from the
 * real app running on a simulator/emulator.
 *
 *   node tools/promo-screenshots/buildPromoScreenshots.mjs \
 *     [--batch=ag-8-22-26] [--profile=iphone-6.9] [--only=03]
 *
 * Output, RGB with no alpha at each profile's exact store dimensions:
 *
 *   iphone-6.9      1320x2868   screenshots/app-store/iphone-6.9
 *   ipad-13         2064x2752   screenshots/app-store/ipad-13
 *   android-phone   1080x1920   screenshots/play-store/phone
 *   android-tablet  1440x2560   screenshots/play-store/tablet
 *
 * Seven slides per set — within Play's 8-per-device-type cap, so all four sets
 * are identical in content.
 *
 * Full documentation: docs/promo-screenshots.md
 */

import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";
import sharp from "sharp";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const TOOL = HERE;
const ROOT = path.resolve(HERE, "..", "..");

const args = process.argv.slice(2);
const readFlag = (name, fallback) => {
  const hit = args.find((a) => a.startsWith(`--${name}=`));
  return hit ? hit.slice(name.length + 3) : fallback;
};

const batch = readFlag("batch", "ag-8-22-26");
const only = readFlag("only", "");
const profileFilter = readFlag("profile", "");

/* ------------------------------------------------------------------ *
 * Palette — the app's own Compose theme, not re-invented here.
 * Source: Native Mobile/shared/.../ui/theme/Theme.kt
 * ------------------------------------------------------------------ */

const T = {
  gold: "#E8B34B",
  goldDark: "#D4952A",
  bg: "#0E0F13",
  card: "#181A21",
  surfaceVariant: "#1C1E26",
  textPrimary: "#F0EDE6",
  textSecondary: "#9B978E",
  textMuted: "#5A5750",
};

/* ------------------------------------------------------------------ *
 * Profiles.
 * ------------------------------------------------------------------ */

const scaleLayout = (L, factor) =>
  Object.fromEntries(
    Object.entries(L).map(([key, value]) => [
      key,
      key.endsWith("Rotate") ? value : Math.round(value * factor),
    ]),
  );

const ANDROID_PHONE_LAYOUT = {
  brandTop: 96,
  laurelW: 106,
  laurelH: 288,
  heroGap: 32,
  wordmark: 52,
  heroHeadlineTop: 58,
  heroHeadline: 96,
  heroKickerTop: 30,
  heroKicker: 26,
  heroDeviceLeft: 54,
  heroDeviceTop: 880,
  heroDeviceWidth: 1120,
  heroDeviceRotate: -15,

  featurePadTop: 110,
  featureHeadline: 90,
  featureHeadlineMax: 940,
  featureSubTop: 30,
  featureSub: 42,
  featureSubMax: 860,
  deviceTop: 520,
  deviceWidth: 940,

  bezelPad: 14,
  bezelRadius: 62,
  screenRadius: 48,
};

const PROFILES = {
  "iphone-6.9": {
    width: 1320,
    height: 2868,
    source: "ios/iphone-6.9",
    statusBarCrop: 186,
    out: "screenshots/app-store/iphone-6.9",
    L: {
          brandTop: 140,
      laurelW: 130,
      laurelH: 352,
      heroGap: 40,
      wordmark: 68,
      heroHeadlineTop: 74,
      heroHeadline: 128,
      heroKickerTop: 40,
      heroKicker: 34,
      heroDeviceLeft: 66,
      heroDeviceTop: 1216,
      heroDeviceWidth: 1216,
      heroDeviceRotate: -15,

      featurePadTop: 168,
      featureHeadline: 112,
      featureHeadlineMax: 1140,
      featureSubTop: 40,
      featureSub: 52,
      featureSubMax: 1060,
      deviceTop: 726,
      deviceWidth: 1150,

      bezelPad: 17,
      bezelRadius: 78,
      screenRadius: 62,
    },
  },
  "ipad-13": {
    width: 2064,
    height: 2752,
    source: "ios/ipad-13",
    statusBarCrop: 48,
    out: "screenshots/app-store/ipad-13",
    L: {
          brandTop: 160,
      laurelW: 196,
      laurelH: 512,
      heroGap: 62,
      wordmark: 92,
      heroHeadlineTop: 76,
      heroHeadline: 176,
      heroKickerTop: 44,
      heroKicker: 46,
      heroDeviceLeft: 60,
      heroDeviceTop: 1500,
      heroDeviceWidth: 2150,
      heroDeviceRotate: -10,

      featurePadTop: 190,
      featureHeadline: 168,
      featureHeadlineMax: 1780,
      featureSubTop: 48,
      featureSub: 78,
      featureSubMax: 1560,
      deviceTop: 930,
      deviceWidth: 1760,

      bezelPad: 22,
      bezelRadius: 60,
      screenRadius: 42,
    },
  },
  "android-phone": {
    width: 1080,
    height: 1920,
    source: "android/phone",
    statusBarCrop: 137,
    out: "screenshots/play-store/phone",
    L: ANDROID_PHONE_LAYOUT,
  },
  // 1080x1920 and 1440x2560 are the same 9:16, so the tablet is exactly 4/3 of
  // the phone. Two hero overrides keep the tilted device clear of the kicker.
  "android-tablet": {
    width: 1440,
    height: 2560,
    source: "android/tablet",
    statusBarCrop: 140,
    out: "screenshots/play-store/tablet",
    L: {
      ...scaleLayout(ANDROID_PHONE_LAYOUT, 4 / 3),
      heroDeviceWidth: 1640,
      heroDeviceTop: 1230,
    },
  },
};

const PLAY_MAX_SCREENSHOTS = 8;

/* ------------------------------------------------------------------ *
 * Copy.
 *
 * No review-wall slides: Google Play rejected the Aug 2026 submission under
 * its Metadata policy over the five-star testimonial slides, so star ratings
 * and review walls must never return to any set. The short quotes on the
 * feature slides are trimmed excerpts of real supplied reviews, cut to 10-13
 * words so they stay legible at store-thumbnail size. Never fabricate a
 * review: invented testimonials deceive the people reading the listing and
 * are grounds for removal under both Apple's and Google's policies.
 * ------------------------------------------------------------------ */

const slides = [
  {
    id: "01-hero",
    kind: "hero",
    wordmark: "Armstrong & Getty",
    headline: "Never Miss\nThe Show.",
    kicker: "Podcast · On Demand",
    shot: "01-episodes.png",
  },
  {
    id: "03-episodes",
    kind: "feature",
    headline: "Every Show,\nSorted By Day",
    quote: "Full daily show stitched\ninto one clean episode.",
    shot: "01-episodes.png",
  },
  {
    id: "04-player",
    kind: "feature",
    headline: "Built For Talk,\nNot Music",
    quote: "Full day assembled in order\nwith solid playback controls.",
    shot: "02-player.png",
  },
  {
    id: "05-downloads",
    kind: "feature",
    headline: "Offline Before\nThe Drive",
    quote: "Grab the entire show with one tap.\nNo signal issues.",
    shot: "03-downloads.png",
  },
  {
    id: "06-catalog",
    kind: "feature",
    headline: "The Whole Week,\nStill Waiting",
    quote: "The full daily broadcast,\nready every morning.",
    shot: "05-catalog.png",
  },
  {
    id: "07-more",
    kind: "feature",
    headline: "More Than\nThe Daily Show",
    quote: "Longtime A&G listener\nand this app is excellent.",
    shot: "06-morefrom.png",
  },
  {
    id: "08-about",
    kind: "feature",
    headline: "Smart. Funny.\nIndependent.",
    quote: "No ads, no account, no clutter.",
    shot: "04-about.png",
  },
];

/* ------------------------------------------------------------------ *
 * Fragments.
 * ------------------------------------------------------------------ */

const escapeHtml = (value) =>
  String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

const lines = (value) => escapeHtml(value).replace(/\n/g, "<br>");

/**
 * A laurel half-wreath — the right-hand branch; the left copy is mirrored in
 * CSS. Leaves alternate off a curved stem and sweep up along it, tapering
 * toward the tip.
 */
const laurel = () => {
  const P = [
    [64, 198],
    [34, 152],
    [22, 78],
    [48, 4],
  ];
  const cubic = (t) => {
    const u = 1 - t;
    const at = (i) =>
      u ** 3 * P[0][i] + 3 * u * u * t * P[1][i] + 3 * u * t * t * P[2][i] + t ** 3 * P[3][i];
    const d = (i) =>
      3 * u * u * (P[1][i] - P[0][i]) +
      6 * u * t * (P[2][i] - P[1][i]) +
      3 * t * t * (P[3][i] - P[2][i]);
    return { x: at(0), y: at(1), angle: (Math.atan2(d(1), d(0)) * 180) / Math.PI };
  };

  const COUNT = 9;
  const leaves = [];
  for (let i = 0; i < COUNT; i += 1) {
    const t = 0.06 + (i / (COUNT - 1)) * 0.86;
    const { x, y, angle } = cubic(t);
    const side = i % 2 === 0 ? -1 : 1;
    const scale = 1 - 0.42 * t;
    leaves.push(
      `<path d="M0 0 C 12 -9, 30 -9, 42 0 C 30 9, 12 9, 0 0 Z" transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${(angle + side * 46).toFixed(1)}) scale(${scale.toFixed(3)})"/>`,
    );
  }
  const stem = `M${P[0][0]} ${P[0][1]} C ${P[1][0]} ${P[1][1]}, ${P[2][0]} ${P[2][1]}, ${P[3][0]} ${P[3][1]}`;
  return `<svg class="laurel" viewBox="6 -20 88 238" aria-hidden="true">
    <path d="${stem}" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
    <g fill="currentColor">${leaves.join("")}</g>
  </svg>`;
};

/* ------------------------------------------------------------------ *
 * Fonts.
 *
 * The app declares no custom typeface — Compose Material 3 renders SF on iOS
 * and Roboto on Android. Inter is the neutral grotesque closest to both, so
 * the promo type reads as the same voice on either store. Inlined as a data
 * URI so a missed webfont can never silently substitute a system face and
 * reflow the art-directed line breaks.
 * ------------------------------------------------------------------ */

const GOOGLE_FONTS_CSS =
  "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400..900&family=Inter:wght@400..800&display=block";
const MODERN_UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

async function buildFontFaceCss() {
  const cacheDir = path.join(TOOL, ".cache");
  await mkdir(cacheDir, { recursive: true });
  const manifestPath = path.join(cacheDir, "faces.json");

  let manifest = null;
  try {
    manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  } catch {
    /* first run, or a cleared cache */
  }

  if (!manifest) {
    const sheet = await fetch(GOOGLE_FONTS_CSS, { headers: { "User-Agent": MODERN_UA } });
    if (!sheet.ok) throw new Error(`Google Fonts CSS fetch failed: ${sheet.status}`);
    const text = await sheet.text();

    // The plain `latin` subset of each family — the promo copy is English.
    manifest = [];
    for (const block of text.split("@font-face")) {
      const family = block.match(/font-family:\s*'([^']+)'/)?.[1];
      const url = block.match(/url\((https:\/\/[^)]+\.woff2)\)/)?.[1];
      const range = block.match(/unicode-range:\s*([^;]+);/)?.[1] ?? "";
      if (!family || !url || !range.includes("U+0000-00FF")) continue;
      manifest.push({ family, url, file: `${family.toLowerCase().replace(/\s+/g, "-")}-latin.woff2` });
    }
    if (manifest.length !== 2) {
      throw new Error(`Expected one latin face each for Playfair Display and Inter, got ${manifest.length}.`);
    }

    for (const face of manifest) {
      const res = await fetch(face.url, { headers: { "User-Agent": MODERN_UA } });
      if (!res.ok) throw new Error(`Font fetch failed for ${face.family}: ${res.status}`);
      await writeFile(path.join(cacheDir, face.file), Buffer.from(await res.arrayBuffer()));
    }
    await writeFile(manifestPath, JSON.stringify(manifest, null, 2), "utf8");
  }

  const faces = [];
  for (const face of manifest) {
    const data = await readFile(path.join(cacheDir, face.file));
    faces.push(`@font-face{
      font-family:'${face.family}';
      font-style:normal;
      font-weight:400 900;
      font-display:block;
      src:url(data:font/woff2;base64,${data.toString("base64")}) format('woff2');
    }`);
  }
  return faces.join("\n");
}

const fontFaceCss = await buildFontFaceCss();

/* ------------------------------------------------------------------ *
 * Document.
 * ------------------------------------------------------------------ */

const buildCss = ({ width, height, L }) => `
  ${fontFaceCss}

  * { box-sizing: border-box; margin: 0; padding: 0; }

  html, body {
    width: ${width}px;
    height: ${height}px;
    overflow: hidden;
  }

  body {
    position: relative;
    font-family: "Inter", system-ui, sans-serif;
    color: ${T.textPrimary};
    -webkit-font-smoothing: antialiased;
    text-rendering: optimizeLegibility;

    /* The app's own near-black canvas, lifted by a single gold bloom so the
       device has something to sit against. */
    background:
      radial-gradient(116% 54% at 50% -8%, rgba(232, 179, 75, 0.22), transparent 62%),
      radial-gradient(88% 46% at 8% 106%, rgba(212, 149, 42, 0.10), transparent 66%),
      linear-gradient(168deg, #1A1C24 0%, ${T.bg} 52%, #08090C 100%);
  }

  .slide {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  /* ---- shared type ---- */

  .headline {
    font-family: "Playfair Display", Georgia, serif;
    font-weight: 700;
    letter-spacing: -0.012em;
    line-height: 1.08;
    color: ${T.textPrimary};
  }

  .quote {
    font-family: "Playfair Display", Georgia, serif;
    font-weight: 400;
    line-height: 1.34;
    color: rgba(240, 237, 230, 0.74);
  }

  /* ---- device ---- */

  .phone {
    position: relative;
    border-radius: ${L.bezelRadius}px;
    padding: ${L.bezelPad}px;
    background: linear-gradient(152deg, #45484F 0%, #24262D 44%, #383B43 76%, #17181D 100%);
    box-shadow:
      0 0 ${Math.round(L.bezelRadius * 2.4)}px rgba(232, 179, 75, 0.10),
      0 54px 116px rgba(0, 0, 0, 0.62),
      0 10px 30px rgba(0, 0, 0, 0.5);
  }

  .phone::after {
    content: "";
    position: absolute;
    inset: 0;
    border-radius: inherit;
    box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.12);
    pointer-events: none;
  }

  .phone img {
    display: block;
    width: 100%;
    border-radius: ${L.screenRadius}px;
  }

  /* ---- 1: hero ---- */

  .wordmark {
    font-family: "Playfair Display", Georgia, serif;
    font-size: ${L.wordmark}px;
    font-weight: 600;
    letter-spacing: -0.012em;
    margin-top: ${L.brandTop}px;
  }

  .hero-frame {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: ${L.heroGap}px;
    margin-top: ${L.heroHeadlineTop}px;
    color: rgba(232, 179, 75, 0.34);
  }

  .laurel { width: ${L.laurelW}px; height: ${L.laurelH}px; }
  .laurel.left { transform: scaleX(-1); }

  .hero-headline {
    font-size: ${L.heroHeadline}px;
  }

  .hero-kicker {
    margin-top: ${L.heroKickerTop}px;
    font-size: ${L.heroKicker}px;
    font-weight: 700;
    letter-spacing: 0.26em;
    text-transform: uppercase;
    color: ${T.gold};
  }

  .hero-phone {
    position: absolute;
    left: ${L.heroDeviceLeft}px;
    top: ${L.heroDeviceTop}px;
    width: ${L.heroDeviceWidth}px;
    transform-origin: top left;
    transform: rotate(${L.heroDeviceRotate}deg);
  }

  /* ---- feature slides ---- */

  .feature { padding-top: ${L.featurePadTop}px; }

  .feature-headline {
    font-size: ${L.featureHeadline}px;
    max-width: ${L.featureHeadlineMax}px;
  }

  .feature-quote {
    margin-top: ${L.featureSubTop}px;
    font-size: ${L.featureSub}px;
    max-width: ${L.featureSubMax}px;
  }

  .feature-phone {
    position: absolute;
    top: ${L.deviceTop}px;
    left: 50%;
    width: ${L.deviceWidth}px;
    transform: translateX(-50%);
  }
`;

const renderSlide = (slide, shotsDir) => {
  const phone = (shot) =>
    `<div class="phone"><img src="${escapeHtml(path.join(shotsDir, shot))}" alt=""></div>`;

  if (slide.kind === "hero") {
    return `<div class="slide">
      <div class="wordmark">${escapeHtml(slide.wordmark)}</div>
      <div class="hero-frame">
        ${laurel().replace('class="laurel"', 'class="laurel left"')}
        <div class="headline hero-headline">${lines(slide.headline)}</div>
        ${laurel()}
      </div>
      <div class="hero-kicker">${escapeHtml(slide.kicker)}</div>
      <div class="hero-phone">${phone(slide.shot)}</div>
    </div>`;
  }

  return `<div class="slide feature">
    <div class="headline feature-headline">${lines(slide.headline)}</div>
    <div class="quote feature-quote">&ldquo;${lines(slide.quote)}&rdquo;</div>
    <div class="feature-phone">${phone(slide.shot)}</div>
  </div>`;
};

const document_ = (slide, profile, shotsDir) => `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><style>${buildCss(profile)}</style></head>
<body>${renderSlide(slide, shotsDir)}</body></html>`;

/* ------------------------------------------------------------------ *
 * Render.
 * ------------------------------------------------------------------ */

async function launchBrowser() {
  const candidates = [
    { label: "Google Chrome", options: { channel: "chrome", headless: true } },
    { label: "Microsoft Edge", options: { channel: "msedge", headless: true } },
    { label: "Playwright Chromium", options: { headless: true } },
  ];
  const failures = [];
  for (const candidate of candidates) {
    try {
      const browser = await chromium.launch(candidate.options);
      console.log(`Using ${candidate.label}.`);
      return browser;
    } catch (error) {
      failures.push(`${candidate.label}: ${error?.message || error}`);
    }
  }
  throw new Error(`No supported Chromium browser is available.\n${failures.join("\n")}`);
}

const profileNames = Object.keys(PROFILES).filter((n) => !profileFilter || n === profileFilter);
if (profileNames.length === 0) {
  throw new Error(`Unknown profile "${profileFilter}". Known: ${Object.keys(PROFILES).join(", ")}`);
}

const staging = path.join(TOOL, ".staging");
await rm(staging, { recursive: true, force: true });
await mkdir(staging, { recursive: true });

const browser = await launchBrowser();
const report = [];

try {
  for (const name of profileNames) {
    const profile = PROFILES[name];
    const shotsDir = path.join(ROOT, "screenshots", batch, profile.source);
    const outDir = path.join(ROOT, profile.out);

    if (profile.out.startsWith("screenshots/play-store/") && slides.length > PLAY_MAX_SCREENSHOTS) {
      throw new Error(
        `${name} would upload ${slides.length} screenshots; Play accepts at most ${PLAY_MAX_SCREENSHOTS} per device type.`,
      );
    }

    const present = new Set(await readdir(shotsDir));
    for (const slide of slides) {
      if (slide.shot && !present.has(slide.shot)) {
        throw new Error(`Missing source capture ${slide.shot} in ${shotsDir}`);
      }
    }

    // Crop the platform status bar off every source so all four sets read as
    // one system — the reference set has no status bars either, and Android's
    // notification icons cannot be reliably suppressed on a modern emulator.
    // The raw captures under screenshots/ stay untouched as evidence.
    const framedDir = path.join(staging, `${name}-shots`);
    await mkdir(framedDir, { recursive: true });
    const framed = new Map();
    for (const shot of new Set(slides.filter((s) => s.shot).map((s) => s.shot))) {
      const meta = await sharp(path.join(shotsDir, shot)).metadata();
      const height = meta.height - profile.statusBarCrop;
      await sharp(path.join(shotsDir, shot))
        .extract({ left: 0, top: profile.statusBarCrop, width: meta.width, height })
        .png()
        .toFile(path.join(framedDir, shot));
      framed.set(shot, { width: meta.width, height });
    }

    // The template requires every device to run off the bottom edge. A capture
    // whose aspect leaves it floating mid-canvas is a layout bug, not a style
    // choice — catch it here rather than in App Store review.
    for (const slide of slides.filter((s) => s.kind === "feature")) {
      const meta = framed.get(slide.shot);
      const screenH = (profile.L.deviceWidth - profile.L.bezelPad * 2) * (meta.height / meta.width);
      const bottom = profile.L.deviceTop + screenH + profile.L.bezelPad * 2;
      const MIN_BLEED = 100;
      if (bottom < profile.height + MIN_BLEED) {
        throw new Error(
          `${name}/${slide.id}: device bottom lands at ${Math.round(bottom)}px on a ${profile.height}px canvas — needs to overshoot by at least ${MIN_BLEED}px or it reads as a floating card. Raise deviceWidth or deviceTop.`,
        );
      }
    }

    await mkdir(outDir, { recursive: true });

    // Prune outputs from an earlier slide list. Without this a renamed or
    // dropped slide leaves an orphan PNG sitting in the upload folder, which
    // is the kind of thing that only gets noticed in App Store Connect.
    if (!only) {
      const expected = new Set(slides.map((slide) => `${slide.id}.png`));
      for (const file of await readdir(outDir)) {
        if (file.endsWith(".png") && !expected.has(file)) {
          await rm(path.join(outDir, file));
          console.log(`  – pruned stale ${file}`);
        }
      }
    }

    const context = await browser.newContext({
      viewport: { width: profile.width, height: profile.height },
      deviceScaleFactor: 1,
    });
    const page = await context.newPage();
    console.log(`\n${name} (${profile.width}x${profile.height}) ← ${batch}`);

    const written = [];
    for (const slide of slides) {
      if (only && !slide.id.startsWith(only)) continue;

      const htmlPath = path.join(staging, `${name}-${slide.id}.html`);
      await writeFile(htmlPath, document_(slide, profile, framedDir), "utf8");
      await page.goto(`file://${htmlPath}`, { waitUntil: "load" });

      await page.evaluate(() => document.fonts.ready);
      const fontsOk = await page.evaluate(
        () =>
          document.fonts.check('700 112px "Playfair Display"') &&
          document.fonts.check("700 65px Inter"),
      );
      if (!fontsOk) {
        throw new Error(`Playfair Display/Inter did not load for ${name}/${slide.id}; refusing to render a fallback face.`);
      }

      const target = path.join(outDir, `${slide.id}.png`);
      await page.screenshot({ path: target, type: "png", omitBackground: false });
      written.push(target);
      console.log(`  ✓ ${slide.id}.png`);
    }
    await context.close();

    // App Store Connect rejects alpha; Play requires "24-bit PNG (no alpha)".
    // Chromium writes RGBA — flatten, then verify.
    for (const file of written) {
      const flat = await sharp(file).removeAlpha().png({ compressionLevel: 9 }).toBuffer();
      await writeFile(file, flat);
      const meta = await sharp(await readFile(file)).metadata();
      if (meta.width !== profile.width || meta.height !== profile.height || meta.channels !== 3) {
        throw new Error(
          `${path.basename(file)} is ${meta.width}x${meta.height} ${meta.channels}ch; expected ${profile.width}x${profile.height} 3ch.`,
        );
      }
    }

    if (!only) {
      const sheet = path.join(path.dirname(outDir), `contact-sheet-${path.basename(outDir)}.jpg`);
      const TILE_W = 420;
      const TILE_H = Math.round((TILE_W * profile.height) / profile.width);
      const GAP = 14;
      const COLS = 4;
      const rows = Math.ceil(written.length / COLS);
      const tiles = await Promise.all(written.map((f) => sharp(f).resize(TILE_W, TILE_H).toBuffer()));
      await sharp({
        create: {
          width: COLS * TILE_W + (COLS + 1) * GAP,
          height: rows * TILE_H + (rows + 1) * GAP,
          channels: 3,
          background: "#000000",
        },
      })
        .composite(
          tiles.map((input, i) => ({
            input,
            left: GAP + (i % COLS) * (TILE_W + GAP),
            top: GAP + Math.floor(i / COLS) * (TILE_H + GAP),
          })),
        )
        .jpeg({ quality: 90 })
        .toFile(sheet);
    }

    report.push(`${written.length} × ${profile.width}x${profile.height} → ${profile.out}`);
  }
} finally {
  await browser.close();
}

await rm(staging, { recursive: true, force: true });
console.log(`\n${report.join("\n")}`);
