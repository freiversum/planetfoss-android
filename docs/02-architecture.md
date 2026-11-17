# Phase 2 – Architecture and UX

Status: draft, 2026-10-29. Builds on [01-analysis.md](01-analysis.md) and
[02-api.md](02-api.md).

## Decisions so far

| Topic | Decision | Why |
|---|---|---|
| Data source | own JSON API on planetfoss.net (`/api/app/v1/`) | feeds lack images, rubrics, bundles, search; we own the server, no scraping |
| RSS in the app | **not parsed** | API and RSS run on the same server – an RSS fallback would only help in a case that doesn't happen, and would double the parsing code. Supernova ids are identical to the RSS guids anyway |
| Push | WorkManager periodic check only | bender/ntfy excluded; no third-party push server |
| Full text | none, original opens in the chosen browser | texts belong to the sources |
| Application ID | `net.planetfoss.app` | planetfoss.net is ours |
| Android versions | 14–17 (`minSdk 34`) | only versions with security updates from Google; no legacy code (decision 2026-10-29) |
| Licence | GPL-3.0-or-later | requirement |
| Ads | none in v1 | the site's ads count views – no counting in the app; revisit separately |

## Languages

Every text of the app exists in English and German – menus, dialogs, errors,
notifications, accessibility descriptions. The app language follows the phone by default
and can be set in the app (Android 13+: the system's per-app language; older Android: stored
by the app and applied at start). It is independent of the content language (All / German /
English posts). Texts the API sends (rubric names, supernova summary) come in the app
language via `ui=`. The composed supernova image is content and follows the content
language.

## Modules

```
:core   pure Kotlin/JVM – no Android
        api/        DTOs (kotlinx.serialization), mapping to domain models
        domain/     Item, Supernova, Rubric, Source, BrowserMode, …
        nova/       NovaDetector (which supernovas are new → notify)
        adblock/    filter list parser, matcher, validator
        url/        UrlPolicy (which scheme/host goes where)
:app    Android
        data/       Room (db/), DataStore (prefs/), OkHttp client, repositories
        sync/       NovaCheckWorker (WorkManager), scheduling
        notify/     channel, notification builder, permission handling
        browser/    InternalBrowserScreen (WebView), CustomTabs launcher
        adblock/    FilterListUpdater, WebView request interceptor
        ui/         Compose screens, ViewModels, theme, navigation
```

Two modules only: `:core` holds everything that must be tested hard (detection,
filtering, URL policy, JSON mapping) and runs as plain JUnit in seconds. More
modules would add build time without a benefit at this size.

## Stack

| Concern | Choice | Reason / alternative rejected |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose, Material 3 | requirement; Expressive components only where stable in the BOM used |
| Adaptive nav | `material3-adaptive-navigation-suite` | bar on phones, rail on tablets/landscape, one code path |
| DI | Hilt | same as Hausmusik, F-Droid fine (Apache 2) |
| Network | **OkHttp + kotlinx.serialization**, no Retrofit | 8 GET endpoints; OkHttp's HTTP cache gives ETag/304 for free; Retrofit/Ktor add a layer without benefit here |
| Images | Coil 3 (`coil-compose`, `coil-network-okhttp`) | Compose-native, shares the OkHttp client, memory + disk cache; Apache 2 |
| Local data | Room | cached lists, saved items, seen supernovas, filter list metadata |
| Settings | DataStore (Preferences) | browser mode, adblock on/off, exceptions, notification toggle, content language |
| Background | WorkManager | the only correct tool for deferrable periodic work |
| Browser | `android.webkit.WebView` + `androidx.webkit`; `androidx.browser` Custom Tabs | `androidx.webkit` for feature-checked safe settings |
| Tests | JUnit 4/5, kotlinx-coroutines-test, Turbine, Room in-memory, Compose UI test, MockWebServer | MockWebServer for API and adblock fixtures |
| Build | Gradle Kotlin DSL, version catalog, no Google services plugin | F-Droid |

