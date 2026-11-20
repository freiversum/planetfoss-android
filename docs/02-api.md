# PlanetFOSS App API v1 (specification)

Status: 2026-10-30 – all endpoints below are **live on planetfoss.net** (PlanetFOSS `dfcef38`). To be implemented in `web/planetfoss.net` as a thin layer
over the existing `Site`/`NovaFeed` functions, so the app sees exactly what the
website shows (same bundling, same translation filter, same image rules).

## Principles

- **Read-only, GET only.** No accounts, no cookies, no session, no ids.
- **No counting.** App requests are not page views, not clicks (`Clicks`), not ad
  views (`Ads::countView`). The API never writes to the database.
- **Same rules as the website.** Every list goes through `Site::query`/`Site::cards`
  (bundles, `withoutTranslations`, `image_mode` of the source: `motiv`/`keins` →
  no image). Every place that lists or counts supernova reports uses
  `Site::translationIds` (see the rule in the project memory).
- **Cacheable.** `Cache-Control: public, max-age=300` (supernovas: 120), strong
  `ETag` over the body, `304` on `If-None-Match`. Responses go through the existing
  file cache where possible – the webspace budget is the bottleneck.
- **Versioned path.** `/api/app/v1/…`; breaking changes → `v2`, v1 stays at least
  until the app version using it is no longer offered.
- `robots.txt` already disallows `/api/`.

## Common parameters

| Name | Values | Meaning |
|---|---|---|
| `lang` | `de`, `en`, empty | content language; empty = “Alle” as on `/` |
| `ui` | `de`, `en` | language of labels (rubric names, supernova text); default `de` |
| `page` | 1… | page of 30 items |

Times are ISO 8601 UTC (`2026-10-29T16:27:14Z`). Image URLs are absolute and always
point to planetfoss.net (`/img/…-480.webp`, `-960.webp`).

## Objects

### Item

```json
{
  "id": 24970,
  "url": "https://linuxiac.com/tor-browser-16-0-released…/",
  "title": "Tor Browser 16.0 Released with Linux ARM64 Support",
  "excerpt": "Tor Browser 16.0 adds native Linux ARM64 support…",
  "lang": "en",
  "published_at": "2026-10-29T16:27:14Z",
  "kind": "meldung",
  "is_video": false,
  "stars": 3,
  "source": { "slug": "linuxiac", "name": "Linuxiac", "lang": "en", "kind": "magazin",
              "color": "#1f6feb", "logo": "https://planetfoss.net/img/logos/linuxiac-ab12.webp",
              "support": null, "open_external": false },
  "image": { "small": "https://planetfoss.net/img/2026/10/<sha1>-480.webp",
             "large": "https://planetfoss.net/img/2026/10/<sha1>-960.webp",
             "width": 960, "height": 540, "fit": false },
  "categories": [ { "slug": "sicherheit", "name": "Sicherheit" } ],
  "topic": { "key": "tor-browser-16-0", "label": "Tor Browser 16.0", "supernova": false },
  "bundle": { "id": 149, "also": [ { "source": "9to5Linux", "url": "…", "title": "…", "lang": "en" } ] }
}
```

`source.support` is `null` or `{ "url": "…", "mode": "self" | "forward", "name": null | "…" }`: `self` = the
source welcomes a subscription or donation at `url`; `forward` = the source needs no support and recommends the
project `name` instead, `url` is that project's page.

`title` is the clean title (the RSS feed appends “(Source)”). `image`, `topic`,
`bundle` may be `null`. `kind` is the topic item kind where known
(`offiziell`, `meldung`, `test`, `anleitung`, `video`), else `null`.

### Supernova

