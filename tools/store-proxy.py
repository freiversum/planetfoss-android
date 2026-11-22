#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Christoph Langner
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Live data for store screenshots: passes the app API v1 of planetfoss.net through, with all sources,
but only with the article images of cleared sources.

    tools/store-proxy.py [--port 8099]
    phone/emulator: adb reverse tcp:8099 tcp:8099, build with -PPLANETFOSS_DEBUG_URL=http://localhost:8099/

Unlike tools/demo-server.py (only Christoph's own posts, made-up times) this shows the real mix of
sources: titles, source names and logos as the app shows them. Article images stay only for the
sources in IMAGES (linuxundich.de; OMG! Ubuntu and LinuxNews cleared by Christoph on 2026-11-22); every
other `image` becomes null. `--feed a,b,…` limits Latest (`home`, and lead, side column and rubrics of
`front`) to those sources, so the first screen shows them. Sponsor cards (`ad`) are dropped. Read-only; each request is one live API call.
"""
import argparse
import json
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = "https://planetfoss.net"
IMAGES = ("linux-und-ich", "linux-und-ich-2", "omg-ubuntu", "linuxnews")


def own_images(node, found):
    """Image URLs of posts from the sources in IMAGES anywhere in the answer."""
    if isinstance(node, dict):
        src = node.get("source")
        if isinstance(src, dict) and src.get("slug") in IMAGES and isinstance(node.get("image"), dict):
            found.update(v for v in node["image"].values() if isinstance(v, str))
        for v in node.values():
            own_images(v, found)
    elif isinstance(node, list):
        for v in node:
            own_images(v, found)


def strip(node, own):
    if isinstance(node, dict):
        img = node.get("image")
        if isinstance(img, dict):
            src = node.get("source")
            mine = isinstance(src, dict) and src.get("slug") in IMAGES
            # leads without a source (logbook, weekly) keep the image only if it is one of those posts'
            if not mine and not any(v in own for v in img.values() if isinstance(v, str)):
                node["image"] = None
        if "ad" in node:
            node["ad"] = None
        for k, v in node.items():
            if k != "image":
                strip(v, own)
    elif isinstance(node, list):
        for v in node:
            strip(v, own)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8099)
    ap.add_argument("--feed", default="", help="comma-separated source slugs for the Latest list")
    ap.add_argument("--no-novas", action="store_true", help="no supernovas or comets (Latest then opens on the lead)")
    args = ap.parse_args()
    feed = {x for x in args.feed.split(",") if x}

    def fetch(path):
        req = urllib.request.Request(BASE + path, headers={"User-Agent": "PlanetFOSS-store-proxy"})
        with urllib.request.urlopen(req, timeout=30) as r:
            body = json.load(r)
        own = set()
        own_images(body, own)
        strip(body, own)
        return body

    def feed_rows(path):
        """The first five live pages of Latest, only the chosen sources: one long first page."""
        rows = []
        for page in range(1, 6):
            rows += fetch(path.replace("page=1", f"page={page}")).get("items", [])
        return [i for i in rows if i.get("source", {}).get("slug") in feed]

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            req = urllib.request.Request(BASE + self.path, headers={"User-Agent": "PlanetFOSS-store-proxy"})
            try:
                with urllib.request.urlopen(req, timeout=30) as r:
                    code, raw, ctype = r.status, r.read(), r.headers.get("Content-Type", "")
            except urllib.error.HTTPError as e:
                code, raw, ctype = e.code, e.read(), e.headers.get("Content-Type", "")
            if "json" in ctype:
                body = json.loads(raw)
                own = set()
                own_images(body, own)
                strip(body, own)
                if feed and self.path.startswith("/api/app/v1/home") and isinstance(body.get("items"), list):
                    body["items"] = feed_rows(self.path) if "page=1" in self.path else []
                    body["has_more"] = False
                if args.no_novas and self.path.startswith("/api/app/v1/supernovas"):
                    body["supernovas"], body["comets"] = [], []
                if feed and self.path.startswith("/api/app/v1/front"):
                    # lead and side column from the chosen sources: the newest post with a picture leads (linuxundich.de only if no other has one)
                    rows = feed_rows("/api/app/v1/home?" + self.path.partition("?")[2] + "&page=1")
                    pictured = [i for i in rows if i.get("image")]
                    lead = next((i for i in pictured if not i["source"]["slug"].startswith("linux-und-ich")),
                                pictured[0] if pictured else (rows[0] if rows else None))
                    body["lead"] = lead
                    side, seen = [], {lead["source"]["slug"]} if lead else set()
                    # one post per source, so the column shows the mix; other sources with a picture first, linuxundich.de last
                    order = sorted(rows, key=lambda i: (i["source"]["slug"].startswith("linux-und-ich"), not i.get("image")))
                    for i in order:
                        if i is not lead and i["source"]["slug"] not in seen and len(side) < 3:
                            side.append(i)
                            seen.add(i["source"]["slug"])
                    body["side"] = side
                    for r in body.get("rubrics", []):
                        r["items"] = [i for i in r.get("items", []) if i.get("source", {}).get("slug") in feed]
                    body["rubrics"] = [r for r in body.get("rubrics", []) if r.get("items")]
                raw = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(code)
            self.send_header("Content-Type", ctype or "application/octet-stream")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def log_message(self, fmt, *a):
            print(self.command, self.path)

    print(f"store proxy for {BASE} on :{args.port}")
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
