# Technical notes

How PlanetFOSS for Android is built, tested and documented. For what the app does, see the [README](../README.md).

## Notifications

Supernova notifications are off until you turn them on. The app then checks PlanetFOSS
periodically in the background (every 15 minutes to 3 hours, Android decides the exact
time). There is no push server and no Google service involved, so a notification can come
later than the supernova itself.

## Documentation

| Document | Content |
|---|---|
| [docs/01-analysis.md](01-analysis.md) | PlanetFOSS as data source, supernova definition, push options |
| [docs/02-api.md](02-api.md) | specification of the read-only app API on planetfoss.net |
| [docs/02-architecture.md](02-architecture.md) | modules, stack, navigation, sync, browser, tracker protection, privacy |
| [docs/03-skeleton.md](03-skeleton.md) | phase 3: what exists, device checks, deviations |
| [docs/04-core-features.md](04-core-features.md) | phase 4: lists, search, saved, notifications, tests |
| [docs/05-browser.md](05-browser.md) | phase 5: browser modes, security decisions of the internal browser |
| [docs/06-adblock.md](06-adblock.md) | phase 6: tracker protection, filter list, limits; fair to the sources |
| [docs/07-qa.md](07-qa.md) | phase 7: tests, network check, security review, licences, F-Droid, measurements |
| [docs/07b-ui-audit.md](07b-ui-audit.md) | UI/UX audit: fonts and colours of the website, spacing scale, compact cards, settings dialogs |
| [docs/08-release.md](08-release.md) | phase 8: store texts, privacy, F-Droid recipe draft, open decisions |
| [docs/09-latest-tab.md](09-latest-tab.md) | the four tabs: Latest, Radar, Discover, Saved |
| [LICENSES.md](../LICENSES.md) | licences of all libraries, assets and runtime data |

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
`:baselineprofile` module, see [docs/08-release.md](08-release.md#baseline-profile-2026-10-31).

Release signing is optional and read from `~/.gradle/gradle.properties`
(`PLANETFOSS_STORE_FILE`, `PLANETFOSS_STORE_PASSWORD`, `PLANETFOSS_KEY_ALIAS`,
`PLANETFOSS_KEY_PASSWORD`); without them the APK stays unsigned.

### Testing with fixed or crafted data

`tools/fixture-server.py` converts the public RSS feeds of planetfoss.net into answers
of the app API and serves them locally (development only, nothing is stored):

```sh
tools/fixture-server.py --port 8099   # --ads serves invented sponsor cards; --support SLUG=URL[|PROJECT] adds a support link or recommendation
adb reverse tcp:8099 tcp:8099          # phone over USB and emulator alike
./gradlew :app:installDebug -PPLANETFOSS_DEBUG_URL=http://localhost:8099/
```

World UFO Day (2 July and 24 June: the UFO of freiversum.de flies over Settings each time
they are opened) can be simulated in debug builds; release builds ignore the extra:

```sh
adb shell am start -S -n net.planetfoss.app.debug/net.planetfoss.app.MainActivity --ez ufo_day true
```