```json
{
  "id": "tag:planetfoss.net,2026:supernova/libreoffice-26-8/1793287928",
  "key": "libreoffice-26-8",
  "label": "LibreOffice 26.8",
  "ignited_at": "2026-10-29T15:32:08Z",
  "announced_at": "2026-10-29T16:02:08Z",
  "url": "https://planetfoss.net/thema/libreoffice-26-8/",
  "summary": "4 Quellen berichten darüber.",
  "quote": { "text": "Writer, Calc and Impress receive fixes…", "source": "FOSS Force" },
  "stats": { "items": 4, "sources": 4 },
  "burning": true,
  "announced": true,
  "reports": [ Item, … ]
}
```

`id` is **byte-identical to the RSS guid** of `NovaFeed` – one identity for feed
readers, Mastodon and the app. `announced_at` = ignition + 30 min (`Mastodon::DELAY_SECONDS`);
before that a supernova is not listed (misfires can still be hidden) – unless the app asks with
`pending=1` (2026-11-20): then it is listed from its ignition on, as on the website, with
`announced: false` until `announced_at`. The app shows it at once but notifies only once
`announced` is true (`NovaDetector`); an app without the parameter never sees it early, so it
cannot notify a misfire. Without `pending=1` the field is always `true`.
`burning` = topic currently has status `supernova` (else afterglow, still within
the 7 days).
`reports` = the first five reports (as in the feed) as full items, so the app shows
excerpt, image, time and source logo like in the lists; a report whose source is no
longer listed is left out. Reports in `lang` come first. Unlike the language feeds
(two reports in that language), the app list keeps a supernova whatever language its
reports are in; the app notes "only English sources" itself (2026-11-10,
`docs/09-latest-tab.md` D-L2).

Next to the supernovas the answer carries `comets` – up to four rising topics
(status `komet`), newest first:

```json
{ "key": "tor-16", "label": "Tor 16", "url": "https://planetfoss.net/thema/tor-16/",
  "first_at": "2026-10-29T16:27:14Z", "last_at": "2026-10-30T06:30:00Z",
  "stats": { "items": 3, "sources": 3 }, "reports": [ Item, … ] }
```

Counts and the three `reports` cover the last 24 hours in the chosen language. Topics
already listed as a supernova are left out, and so are posts of those supernovas (a
broad topic like “Ubuntu” next to the supernova “Ubuntu Core”); a comet with nothing
left is dropped. The app puts one comet after each supernova, never before the first.

## Endpoints

