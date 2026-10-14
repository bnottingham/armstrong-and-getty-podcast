#!/usr/bin/env python3
"""One DataForSEO measurement pass for the A&G app — web + App Store + Google Play.

Reads tools/seo/tracking.json, calls DataForSEO directly with the Basic-auth
credentials in the macOS Keychain (service "dataforseo-api"), and writes every
raw response plus a cost ledger to tools/seo/data/<date>/ (gitignored). Re-run
the same stages on a later date and diff the outputs to measure progress.

  python3 tools/seo/dataforseo_pass.py stage1   # volumes, KD, discovery, domains, backlinks, store keyword data
  python3 tools/seo/dataforseo_pass.py stage2   # live Google SERPs + App Store / Play search positions

Every call is billed. --budget caps the cumulative spend recorded in the
ledger for that date (default $6); a call that would start past it is refused.
"""

import argparse
import base64
import datetime
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

API = "https://api.dataforseo.com"
HERE = pathlib.Path(__file__).resolve().parent
KEYCHAIN_SERVICE = "dataforseo-api"


def keychain_auth():
    meta = subprocess.run(["security", "find-generic-password", "-s", KEYCHAIN_SERVICE],
                          capture_output=True, text=True, check=True).stdout
    login = re.search(r'"acct"<blob>="([^"]*)"', meta).group(1)
    password = subprocess.run(["security", "find-generic-password", "-s", KEYCHAIN_SERVICE, "-w"],
                              capture_output=True, text=True, check=True).stdout.strip()
    return "Basic " + base64.b64encode(f"{login}:{password}".encode()).decode()


