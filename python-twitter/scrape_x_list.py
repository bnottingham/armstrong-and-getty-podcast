#!/usr/bin/env python3
"""
Scrape an X (Twitter) list by intercepting the internal GraphQL API calls.

Uses Playwright with a persistent browser session so you only need to log in once.
On first run, a browser window opens — log into X manually, then the script proceeds.

Usage:
    python scrape_x_list.py                          # scrape default list
    python scrape_x_list.py --url <list_url>         # scrape a specific list
    python scrape_x_list.py --output results.json    # custom output file
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import sys
import time

from playwright.async_api import async_playwright

DEFAULT_LIST_URL = "https://x.com/i/lists/2030705875623755954"
BROWSER_DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "browser_data")

# How long to wait (seconds) with no new members before assuming we've loaded them all
SCROLL_IDLE_TIMEOUT = 5
# Max scrolls before giving up
MAX_SCROLLS = 200
# Navigation timeout in milliseconds (X can be slow)
NAV_TIMEOUT = 90_000
# Max retries for page navigation
NAV_RETRIES = 3


def parse_user_from_result(result: dict) -> dict | None:
    """Extract a clean user dict from an X GraphQL user_results object."""
    if not result or result.get("__typename") == "UserUnavailable":
        return None

    legacy = result.get("legacy", {})
    if not legacy:
        return None

    # screen_name / name can live in legacy OR at the top-level result
    screen_name = legacy.get("screen_name") or result.get("screen_name") or result.get("core", {}).get("screen_name")
    name = legacy.get("name") or result.get("name")

    return {
        "id": result.get("rest_id"),
        "name": name,
        "screen_name": screen_name,
        "description": legacy.get("description"),
        "location": legacy.get("location"),
        "url": legacy.get("url"),
        "verified": legacy.get("verified", False),
        "is_blue_verified": result.get("is_blue_verified", False),
        "followers_count": legacy.get("followers_count"),
        "following_count": legacy.get("friends_count"),
        "statuses_count": legacy.get("statuses_count"),
        "listed_count": legacy.get("listed_count"),
        "created_at": legacy.get("created_at"),
        "profile_image_url": legacy.get("profile_image_url_https", "").replace("_normal", "_400x400"),
        "profile_banner_url": legacy.get("profile_banner_url"),
        "pinned_tweet_ids": legacy.get("pinned_tweet_ids_str", []),
        "professional": result.get("professional"),
    }


def parse_tweet_from_result(result: dict) -> dict | None:
    """Extract a clean tweet dict from an X GraphQL tweet_results object."""
    if not result or result.get("__typename") == "TweetUnavailable":
        return None

    tweet = result.get("tweet", result)  # handle TweetWithVisibilityResults wrapper
    legacy = tweet.get("legacy", {})
    core = tweet.get("core", {})
    if not legacy:
        return None

    author = None
    user_results = core.get("user_results", {}).get("result")
    if user_results:
        author = parse_user_from_result(user_results)

    full_text = _get_full_text(tweet, legacy)

    # For retweets, X truncates legacy.full_text. The complete original text
    # lives inside retweeted_status_result at the tweet level (or sometimes
    # inside legacy). Reconstruct the full "RT @user: ..." text from there.
    is_retweet = False
    rt_result = (
        tweet.get("retweeted_status_result", {}).get("result")
        or legacy.get("retweeted_status_result", {}).get("result")
    )
    if rt_result:
        is_retweet = True
        rt_tweet = rt_result.get("tweet", rt_result)  # unwrap visibility wrapper
        rt_legacy = rt_tweet.get("legacy", {})
        rt_core = rt_tweet.get("core", {})
        rt_full_text = _get_full_text(rt_tweet, rt_legacy)
        # Try multiple places for the original author's screen_name
        rt_screen_name = (
            rt_legacy.get("screen_name")
            or rt_tweet.get("screen_name")
            or rt_core.get("user_results", {}).get("result", {}).get("legacy", {}).get("screen_name")
            or ""
        )
        if rt_full_text:
            full_text = f"RT @{rt_screen_name}: {rt_full_text}" if rt_screen_name else rt_full_text

    # For quote tweets, also grab the quoted tweet's full text
    quoted_text = None
    qt_result = (
        tweet.get("quoted_status_result", {}).get("result")
        or legacy.get("quoted_status_result", {}).get("result")
    )
    if qt_result:
        qt_tweet = qt_result.get("tweet", qt_result)
        qt_legacy = qt_tweet.get("legacy", {})
        qt_raw = _get_full_text(qt_tweet, qt_legacy)
        quoted_text, _ = _strip_tco_urls(qt_raw) if qt_raw else (None, [])

    # Separate the actual text from the t.co short-links X appends
    text_clean, tco_urls = _strip_tco_urls(full_text) if full_text else ("", [])

    # Build a direct link to this tweet on X
    tweet_id = tweet.get("rest_id")
    screen_name = (author or {}).get("screen_name")
    if screen_name:
        tweet_url = f"https://x.com/{screen_name}/status/{tweet_id}"
    else:
        # Fallback format that works without knowing the author's handle
        tweet_url = f"https://x.com/i/web/status/{tweet_id}" if tweet_id else None

    return {
        "id": tweet.get("rest_id"),
        "text": text_clean,
        "tweet_url": tweet_url,
        "tco_urls": tco_urls,
        "created_at": legacy.get("created_at"),
        "author": author,
        "retweet_count": legacy.get("retweet_count"),
        "favorite_count": legacy.get("favorite_count"),
        "reply_count": legacy.get("reply_count"),
        "quote_count": legacy.get("quote_count"),
        "bookmark_count": legacy.get("bookmark_count"),
        "lang": legacy.get("lang"),
        "is_retweet": is_retweet,
        "is_quote": legacy.get("is_quote_status", False),
        "quoted_text": quoted_text,
        "media": _extract_media(legacy),
        "urls": _extract_urls(legacy),
    }


_TRAILING_TCO_RE = re.compile(r'\s*https://t\.co/\w+')


def _get_full_text(tweet: dict, legacy: dict) -> str | None:
    """Get the untruncated text for a tweet.

    Checks note_tweet first (long-form tweets >280 chars), then falls
    back to legacy.full_text.
    """
    note_text = (
        tweet.get("note_tweet", {})
        .get("note_tweet_results", {})
        .get("result", {})
        .get("text")
    )
    return note_text or legacy.get("full_text")


def _strip_tco_urls(text: str) -> tuple[str, list[str]]:
    """Strip all t.co short-URLs from text.

    Returns (cleaned_text, list_of_tco_urls).
    """
    tco_urls = _TRAILING_TCO_RE.findall(text)
    tco_urls = [u.strip() for u in tco_urls]
    cleaned = _TRAILING_TCO_RE.sub('', text).strip()
    return cleaned, tco_urls


def _extract_media(legacy: dict) -> list:
    """Pull media items from a tweet's legacy object."""
    media_list = []
    for m in legacy.get("extended_entities", {}).get("media", []):
        item = {
            "type": m.get("type"),
            "url": m.get("media_url_https"),
            "expanded_url": m.get("expanded_url"),
        }
        if m.get("type") == "video":
            variants = m.get("video_info", {}).get("variants", [])
            best = max(
                (v for v in variants if v.get("bitrate") is not None),
                key=lambda v: v.get("bitrate", 0),
                default=None,
            )
            if best:
                item["video_url"] = best["url"]
        media_list.append(item)
    return media_list


