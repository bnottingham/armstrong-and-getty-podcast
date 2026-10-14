#!/usr/bin/env python3
"""Condense one dataforseo_pass.py snapshot into small, diffable tables.

  python3 tools/seo/digest_pass.py --date 2026-10-13

Reads tools/seo/data/<date>/*.json (raw API responses) and writes
tools/seo/data/<date>/digest/*.json — the inputs for analysis and for
comparing one measurement pass against the next.
"""

import argparse
import datetime
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent


def results(path):
    if not path.exists():
        return []
    out = []
    for task in json.loads(path.read_text()).get("tasks", []):
        out.extend(task.get("result") or [])
    return out


def items(path):
    return [it for r in results(path) for it in (r.get("items") or [])]


def kw_row(it):
    """Labs keyword item (keyword_overview / suggestions / ideas / related['keyword_data'])."""
    info = it.get("keyword_info") or {}
    props = it.get("keyword_properties") or {}
    intent = (it.get("search_intent_info") or {}).get("main_intent")
    return {"keyword": it["keyword"], "volume": info.get("search_volume"), "kd": props.get("keyword_difficulty"),
            "intent": intent, "cpc": info.get("cpc"),
            "trend_yearly": (info.get("search_volume_trend") or {}).get("yearly")}


def google_keywords(d):
    vol = {r["keyword"]: r for r in results(d / "google-ads-search-volume.json")}
    over = {it["keyword"]: kw_row(it) for it in items(d / "labs-keyword-overview.json")}
    rows = []
    for k in sorted(set(vol) | set(over)):
        o = over.get(k, {"keyword": k})
        rows.append({**o, "keyword": k, "ads_volume": (vol.get(k) or {}).get("search_volume")})
    return sorted(rows, key=lambda r: -(r.get("ads_volume") or r.get("volume") or 0))


def discovery(d):
    seen = {}
    for f in sorted(d.glob("labs-suggestions-*.json")) + [d / "labs-ideas-generic.json"]:
        for it in items(f):
            seen.setdefault(it["keyword"], {**kw_row(it), "source": f.stem})
    for f in sorted(d.glob("labs-related-*.json")):
        for it in items(f):
            kd = it.get("keyword_data") or {}
            if kd.get("keyword"):
                seen.setdefault(kd["keyword"], {**kw_row(kd), "source": f.stem})
    return sorted(seen.values(), key=lambda r: -(r.get("volume") or 0))


def ranked(path, limit=None):
    rows = []
    for it in items(path):
        kd = it.get("keyword_data") or {}
        info = kd.get("keyword_info") or {}
        el = (it.get("ranked_serp_element") or {}).get("serp_item") or {}
        rows.append({"keyword": kd.get("keyword"), "volume": info.get("search_volume"),
                     "rank": el.get("rank_group"), "type": el.get("type"), "url": el.get("url"),
                     "etv": el.get("etv")})
    rows.sort(key=lambda r: -(r["volume"] or 0))
    return rows[:limit] if limit else rows


def store_keywords(d, store, cfg):
    names = {k.lower(): v for k, v in {**cfg["competitor_apps"][store], cfg["apps"][store]: "OURS"}.items()}
    by_kw = {}
    per_app = {}
    for f in sorted(d.glob(f"labs-{store}-keywords-for-app-*.json")):
        for r in results(f):
            app = r.get("app_id")
            per_app[app] = {"name": names.get(app.lower(), app), "total_count": r.get("total_count")}
            for it in r.get("items") or []:
                kd = it["keyword_data"]
                el = it["ranked_serp_element"]["serp_item"]
                row = by_kw.setdefault(kd["keyword"], {"keyword": kd["keyword"],
                                                      "store_volume": kd["keyword_info"].get("search_volume"),
                                                      "ranks": {}})
                row["ranks"][names.get(app.lower(), app)] = el.get("rank_group")
    rows = sorted(by_kw.values(), key=lambda r: -(r["store_volume"] or 0))
    return {"apps": per_app, "keywords": rows}


def app_competitors(d, store):
    out = []
    for it in items(d / f"labs-{store}-app-competitors-ours.json"):
        m = (it.get("full_metrics") or {})
        m = next(iter(m.values()), {}) if m else {}
        out.append({"app_id": it.get("app_id"), "intersections": it.get("intersections"),
                    "avg_position": it.get("avg_position"), "search_volume": m.get("search_volume")})
    return out