Explicitly **not used**: Firebase (any), Play Services, Play Core / in-app review,
Crashlytics/ACRA-with-upload, Accompanist (superseded), Gson.
Exact versions are pinned in phase 3 after checking the current stable releases.

`minSdk 34` (Android 14), `targetSdk`/`compileSdk 37`: only the Android versions that still get security updates from Google (Android Security Bulletin October 2026: 14, 15, 16, 17). No compatibility code for older versions; when Google ends support for a version, `minSdk` follows.

## Navigation

```
NavigationSuite (bar on phones, rail ≥ 600 dp)
├── Start        newest items (= planetfoss.net/), pull-to-refresh, infinite paging
│                top bar: language chip [Alle|DE|EN], search, settings
├── Supernovas   burning first, then afterglow (7 days); card → topic screen
├── Rubriken     list of rubrics with hot topics → rubric screen
└── Gespeichert  saved items, offline, newest saved first
Pushed screens:  Topic(key) · Rubric(slug) · Source(slug) · Search · Settings
                 · Browser(url) (internal) · Licences
```

Four tabs instead of five: settings are not a destination one switches to
regularly; they sit in the top bar on every tab (Android convention). Videos and
sources are reachable from rubric/topic/item screens, not as tabs.

Deep links (`android:autoVerify` not required – the app doesn't claim the website):

```
planetfoss://supernova/<key>        from notifications
planetfoss://thema/<key>  planetfoss://rubrik/<slug>  planetfoss://quelle/<slug>
https://planetfoss.net/thema/<key>/ etc.  – optional intent filter, only if
  Christoph wants the app to offer itself for planetfoss.net links (needs
  assetlinks.json for auto-verify; without it the user picks once)
```

## Screens and UI states

Every list screen has one `UiState`:

```kotlin
data class ListState(
    val items: List<ItemUi>,        // from Room, shown immediately (stale-while-revalidate)
    val refreshing: Boolean,
    val loadingMore: Boolean,
    val endReached: Boolean,
    val error: UiError?,            // Offline | Server | Timeout – shown as banner over cached data,
)                                   // or full-screen with retry when there is no cached data
```

Empty states have their own text (“Nichts gespeichert – tippe auf das Lesezeichen
eines Beitrags”, “Gerade brennt keine Supernova”).

**Item card**: source logo + name + relative time (exact time in content
description), title (max 3 lines), excerpt (2 lines, hidden at large font scale
to keep cards readable), image 16:9 (or fitted when `fit`), rubric chip, “+ N
Quellen” for bundles (expands to the bundle's other reports), bookmark button,
overflow: share, copy link, open in other browser mode, show source.
Supernova items get the ✦ badge, like on the website.

**Supernova card**: composed image from PlanetFOSS, label, “N Quellen · seit …”,
quote, up to 5 reports, “Alle Berichte” → topic screen.

**Design**: colours from the PlanetFOSS brand (`public/assets/brand/`, CI
document in `ci/system/`), as a Material 3 colour scheme for light and dark;
dynamic colour (Material You) as an option, off by default so the app looks like
PlanetFOSS. System font (no bundled fonts, no Google Fonts). Rubric colours from
the website's CI.

**Accessibility**: touch targets ≥ 48 dp, content descriptions on every icon button,
cards as one merged semantics node with custom actions (save, share), font scale up
to 200 % tested, contrast ≥ 4.5:1 checked for both schemes.

State: `rememberSaveable` + `SavedStateHandle` for scroll position, tab and
filters; lists come from Room, so process death restores instantly.

## Data and sync

Room tables:

| Table | Content | Limit |
|---|---|---|
| `item` | cached items (all fields of `Item`) | pruned: not in any list and not saved, older than 7 days |
| `list_entry` | (listKey, position, itemId) per list (`home:de`, `rubrik:sicherheit:`, …) | replaced on refresh of page 1 |
| `saved_item` | itemId + savedAt + snapshot of title/url/source/date/rubric | none (user data) |
| `seen_supernova` | id (guid), key, firstSeenAt, notified | pruned after 30 days |
| `filter_list` | name, url, lastSuccess, etag, ruleCount, sha256 | 2 rows |

Paging: page N from the API, appended to `list_entry`; no Paging 3 library – lists are
short (30 per page), and a manual “load more at end” stays simple and testable.

Refresh policy: foreground refresh on open if the list is older than 5 min, and on
pull-to-refresh. No background sync of articles – only the supernova check.

## Supernova detection and notifications

```
NovaCheckWorker (periodic, 30 min default, user can choose 15 min / 30 min / 1 h / 3 h;
                 constraints: network connected; backoff exponential from 5 min)
  └─ GET /api/app/v1/supernovas?lang=<content lang>  (If-None-Match)
       304 → done
       200 → NovaDetector.process(entries, seen, now)
               new  = entries whose id ∉ seen
               fresh = new with announced_at ≥ now − 48 h
               insert all `new` into seen_supernova (notified = id ∈ fresh)
               return fresh
           → notify(fresh) if notifications enabled and permission granted
```

- **Idempotent**: insert-or-ignore on the primary key, notify only rows that this
  run inserted (one Room transaction). Running twice with the same response gives
  zero notifications the second time – this is the core unit test.
- First run after install: everything seen is stored as `notified = false`, so the
  user isn't flooded; only supernovas announced in the last 48 h may notify.
- Re-ignition within 7 days keeps the id → no second notification (same as RSS).
- Changed content of a known id (more sources) updates the stored row, never notifies.
- Hidden misfires disappear from the list; no retraction.
- Errors: network/timeout/5xx → `Result.retry()`; malformed JSON → `Result.failure()`
  for this run (the periodic schedule continues), nothing stored.
- Notification channel `supernova` (default importance), one notification per
  supernova, grouped with a summary when ≥ 2. Tap → `planetfoss://supernova/<key>` →
  topic screen of that supernova, back goes to the Supernovas tab.
- `POST_NOTIFICATIONS` (Android 13+) asked when the user enables notifications
  (onboarding card on the Supernovas tab, not at first start). If denied or the
  channel is blocked, settings show it with a button to the system settings.
- The settings text says “wird regelmäßig geprüft (etwa alle 30 Minuten, Android
  kann das verzögern)” – no promise of instant delivery.

## Browser

`BrowserMode = Internal | CustomTab | Chooser`, stored in DataStore, default
`CustomTab`. Fallback chain: CustomTab without a capable browser → Chooser.

**Internal browser** (own screen, not a separate activity):

- Top bar: close, domain (+ lock icon for https), overflow: reload, forward,
  share, copy URL, open externally, tracker protection off for this site.
- Progress bar, error page (offline, DNS, timeout, HTTP error) with retry.
- Back gesture: WebView history first, then close.
- WebView state saved via `saveState`/`restoreState` in the screen's saved state.

Security settings:

| Setting | Value |
|---|---|
| JavaScript | on (most sources need it), but no `addJavascriptInterface` ever |
| File/content access | `allowFileAccess=false`, `allowContentAccess=false` |
| Mixed content | `MIXED_CONTENT_NEVER_ALLOW` |
| TLS errors | `onReceivedSslError` → `handler.cancel()` + error page, never `proceed()` |
| Geolocation, camera, mic | denied (`onPermissionRequest` → deny, no geolocation callback) |
| Pop-ups / new windows | `setSupportMultipleWindows(false)` – opens in the same view |
| Downloads | handed to the system via `DownloadListener` → external browser |
| Safe Browsing | off: it sends URL hashes to Google; documented in PRIVACY.md |
| Third-party cookies | off; cookies on for first-party (logins on sources) |
| Schemes | `http(s)` stay inside; `mailto:`, `tel:`, `geo:`, `intent:` → `UrlPolicy` decides: known safe schemes → system intent after user tap; `intent:` with fallback URL only; `javascript:`, `file:`, `content:` → blocked |
| Clear data | settings: “Browserdaten löschen” → cookies, cache, web storage, history |

## Tracker protection (internal browser only)

- List: **EasyPrivacy** (dual GPL-3.0 / CC BY-SA 3.0, easylist.to), downloaded from
  `https://easylist.to/easylist/easyprivacy.txt` – only when the user enables the
  protection, then at most weekly
  (WorkManager, unmetered preferred) or on “Jetzt aktualisieren”.
- Not shipped in the APK: lists change daily, a stale snapshot would be misleading,
  and the app stays smaller. Until the first download the screen says so.
- Validation: header `[Adblock Plus …]`, `! Checksum`/`! Expires` read if present,
  size between 10 KB and 10 MB, ≥ 1 000 parsed network rules; otherwise the old list
  stays active and the error is shown with the time of the last success.
- Supported syntax (network rules only): `||host^`, `|https://…`, plain substrings
  with `*` and `^`, `@@` exceptions, options `$third-party`, `$~third-party`,
  `$domain=a|~b`, `$script`, `$image`, `$stylesheet`, `$xmlhttprequest`,
  `$subdocument`, `$important`. Unsupported options → rule skipped (never broadened).
  Cosmetic rules (`##`, `#@#`, `#?#`) are skipped.
- Matcher: host rules in a hash set (walk the label suffixes of the request host),
  the rest indexed by a token (longest literal substring) → candidate list. Built
  off the main thread into a compact binary cache, loaded once per process.
- Interception: `WebViewClient.shouldInterceptRequest` (+ `ServiceWorkerController`
  client on API 24+) returns an empty `WebResourceResponse` for blocked requests.
  The main frame and first-party requests are never blocked.
- Exceptions: per site domain in DataStore; editable list in settings; toggle in the
  browser overflow. Global on/off toggle.
- Limits (shown in the UI and README): WebSockets and some
  requests from service workers/beacons may not be seen, only the internal browser
  is covered – not the external browser, not the system.
- No statistics are stored or sent; a per-page counter of blocked requests is
  held in memory only, for the overflow menu (“12 Anfragen blockiert”).

## Network targets (complete list)

| Host | When | What |
|---|---|---|
| `planetfoss.net` | app use, supernova check | API JSON, images in `/img/` |
| `easylist.to` | only with tracker protection on, ≤ weekly | EasyPrivacy |
| sites the user opens | only in the internal browser, on tap | the article |

Nothing else – no fonts, no CDN, no crash reporting. Checked in phase 7 with
mitmproxy on the emulator.

## Privacy, licences, F-Droid

- `PRIVACY.md`: the list above, what WebView stores locally (cookies, cache,
  DOM storage, form data off), what the visited servers see (IP, User-Agent,
  Referer – the app sends `Referrer-Policy`-like behaviour: no referer to sources
  from API images; WebView default for pages).
- `LICENSES.md`: own code GPL-3.0-or-later; every dependency with licence
  (generated by a Gradle report, checked by hand); EasyPrivacy origin and
  licence; icon/wordmark status (open decision).
- F-Droid: metadata in `fastlane/metadata/android/{de-DE,en-US}/`, version tags
  `vX.Y.Z`, `versionCode` monotonic, no Gradle plugins that phone home, dependencies
  only from Maven Central/Google Maven (both accepted by F-Droid). The submission
  itself is done by Christoph.

## Phase plan from here

3. Skeleton: Gradle, theme, navigation, `:core` with tests, OkHttp/Room/DataStore,
   API v1 in PlanetFOSS (`home`, `supernovas`) on the test environment
   planetfoss.linuxandi.net.
4. Core features: lists, topic/rubric/source/search, saved items, supernova check +
   notifications.
5. Browser: internal/external, URL policy, deep links.
6. Tracker protection.
7. QA: tests, mitmproxy check, licences, F-Droid scanner (`fdroid scanner`), measurements.
8. Release: tags, APK, docs.

The app will talk to the **test environment** until the API is live on
planetfoss.net; the base URL is a build config field, not a user setting.

## Open questions for Christoph

1. App icon/wordmark licence (CC BY-SA vs. trademark, all rights reserved).
2. Should the app offer itself for `https://planetfoss.net/…` links?
3. Ads/sponsors in the app – none in v1 is the proposal.