| Endpoint | Returns | Built on |
|---|---|---|
| `GET /api/app/v1/home?lang=&ui=&page=` | `{ items: [Item], has_more, ad, weekly: {week, items, sources, supernovas, story, image} \| null }` (weekly: page 1, Monday to Wednesday only) | `Site::listing(…, 'home')` / `latestPage` |
| `GET /api/app/v1/supernovas?lang=&ui=&pending=` | `{ supernovas: [Supernova], comets: [Comet] }`, 7 days, newest first; `pending=1` adds the ones not yet announced; comets of the last 24 h | `NovaFeed::entries` + `Site::supernovas` for `burning` |
| `GET /api/app/v1/rubrics?ui=` | `{ rubrics: [{ slug, name, hot_topics: [{key,label,supernova}] }] }` | `categories` (website order) + burning supernovas/comets per rubric |
| `GET /api/app/v1/rubric/<slug>?lang=&ui=&page=` | `{ rubric, items, has_more }` | `Site::listing('rubrik')` |
| `GET /api/app/v1/topic/<key>?lang=&ui=&page=` | `{ topic: {key,label,supernova, status, intro, stats {items, sources, first}, rubric, parent, related, story?}, items, has_more }` | `Site::listing('thema')`; head as the topic page (2026-10-31). `story` (2026-11-03): on page 1 of a supernova with its own story, the ids of that story's posts, listed first; `stats` then count only the story |
| `GET /api/app/v1/source/<slug>?lang=&ui=&page=` | `{ source (+ site_url, lang), items, has_more }` | `Site::listing('quelle')`, videos included |
| `GET /api/app/v1/videos?lang=&page=` | `{ items, has_more }` | `Site::listing('videos')` |
| `GET /api/app/v1/bundle/<id>?lang=&ui=` | `{ bundle: {id, title, sources, topic, langs: {de, en}}, items, has_more: false }` | `Site::bundle()` (website /buendel/<id>/), oldest report first, 2026-10-31; `langs` = sources per language over all reports (2026-11-01) |
| `GET /api/app/v1/weekly?lang=&ui=`, `weekly/<YYYY-WW>` | `{ week, label, from, to, lede, numbers: {items, sources, de, en, videos, prev_items, trend, supernovas}, supernovas: [{key, label, items, sources, card, cover, image, quote: {text, by}}], stories: [{id, name, title, url, sources, langs, lead: Item, supernova: {key, label}, reports: [{source, logo, color, title, url, lang}]}], releases: [{key, label, entity, version, sources}], rubrics: [{slug, name, items, item: Item}], days: [{day, weekday, items}], sections: [{slug, name, items}], top_sources: [{slug, name, logo, color, items}], texts: {days, sections, sources}, topics: [{key, label, items, comet}], videos: [Item], starred: [Item], archive: [YYYY-WW] }` | `Weekly::data()` (website /rueckblick/), last finished ISO week without a week; 404 for the running or an invalid week; counts over both languages, articles in the chosen language; `stories[0]` is the cover story, `cover`/`card` say where a supernova is shown (no duplicates); `lede` and `texts` in the `ui` language; 1 h cache (2026-11-01, magazine fields 2026-11-02) |
| `GET /api/app/v1/meta` | `{ donate: {url, name} \| null }` | backend settings for the app: PlanetFOSS's donation link (Admin → Einstellungen), null while off; 5 min cache, cleared when saved (2026-11-01) |
| `GET /api/app/v1/ads?ui=` | `{ ttl, slots: { app_feed: [Ad], app_list: [Ad] } }` – ad candidates, see “Sponsor cards” | `Ads::running`, 2026-11-06 |
| `GET /api/app/v1/front?lang=&ui=` | `{ lead, side: [items], rubrics: [{rubric, items}], projects: [items], videos: [items] }` | `Site::home()` (website start page) without supernovas, stars and ads, 2026-10-31 |
| `GET /api/app/v1/topics?lang=&ui=` | `{ orbit: [{key, label, supernova, status}], topics: [{key, label, supernova, posts_30d}] }` | `Site::trend()` (website trend bar) and `Site::evergreens()` (/themen/), 2026-10-31 |
| `GET /api/app/v1/sources?lang=&ui=` | `{ groups: [{key, name, sources: [source + lang, week]}] }` | /quellen/ grouped by `Pages::GROUPS`, sorted by name, 2026-10-31 |
| `GET /api/app/v1/starred?lang=&ui=&page=` | `{ items, has_more, ad: null }` | `Site::listing('sterne')`: items of the last 7 days with stars, most stars first; never an ad (2026-10-30) |
| `GET /api/app/v1/search?q=&lang=&ui=` | `{ items: [Item], topics: [{key,label}] }` | `Site::search`, `Site::searchTopics` |

Errors: JSON `{ "error": "not_found" | "bad_request" | "unavailable" }` with 404/400/503. Keys must match `[a-z0-9-]{1,80}`; `search` needs `q` with ≥ 2 characters (cut at 80). A merged topic answers with its target; hidden topics and unknown keys answer 404 (never cached).
During maintenance or migrations 503 with `Retry-After`.

## Size and load

- One `home` page ≈ 30 items ≈ 25–35 KB JSON (gzip ~8 KB).
- The background check calls `supernovas` only, with `If-None-Match`: usually a
  `304` of a few hundred bytes. At 15-minute intervals that is ≤ 96 requests per
  device per day, most of them 304 from the file cache.

## Not part of v1

Stars (proof of work in the browser), ads/sponsors, statistics, admin.

## Sponsor cards (2026-10-30)