class Client:
    def __init__(self, out_dir, budget):
        self.auth = keychain_auth()
        self.out = out_dir
        self.out.mkdir(parents=True, exist_ok=True)
        self.ledger_path = self.out / "ledger.jsonl"
        self.budget = budget

    def spent(self):
        if not self.ledger_path.exists():
            return 0.0
        return sum(json.loads(line)["cost"] for line in self.ledger_path.read_text().splitlines() if line.strip())

    def call(self, name, path, body=None):
        """POST (body) or GET (no body); saves raw JSON as <name>.json, logs cost."""
        if self.spent() >= self.budget:
            raise SystemExit(f"Budget ${self.budget:.2f} reached (spent ${self.spent():.4f}); refusing {name}")
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(API + path, data=data, method="POST" if body is not None else "GET",
                                     headers={"Authorization": self.auth, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                payload = json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            raise SystemExit(f"{name}: HTTP {e.code} {e.read()[:300]!r}")
        cost = float(payload.get("cost") or 0)
        (self.out / f"{name}.json").write_text(json.dumps(payload, indent=1))
        with self.ledger_path.open("a") as f:
            f.write(json.dumps({"name": name, "path": path, "cost": cost}) + "\n")
        bad = [t for t in payload.get("tasks", []) if t.get("status_code") not in (20000, 20100)]
        status = "ok" if not bad else f"{len(bad)} task error(s): {bad[0].get('status_code')} {bad[0].get('status_message')}"
        print(f"  {name:<44} ${cost:<9.4f} total ${self.spent():.4f}  {status}")
        return payload


def results(payload):
    out = []
    for task in payload.get("tasks", []):
        out.extend(task.get("result") or [])
    return out


def slug(text):
    # "&" is kept as "amp" so "armstrong & getty" and "armstrong getty" don't share a file.
    return re.sub(r"[^a-z0-9]+", "-", text.lower().replace("&", " amp ")).strip("-")[:60]


def stage1(c, cfg):
    m = cfg["market"]
    kws = sorted(set(k.lower() for k in cfg["keywords"]))
    print(f"Google keyword metrics for {len(kws)} keywords")
    c.call("google-ads-search-volume", "/v3/keywords_data/google_ads/search_volume/live",
           [{"keywords": kws, **m}])
    c.call("labs-keyword-overview", "/v3/dataforseo_labs/google/keyword_overview/live",
           [{"keywords": kws, **m}])

    print("Keyword discovery")
    for seed in cfg["discovery_seeds"]["suggestions"]:
        c.call(f"labs-suggestions-{slug(seed)}", "/v3/dataforseo_labs/google/keyword_suggestions/live",
               [{"keyword": seed, "include_seed_keyword": True, "limit": 300, **m}])
    for seed in cfg["discovery_seeds"]["related"]:
        c.call(f"labs-related-{slug(seed)}", "/v3/dataforseo_labs/google/related_keywords/live",
               [{"keyword": seed, "depth": 2, "limit": 200, **m}])
    c.call("labs-ideas-generic", "/v3/dataforseo_labs/google/keyword_ideas/live",
           [{"keywords": cfg["discovery_seeds"]["ideas"], "limit": 200, **m}])

    print("Domains and pages")
    for target in [cfg["site"], *cfg["competitor_domains"]]:
        c.call(f"labs-domain-rank-{slug(target)}", "/v3/dataforseo_labs/google/domain_rank_overview/live",
               [{"target": target, **m}])
        c.call(f"labs-ranked-{slug(target)}", "/v3/dataforseo_labs/google/ranked_keywords/live",
               [{"target": target, "limit": 500 if target != cfg["site"] else 200, **m}])
    for page in cfg["competitor_pages"]:
        c.call(f"labs-ranked-page-{slug(page.split('//')[1])}", "/v3/dataforseo_labs/google/ranked_keywords/live",
               [{"target": page, "limit": 200, **m}])
    brand = [k for k in kws if "armstrong" in k or "getty" in k][:200]
    c.call("labs-serp-competitors-brand", "/v3/dataforseo_labs/google/serp_competitors/live",
           [{"keywords": brand, "limit": 40, **m}])

    print("Backlinks")
    for target in [cfg["site"], *cfg["competitor_domains"]]:
        c.call(f"backlinks-summary-{slug(target)}", "/v3/backlinks/summary/live",
               [{"target": target, "include_subdomains": True}])
    c.call(f"backlinks-referring-domains-{slug(cfg['site'])}", "/v3/backlinks/referring_domains/live",
           [{"target": cfg["site"], "limit": 100}])
    c.call(f"backlinks-list-{slug(cfg['site'])}", "/v3/backlinks/backlinks/live",
           [{"target": cfg["site"], "limit": 100, "mode": "as_is"}])

    print("Store keyword data (DataForSEO Labs, US/en only)")
    for store in ("apple", "google"):
        ours = cfg["apps"][store]
        c.call(f"labs-{store}-keywords-for-app-ours", f"/v3/dataforseo_labs/{store}/keywords_for_app/live",
               [{"app_id": ours, "limit": 300, **m}])
        for app_id in cfg["competitor_apps"][store]:
            c.call(f"labs-{store}-keywords-for-app-{slug(app_id)}", f"/v3/dataforseo_labs/{store}/keywords_for_app/live",
                   [{"app_id": app_id, "limit": 300, **m}])
        c.call(f"labs-{store}-app-competitors-ours", f"/v3/dataforseo_labs/{store}/app_competitors/live",
               [{"app_id": ours, "limit": 50, **m}])


def pick_serp_keywords(c, cfg, n):
    vol = {}
    for r in results(json.loads((c.out / "google-ads-search-volume.json").read_text())):
        vol[r["keyword"]] = r.get("search_volume") or 0
    chosen = list(dict.fromkeys(k.lower() for k in cfg["serp_must_include"]))
    for k, _ in sorted(vol.items(), key=lambda kv: -kv[1]):
        if len(chosen) >= n:
            break
        if k not in chosen and vol[k] > 0:
            chosen.append(k)
    return chosen


def collect_tasks(c, get_path, pending):
    """pending: {task_id: output name}. Task GETs are free; poll until each is ready."""
    pending = dict(pending)
    deadline = time.time() + 900
    while pending and time.time() < deadline:
        time.sleep(20)
        for task_id, name in list(pending.items()):
            payload = c.call(name, f"{get_path}/{task_id}")
            if payload["tasks"][0].get("status_code") == 20000:
                del pending[task_id]
            else:
                (c.out / f"{name}.json").unlink(missing_ok=True)
    if pending:
        print(f"  {len(pending)} task(s) not ready after 15 min: {sorted(pending.values())}")


def stage2(c, cfg, serp_n):
    m = cfg["market"]
    kws = pick_serp_keywords(c, cfg, serp_n)
    (c.out / "serp-keywords.json").write_text(json.dumps(kws, indent=1))
    print(f"Live Google SERPs: {len(kws)} keywords x desktop+mobile, depth 20")
    for k in kws:
        for device in ("desktop", "mobile"):
            c.call(f"serp-{device}-{slug(k)}", "/v3/serp/google/organic/live/advanced",
                   [{"keyword": k, "device": device, "depth": 20, **m}])

    print(f"App Store / Google Play search positions: {len(cfg['store_keywords'])} keywords x 2 stores")
    for store in ("apple", "google"):
        posted = c.call(f"app-searches-{store}-post", f"/v3/app_data/{store}/app_searches/task_post",
                        [{"keyword": k, "depth": 100, "tag": k, **m} for k in cfg["store_keywords"]])
        searches = {t["id"]: f"app-search-{store}-{slug(t['data']['tag'])}"
                    for t in posted["tasks"] if t.get("status_code") == 20100}
        info = c.call(f"app-info-{store}-post", f"/v3/app_data/{store}/app_info/task_post",
                      [{"app_id": cfg["apps"][store], **m}])
        infos = {t["id"]: f"app-info-{store}" for t in info["tasks"] if t.get("status_code") == 20100}
        collect_tasks(c, f"/v3/app_data/{store}/app_searches/task_get/advanced", searches)
        collect_tasks(c, f"/v3/app_data/{store}/app_info/task_get/advanced", infos)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("stage", choices=["stage1", "stage2"])
    ap.add_argument("--date", default=datetime.date.today().isoformat())
    ap.add_argument("--budget", type=float, default=6.0)
    ap.add_argument("--serp-keywords", type=int, default=30)
    args = ap.parse_args()
    cfg = json.loads((HERE / "tracking.json").read_text())
    c = Client(HERE / "data" / args.date, args.budget)
    print(f"Output: {c.out}  (already spent this date: ${c.spent():.4f}, budget ${args.budget:.2f})")
    if args.stage == "stage1":
        stage1(c, cfg)
    else:
        stage2(c, cfg, args.serp_keywords)
    print(f"Done. Spent this date: ${c.spent():.4f}")


if __name__ == "__main__":
    sys.exit(main())