def _extract_urls(legacy: dict) -> list:
    """Pull expanded URLs from a tweet's legacy object."""
    return [
        {"url": u.get("expanded_url"), "display": u.get("display_url")}
        for u in legacy.get("entities", {}).get("urls", [])
    ]


def extract_entries_from_instructions(instructions: list) -> list:
    """Flatten timeline instructions into a list of entry objects."""
    entries = []
    for instruction in instructions:
        inst_type = instruction.get("type", "")
        if inst_type in ("TimelineAddEntries", "TimelineAddToModule"):
            entries.extend(instruction.get("entries", []))
            # TimelineAddToModule puts items in moduleItems
            entries.extend(instruction.get("moduleItems", []))
    return entries


def parse_members_response(data: dict) -> list[dict]:
    """Parse a ListMembers GraphQL response into a list of user dicts."""
    members = []
    try:
        timeline = data["data"]["list"]["members_timeline"]["timeline"]
        entries = extract_entries_from_instructions(timeline.get("instructions", []))
        for entry in entries:
            content = entry.get("content", entry.get("item", {}))
            item_content = content.get("itemContent", {})
            user_result = item_content.get("user_results", {}).get("result")
            if user_result:
                user = parse_user_from_result(user_result)
                if user:
                    members.append(user)
    except (KeyError, TypeError):
        pass
    return members


