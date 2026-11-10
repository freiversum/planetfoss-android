# PlanetFOSS for Android

A native Android client for [PlanetFOSS](https://planetfoss.net), the Linux and
open source news aggregator: the daily logbook, a Radar of what you follow with what is new
since your last visit, newest posts, supernovas (topics many sources report on at once) with
notifications, rubrics, search, saved articles and two home screen widgets. Articles open
in an internal browser with optional tracker protection or in your own browser.

No tracking, no account, no ad or analytics services, no Google services. Counting opened
articles for the sources is opt-in ([PRIVACY.md](PRIVACY.md)). Runs on Android 14 and newer – the
versions that still get security updates. Licensed under GPL-3.0-or-later.
Built to be published on F-Droid.

**Status:** version 0.5.1 (2026-11-01), quality assurance done (phase 7 of 8). Latest
posts with the front page, supernovas and comets, supernova notifications, following topics and
sources, rubrics, topics, sources, bundles, search, saved articles, the weekly review, hiding
sources and rubrics, and the internal browser with its optional tracker protection
work; the public release (phase 8) follows. It uses the app API on
planetfoss.net (live since 2026-10-30; `weekly` and `meta` are on the test instance until the
next website deploy, see CHANGELOG).

## Notifications

Supernova notifications are off until you turn them on. The app then checks PlanetFOSS
periodically in the background (every 15 minutes to 3 hours, Android decides the exact
time). There is no push server and no Google service involved, so a notification can come
later than the supernova itself.

| Document | Content |
|---|---|
| [docs/01-analysis.md](docs/01-analysis.md) | PlanetFOSS as data source, supernova definition, push options |
| [docs/02-api.md](docs/02-api.md) | specification of the read-only app API on planetfoss.net |
| [docs/02-architecture.md](docs/02-architecture.md) | modules, stack, navigation, sync, browser, tracker protection, privacy |
| [docs/03-skeleton.md](docs/03-skeleton.md) | phase 3: what exists, device checks, deviations |
| [docs/04-core-features.md](docs/04-core-features.md) | phase 4: lists, search, saved, notifications, tests |
| [docs/05-browser.md](docs/05-browser.md) | phase 5: browser modes, security decisions of the internal browser |
| [docs/06-adblock.md](docs/06-adblock.md) | phase 6: tracker protection, filter list, limits; fair to the sources |
| [docs/07-qa.md](docs/07-qa.md) | phase 7: tests, network check, security review, licences, F-Droid, measurements |
| [docs/07b-ui-audit.md](docs/07b-ui-audit.md) | UI/UX audit: fonts and colours of the website, spacing scale, compact cards, settings dialogs |
| [docs/08-release.md](docs/08-release.md) | phase 8: store texts, privacy, F-Droid recipe draft, open decisions |
| [LICENSES.md](LICENSES.md) | licences of all libraries, assets and runtime data |

## Building

Requirements: JDK 17 or newer, Android SDK with platform 37 and build-tools 37.0.0
(`local.properties` with `sdk.dir=…`). No Google services, no API keys, no secrets.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug   # tests and lint
./gradlew :app:assembleRelease                                # unsigned release APK
```

`./gradlew :app:assembleQa -PPLANETFOSS_DEBUG_URL=…` builds the release configuration (R8)
signed with the debug key, for device tests before a release. `tools/licenses.py` checks
the licences of everything in the release APK; `tools/host-log-proxy.py` logs which hosts a
test device talks to. `app/src/main/baseline-prof.txt` (faster start) is recorded with the
`:baselineprofile` module, see [docs/08-release.md](docs/08-release.md#baseline-profile-2026-10-31).

Release signing is optional and read from `~/.gradle/gradle.properties`
(`PLANETFOSS_STORE_FILE`, `PLANETFOSS_STORE_PASSWORD`, `PLANETFOSS_KEY_ALIAS`,
`PLANETFOSS_KEY_PASSWORD`); without them the APK stays unsigned.

### Testing with fixed or crafted data

`tools/fixture-server.py` converts the public RSS feeds of planetfoss.net into answers
of the app API and serves them locally (development only, nothing is stored):

```sh
tools/fixture-server.py --port 8099   # --ads serves invented sponsor cards; --support SLUG=URL[|PROJECT] adds a support link or recommendation
adb reverse tcp:8099 tcp:8099          # phone over USB; the emulator uses 10.0.2.2
./gradlew :app:installDebug -PPLANETFOSS_DEBUG_URL=http://localhost:8099/
```

## License

Copyright (C) 2026 Christoph Langner

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
See [LICENSE](LICENSE).

The PlanetFOSS name and logo are not covered by this licence.
