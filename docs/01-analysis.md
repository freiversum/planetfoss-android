# Phase 1 – Analysis of PlanetFOSS as a data source

Status: 2026-10-29. Sources: the PlanetFOSS code (`web/planetfoss.net`, master
`e5abe8b`) and the live feeds on planetfoss.net, fetched the same day.

## 1. What PlanetFOSS is

PlanetFOSS is an aggregator, not a publisher. It collects posts from ~hundreds of
Linux/FOSS websites, blogs and YouTube channels, groups them into **topics**
(`/thema/<key>/`), **bundles** (same story from several sources) and **rubrics**
(`/rubrik/<slug>/`), and links every card to the original article. There is no
article text of its own except the source's teaser.

Consequences for the app:

- **No native full-text view.** The article bodies belong to the sources. The app
  shows title, teaser, source, time and image, and opens the original in the
  chosen browser. This also avoids showing the same content twice.
- **Offline reading** of article bodies is out of scope for the same reason.
  Saved articles keep their metadata offline (list works without network).

## 2. Available data sources

| Endpoint | Content | Usable for the app |
|---|---|---|
| `/feed.xml`, `/en/feed.xml` | RSS 2.0, ~40 newest items, plus supernova entries | yes, but thin (see below) |
| `/supernovas/feed.xml`, `/en/supernovas/feed.xml` | RSS, only supernovas of the last 7 days | **yes – the documented supernova source** |
| `/rubrik/<slug>/feed.xml`, `/thema/<key>/feed.xml`, `/quelle/<slug>/feed.xml`, `/quellen/<group>/feed.xml`, `/sternstunden/feed.xml` | RSS per rubric/topic/source/group/stars | yes, per filter |
| `/suche/?q=` | HTML only | only by scraping – not wanted |
| `/api/stars`, `/api/star`, `/api/out`, `/api/ad-view`, … | internal endpoints of the website (stars with proof of work, click and ad counting) | **no**; `/api/out` and `/api/ad-view` are statistics and must never be called by the app |
| `/status.json`, `/sitemap.xml` | operational status, sitemap | no |

There is **no public JSON API**. `robots.txt` disallows `/api/`.

### What a regular feed item contains

```
title        "Tor Browser 16.0 Released … (Linuxiac)"   – source name appended
link / guid  original article URL (isPermaLink=true)
pubDate      RFC 822
source       name + URL of the per-source feed
description  plain-text teaser, cut at ~200 chars
```

Missing in the feed, although PlanetFOSS has it in its database: article image
(`items.image_file`, stored as own WebP copy in 480/960 px), rubric, topic,
bundle, item kind (official/report/test/howto/video), language, source logo,
star count. Only 1–2 of ~40 items carry `media:content` (the supernova entries).

### What a supernova entry contains

```
title        "✦ Supernova: LibreOffice 26.8"
link         https://planetfoss.net/thema/libreoffice-26-8/
guid         tag:planetfoss.net,2026:supernova/libreoffice-26-8/1793287928
                                           topic_key ─┘   ignition unix time ─┘
category     Supernova
pubDate      ignition + 30 min
media:content  1200×675 JPEG composed by PlanetFOSS (img/nova/…)
description  HTML: image, "N Quellen berichten darüber", quote, up to 5 report links
```

## 3. How a supernova is defined (from the code, not invented)

`src/Topics.php`, `src/NovaFeed.php`, KONZEPT.md §“Supernova”:

- A topic ignites when **≥ 4 different sources** report on it within 24 h
  (versioned topics like “GNOME 51” and plain names alike, `NEED_VERSIONED`,
  `NEED_PLAIN`). At most **3 supernovas** burn at once; the third needs ≥ 4
  sources in 12 h and half the score of the leader.
- Minimum burn time 12 h, then afterglow; a topic can **re-ignite** (≥ 2 new
  sources after cooling, ≤ 6 h old).
- Admins can ignite, hide or rename by hand; a misfire can be hidden within the
  first 30 minutes.
- `NovaFeed` publishes an entry **30 min after ignition** (same delay as
  Mastodon), keeps it **7 days**, and **freezes** its content so readers don't
  see changed entries. Re-ignition within 7 days keeps the original timestamp.
- The language feeds only list supernovas with ≥ 2 reports in that language;
  `/supernovas/feed.xml` without language prefix is the mixed (German-text) feed.

### Reliable detection in the app