def serps(d, cfg):
    site = cfg["site"]
    apple = f"id{cfg['apps']['apple']}"
    google = cfg["apps"]["google"]
    rows = []
    for f in sorted(d.glob("serp-desktop-*.json")) + sorted(d.glob("serp-mobile-*.json")):
        for r in results(f):
            ours, features, top = {}, [], []
            for it in r.get("items") or []:
                t = it.get("type")
                url = it.get("url") or ""
                if t != "organic":
                    features.append(f"{t}@{it.get('rank_absolute')}")
                if t == "organic" and len(top) < 10:
                    top.append(f"{it.get('rank_group')}. {it.get('domain')}")
                for label, needle in (("site", site), ("app_store", apple), ("play", google)):
                    hit = needle in url or any(needle in (sub.get("url") or "")
                                               for sub in (it.get("items") or []) if isinstance(sub, dict))
                    if hit and label not in ours:
                        ours[label] = {"rank_group": it.get("rank_group"), "rank_absolute": it.get("rank_absolute"), "type": t}
            rows.append({"keyword": r.get("keyword"), "device": f.stem.split("-")[1], "ours": ours,
                         "features": features, "top10": top, "results_count": r.get("se_results_count")})
    return rows


def app_searches(d, cfg, store):
    ours = cfg["apps"][store]
    rows = []
    for f in sorted(d.glob(f"app-search-{store}-*.json")):
        for r in results(f):
            its = r.get("items") or []
            pos = next((it.get("rank_group") for it in its if it.get("app_id") == ours), None)
            rows.append({"keyword": r.get("keyword"), "our_rank": pos, "results": len(its),
                         "top5": [f"{it.get('rank_group')}. {it.get('title')} ({(it.get('rating') or {}).get('votes_count')} ratings)"
                                  for it in its[:5]]})
    return sorted(rows, key=lambda r: (r["our_rank"] is None, r["our_rank"] or 0))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--date", default=datetime.date.today().isoformat())
    args = ap.parse_args()
    cfg = json.loads((HERE / "tracking.json").read_text())
    d = HERE / "data" / args.date
    out = d / "digest"
    out.mkdir(exist_ok=True)
    tables = {
        "google-keywords": google_keywords(d),
        "discovery-keywords": discovery(d),
        "ranked-our-site": ranked(d / f"labs-ranked-{cfg['site'].replace('.', '-')}.json"),
        "ranked-armstrongandgetty-com": ranked(d / "labs-ranked-armstrongandgetty-com.json", 300),
        "ranked-competitor-pages": {f.stem.replace("labs-ranked-page-", ""): ranked(f, 100)
                                    for f in sorted(d.glob("labs-ranked-page-*.json"))},
        "serp-competitors-brand": [{"domain": it.get("domain"), "avg_position": it.get("avg_position"),
                                    "keywords_count": it.get("keywords_count"), "etv": it.get("etv"),
                                    "visibility": it.get("visibility")}
                                   for it in items(d / "labs-serp-competitors-brand.json")],
        "domain-rank": {f.stem.replace("labs-domain-rank-", ""): (items(f) or [{}])[0].get("metrics")
                        for f in sorted(d.glob("labs-domain-rank-*.json"))},
        "backlinks": {f.stem.replace("backlinks-summary-", ""): {k: (results(f) or [{}])[0].get(k) for k in
                      ("rank", "backlinks", "referring_domains", "referring_main_domains", "broken_backlinks")}
                      for f in sorted(d.glob("backlinks-summary-*.json"))},
        "store-apple-keywords": store_keywords(d, "apple", cfg),
        "store-google-keywords": store_keywords(d, "google", cfg),
        "store-apple-app-competitors": app_competitors(d, "apple"),
        "store-google-app-competitors": app_competitors(d, "google"),
        "serps": serps(d, cfg),
        "app-searches-apple": app_searches(d, cfg, "apple"),
        "app-searches-google": app_searches(d, cfg, "google"),
        "app-info": {s: (items(d / f"app-info-{s}.json") or [None])[0] for s in ("apple", "google")},
    }
    for name, data in tables.items():
        (out / f"{name}.json").write_text(json.dumps(data, indent=1))
        size = len(data) if isinstance(data, (list, dict)) else 0
        print(f"  {name:<36} {size} entries")
    ledger = d / "ledger.jsonl"
    if ledger.exists():
        total = sum(json.loads(line)["cost"] for line in ledger.read_text().splitlines() if line.strip())
        print(f"Pass cost so far: ${total:.4f}")


if __name__ == "__main__":
    main()
