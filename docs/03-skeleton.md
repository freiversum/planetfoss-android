# Phase 3 – Skeleton

Status: 2026-10-29. What exists, how it was checked, and where it deviates from
[02-architecture.md](02-architecture.md).

## Delivered

**Server (PlanetFOSS, branch `app-api` in `web/planetfoss.net`, not merged, not deployed)**

- `src/AppApi.php`: `GET /api/app/v1/home` and `/api/app/v1/supernovas` as specified in
  [02-api.md](02-api.md); file cache in `data/app-api/` (5 / 2 min), ETag + 304, no
  cookies, no counting (StatsLog skips `/api/`).
- `NovaFeed::entries` returns its fields individually as well; the RSS output is unchanged.
- `tests/AppApiTest.php` (27 checks) in `tests/run.sh`; the whole suite is green.

**App**

| Part | State |
|---|---|
| Gradle, version catalog, `:core` (JVM) + `:app` | done |
| `:core`: API models, OkHttp client with error types, `NovaDetector` | done, 16 tests |
| Room (items, list entries, list meta, supernova lists, seen supernovas) | done; schema exported to `app/schemas/` |
| DataStore settings (content language) | done |
| Theme from the PlanetFOSS colours, light and dark | done |
| Adaptive navigation (bar / rail), 4 tabs + settings | done |
| Latest: cached list, pull-to-refresh, paging, error banner, empty/loading states | done |
| Supernovas: cards with PlanetFOSS image, quote, reports | done |
| Rubrics, Saved | placeholder screens, clearly marked as not implemented |
| Opening articles | Custom Tabs → system chooser (internal browser follows in phase 5) |
| Supernova check in the background, notifications | **not yet** (phase 4) – only the detector logic exists |
| Repository tests (Robolectric, in-memory Room, MockWebServer) | 7 tests |

## Checked on a real device

Pixel 10 (Android 17), debug build against `tools/fixture-server.py` – it converts the live
RSS feeds of planetfoss.net into v1 answers because the API is not deployed yet
(`adb reverse tcp:8099 tcp:8099`, `-PPLANETFOSS_DEBUG_URL=http://localhost:8099/`).

| Check | Result |
|---|---|
| Latest with real posts, images, logos, rubric | ok |
| Supernovas with composed image, quote and reports | ok |
| Paging while scrolling (pages 2 and 3 requested) | ok |
| Offline: refresh shows banner over the cached list; retry after reconnect clears it | ok |
| HTTP cache: within `max-age` a refresh offline is served from the cache (no error) | ok, as intended |
| Dark mode | ok |
| Font scale 200 %: tab labels broke mid-word → fixed (one line, ellipsis); excerpt hidden | fixed and rechecked |
| German relative time was capitalised mid-sentence (“Vor 2 Stunden”) | fixed |
| Content language EN survives a force stop | ok |
| Article tap opens a Custom Tab (Firefox Beta) | ok |
| Cold start (`am start -W`, debug build) | 632–679 ms |

Not checked yet: emulator with an older Android (the emulator plus Gradle ran the
machine out of memory on 2026-10-29 – test it alone next time), TalkBack walk-through,
tablet layout (rail).

## Build and dependency checks

- `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug` – 23 tests green, lint
  without findings (lint aborts on errors).
- Release APK (R8, resources shrunk, unsigned): 2.0 MB; `dependenciesInfo` off for F-Droid.
- Release runtime classpath contains no `gms`, Firebase, Play Services, Crashlytics or
  analytics artifacts. Transitive Google artifacts are free: Dagger/Hilt, Guava
  (via Hilt), Accompanist drawablepainter (via Coil), all Apache-2.0.

## Deviations from the architecture draft

- Material 3 **1.4.0** (stable, from the BOM) instead of Expressive – Expressive is still
  beta (1.5.0-beta01).
- Versions raised to the current stable releases during the phase: AGP 9.5.0,
  Gradle 9.8.2, Kotlin 2.4.21, DataStore 1.3.0, WorkManager 2.12.0, OkHttp 5.5.1,
  kotlinx.serialization 1.12.0.
- Launcher icon rendered from the PlanetFOSS favicon (adaptive + monochrome); its
  licence is still the open decision from phase 1.
- `allowBackup="false"` and no cloud backup/device transfer for now; an export of
  saved articles is planned instead.