- Identity = `topic_key` + ignition time, both parsed from the `guid`. A re-ignition
  within 7 days keeps the guid → no second notification. A new ignition after the
  7-day window gets a new guid → counts as a new supernova (matches the website).
- Store every seen guid locally (Room) with first-seen time; notify only for guids
  not seen before **and** with `pubDate` not older than a cut-off (e.g. 48 h), so a
  fresh install or a long offline phase doesn't flood the user with week-old
  entries. Idempotent by construction (insert-if-absent, notify from the inserted
  set only).
- An entry that disappears (hidden misfire) is simply no longer shown; no retraction
  notification.

## 4. Gap: the feeds are too thin for a good native list

With RSS only, the app can show: title, teaser, source, time, link, and supernovas
with image. It can **not** show article images, rubrics, topics/bundles or offer
search and filters by rubric/kind without scraping HTML.

Two ways forward (decision needed, see §8):

**A – RSS only.** Works today, zero server work. Lists without images (or images
fetched from the original sites – rejected: extra third-party connections and a
privacy leak), rubric browsing via the per-rubric feeds, search only locally over
what was synced.

**B – small read-only JSON API on planetfoss.net** (recommended). PlanetFOSS is our
own server, so a documented interface is cleaner than any workaround:

```
GET /api/app/v1/items?lang=de&rubric=&topic=&source=&before=<id>&limit=30
GET /api/app/v1/supernovas?lang=de          (same rules and ids as NovaFeed)
GET /api/app/v1/rubrics?lang=de
GET /api/app/v1/topic/<key>?lang=de          (topic page: reports, bundle)
GET /api/app/v1/search?q=…&lang=de           (same as /suche/)
```

Images come from PlanetFOSS's own WebP copies (`/img/…-480.webp`), so the app
only ever talks to planetfoss.net for content. Responses cacheable (ETag), no
cookies, no ids, no logging beyond the normal web server log. The RSS feeds stay
the fallback and the reference for supernova identity.

## 5. Push without Google

PlanetFOSS has no push infrastructure. The homelab server (bender) and its ntfy
must play **no role** for the app (decision 2026-10-29) – everything the app talks
to is planetfoss.net on the webspace.

- **Chosen: WorkManager periodic job** (15 min minimum, Android may defer it in
  Doze/standby; realistic delay 15 min – a few hours). One conditional GET of
  `/supernovas/feed.xml` (or the API) per run, a few KB. No foreground service.
  The UI says “checked periodically”, not “instant”.
- **Not planned: server push.** UnifiedPush would need a push server (own ntfy =
  bender, excluded; public ntfy.sh = additional third party). Can be revisited if
  PlanetFOSS ever gets its own push endpoint on the webspace.

## 6. Images, logos, trademarks

- Article images are copies of the sources' og:images. Showing them in the app is
  the same use as on the website, but the rights stay with the sources – no
  bundling of such images in the repo, screenshots for F-Droid only with own or
  cleared content.
- PlanetFOSS logo/wordmark (`public/assets/brand/`) are Christoph's; for the app
  icon a separate licence statement is needed (GPL code ≠ trademark). Decide:
  CC BY-SA for the icon, or “all rights reserved, trademark” with a note in
  `LICENSES.md` (F-Droid accepts non-free trademarks as long as the code is free;
  forks must rename).

## 7. Toolchain on this machine

Android SDK in `~/Android/Sdk` (platforms 36, 37; build-tools 36.0.0, 37.0.0),
OpenJDK 27. The sister project Hausmusik uses AGP 9.4.1, Kotlin 2.3.21, Compose
BOM 2026.09.00, Hilt, Navigation Compose – same stack, same versions as starting
point, verified against current releases in phase 3.

Proposed: `minSdk 26` (Android 8.0: adaptive icons, notification channels,
`java.time`, WebView `safeBrowsing` off switch; covers ~97 % of active devices),
`targetSdk`/`compileSdk 37`.

## 8. Open decisions

1. Data source: **B** (JSON API on planetfoss.net, RSS as fallback) or **A** (RSS only).
2. Application ID: `net.planetfoss.app` (domain is ours) – name “PlanetFOSS”.
3. Repository: `gh:freiversum/planetfoss-android`, public, GPL-3.0-or-later.
4. ~~Push~~ – decided: WorkManager only, no bender, no ntfy.
5. App icon licence (see §6).
6. F-Droid submission: done by Christoph himself (F-Droid's stance on AI-written
   merge requests is to be checked before submitting, as with Flathub).