def parse_timeline_response(data: dict) -> list[dict]:
    """Parse a ListLatestTweetsTimeline GraphQL response into a list of tweet dicts."""
    tweets = []
    try:
        timeline = data["data"]["list"]["tweets_timeline"]["timeline"]
        entries = extract_entries_from_instructions(timeline.get("instructions", []))
        for entry in entries:
            content = entry.get("content", entry.get("item", {}))
            item_content = content.get("itemContent", {})
            tweet_result = item_content.get("tweet_results", {}).get("result")
            if tweet_result:
                tweet = parse_tweet_from_result(tweet_result)
                if tweet:
                    tweets.append(tweet)
            # Conversation threads live under a "module" entry with an inner items list.
            for module_item in content.get("items", []):
                ic = module_item.get("item", {}).get("itemContent", {})
                tr = ic.get("tweet_results", {}).get("result")
                if tr:
                    tweet = parse_tweet_from_result(tr)
                    if tweet:
                        tweets.append(tweet)
    except (KeyError, TypeError):
        pass
    return tweets


def parse_list_details(data: dict) -> dict | None:
    """Parse a ListByRestId response for list metadata."""
    try:
        lst = data["data"]["list"]
        return {
            "id": lst.get("id_str"),
            "name": lst.get("name"),
            "description": lst.get("description"),
            "member_count": lst.get("member_count"),
            "subscriber_count": lst.get("subscriber_count"),
            "created_at": lst.get("created_at"),
            "mode": lst.get("mode"),
        }
    except (KeyError, TypeError):
        return None


async def wait_for_login(page) -> None:
    """Wait until the user is logged in (detect a logged-in indicator)."""
    print("\n🔑  Please log into X in the browser window...")
    print("    (waiting for login to complete)\n")
    while True:
        logged_in = await page.evaluate("""() => {
            return document.cookie.includes('auth_token') ||
                   document.cookie.includes('ct0');
        }""")
        if logged_in:
            print("✅  Login detected!\n")
            return
        await asyncio.sleep(2)


async def do_login() -> None:
    """Open a visible browser so the user can log into X. Session is saved for future headless runs."""
    async with async_playwright() as p:
        context = await p.chromium.launch_persistent_context(
            user_data_dir=BROWSER_DATA_DIR,
            headless=False,
            viewport={"width": 1280, "height": 900},
            args=["--disable-blink-features=AutomationControlled"],
        )
        page = context.pages[0] if context.pages else await context.new_page()
        await page.goto("https://x.com/login", wait_until="domcontentloaded")
        await wait_for_login(page)
        print("Session saved. You can now run scrapes headlessly.")
        await context.close()


async def _goto_with_retry(page, url: str) -> None:
    """Navigate to a URL with retries and a generous timeout.

    X can be very slow to respond, especially after heavy scrolling, so we
    allow multiple attempts and fall back to waiting for any network activity
    to settle rather than requiring a specific load event.
    """
    for attempt in range(1, NAV_RETRIES + 1):
        try:
            await page.goto(url, wait_until="domcontentloaded", timeout=NAV_TIMEOUT)
            await asyncio.sleep(3)
            return
        except Exception as e:
            if attempt < NAV_RETRIES:
                print(f"   ⚠  Navigation timed out (attempt {attempt}/{NAV_RETRIES}), retrying...")
                await asyncio.sleep(2)
            else:
                # Last resort: try with a more lenient wait condition
                print(f"   ⚠  Retries exhausted, trying with 'commit' wait...")
                try:
                    await page.goto(url, wait_until="commit", timeout=NAV_TIMEOUT)
                    await asyncio.sleep(5)
                    return
                except Exception:
                    raise RuntimeError(
                        f"Failed to navigate to {url} after {NAV_RETRIES} attempts: {e}"
                    ) from e


