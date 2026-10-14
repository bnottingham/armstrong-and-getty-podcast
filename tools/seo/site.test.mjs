// Regression tests for the static website in docs/ (Firebase Hosting, cleanUrls).
//
// Zero dependencies: node:test + node:assert + node:fs only.
// Run from the repo root:  node --test 'tools/seo/*.test.mjs'
// (`node --test tools/seo/` also works, but only runs package.json `main`, i.e. this file.)
//
// What it guards (see the SEO plan of 2026-10-13):
//   * every public page: one <title> (<= 60 chars), a meta description (<= 160),
//     one <h1>, a self-referencing clean canonical on the production origin,
//     Open Graph tags, the "unofficial / not affiliated" disclaimer, parseable JSON-LD
//   * sitemap.xml <-> pages (namespace, closing tag), robots.txt -> sitemap
//   * no "official app" wording, no imprecise ad claims ("no ads", "ad-free",
//     "commercial-free", ...), no false feature claims (a next-segment control,
//     "ready every morning")
//   * every internal link, asset reference (incl. srcset) and #fragment resolves
//   * well-formed markup (balanced tags), every <table> inside .table-scroll
//   * JSON-LD: schema.org @context, allowlisted @types, no ratings or reviews;
//     FAQPage answers match the visible answers
//   * store badge links point at our App Store id / Play package
//   * the footer disclaimer is visible (not hidden) and inside <footer>

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
// SITE_DOCS lets the suite be pointed at a scratch copy (e.g. to prove a check fails).
const DOCS = resolve(process.env.SITE_DOCS ?? resolve(HERE, '../../docs'));
const ORIGIN = 'https://armstrong-and-getty-podcast.web.app';
const APP_STORE_ID = '6796129205';
const PLAY_PACKAGE = 'com.nomnomsom.armstrongandgetty';

// JSON-LD types this site uses. Anything else (Review, AggregateRating, Rating...) fails.
const JSONLD_TYPES = new Set([
  'WebSite', 'WebPage', 'Organization', 'MobileApplication', 'Offer', 'RadioSeries',
  'BreadcrumbList', 'ListItem', 'FAQPage', 'Question', 'Answer',
]);
const JSONLD_BANNED_KEYS = ['aggregateRating', 'review', 'reviews', 'ratingValue', 'ratingCount', 'reviewCount'];

const VOID_ELEMENTS = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'source', 'track', 'wbr']);
// Elements whose end tag HTML lets you omit.
const OPTIONAL_END = new Set(['p', 'li', 'tr', 'td', 'th', 'thead', 'tbody', 'caption', 'dt', 'dd', 'option']);

const NOT_FOUND_PAGE = '404.html';
// Public pages that must never be listed in the sitemap.
const NEVER_IN_SITEMAP = new Set([NOT_FOUND_PAGE, 'foreground-service.html']);

const TITLE_MAX = 60;
const DESCRIPTION_MAX = 160;

// ---------------------------------------------------------------- helpers

const ENTITIES = {
  amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', middot: '·',
  mdash: '—', ndash: '–', rarr: '→', larr: '←', hellip: '…', copy: '©',
  ldquo: '“', rdquo: '”', lsquo: '‘', rsquo: '’', times: '×', reg: '®', trade: '™',
};

function decodeEntities(s) {
  return s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e) => {
    if (e[0] === '#') {
      const code = e[1].toLowerCase() === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return String.fromCodePoint(code);
    }
    return ENTITIES[e.toLowerCase()] ?? m;
  });
}

const squash = (s) => decodeEntities(s).replace(/[\s\u00a0]+/g, ' ').trim();

// Markup with comments, scripts and styles removed, so tag scans never match code.
function markupOnly(html) {
  return html
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[\s\S]*?<\/style>/gi, ' ');
}

