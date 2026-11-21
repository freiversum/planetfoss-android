# Idea: save to a bookmark service

Status: analysis only (2026-11-21), nothing built. Tracked in `BACKLOG.md`.

## Goal

Tapping the bookmark icon on an article saves it in the app (as today) **and**
creates the entry in the reader's own bookmark service, for example a
self-hosted Karakeep. One tap, no share sheet, no second app.

## What the app has today

- Saved articles live only on the device (`ui/saved`), with JSON export/import.
- Sharing goes through the Android share sheet (`ACTION_SEND`,
  `ui/components/ItemActions.kt`). This already reaches every service that has
  an Android app, but it takes several taps and leaves the app.

## Candidates

Only free, open-source services that can be self-hosted. Pocket (shut down
July 2025) and Omnivore (shut down November 2024) are gone; Raindrop.io and
Pinboard are not free software and are left out.

| Service | Kind | Create call | Auth | Effort | Notes |
|---|---|---|---|---|---|
| **Karakeep** (ex Hoarder) | bookmarks + archive, AI tags | `POST /api/v1/bookmarks` `{type:"link", url, title}`; tags via `POST /api/v1/bookmarks/{id}/tags` | API key (Settings > API Keys), Bearer | low | Also Karakeep Cloud. Official Android app. The user's example. |
| **linkding** | lean bookmarks | `POST /api/bookmarks/` `{url, title, tag_names[]}` | API token (Settings > Integrations), `Authorization: Token …` | low | Single container, very common in homelabs. Tags in the same call. |
| **Linkwarden** | bookmarks + archive (screenshot, PDF) | `POST /api/v1/links` `{url, name, tags[{name}], collection}` | access token (Settings > Access Tokens), Bearer | low | Collections; token lifetime chosen by the user. |
| **Readeck** | read-later, single Go binary | `POST /api/bookmarks` `{url, title, labels[]}` | API token (profile), Bearer | low | Returns `202` + `Location`; content is fetched in the background. Labels = tags. |
| **wallabag** | read-later (oldest, 2013) | `POST /api/entries.json` `{url, title, tags}` | OAuth2: client id + secret **and** username/password for a token, refresh token | medium | More setup for the reader (create an API client first). Hosted option wallabag.it. Big Pocket refugee base. |
| **Nextcloud Bookmarks** | bookmarks inside Nextcloud | `POST /index.php/apps/bookmarks/public/rest/v2/bookmark` `{url, title, tags[]}` | app password via Login Flow v2 (browser), Basic auth | medium | Fits readers who already run Nextcloud; Login Flow avoids typing a password. |
| **LinkAce** | bookmarks, link checks | `POST /api/v2/links` `{url, title, tags}` | API token, Bearer | low | Smaller user base. |
| **Shaarli** | minimalist, no database | `POST /api/v1/links` `{url, title, tags[]}` | JWT (HS512) signed with the instance API secret, short `iat` | medium | Token must be signed per request in the app. |
| **Shiori** | lean bookmarks + archive | login → session token, then `POST /api/bookmarks` | username/password → session | medium | API v1 changing; stores the password or re-logs in. Low priority. |

All endpoints above are as documented by the projects or used by the
[Miniflux integrations](https://miniflux.app/docs/karakeep.html), which call
most of these services the same way. Verify against the version in use before
building.

## Recommendation

1. **Phase 1, one generic "Bearer token + URL" client** covering Karakeep,
   linkding, Linkwarden and Readeck. They differ only in path, header prefix and
   field names, so one small adapter table is enough. This covers the services
   most homelab readers run in 2026.
2. **Phase 2:** wallabag (OAuth2 flow) and Nextcloud Bookmarks (Login Flow v2),
   both with more reach than effort suggests.
3. **Later, if asked for:** LinkAce, Shaarli, Shiori.
4. **Always available:** the share sheet stays as the fallback for anything else.

## Design sketch

- **Settings > Saving > "Also save to"**: pick a service, enter the server
  address and token, button "Test connection" (one harmless read call, e.g.
  list one bookmark). One service at a time.
- **On save:** the app saves locally as today, then sends the create call in a
  `WorkManager` job (retries when offline, no blocking UI). A small mark on the
  saved item shows "sent" / "failed, tap to retry".
- **Which URL:** the original article URL (the source), not the planetfoss.net
  page, so the bookmark service archives the real article. Title from the item.
- **Tags/labels:** optional, default `planetfoss`; plus the article's topic if
  the reader enables it.
- **Unsave:** does not delete anything in the service (one-way; the reader
  owns that data). Possibly an option later.
- **No server of ours in between.** The app talks directly to the reader's
  server; planetfoss.net never sees tokens or bookmarks. Fits the "count only
  what the website counts" rule.

## Security and privacy

- Token stored encrypted with an Android Keystore key (DataStore + Tink or
  similar); never logged, excluded from the JSON export and from backups.
- HTTPS required; plain HTTP only for LAN addresses after an explicit warning
  (many homelab instances run on `http://192.168…`). Self-signed certificates:
  no custom trust store in phase 1; document "use a proper certificate".
- Network security config must allow the reader's host; the app has no fixed
  host list for this feature.
- F-Droid: plain OkHttp/Ktor calls, no SDKs, no proprietary dependencies.

## Open questions

- Is one service at a time enough, or do some readers want two (e.g. Karakeep
  for bookmarks, wallabag for reading)?
- Should "save" on the website side (planetfoss.net) get the same option?
  Probably not: it would need server-side tokens.
- Send the planetfoss.net discussion/summary as a note (Karakeep, linkding and
  Linkwarden have description fields)?
