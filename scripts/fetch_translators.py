#!/usr/bin/env python3
"""Builds the translators list shown in the app from the public Weblate API.

Writes {"<lang>": [{"username": "...", "avatar": "<url>"}, ...], ...} to the given path.
Never fails the build: on any error (rate limit, network) it writes whatever it has so far.
Usage: fetch_translators.py <output.json>
"""
import json
import sys
import time
import urllib.error
import urllib.request
from collections import defaultdict

BASE = "https://hosted.weblate.org"
PROJECT = "ytdlnis"
PAGE_SIZE = 1000
# 2 = translation changed, 5 = translation added, 7 = suggestion accepted
ACTIONS = (2, 5, 7)
IGNORED_USERS = {"anonymous"}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "ytdlnis-ci", "Accept": "application/json"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                raise
            if attempt == 2:
                raise
        except urllib.error.URLError:
            if attempt == 2:
                raise
        time.sleep(2)


def collect(counts):
    for action in ACTIONS:
        url = f"{BASE}/api/projects/{PROJECT}/changes/?action={action}&page_size={PAGE_SIZE}"
        while url:
            page = get(url)
            for change in page["results"]:
                author = change.get("author")
                translation = change.get("translation")
                if not author or not translation:
                    continue
                username = author.rstrip("/").rsplit("/", 1)[-1]
                if username in IGNORED_USERS:
                    continue
                lang = translation.rstrip("/").rsplit("/", 1)[-1]
                counts[lang][username] += 1
            url = page.get("next")


def main(out):
    counts = defaultdict(lambda: defaultdict(int))
    try:
        collect(counts)
    except Exception as e:  # rate limit / network: keep partial data
        print(f"warning: stopped early: {e}", file=sys.stderr)

    result = {}
    for lang in sorted(counts):
        users = sorted(counts[lang].items(), key=lambda kv: (-kv[1], kv[0]))
        result[lang] = [
            {"username": u, "avatar": f"{BASE}/avatar/128/{u}.png"} for u, _ in users
        ]

    with open(out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print(f"wrote {sum(len(v) for v in result.values())} entries for {len(result)} languages")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "translators.json")