Page 1 of `home` and of the lists (`rubric`, `topic`, `source`, `videos`) may carry `ad`:
PlanetFOSS's own sponsor card or affiliate tile, picked like on the website, and only while
ads are switched on in its admin (else `null`). No ad network, no third-party script.

```json
{ "id": 9, "slot": "feed", "lang": "de", "after": 4, "kind": "sponsor", "label": "Anzeige",
  "by": "Pinguin-Rechner GmbH", "logo": "https://…/img/ads/sponsor-8.webp", "color": "#F26B1D",
  "title": "…", "body": "…", "cta": "Zum Angebot", "note": null,
  "image": { "small": "…-480.webp", "large": "…-960.webp" },
  "click_url": "https://planetfoss.net/a/9?s=feed&l=de" }
```

**Placements (2026-11-06):** slots are `app_feed` (Latest, standard card) and `app_list` (lists, compact card); `format` says which.
`GET /api/app/v1/ads?ui=` returns up to five candidates per slot (`weight` 1–10, `topics`: shown only on these topic lists, empty = all),
outside the list answers (TTL 300 s, empty lists while ads are off). The app rolls weighted dice each time a screen opens and never shows the
same card twice on one screen; it sends `X-PlanetFOSS-Part: 1` with this request. The `ad` field of the list answers stays for app versions
before the change (picked from the same app slots). No affiliate tiles in the app.

The app places the card after `after` posts (feed 4, lists 8) and keeps it in memory only
(not in Room, never offline). A tap opens `click_url` in the user's browser; planetfoss.net
counts the click and forwards. When half of the card is on screen – and only with *Count for the sources* on – the app posts
`{"views":[{"id","slot","lang"}]}` to `/api/ad-view` once – no identifier, nothing else.

## Opened articles (2026-10-30)

Only if the user switched on *Count for the sources* (opt-in, off by default): when the user opens an article (any browser mode) the app posts
`{"u": "<article url>", "place": "liste|supernova|buendel", "lang": "|de|en"}` to
`/api/app-open` with its own user agent. planetfoss.net adds it to `stat_click_day` as a
daily total with page `app`: part of every click sum, shown apart as “from the app”. No
identifier, no time of day; failures are dropped.

## Reach numbers (2026-11-02)

planetfoss.net counts the list requests the app makes anyway, like page views in the website's access log: daily
totals per list (page 1 only), ref, content and app language and version from the user agent – no IP address, nothing
stored on the phone, no opt-in needed. Two request headers (only on `/api/app/v1/`, set by `AppVisibility`):

- `X-PlanetFOSS-Background: 1` while no activity is visible, and always on the supernova and follow checks
  (`Use.BACKGROUND`; after an update they run while the app is open): not counted, never a start;
- `X-PlanetFOSS-Start: 1` on the first list request after the app came to the front (start, or after 30 minutes away):
  counted as a start, an approximation of visits like the website's entries.
- `X-PlanetFOSS-Part: 1` on requests that only fill part of a screen – Latest's Sternstunden, orbit (`topics`) and
  supernova band – like a web page's images: not a view. Latest itself is counted through `front`, which it requests
  every time it opens; `home` (only fetched when the cache is stale) and further pages are not counted. Also on the
  second and further followed lists when Radar opens, and on the second and further queries of one visit to the search
  (it searches while typing).
- `X-PlanetFOSS-Screen: radar` on one request per opening of Radar (`Use.RADAR`): planetfoss.net counts it as one view
  "Radar" instead of a view of that topic or source. Before 0.6.4 every followed list counted as a view of its own.

The start is counted on whichever request carries it, also the one for the sponsor card candidates (`ads`). Builds
whose user agent has a suffix after the version (`-dev+…`, `-debug`, `-qa`) are not counted at all: they are not users.

Requests the HTTP cache answers without the network are not seen, so the numbers are minimums. Opened articles
still need *Count for the sources*; seen sponsor cards are always reported (extra reports from the phone).