function parseAttrs(tag) {
  const attrs = {};
  const inner = tag.replace(/^<[a-z0-9-]+/i, '').replace(/\/?>$/, '');
  const re = /([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g;
  let m;
  while ((m = re.exec(inner))) {
    attrs[m[1].toLowerCase()] = decodeEntities(m[2] ?? m[3] ?? m[4] ?? '');
  }
  return attrs;
}

function startTags(html, name) {
  const re = new RegExp(`<${name}\\b[^>]*>`, 'gi');
  return (markupOnly(html).match(re) ?? []).map(parseAttrs);
}

function metaContent(html, key) {
  return startTags(html, 'meta')
    .filter((a) => a.name === key || a.property === key)
    .map((a) => a.content ?? '');
}

function visibleText(html) {
  const body = (html.match(/<body\b[\s\S]*<\/body>/i) ?? [html])[0];
  return squash(markupOnly(body).replace(/<[^>]+>/g, ' '));
}

function jsonLdBlocks(html) {
  const re = /<script\b[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
  return [...html.matchAll(re)].map((m) => m[1]);
}

function walkJson(value, visit, path = '$') {
  visit(value, path);
  if (Array.isArray(value)) value.forEach((v, i) => walkJson(v, visit, `${path}[${i}]`));
  else if (value && typeof value === 'object') {
    for (const [k, v] of Object.entries(value)) walkJson(v, visit, `${path}.${k}`);
  }
}

const isNoindex = (html) =>
  metaContent(html, 'robots').some((c) => /\bnoindex\b/i.test(c));

function canonicalFor(file) {
  return file === 'index.html' ? `${ORIGIN}/` : `${ORIGIN}/${file.replace(/\.html$/, '')}`;
}

// Where a page is served from. 404.html is served at any missing URL, so it is
// checked from a nested path: its links must work from anywhere.
function servedAt(file) {
  if (file === NOT_FOUND_PAGE) return `${ORIGIN}/some/missing/page`;
  return canonicalFor(file);
}

// Map a URL on our origin to the file Firebase Hosting would serve (cleanUrls on,
// trailingSlash off). Returns { file, fragment, problem }.
function resolveOnSite(rawUrl, base) {
  const url = new URL(rawUrl, base);
  const fragment = decodeURIComponent(url.hash.slice(1));
  const path = decodeURIComponent(url.pathname);
  if (path === '/') return { file: 'index.html', fragment };
  if (path.endsWith('/')) return { problem: `trailing slash (redirects): ${rawUrl}` };
  if (path.endsWith('.html')) return { problem: `not a clean URL (redirects): ${rawUrl}` };
  const rel = path.slice(1);
  const hasExtension = /\.[a-z0-9]+$/i.test(rel);
  const file = hasExtension ? rel : `${rel}.html`;
  return { file, fragment };
}

function isOnSite(rawUrl, base) {
  if (/^(mailto:|tel:|javascript:|data:|sms:)/i.test(rawUrl)) return false;
  const url = new URL(rawUrl, base);
  return url.origin === ORIGIN;
}

function fileExists(rel) {
  const full = join(DOCS, rel);
  return full.startsWith(DOCS) && existsSync(full) && statSync(full).isFile();
}

// Fragment targets: id attributes, plus legacy <a name>. Never <meta name>.
function idsIn(html) {
  const ids = new Set();
  const markup = markupOnly(html);
  for (const m of markup.matchAll(/\sid\s*=\s*["']([^"']+)["']/gi)) ids.add(m[1]);
  for (const m of markup.matchAll(/<a\b[^>]*\sname\s*=\s*["']([^"']+)["']/gi)) ids.add(m[1]);
  return ids;
}

// Stack walk over start/end tags. Returns a list of problems (stray or unclosed tags).
function tagBalanceProblems(html) {
  const problems = [];
  const stack = [];
  const re = /<(\/?)([a-zA-Z][a-zA-Z0-9-]*)\b(?:[^>"']|"[^"]*"|'[^']*')*?(\/?)>/g;
  const markup = markupOnly(html).replace(/<!DOCTYPE[^>]*>/i, ' ');
  for (const m of markup.matchAll(re)) {
    const [, closing, rawName, selfClosing] = m;
    const name = rawName.toLowerCase();
    const line = markup.slice(0, m.index).split('\n').length;
    if (!closing) {
      if (!VOID_ELEMENTS.has(name) && !selfClosing) stack.push({ name, line });
      continue;
    }
    if (VOID_ELEMENTS.has(name)) { problems.push(`stray </${name}> (line ~${line})`); continue; }
    const at = stack.map((e) => e.name).lastIndexOf(name);
    if (at === -1) { problems.push(`stray </${name}> (line ~${line})`); continue; }
    for (const open of stack.splice(at + 1)) {
      if (!OPTIONAL_END.has(open.name)) problems.push(`<${open.name}> (line ~${open.line}) is not closed before </${name}> (line ~${line})`);
    }
    stack.pop();
  }
  for (const open of stack) {
    if (!OPTIONAL_END.has(open.name)) problems.push(`<${open.name}> (line ~${open.line}) is never closed`);
  }
  return problems;
}

const stripTags = (s) => s.replace(/<[^>]+>/g, ' ');

// ---------------------------------------------------------------- fixtures

const htmlFiles = readdirSync(DOCS).filter((f) => f.endsWith('.html')).sort();
const pages = new Map(htmlFiles.map((f) => [f, readFileSync(join(DOCS, f), 'utf8')]));
const publicPages = htmlFiles.filter((f) => f !== NOT_FOUND_PAGE);
const indexablePages = publicPages.filter((f) => !isNoindex(pages.get(f)));

const sitemapXml = readFileSync(join(DOCS, 'sitemap.xml'), 'utf8');
const sitemapEntries = [...sitemapXml.matchAll(/<url>([\s\S]*?)<\/url>/g)].map((m) => ({
  loc: (m[1].match(/<loc>\s*([^<]+?)\s*<\/loc>/) ?? [])[1],
  lastmod: (m[1].match(/<lastmod>\s*([^<]+?)\s*<\/lastmod>/) ?? [])[1],
}));
const robotsTxt = readFileSync(join(DOCS, 'robots.txt'), 'utf8');

// ---------------------------------------------------------------- tests

test('docs/ has the expected pages', () => {
  for (const f of ['index.html', 'support.html', 'privacy.html', 'how-to-listen.html', 'podcast-feeds.html', NOT_FOUND_PAGE]) {
    assert.ok(pages.has(f), `missing docs/${f}`);
  }
});

for (const file of publicPages) {
  const html = pages.get(file);
  const label = `docs/${file}`;

  test(`${label}: head basics (lang, viewport, one title <= ${TITLE_MAX}, one description <= ${DESCRIPTION_MAX})`, () => {
    assert.match(html, /<html\b[^>]*\blang=["']en/i, 'missing <html lang="en">');
    assert.equal(metaContent(html, 'viewport').length, 1, 'needs exactly one viewport meta');

    const titles = [...html.matchAll(/<title\b[^>]*>([\s\S]*?)<\/title>/gi)].map((m) => squash(m[1]));
    assert.equal(titles.length, 1, `expected exactly one <title>, found ${titles.length}`);
    assert.ok(titles[0].length > 10, 'title is empty or too short');
    assert.ok(titles[0].length <= TITLE_MAX, `title is ${titles[0].length} chars (> ${TITLE_MAX}): "${titles[0]}"`);

    const descriptions = metaContent(html, 'description').map(squash);
    assert.equal(descriptions.length, 1, `expected exactly one meta description, found ${descriptions.length}`);
    assert.ok(descriptions[0].length >= 50, `meta description too short: "${descriptions[0]}"`);
    assert.ok(descriptions[0].length <= DESCRIPTION_MAX,
      `meta description is ${descriptions[0].length} chars (> ${DESCRIPTION_MAX}): "${descriptions[0]}"`);
  });

  test(`${label}: exactly one <h1>`, () => {
    const count = (markupOnly(html).match(/<h1\b/gi) ?? []).length;
    assert.equal(count, 1, `expected one <h1>, found ${count}`);
  });

  test(`${label}: self-referencing clean canonical on ${ORIGIN}`, () => {
    const canonicals = startTags(html, 'link').filter((a) => (a.rel ?? '').split(/\s+/).includes('canonical'));
    assert.equal(canonicals.length, 1, `expected one canonical, found ${canonicals.length}`);
    assert.equal(canonicals[0].href, canonicalFor(file));
  });

  test(`${label}: Open Graph tags`, () => {
    for (const key of ['og:type', 'og:site_name', 'og:title', 'og:description', 'og:url', 'og:image']) {
      const values = metaContent(html, key);
      assert.equal(values.length, 1, `expected exactly one ${key}, found ${values.length}`);
      assert.ok(values[0].trim(), `${key} is empty`);
    }
    assert.equal(metaContent(html, 'og:url')[0], canonicalFor(file), 'og:url must equal the canonical');
    const image = metaContent(html, 'og:image')[0];
    assert.ok(image.startsWith(`${ORIGIN}/`), `og:image must be absolute on ${ORIGIN}: ${image}`);
    const { file: imageFile } = resolveOnSite(image, ORIGIN);
    assert.ok(fileExists(imageFile), `og:image file missing: docs/${imageFile}`);
  });

  test(`${label}: raster favicon present (Google Search ignores SVG-only)`, () => {
    const icons = startTags(html, 'link').filter((a) => (a.rel ?? '').split(/\s+/).includes('icon'));
    assert.ok(icons.some((a) => /\.(ico|png)$/i.test(a.href ?? '')), 'needs an .ico or .png rel=icon');
  });

  test(`${label}: JSON-LD parses${isNoindex(html) ? '' : ' (and is present)'}`, () => {
    const blocks = jsonLdBlocks(html);
    if (!isNoindex(html)) assert.ok(blocks.length >= 1, 'indexable page has no JSON-LD');
    for (const [i, raw] of blocks.entries()) {
      let data;
      assert.doesNotThrow(() => { data = JSON.parse(raw); }, `JSON-LD block ${i} is not valid JSON`);
      assert.ok(data && typeof data === 'object' && !Array.isArray(data), `JSON-LD block ${i} must be an object`);
      assert.match(String(data['@context'] ?? ''), /^https?:\/\/schema\.org\/?$/,
        `JSON-LD block ${i} needs "@context": "https://schema.org"`);
      walkJson(data, (value, path) => {
        if (value && typeof value === 'object' && !Array.isArray(value)) {
          for (const banned of JSONLD_BANNED_KEYS) {
            assert.ok(!(banned in value), `JSON-LD must not carry ${banned} (${path})`);
          }
          if ('@type' in value) {
            for (const t of [].concat(value['@type'])) {
              assert.ok(JSONLD_TYPES.has(t), `JSON-LD ${path}: @type "${t}" is not on the allowlist (no Review/Rating types)`);
            }
          }
        }
        if (typeof value === 'string' && value.startsWith(`${ORIGIN}/`)) {
          const r = resolveOnSite(value, ORIGIN);
          assert.ok(!r.problem, `JSON-LD ${path}: ${r.problem}`);
          assert.ok(fileExists(r.file), `JSON-LD ${path} points at a missing file: ${value}`);
        }
      });
    }
  });

  test(`${label}: FAQPage JSON-LD answers match the visible answers`, () => {
    const visible = new Map();
    for (const m of markupOnly(html).matchAll(/<details\b[^>]*>\s*<summary\b[^>]*>([\s\S]*?)<\/summary>([\s\S]*?)<\/details>/gi)) {
      visible.set(squash(stripTags(m[1])), squash(stripTags(m[2])));
    }
    for (const raw of jsonLdBlocks(html)) {
      let data;
      try { data = JSON.parse(raw); } catch { continue; } // reported by the JSON-LD test
      walkJson(data, (value) => {
        if (!value || typeof value !== 'object' || value['@type'] !== 'Question') return;
        const name = squash(String(value.name ?? ''));
        assert.ok(visible.has(name), `FAQ question "${name}" has no matching visible <summary>`);
        const answer = squash(stripTags(String(value.acceptedAnswer?.text ?? '')));
        assert.equal(answer, visible.get(name), `FAQ answer for "${name}" differs from the visible answer`);
      });
    }
  });

  test(`${label}: Smart App Banner, if present, uses the real App Store id`, () => {
    const banners = metaContent(html, 'apple-itunes-app');
    assert.ok(banners.length <= 1, 'more than one apple-itunes-app meta');
    if (banners.length) assert.equal(banners[0], `app-id=${APP_STORE_ID}`);
    if (file === 'index.html') assert.equal(banners.length, 1, 'homepage needs the Smart App Banner');
  });
}

for (const file of htmlFiles) {
  const html = pages.get(file);
  const label = `docs/${file}`;

  test(`${label}: shows the unofficial / not-affiliated disclaimer`, () => {
    const text = visibleText(html);
    assert.match(text, /unofficial companion app and website, not affiliated with, endorsed by, or sponsored by Armstrong & Getty, iHeartMedia/,
      'footer disclaimer missing or reworded');
    const markup = markupOnly(html);
    const at = markup.search(/<p\b[^>]*class=["'][^"']*\bdisclaimer\b/i);
    assert.ok(at !== -1, 'no <p class="disclaimer">');
    const attrs = parseAttrs(markup.slice(at).match(/^<p\b[^>]*>/i)[0]);
    assert.ok(!('hidden' in attrs) && !/display\s*:\s*none|visibility\s*:\s*hidden/i.test(attrs.style ?? ''),
      'the disclaimer must not be hidden');
    const before = markup.slice(0, at);
    const footerOpen = before.search(/<footer\b[^>]*>(?![\s\S]*<footer\b)/i);
    assert.ok(footerOpen !== -1 && !/<\/footer>/i.test(before.slice(footerOpen)), 'the disclaimer must sit inside <footer>');
    const footerAttrs = parseAttrs(before.slice(footerOpen).match(/^<footer\b[^>]*>/i)[0]);
    assert.ok(!('hidden' in footerAttrs), 'the footer must not be hidden');
  });

  test(`${label}: well-formed markup (no stray or unclosed tags)`, () => {
    const problems = tagBalanceProblems(html);
    assert.deepEqual(problems, [], `tag balance:\n  ${problems.join('\n  ')}`);
  });

  test(`${label}: every <table> sits inside .table-scroll (no sideways page scroll)`, () => {
    const markup = markupOnly(html);
    const tables = (markup.match(/<table\b/gi) ?? []).length;
    const wrapped = (markup.match(/class=["']table-scroll[^"']*["'][^>]*>\s*<table\b/gi) ?? []).length;
    assert.equal(wrapped, tables, `${tables - wrapped} <table> element(s) not directly inside a .table-scroll wrapper`);
  });

  test(`${label}: store links point at our own listings`, () => {
    for (const a of startTags(html, 'a')) {
      const href = a.href ?? '';
      if (/^https?:\/\/apps\.apple\.com\//i.test(href)) {
        assert.ok(href.includes(`/id${APP_STORE_ID}`), `App Store link is not our app: ${href}`);
      }
      if (/^https?:\/\/play\.google\.com\/store\/apps\/details/i.test(href)) {
        assert.ok(new URL(href).searchParams.get('id') === PLAY_PACKAGE, `Play link is not our app: ${href}`);
      }
    }
  });

  test(`${label}: no "official app" wording, no imprecise ad claims, no false feature claims`, () => {
    // Raw markup too, so meta tags and JSON-LD are covered, not just visible text.
    const all = squash(html);
    const rules = [
      // Question headings ("Is this the official Armstrong & Getty app?") are allowed.
      [/\bofficial\s+(?:A&G\s+|Armstrong\s*&\s*Getty\s+)?app\b(?![^<]{0,3}\?)/i, 'never call anything here the "official app"'],
      [/\bad[- ]free\b|\bads[- ]free\b/i, 'never claim "ad-free": the episodes carry the show\'s sponsor breaks'],
      [/\bno ads\b(?! of its own| added by the app)/i, 'a bare "no ads" claim is imprecise: say the app adds no ads of its own'],
      [/\b(?:without|zero) ads\b|\bcommercial[- ]free\b|\bno advertising\b(?! SDKs?\b| identifiers?\b)|\bnot show (?:any )?ads\b/i,
        'imprecise ad claim: say the app adds no ads of its own (the show\'s sponsor breaks play as published)'],
      // The app has no next-segment control: only ±10/±30 s skips; segments are reached by tapping a row.
      [/\bnext segment\b|\bsegment[- ]skip\b|\bskip controls?\b[^.<"]{0,80}\bsegments?\b/i,
        'no next-segment / segment-skip control exists: say "tap any segment to jump to it"'],
      // Hour 4 posts mid-to-late morning Pacific and iOS background refresh is best-effort.
      [/\b(?:ready|fresh) every morning\b|\bready before you are\b/i, 'the full show is not ready every morning'],
    ];
    // ...and the visible text, so inline tags ("do <strong>not</strong> show ads") can't split a phrase.
    for (const text of [all, visibleText(html)]) {
      for (const [re, why] of rules) {
        const m = text.match(re);
        assert.ok(!m, m && `${why}: "...${text.slice(Math.max(0, m.index - 60), m.index + 60)}..."`);
      }
    }
  });

  test(`${label}: internal links, assets and #fragments resolve`, () => {
    const base = servedAt(file);
    const refs = [];
    for (const [tag, attr] of [['a', 'href'], ['link', 'href'], ['img', 'src'], ['script', 'src'], ['source', 'src'], ['video', 'poster'], ['iframe', 'src']]) {
      // <script src> lives inside script tags, which markupOnly() strips; scan raw for those.
      const found = tag === 'script'
        ? [...html.matchAll(/<script\b[^>]*>/gi)].map((m) => parseAttrs(m[0]))
        : startTags(html, tag);
      for (const a of found) if (a[attr] !== undefined) refs.push(a[attr]);
    }
    for (const key of ['og:url', 'og:image', 'twitter:image']) refs.push(...metaContent(html, key));
    for (const a of [...startTags(html, 'img'), ...startTags(html, 'source')]) {
      for (const candidate of (a.srcset ?? '').split(',')) {
        const url = candidate.trim().split(/\s+/)[0];
        if (url) refs.push(url);
      }
    }

    const problems = [];
    for (const ref of refs) {
      if (!ref || !isOnSite(ref, base)) continue;
      const r = resolveOnSite(ref, base);
      if (r.problem) { problems.push(r.problem); continue; }
      if (!fileExists(r.file)) { problems.push(`${ref} -> docs/${r.file} does not exist`); continue; }
      if (r.fragment && r.file.endsWith('.html')) {
        const target = r.file === file ? html : pages.get(r.file) ?? readFileSync(join(DOCS, r.file), 'utf8');
        if (!idsIn(target).has(r.fragment)) problems.push(`${ref} -> no id="${r.fragment}" in docs/${r.file}`);
      }
    }
    assert.deepEqual(problems, [], `broken internal references:\n  ${problems.join('\n  ')}`);
  });
}

test('titles and meta descriptions are unique across public pages', () => {
  const seen = new Map();
  for (const file of publicPages) {
    const html = pages.get(file);
    const title = squash((html.match(/<title\b[^>]*>([\s\S]*?)<\/title>/i) ?? [])[1] ?? '');
    const description = squash(metaContent(html, 'description')[0] ?? '');
    for (const [kind, value] of [['title', title], ['description', description]]) {
      const key = `${kind}:${value}`;
      assert.ok(!seen.has(key), `duplicate ${kind} on docs/${file} and docs/${seen.get(key)}: "${value}"`);
      seen.set(key, file);
    }
  }
});

test('docs/404.html is noindex and works from any path', () => {
  const html = pages.get(NOT_FOUND_PAGE);
  assert.ok(isNoindex(html), '404 page must be noindex');
  assert.equal((markupOnly(html).match(/<h1\b/gi) ?? []).length, 1, '404 needs one <h1>');
  assert.equal([...html.matchAll(/<title\b/gi)].length, 1, '404 needs one <title>');
  for (const a of [...startTags(html, 'a'), ...startTags(html, 'link')]) {
    const ref = a.href ?? '';
    if (ref && !/^(https?:|mailto:|#)/i.test(ref)) {
      assert.ok(ref.startsWith('/'), `404 links must be root-relative (it is served at any path): ${ref}`);
    }
  }
});

test('sitemap.xml: every URL is clean, unique, dated and maps to an indexable page', () => {
  assert.match(sitemapXml, /^\s*<\?xml\b[^>]*\?>/, 'sitemap.xml needs an XML declaration');
  assert.ok(sitemapXml.includes('<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"'),
    'sitemap.xml <urlset> needs the sitemaps.org namespace');
  assert.match(sitemapXml, /<\/urlset>\s*$/, 'sitemap.xml must end with </urlset>');
  assert.equal((sitemapXml.match(/<url>/g) ?? []).length, (sitemapXml.match(/<\/url>/g) ?? []).length,
    'sitemap.xml has unbalanced <url> tags');
  assert.ok(sitemapEntries.length > 0, 'sitemap has no <url> entries');
  const locs = new Set();
  for (const { loc, lastmod } of sitemapEntries) {
    assert.ok(loc, 'a <url> entry has no <loc>');
    assert.ok(!locs.has(loc), `duplicate sitemap URL ${loc}`);
    locs.add(loc);
    assert.ok(loc.startsWith(`${ORIGIN}/`), `sitemap URL not on ${ORIGIN}: ${loc}`);
    const r = resolveOnSite(loc, ORIGIN);
    assert.ok(!r.problem, `sitemap: ${r.problem}`);
    assert.ok(r.file.endsWith('.html') && pages.has(r.file), `sitemap URL ${loc} has no page in docs/`);
    assert.ok(!NEVER_IN_SITEMAP.has(r.file), `docs/${r.file} must not be in the sitemap`);
    assert.ok(!isNoindex(pages.get(r.file)), `sitemap lists noindex page docs/${r.file}`);
    assert.equal(loc, canonicalFor(r.file), `sitemap URL ${loc} differs from the page's canonical`);
    assert.match(lastmod ?? '', /^\d{4}-\d{2}-\d{2}$/, `bad or missing <lastmod> for ${loc}`);
    assert.ok(!Number.isNaN(Date.parse(lastmod)), `invalid <lastmod> date for ${loc}`);
  }
});

test('sitemap.xml lists every indexable public page (all but 404 and foreground-service)', () => {
  const listed = new Set(sitemapEntries.map((e) => e.loc));
  const missing = indexablePages
    .filter((f) => !NEVER_IN_SITEMAP.has(f))
    .filter((f) => !listed.has(canonicalFor(f)));
  assert.deepEqual(missing, [], `pages missing from sitemap.xml: ${missing.join(', ')}`);
  for (const f of ['index.html', 'how-to-listen.html', 'podcast-feeds.html', 'support.html', 'privacy.html']) {
    assert.ok(listed.has(canonicalFor(f)), `sitemap must list ${canonicalFor(f)}`);
  }
});

test('robots.txt references the sitemap and blocks no sitemap URL', () => {
  assert.match(robotsTxt, /^User-agent:\s*\*\s*$/im, 'robots.txt needs a "User-agent: *" group');
  assert.match(robotsTxt, new RegExp(`^Sitemap:\\s*${ORIGIN.replace(/[.]/g, '\\.')}/sitemap\\.xml\\s*$`, 'im'),
    'robots.txt must reference the sitemap');
  const disallows = [...robotsTxt.matchAll(/^Disallow:\s*(\S*)\s*$/gim)].map((m) => m[1]).filter(Boolean);
  for (const { loc } of sitemapEntries) {
    const path = new URL(loc).pathname;
    for (const rule of disallows) {
      assert.ok(!path.startsWith(rule), `robots.txt "Disallow: ${rule}" blocks sitemap URL ${loc}`);
    }
  }
});