async def scrape_list(list_url: str, output_path: str, include_timeline: bool = True) -> None:
    """Main scraping logic."""
    members_by_id: dict[str, dict] = {}
    tweets_by_id: dict[str, dict] = {}
    list_info: dict | None = None

    async with async_playwright() as p:
        context = await p.chromium.launch_persistent_context(
            user_data_dir=BROWSER_DATA_DIR,
            headless=True,
            viewport={"width": 1280, "height": 900},
            args=["--disable-blink-features=AutomationControlled"],
        )
        page = context.pages[0] if context.pages else await context.new_page()

        async def on_response(response):
            nonlocal list_info
            url = response.url
            if "/graphql/" not in url:
                return
            try:
                if response.status != 200:
                    return
                body = await response.json()
            except Exception:
                return

            if "ListMembers" in url:
                for m in parse_members_response(body):
                    members_by_id[m["id"]] = m
            elif "ListLatestTweetsTimeline" in url or "ListTweets" in url:
                for t in parse_timeline_response(body):
                    tweets_by_id[t["id"]] = t
            elif "ListByRestId" in url:
                info = parse_list_details(body)
                if info:
                    list_info = info

        page.on("response", on_response)

        print(f"Navigating to list: {list_url}")
        await _goto_with_retry(page, list_url)

        if "login" in page.url.lower() or await page.query_selector('[data-testid="loginButton"]'):
            await context.close()
            sys.exit("ERROR: Not logged in. Run with --login first to sign into X.")

        # Scrape the timeline first — navigating to /members clears the tweet responses we've intercepted.
        if include_timeline:
            print("📰  Collecting timeline tweets...")
            prev_count = 0
            idle_count = 0
            for i in range(MAX_SCROLLS):
                await page.evaluate("window.scrollBy(0, 1500)")
                await asyncio.sleep(1.5)
                count = len(tweets_by_id)
                if count == prev_count:
                    idle_count += 1
                    if idle_count >= SCROLL_IDLE_TIMEOUT:
                        break
                else:
                    idle_count = 0
                    prev_count = count
                    print(f"   tweets collected: {count}", end="\r")
            print(f"\n   ✅  Total tweets: {len(tweets_by_id)}")

        members_url = list_url.rstrip("/") + "/members"
        print(f"\n👥  Navigating to members: {members_url}")
        await _goto_with_retry(page, members_url)

        print("   Scrolling to load all members...")
        prev_count = 0
        idle_count = 0
        for i in range(MAX_SCROLLS):
            await page.evaluate("window.scrollBy(0, 1500)")
            await asyncio.sleep(1.5)
            count = len(members_by_id)
            if count == prev_count:
                idle_count += 1
                if idle_count >= SCROLL_IDLE_TIMEOUT:
                    break
            else:
                idle_count = 0
                prev_count = count
                print(f"   members loaded: {count}", end="\r")

        print(f"\n   ✅  Total members: {len(members_by_id)}")

        await context.close()

    output = {
        "scraped_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "list_url": list_url,
        "list_info": list_info,
        "members_count": len(members_by_id),
        "members": sorted(members_by_id.values(), key=lambda u: u.get("followers_count", 0), reverse=True),
    }
    if include_timeline:
        output["tweets_count"] = len(tweets_by_id)
        output["tweets"] = sorted(tweets_by_id.values(), key=lambda t: t.get("id", ""), reverse=True)

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"\n💾  Saved to {output_path}")
    print(f"    Members: {len(members_by_id)}")
    if include_timeline:
        print(f"    Tweets:  {len(tweets_by_id)}")


def main():
    parser = argparse.ArgumentParser(description="Scrape an X (Twitter) list to JSON")
    parser.add_argument("--url", default=DEFAULT_LIST_URL, help="X list URL to scrape")
    parser.add_argument("--output", "-o", default="x_list_data.json", help="Output JSON file path")
    parser.add_argument("--no-timeline", action="store_true", help="Skip scraping the timeline, only get members")
    parser.add_argument("--login", action="store_true", help="Open a browser to log into X (run once, then scrape headlessly)")
    args = parser.parse_args()

    if args.login:
        asyncio.run(do_login())
    else:
        asyncio.run(scrape_list(args.url, args.output, include_timeline=not args.no_timeline))


if __name__ == "__main__":
    main()
