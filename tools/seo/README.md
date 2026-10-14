# SEO research tooling

Last updated: October 13, 2026

Two data layers, both billed to the same pay-as-you-go DataForSEO account
(`brett@nomnomsom.com`, app.dataforseo.com):

| Piece | Where | What it is for | Auth |
| --- | --- | --- | --- |
| **OpenSEO** (self-hosted) | Sibling checkout `/Users/brett/Documents/github/open-seo`, served at `http://localhost:3001`. Shared with the Renew repo — same instance, same port, separate projects. | Projects, saved keywords, rank tracker, site audits, research history; the vendored skills in `.claude/skills/` drive it. | `DATAFORSEO_API_KEY` in the checkout's gitignored `.env.local`, written from the Keychain by `sync-key`. |
| **`openseo` MCP** | `.mcp.json` in this repo → `http://localhost:3001/mcp` | The tools the skills call (`research_keywords`, `get_serp_results`, `get_ranked_keywords`, `run_site_audit`, ...). | None — `AUTH_MODE=local_noauth`. Never expose port 3001 beyond localhost. |
| **`dataforseo` MCP** (official, hosted v3) | User-scoped in `~/.claude.json` → `https://mcp.dataforseo.com/v3/mcp` | Any DataForSEO endpoint OpenSEO does not wrap — notably the **App Data API** (App Store / Google Play search positions and listings) — plus DataForSEO's own docs (`docs_index`, `docs_search`, `api_request`). | OAuth, granted once by the owner via `/mcp` → Authenticate. No secret on disk. |
| **Keychain** | macOS login keychain, service `dataforseo-api` (account = API login, password = plain API password) | Single source of truth for the Basic-auth credentials, so a re-clone of `open-seo` cannot lose them. | — |

OpenSEO project: **Armstrong & Getty Podcast App**
(`projectId bb4a5f3a-e79e-4983-b815-13fab3af8f66`, domain
`armstrong-and-getty-podcast.web.app`, market US/en). `list_projects`
re-derives the ID if OpenSEO's local database is ever reset.

## Daily use

```bash
tools/seo/openseo-local.sh status    # is it up, is dataforseo "ok"?
tools/seo/openseo-local.sh start
```

Then in Claude Code use the skills (`/keyword-research`, `/competitive-landscape`,
`/competitor-analysis`, `/seo-audit`, `/link-prospecting`, ...) or ask for
App Store / Play data, which goes through the `dataforseo` MCP.

Cost guardrail (from the OpenSEO MCP's own guidance): normal focused research
is fine; confirm with the owner before any planned batch over ~2,000 credits.
Free account check (balance, limits): `GET /v3/appendix/user_data`.

## Website regression test

`node --test 'tools/seo/*.test.mjs'` (from the repo root; Node 21+ for the glob; no
dependencies) runs every test file here against `docs/`. Today that is `site.test.mjs`.
`node --test tools/seo/` also works, but it resolves the directory through
`package.json` `main` and so runs **only** `site.test.mjs`; any second `*.test.mjs` file
would be skipped silently. Inside `tools/seo/`, `npm test` runs the glob.

It checks every page's title (<= 60 chars), meta description (<= 160), single H1,
clean self-canonical, Open Graph tags, the unofficial / not-affiliated disclaimer
(visible, inside `<footer>`) and JSON-LD (schema.org `@context`, allowlisted `@type`s,
no ratings or reviews, FAQPage answers identical to the visible answers); balanced
tags and every `<table>` inside `.table-scroll`; that `sitemap.xml` (namespace,
closing tag) and `robots.txt` match the pages; that store links point at our own App
Store id and Play package; that no page says "official app", "ad-free", a bare
"no ads" or similar ("commercial-free", "without ads"), claims a next-segment /
segment-skip control, or says the show is "ready every morning"; and that every
internal link, asset, `srcset` entry and `#fragment` resolves under Firebase
`cleanUrls`. Set `SITE_DOCS=/path/to/copy` to point it at a scratch copy of the site
(that is how each check was shown to fail on an injected fault).

## AI-optimized (`.ai`) responses — where to use them

DataForSEO serves a trimmed, LLM-oriented JSON for any Live or Task GET
endpoint when `.ai` is appended to the path (e.g.
`/v3/serp/google/organic/live/advanced.ai`). No extra charge. It drops empty
fields, `position` and `xpath`, rounds floats to 3 decimals, and returns
`monthly_searches` as a `{ "YYYY-MM": n }` object.

- **The `dataforseo` MCP uses `.ai` by default** (`api_request`; pass
  `noAiMode: true` for the raw shape). Right for agent research — far fewer
  tokens per call.
- **`.ai` defaults `depth`/`limit` to 10.** For any rank check, set `depth`
  explicitly (e.g. 100) or anything below #10 silently disappears.
- **OpenSEO stays on the standard responses.** Its client parses the full
  schemas; do not patch it to `.ai`.

## One-time setup (done 2026-10-13) and recovery

1. DataForSEO → API Access → **Reset password** → **Send by e-mail** (gives API
   login + API password + a Base64 string). Leave **IP access** empty — an IP
   allowlist breaks the hosted MCP.
2. Save the **plain API password** (not the Base64 string) to the Keychain —
   run in an interactive terminal (it prompts; nothing is echoed):

   ```bash
   security add-generic-password -U -s dataforseo-api -a brett@nomnomsom.com -l "DataForSEO API" -w
   ```
3. `tools/seo/openseo-local.sh sync-key && tools/seo/openseo-local.sh restart`
   — writes `DATAFORSEO_API_KEY` as a **single** base64 layer of
   `login:password`. (Renew hit a double-encoded key once: OpenSEO's health
   check said `ok` while every data call failed with HTTP 401. Check with
   `sed -n 's/^DATAFORSEO_API_KEY=//p' ../open-seo/.env.local | base64 -d` —
   it must print `login:password`, not another base64 blob. Don't echo it
   anywhere persistent.)
4. `claude mcp add --transport http --scope user dataforseo https://mcp.dataforseo.com/v3/mcp`,
   then in an interactive `claude`: `/mcp` → dataforseo → Authenticate.
   `claude mcp get dataforseo` should say `✔ Connected`.

**After re-cloning or `git pull`-ing `open-seo`:**
`pnpm install --frozen-lockfile && pnpm run db:migrate:local` in the checkout,
then step 3. If the local DB was reset, recreate the project
(`create_project`, domain above) and update the ID in this file.

## Keeping the vendored skills fresh

`.claude/skills/` holds the twelve official OpenSEO skills, copied verbatim
(MIT) from `open-seo/plugins/openseo/skills/` at upstream `89e5a00`
(v0.1.12). Their `openseo.so` URLs are report-footer doc links only; the MCP
endpoint comes from `.mcp.json`. To refresh:

```bash
cd /Users/brett/Documents/github/open-seo && git pull
cp -R plugins/openseo/skills/* /Users/brett/Documents/github/armstrong-and-getty-podcast/.claude/skills/
```

Review the diff before committing — the skills run with this repo's context.

## Troubleshooting

- `tools/seo/openseo-local.sh logs 100` — vite/wrangler boot errors.
- `curl -s http://localhost:3001/api/health` — per-dependency status.
- MCP tools missing in a Claude Code session: the session must start in this
  repo after `.mcp.json` exists, with OpenSEO running; approve the project
  server when prompted.
- Data calls returning 401: re-check the encoding depth (step 3).
