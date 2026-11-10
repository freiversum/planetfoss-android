#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Christoph Langner
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Development only: serves /api/app/v1/home, /api/app/v1/supernovas (and test lists such as starred) in the v1
format (docs/02-api.md) while the API is not deployed. On start it fetches the
live RSS feeds and the start pages of planetfoss.net once (for images, logos and
rubrics) and converts them; afterwards it answers from memory. Supports ETag/304. Also rubrics, rubric/topic/source (from the
website's own feeds on first request), search (over what was loaded) and a test hook
GET /_inject?key=<key> that adds a supernova announced now (for notification tests)
and GET /_inject_item?url=<url>&title=<t> that puts a post with any URL on top of Latest.

    tools/fixture-server.py [--port 8099] [--ads] [--replies] [--support SLUG=URL[|PROJECT] ...]
      --replies adds invented fediverse replies ("example.invalid") to every topic head (report/block menu tests)
      --ads serves invented sponsor cards ("Musterhost (Demo)") as the candidates of both app slots
      --support gives a source a support link; with |PROJECT it recommends that project instead (“forward”)
    emulator: ./gradlew :app:installDebug -PPLANETFOSS_DEBUG_URL=http://10.0.2.2:8099/
    phone:    adb reverse tcp:8099 tcp:8099, then -PPLANETFOSS_DEBUG_URL=http://localhost:8099/

The data stays in memory; nothing from the sources is written to the repository.
"""
import argparse
import email.utils
import hashlib
import html
import json
import re
import zlib
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BASE = "https://planetfoss.net"
UA = "PlanetFOSS-Android-fixture/0.1"
PER_PAGE = 30


def fetch(path):
    req = urllib.request.Request(BASE + path, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8")


def iso(rfc822):
    return email.utils.parsedate_to_datetime(rfc822).strftime("%Y-%m-%dT%H:%M:%SZ")


def cards(page_html):
    """Article URL → image, logo, rubric, topic from the website's cards."""
    out = {}
    for a in re.findall(r"<article.*?</article>", page_html, re.S):
        link = re.search(r'<h3><a href="(https?://[^"]+)"', a)
        if not link:
            continue
        img = re.search(r'src="(/img/\d{4}/\d\d/[0-9a-f]+)-480\.webp"', a)
        logo = re.search(r'<span class="src"><img class="fav" src="(/img/logos/[^"]+)"', a)
        rubric = re.search(r'<a class="rb" href="/(?:en/)?(?:rubrik|category)/([a-z0-9-]+)/">([^<]+)</a>', a)
        topic = re.search(r'<a class="tchip" href="/(?:en/)?(?:thema|topic)/([a-z0-9-]+)/">([^<]+)</a>', a)
        nova = "Supernova" in a or "nova" in (re.search(r'class="tchip[^"]*"', a) or [""])[0]
        out[html.unescape(link.group(1))] = {
            "img": img.group(1) if img else None,
            "logo": logo.group(1) if logo else None,
            "rubric": (rubric.group(1), html.unescape(rubric.group(2))) if rubric else None,
            "topic": (topic.group(1), html.unescape(topic.group(2)), nova) if topic else None,
        }
    return out


CARDS = {}  # article URL → image, logo, rubric, topic (from the start pages)
RUBRICS = {}  # slug → name, from the cards


def items(lang):
    prefix = "/" + lang if lang else ""
    extra = cards(fetch(prefix + "/" if prefix else "/"))
    CARDS.update(extra)
    for e in extra.values():
        if e.get("rubric"):
            RUBRICS.setdefault(lang, {})[e["rubric"][0]] = e["rubric"][1]
    return [i for i in feed_items(prefix + "/feed.xml", lang) if not i["is_video"]]


def feed_items(path, lang):
    feed = ET.fromstring(fetch(path))
    extra = CARDS
    out = []
    for it in feed.iter("item"):
        if (it.findtext("category") or "") == "Supernova":
            continue
        src = it.find("source")
        name = src.text if src is not None else "?"
        slug = re.search(r"/(?:quelle|source)/([^/]+)/", src.get("url", "")) if src is not None else None
        link = it.findtext("link")
        title = re.sub(r"\s*\(" + re.escape(name) + r"\)$", "", it.findtext("title") or "")
        e = extra.get(link, {})
        out.append({
            "id": int(hashlib.sha1(link.encode()).hexdigest()[:12], 16),
            "url": link,
            "title": title,
            "excerpt": it.findtext("description") or None,
            "lang": lang or ("de" if re.search(r"[äöüß]| der | und | die ", title + " " + (it.findtext("description") or "")) else "en"),
            "published_at": iso(it.findtext("pubDate")),
            "is_video": "youtube.com" in link,
            "stars": 0,
            "source": {"slug": slug.group(1) if slug else "unknown", "name": name, "kind": None, "color": "#7C5CFF",
                       "logo": BASE + e["logo"] if e.get("logo") else None,
                       "support": support_for(slug.group(1) if slug else "unknown")},
            "image": {"small": BASE + e["img"] + "-480.webp", "large": BASE + e["img"] + "-960.webp", "width": 960, "height": 540, "fit": False}
            if e.get("img") else None,
            "categories": [{"slug": e["rubric"][0], "name": e["rubric"][1]}] if e.get("rubric") else [],
            "topic": {"key": e["topic"][0], "label": e["topic"][1], "supernova": e["topic"][2]} if e.get("topic") else None,
            "bundle": None,
        })
    return out


SUPPORT = {}  # slug → support object (--support), added to every item and the source head


def support_for(slug):
    return SUPPORT.get(slug)


LIST_PATHS = {  # API kind → website path per language ("" and "de" share the German paths)
    "rubric": {"": "/rubrik/{}/feed.xml", "de": "/de/rubrik/{}/feed.xml", "en": "/en/category/{}/feed.xml"},
    "topic": {"": "/thema/{}/feed.xml", "de": "/de/thema/{}/feed.xml", "en": "/en/topic/{}/feed.xml"},
    "source": {"": "/quelle/{}/feed.xml", "de": "/de/quelle/{}/feed.xml", "en": "/en/source/{}/feed.xml"},
}
LISTS = {}


def list_answer(kind, ref, lang):
    """rubric/topic/source from the website's own feeds, fetched once on first request."""
    if (kind, ref, lang) not in LISTS:
        try:
            rows = feed_items(LIST_PATHS[kind][lang].format(ref), lang)
        except Exception:
            return None
        head = {}
        if kind == "rubric":
            head = {"rubric": {"slug": ref, "name": RUBRICS.get(lang, {}).get(ref) or RUBRICS.get("", {}).get(ref, ref.title()), "hot_topics": []}}
        elif kind == "topic":
            label = next((i["topic"]["label"] for i in rows if i.get("topic") and i["topic"]["key"] == ref), ref)
            head = {"topic": {"key": ref, "label": label, "supernova": False}}
        else:
            name = rows[0]["source"]["name"] if rows else ref
            head = {"source": {"slug": ref, "name": name, "support": support_for(ref), "site_url": rows[0]["url"].split("/", 3)[0] + "//" + rows[0]["url"].split("/")[2] + "/" if rows else None}}
        LISTS[(kind, ref, lang)] = (head, rows)
    return LISTS[(kind, ref, lang)]


def supernovas(lang):
    prefix = "/" + lang if lang else ""
    feed = ET.fromstring(fetch(prefix + "/supernovas/feed.xml"))
    out = []
    for it in feed.iter("item"):
        guid = it.findtext("guid")
        m = re.search(r"supernova/([^/]+)/(\d+)$", guid or "")
        if not m:
            continue
        desc = it.findtext("description") or ""
        quote = re.search(r"<blockquote><p>[„“](.*?)[“”]</p><p>— (.*?)</p></blockquote>", desc, re.S)
        facts = re.search(r"<p><strong>(.*?)</strong></p>", desc)
        ignited = int(m.group(2))
        # the feed has title, link and source only; the API sends full items (no excerpt or image here)
        stamp = datetime.fromtimestamp(ignited, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        reports = [{"id": zlib.crc32(u.encode()), "url": html.unescape(u), "title": html.unescape(t), "lang": "en" if ", EN" in s or lang == "en" else "de",
                    "published_at": stamp, "source": {"slug": re.sub(r"\W+", "-", s.split(",")[0].lower()), "name": html.unescape(s.split(",")[0])}}
                   for u, t, s in re.findall(r'<li><a href="([^"]+)">(.*?)</a> \((.*?)\)</li>', desc)]
        out.append({
            "id": guid,
            "key": m.group(1),
            "label": (it.findtext("title") or "").replace("✦ Supernova: ", ""),
            "ignited_at": datetime.fromtimestamp(ignited, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "announced_at": iso(it.findtext("pubDate")),
            "url": it.findtext("link"),
            "summary": html.unescape(facts.group(1)) if facts else None,
            "quote": {"text": html.unescape(quote.group(1)), "source": html.unescape(quote.group(2))} if quote else None,
            "stats": {"items": len(reports), "sources": len(reports)},
            "burning": True,
            "reports": reports,
        })
    return out


def comets_of(home, novas):
    """Stand-in for the API's comets: topics of Latest with two or more posts that are no supernova."""
    seen = {n["key"] for n in novas}
    groups = {}
    for i in home:
        t = i.get("topic")
        if t and not t.get("supernova") and t["key"] not in seen:
            groups.setdefault(t["key"], (t, []))[1].append(i)
    out = []
    for key, (t, rows) in groups.items():
        if len(rows) < 2:
            continue
        at = sorted(r["published_at"] for r in rows)
        out.append({"key": key, "label": t["label"], "url": f"{BASE}/thema/{key}/", "first_at": at[0], "last_at": at[-1],
                    "stats": {"items": len(rows), "sources": len({r["source"]["slug"] for r in rows})}, "reports": rows[:3]})
    return out[:4]


def fake_replies():
    """Invented replies (Mastodon and Bluesky, one of them PlanetFOSS's own) for the report/block menu."""
    now = datetime.now(timezone.utc)
    def r(site, name, handle, text, minutes, depth=1, own=False):
        return {"site": site, "name": name, "handle": handle, "author_url": "", "text": text, "own": own, "depth": depth,
                "url": f"https://example.invalid/{site}/{handle.strip('@').replace('@', '_')}/{minutes}",
                "posted_at": (now - timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M:%SZ")}
    return [
        r("mastodon", "Anna Muster (Demo)", "@anna@example.invalid", "Invented reply for the report and block menu.", 50),
        r("mastodon", "PlanetFOSS", "@planetfoss@mastodon.social", "Invented answer of PlanetFOSS itself – no menu here.", 40, 2, True),
        r("bluesky", "Ben Beispiel (Demo)", "@ben.example.invalid", "A second invented reply from another service.", 20),
    ]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8099)
    ap.add_argument("--ads", action="store_true", help="serve invented sponsor cards at /api/app/v1/ads (standard and compact, two weights)")
    ap.add_argument("--replies", action="store_true", help="add invented fediverse replies to every topic head")
    ap.add_argument("--support", action="append", default=[], metavar="SLUG=URL[|PROJECT]")
    args = ap.parse_args()
    for spec in args.support:
        slug, _, rest = spec.partition("=")
        url, _, project = rest.partition("|")
        SUPPORT[slug] = {"url": url, "mode": "forward" if project else "self", "name": project or None}
    data = {}
    for lang in ("", "de", "en"):
        data[("home", lang)] = items(lang)
        data[("supernovas", lang)] = supernovas(lang)
        data[("comets", lang)] = comets_of(data[("home", lang)], data[("supernovas", lang)])
        print(f"lang={lang or 'all'}: {len(data[('home', lang)])} items, {len(data[('supernovas', lang)])} supernovas, {len(data[('comets', lang)])} comets")

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            # counting endpoints (app-open, ad-view, app-ping, app-view): print what the app sends, answer ok, keep nothing
            body = self.rfile.read(int(self.headers.get("Content-Length") or 0)).decode("utf-8", "replace")
            if self.path in ("/api/app-open", "/api/ad-view", "/api/app-ping", "/api/app-view"):
                print(f"POST {self.path} [{self.headers.get('User-Agent', '')}] {body}", flush=True)
                return self.answer(200, {"ok": True})
            return self.answer(404, {"error": "not_found"})

        def do_GET(self):
            url = urlparse(self.path)
            # reach headers (AppVisibility): shown so the counting can be checked by hand
            marks = [h for h in ("X-PlanetFOSS-Start", "X-PlanetFOSS-Background", "X-PlanetFOSS-Part") if self.headers.get(h)]
            if marks:
                print(f"GET {self.path} {' '.join(marks)}", flush=True)
            q = {k: v[0] for k, v in parse_qs(url.query, keep_blank_values=True).items()}
            name = url.path.removeprefix("/api/app/v1/")
            lang = q.get("lang", "")
            page = max(1, int(q.get("page", "1") or 1))
            if url.path == "/_inject":
                # test hook: a new supernova announced now, in every language
                key = re.sub(r"[^a-z0-9-]", "", q.get("key", "test-nova")) or "test-nova"
                now = datetime.now(timezone.utc)
                nova = {"id": f"tag:planetfoss.net,2026:supernova/{key}/{int(now.timestamp())}", "key": key, "label": q.get("label", key),
                        "ignited_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"), "announced_at": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
                        "url": f"{BASE}/thema/{key}/", "summary": "Test", "quote": None,
                        "stats": {"items": 4, "sources": 4}, "burning": True, "reports": []}
                for l in ("", "de", "en"):
                    data[("supernovas", l)].insert(0, nova)
                return self.answer(200, {"injected": key})
            if url.path == "/_inject_item":
                # test hook: a post with any URL on top of Latest (e.g. a badssl.com page for TLS tests)
                link = q.get("url", "")
                if not link.startswith(("https://", "http://")):
                    return self.answer(400, {"error": "bad_request"})
                now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
                item = {"id": int(hashlib.sha1(link.encode()).hexdigest()[:12], 16), "url": link, "title": q.get("title", link),
                        "excerpt": "Test", "lang": "en", "published_at": now, "is_video": False, "stars": 0,
                        "source": {"slug": "test", "name": "Test", "color": "#7C5CFF"}, "image": None, "categories": [], "topic": None, "bundle": None}
                for l in ("", "de", "en"):
                    data[("home", l)].insert(0, item)
                return self.answer(200, {"injected": link})
            m = re.fullmatch(r"(rubric|topic|source)/([a-z0-9-]{1,80})", name)
            if m:
                found = list_answer(m.group(1), m.group(2), lang)
                if found is None:
                    return self.answer(404, {"error": "not_found"})
                head, rows = found
                if args.replies and m.group(1) == "topic":
                    head = {"topic": {**head["topic"], "replies": fake_replies()}}
                return self.answer(200, {**head, "items": rows[(page - 1) * PER_PAGE: page * PER_PAGE], "has_more": len(rows) > page * PER_PAGE})
            if name.startswith("logbook"):
                # a minimal entry, so Latest does not fail on the missing endpoint
                return self.answer(200, {"day": "2026-11-05", "label": "5. November", "url": f"{BASE}/logbuch/2026-11-05/", "headline": "Test"})
            if name == "front":
                # no front page here: Latest is the plain list
                return self.answer(200, {})
            if name == "ads":
                if not args.ads:
                    return self.answer(200, {"ttl": 300, "slots": {"app_feed": [], "app_list": []}})
                ui = q.get("ui", "de")
                def card(i, slot, fmt, weight, title, body, **kw):
                    return {"id": i, "slot": slot, "lang": ui, "after": 4 if slot == "app_feed" else 8, "format": fmt, "weight": weight, "topics": [],
                            "kind": "sponsor", "label": "Anzeige" if ui == "de" else "Ad", "by": "Musterhost (Demo)", "logo": None, "color": "#F26B1D",
                            "title": title, "body": body, "cta": "Zum Angebot" if ui == "de" else "Learn more", "note": None, "image": None,
                            "click_url": f"https://example.org/a/{i}?s={slot}&l={ui}", **kw}
                return self.answer(200, {"ttl": 60, "slots": {
                    "app_feed": [card(901, "app_feed", "standard", 5, "Managed Linux-Hosting aus Deutschland", "NVMe, tägliche Backups, Support per Mail."),
                                 card(902, "app_feed", "standard", 5, "Server für dein Homelab", "Ab 4 € im Monat, monatlich kündbar.")],
                    "app_list": [card(903, "app_list", "compact", 5, "Musterhost: Server ab 4 €", "Linux-Hosting aus Deutschland")]}})
            if name == "rubrics":
                names = RUBRICS.get("en" if q.get("ui") == "en" else "", {}) or RUBRICS.get("", {})
                return self.answer(200, {"rubrics": [{"slug": k, "name": v, "hot_topics": []} for k, v in sorted(names.items(), key=lambda kv: kv[1])]})
            if name == "videos":
                return self.answer(200, {"items": [], "has_more": False})
            if name == "starred":
                # test data only: the first posts of Latest with made-up star counts, most first (the live API counts readers' stars)
                rows = [dict(i, stars=n) for i, n in zip(data[("home", lang)], (12, 9, 7, 5, 4, 3, 2, 1))]
                return self.answer(200, {"items": rows, "has_more": False, "ad": None})
            if name == "search":
                needle = q.get("q", "").strip().lower()
                if len(needle) < 2:
                    return self.answer(400, {"error": "bad_request"})
                pool = data[("home", lang)] + [i for (_, _, l), (_, rows) in LISTS.items() if l == lang for i in rows]
                hits = list({i["id"]: i for i in pool if needle in (i["title"] + " " + (i["excerpt"] or "")).lower()}.values())
                topics = list({i["topic"]["key"]: i["topic"] for i in hits if i.get("topic")}.values())[:4]
                return self.answer(200, {"items": hits[:60], "topics": topics})
            if (name, lang) not in data:
                return self.answer(404, {"error": "not_found"})
            if name == "home":
                rows = data[(name, lang)]
                body = {"items": rows[(page - 1) * PER_PAGE: page * PER_PAGE], "has_more": len(rows) > page * PER_PAGE}
            else:
                body = {"supernovas": data[(name, lang)], "comets": data[("comets", lang)]}
            self.answer(200, body)

        def answer(self, code, body):
            raw = json.dumps(body, ensure_ascii=False).encode()
            etag = '"' + hashlib.sha1(raw).hexdigest()[:20] + '"'
            if code == 200 and self.headers.get("If-None-Match") == etag:
                self.send_response(304)
                self.send_header("ETag", etag)
                self.end_headers()
                return
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "public, max-age=60")
            self.send_header("ETag", etag)
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

    print(f"serving on :{args.port}")
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
