#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Christoph Langner
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Demo data for store screenshots: serves the app API v1 with Christoph's own posts only.

    tools/demo-server.py [--port 8099]
    phone/emulator: adb reverse tcp:8099 tcp:8099, build with -PPLANETFOSS_DEBUG_URL=http://localhost:8099/

Everything comes from one call to the live API, /api/app/v1/source/linux-und-ich (posts and
images of linuxundich.de, which belong to Christoph). Other sources' titles, images and logos
never appear. Made up for the demo, so screenshots show every part of the app:
- times: the newest post is 25 minutes old, the gaps between posts shrink to an eighth;
- a burning supernova (Raspberry Pi), one in afterglow (TUXEDO), a comet (GNOME) – each from
  those posts, with honest numbers (one source);
- star counts for the Sternstunden.
Bundles are dropped (they name other sources). Read-only, nothing is counted.
"""
import argparse
import hashlib
import json
import urllib.request
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BASE = "https://planetfoss.net"
SOURCE = "linux-und-ich"
NOVA, AFTERGLOW, COMET = "raspberry-pi", "tuxedo", "gnome"
STARS = (11, 8, 6, 4, 3)


def fetch(ui):
    req = urllib.request.Request(f"{BASE}/api/app/v1/source/{SOURCE}?lang=&ui={ui}", headers={"User-Agent": "PlanetFOSS-demo-server"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def build(ui):
    answer = fetch(ui)
    items = [dict(i, bundle=None) for i in answer["items"]]
    # recent times: newest 25 minutes ago, gaps an eighth of the real ones
    times = [datetime.fromisoformat(i["published_at"].replace("Z", "+00:00")) for i in items]
    now = datetime.now(timezone.utc)
    for i, t in zip(items, times):
        i["published_at"] = iso(now - timedelta(minutes=25) - (times[0] - t) / 8)
    by_topic = {}
    for i in items:
        if i.get("topic"):
            by_topic.setdefault(i["topic"]["key"], []).append(i)
    for i in by_topic.get(NOVA, []):
        i["topic"] = dict(i["topic"], supernova=True)
    starred = [dict(i, stars=n) for i, n in zip(items[:12:2], STARS)]
    stars = {i["id"]: i["stars"] for i in starred}
    for i in items:
        i["stars"] = stars.get(i["id"], 0)

    def nova(key, burning):
        rows = by_topic.get(key, [])
        if not rows:
            return None
        n = len(rows)
        summary = f"{n} Beiträge · 1 Quelle" if ui == "de" else f"{n} posts · 1 source"
        return {"id": f"tag:planetfoss.net,2026:supernova/{key}/demo", "key": key, "label": rows[0]["topic"]["label"],
                "ignited_at": rows[0]["published_at"], "announced_at": rows[0]["published_at"], "url": f"{BASE}/thema/{key}/",
                "summary": summary, "quote": None, "stats": {"items": n, "sources": 1}, "burning": burning, "reports": rows}

    novas = [n for n in (nova(NOVA, True), nova(AFTERGLOW, False)) if n]
    comet_rows = by_topic.get(COMET, [])
    comets = [{"key": COMET, "label": comet_rows[0]["topic"]["label"], "url": f"{BASE}/thema/{COMET}/",
               "first_at": comet_rows[-1]["published_at"], "last_at": comet_rows[0]["published_at"],
               "stats": {"items": len(comet_rows), "sources": 1}, "reports": comet_rows}] if len(comet_rows) >= 2 else []
    rubrics = {}
    for i in items:
        for c in i["categories"]:
            r = rubrics.setdefault(c["slug"], {"slug": c["slug"], "name": c["name"], "hot_topics": []})
            t = i.get("topic")
            if t and t["key"] in (NOVA, COMET) and t not in r["hot_topics"]:
                r["hot_topics"].append(t)
    return {"items": items, "source": answer["source"], "novas": novas, "comets": comets, "starred": starred, "rubrics": list(rubrics.values())}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8099)
    args = ap.parse_args()
    demo = {ui: build(ui) for ui in ("de", "en")}
    print(f"demo: {len(demo['de']['items'])} posts, {len(demo['de']['novas'])} supernovas, {len(demo['de']['comets'])} comets")

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            url = urlparse(self.path)
            q = {k: v[0] for k, v in parse_qs(url.query, keep_blank_values=True).items()}
            d = demo["en" if q.get("ui") == "en" else "de"]
            # the demo posts are German: “English only” shows an empty list, as it would for this source
            items = [] if q.get("lang") == "en" else d["items"]
            name = url.path.removeprefix("/api/app/v1/")
            kind, _, ref = name.partition("/")
            if q.get("page", "1") not in ("", "1"):
                return self.answer(200, {"items": [], "has_more": False})
            if name == "home":
                return self.answer(200, {"items": items, "has_more": False, "ad": None})
            if name == "supernovas":
                return self.answer(200, {"supernovas": d["novas"] if items else [], "comets": d["comets"] if items else []})
            if name == "starred":
                return self.answer(200, {"items": d["starred"] if items else [], "has_more": False, "ad": None})
            if name == "rubrics":
                return self.answer(200, {"rubrics": d["rubrics"]})
            if name == "videos":
                return self.answer(200, {"items": [], "has_more": False})
            if kind == "rubric":
                r = next((x for x in d["rubrics"] if x["slug"] == ref), None)
                rows = [i for i in items if any(c["slug"] == ref for c in i["categories"])]
                return self.answer(200, {"rubric": r, "items": rows, "has_more": False}) if r else self.answer(404, {"error": "not_found"})
            if kind == "topic":
                rows = [i for i in items if (i.get("topic") or {}).get("key") == ref]
                head = rows[0]["topic"] if rows else {"key": ref, "label": ref, "supernova": False}
                return self.answer(200, {"topic": head, "items": rows, "has_more": False})
            if kind == "source" and ref == SOURCE:
                return self.answer(200, {"source": d["source"], "items": items, "has_more": False})
            if name == "search":
                needle = q.get("q", "").strip().lower()
                if len(needle) < 2:
                    return self.answer(400, {"error": "bad_request"})
                hits = [i for i in items if needle in (i["title"] + " " + (i.get("excerpt") or "")).lower()]
                topics = list({i["topic"]["key"]: i["topic"] for i in hits if i.get("topic")}.values())[:4]
                return self.answer(200, {"items": hits, "topics": topics})
            self.answer(404, {"error": "not_found"})

        def answer(self, code, body):
            raw = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("ETag", '"' + hashlib.sha1(raw).hexdigest()[:20] + '"')
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def log_message(self, fmt, *a):
            pass

    print(f"serving on :{args.port}")
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
