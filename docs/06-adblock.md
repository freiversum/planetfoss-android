# Phase 6 – Tracker protection

Status: 2026-11-10. Optional tracker protection for the **internal browser only**.

## Fair to the sources

PlanetFOSS lives on the articles of its sources, and they live on their ads. So the app
blocks **trackers of other sites** only: EasyPrivacy, applied to **third-party** requests. The
sources' ads and their own visitor statistics stay, and readers from the app show up there:
the internal browser sends `Referer: https://planetfoss.net/` with the first page, the Custom
Tab passes `android-app://<package>` as `EXTRA_REFERRER`.

## What it is and is not

- Off by default. Settings → *Trackers*: switch, status of the list (rules, last update,
  last error), *Update the list now*, sites without blocking (add/remove), a plain-language
  list of the limits and the list's origin and licence.
- Browser menu (while the protection is on): “N requests blocked” for the open page, and
  “Don't block on this site” / “Block again on this site”, which reloads the page.
- It covers the app's own WebView. It is **not** a system-wide blocker, does not touch the
  user's browser or other apps, needs no VPN and does not intercept TLS.
- No statistics: the counter lives in memory for the open page; nothing is stored or sent.

## Filter list

| List | Source | Licence |
|---|---|---|
| EasyPrivacy | `https://easylist.to/easylist/easyprivacy.txt` | GPL-3.0-or-later or CC BY-SA 3.0 (dual, easylist.to/pages/licence.html) |

- Downloaded **only while the protection is on**: right after switching it on, then weekly
  (WorkManager, network + battery not low), or on *Update the list now*. Off = no traffic to
  easylist.to and the parsed rules are freed. Nothing is shipped in the APK.
- Conditional GET with the stored ETag; past the app's shared HTTP cache.
- Validation before a download replaces the stored file: size 10 KB – 15 MB, `[Adblock Plus`
  header, at least 1 000 usable network rules. A failed or invalid download keeps the
  previous list and shows the error.
- Lists are data, never code: rules are parsed into patterns; no regular expressions from
  the list are compiled (regex rules are skipped), nothing is executed.
- Requests are decided on the phone. No visited URL is sent to easylist.to or anyone else.

## Matching

`:core` `net.planetfoss.core.adblock`:

- Supported: `||host^` rules (host map, label walk), patterns with `*`, `^`, `|` anchors and
  `||`, exceptions `@@`, `$important`, `$third-party` / `$~third-party`, `$domain=a|~b`,
  type options (`script`, `image`, `stylesheet`, `xmlhttprequest`, `subdocument`, `font`,
  `media`, `object`, `ping`, `other`, also negated), `@@…$document` for whole pages.
- Skipped, never broadened: cosmetic rules (`##`, `#@#`, …), regex rules, `$popup`,
  `$redirect`, `$csp`, `$rewrite`, `$removeparam`, `$websocket`, `$generichide` and every
  unknown option; rules that would match everything.
- Pattern rules are indexed by one literal token; a URL is checked against the rules of its
  tokens only. Own glob matcher with bounded backtracking (no catastrophic patterns).
- First/third party by registrable domain from the Public Suffix List that ships with
  OkHttp (`HttpUrl.topPrivateDomain`), computed once per request.
- The request type comes from the Accept header and the file extension; unknown = `other`,
  so type-restricted rules do not fire on a guess.
- The page itself (main frame) is never blocked; a blocked sub-resource gets an empty
  `204` response.

## Interception and its limits

`WebViewClient.shouldInterceptRequest` for page requests and a `ServiceWorkerClient` for
requests made by service workers (attributed to the open page). Not seen: WebSocket
connections, some requests the WebView handles internally, and anything a page draws from
its own domain (first-party ads, sponsor banners). Without cosmetic filtering, empty ad
slots or first-party promotions may remain.

## Interception and its limits

`WebViewClient.shouldInterceptRequest` for page requests and a `ServiceWorkerClient` for
requests made by service workers (attributed to the open page). Not seen: WebSocket
connections and some requests the WebView handles internally. First-party requests are never
blocked, by design.

Debug builds log the parse time and every blocked host with its rule to the local logcat;
release builds log nothing.

## Tests

- `FilterSetTest` (`:core`): host rules, wildcards/anchors, exceptions vs. `$important`,
  party and domain options, types, `$document`, unsupported syntax skipped, case, registrable
  domains, validation, a pathological pattern, and real lists when given
  (`./gradlew :core:test -Pplanetfoss.lists=<dir>`).
- `AdblockRepositoryTest` (`:app`): download → validate → block third-party trackers only,
  broken download keeps the old list, too small/too few rules rejected, server error recorded,
  304 with ETag, site exceptions normalised and covering subdomains, clearing frees the rules,
  request types, switching off needs no list.
