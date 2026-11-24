# Phase 8 – Release

Status: 2026-10-30. Preparation; the release itself waits for the decisions below.

## Done

- Store texts for F-Droid in `fastlane/metadata/android/{de-DE,en-US}/` (title, short and
  full description), changelogs per `versionCode` (`changelogs/67.txt` = v0.2.0), screenshots.
- `PRIVACY.md`: everything that leaves the phone.
- Release build: `./gradlew :app:assembleRelease` (R8, unsigned without the keys in
  `~/.gradle/gradle.properties`). Version name from the Git tag `vX.Y.Z`, `versionCode` =
  number of commits + 140 (monotonic; the offset bridges the history split of 2026-11-10, after which the count
  started again at 1 while v0.6.0 had 139).

## Release key (2026-10-30)

- `~/.android/planetfoss-release.p12` (PKCS12, RSA 4096, alias `planetfoss`, valid 30 years); passwords only in
  `~/.gradle/gradle.properties` (`PLANETFOSS_STORE_FILE`, `…_STORE_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD`).
  **Back up the key file and the passwords** – without them no update can replace an installed release. (Backed up by Christoph, 2026-11-10.)
- Certificate SHA-256: `164875b3462be321c125b3c07127827346afbfdf5ec54afa234697471c70caa6`
  (DN `CN=Christoph Langner, O=PlanetFOSS, C=DE`).
- Signed with APK Signature Scheme v3 (enough from Android 14; v1/v2 are left out by the build for minSdk 34).
- v0.2.0: built from the tag in a clean worktree → `dist/planetfoss-0.2.0.apk` (not in Git), versionCode 67,
  SHA-256 `96316ebf84343a77c8176afc230e29cc50d253bf264992c93eff2a7fec9c0222`; installs and starts on the
  Android 17 emulator.
- v0.2.1 (fix: meta line wraps): `dist/planetfoss-0.2.1.apk`, versionCode 71, SHA-256
  `9286def9db26a6841078d3d62050782c7caff5e4ec7358c01b10c1acc85ac4e7`; installed on the Pixel 10 as an update over 0.2.0.
- v0.2.2 (recommendations instead of own support link, API `support` object): `dist/planetfoss-0.2.2.apk`, versionCode 74,
  SHA-256 `bee90919286ee6032ceb104bb47a4df54e203372b5e265f5820e3b50d880609f`; installed on the Pixel 10 as an update over
  0.2.1, support links work again against the live API.
- v0.2.3 (friendlier support and recommendation texts): `dist/planetfoss-0.2.3.apk`, versionCode 78, SHA-256
  `a0a1491045838a1d02f19cec3b4daa0d6637d84f341365b7636fb49a403cd955`; installed on the Pixel 10 as an update over 0.2.2.
- v0.3.0 (website parity: front page, stars, in orbit, topic head, topics, solar system, follow, bundle page, light/dark,
  help): `dist/planetfoss-0.3.0.apk`, versionCode 87, SHA-256 `06f982a6d65ceb8fdc77f05350cfc2f654b7619587ee472f45e40620475a4fe5`.
- v0.4.0 (more of the website's look: night header, orbit bar, supernova band, rubric colours, intro hint, splash):
  `dist/planetfoss-0.4.0.apk`, versionCode 90, SHA-256 `ad6853ff123a4844a25c9617190921c7561d2f332c898623aba1aee0a336f086`.
- v0.5.0 (page motion after Moshidon, no tab bar jump, baseline profile): `dist/planetfoss-0.5.0.apk`, versionCode 95,
  SHA-256 `f104ea70f180e745be61b99a580b3e855d41e5da722e4ecde79a5362e37e3d53`; installed on the Pixel 10 as an update over
  0.4.0. Sideloaded with adb, the profile is applied on the first start and compiled when the phone is idle
  (`adb shell cmd package compile -m speed-profile -f net.planetfoss.app` does it at once).
- v0.5.1 (back gesture with the page motion instead of Navigation's 70 % scale, tab bar shows with the page below):
  `dist/planetfoss-0.5.1.apk`, versionCode 98, SHA-256 `7cb4ef92d137b88abaea007ea31cc5df7809deda6b955c1d9760cfc0f3e9328a`.

## GitHub (2026-11-17)

- The repository is public: https://github.com/freiversum/planetfoss-android (branch `main`, tags `vX.Y.Z`).
- Every release gets `planetfoss-<version>.apk` (built from the tag in a clean worktree, signed with the release key
  above) and its `.sha256` on the releases page; first one is v0.6.3 (versionCode 163).
- Release notes: first line one sentence for users, then the changes, the install note, and the hidden lines
  `<!-- lui:de … -->` / `<!-- lui:en … -->` for the project page on linuxundich.de.

## Baseline profile (2026-10-31)

`app/src/main/baseline-prof.txt` lists the classes and methods the start and the first minutes need (scrolling
Latest, the Supernovas and Rubrics tabs). Android compiles them ahead of time on install; `profileinstaller` does it on
F-Droid and sideloaded installs, where no store does. The file is checked in, so F-Droid's build stays the same.

Record it again when the start or Latest changed much (not for every release). The `:baselineprofile` module runs against
the app's `benchmark` build type (release without R8, debug-signed, profileable). The emulator is never started next to
Gradle, so the APKs are built first and the test runs with `am instrument`:

```sh
./gradlew :app:assembleBenchmark :baselineprofile:assembleBenchmark && ./gradlew --stop
# start the emulator (Android 17 image); -gpu host crashes in gfxstream when the test force-stops the app,
# so this one run uses -gpu swiftshader_indirect
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb install -r -t baselineprofile/build/outputs/apk/benchmark/baselineprofile-benchmark.apk
adb shell am instrument -w -e class net.planetfoss.baselineprofile.BaselineProfileGenerator \
  -e androidx.benchmark.suppressErrors EMULATOR net.planetfoss.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/media/net.planetfoss.baselineprofile/BaselineProfileGenerator_generate-startup-prof.txt \
  app/src/main/baseline-prof.txt
```

`StartupBenchmark` (same call, other class) measures the cold start without and with the profile. 2026-10-31 on the
Android 17 emulator (software rendering, 8 cold starts each), time to first frame, median: 1,348 ms without,
1,157 ms with the profile (−14 %). Same day on the Pixel 10 (Android 17, 8 cold starts each): 338 ms without, 270 ms
with the profile (−20 %); slowest frames (P99 CPU time) 149 → 82 ms. The app talks to planetfoss.net during all runs;
the benchmark packages were uninstalled from the phone afterwards.

## Decisions before the release

| # | Question | Notes |
|---|---|---|
| 1 | Version | **decided 2026-10-30:** the first release is `v0.2.0` (`v0.1.0` stays a local tag, 0dd9738) |
| 2 | Repository | `gh:freiversum/planetfoss-android`, public – **stays private for now** (2026-10-30); F-Droid needs it public |
| 3 | App icon / wordmark licence | **decided 2026-10-30:** CC BY-SA 4.0; the name stays with the official builds (`LICENSES.md`) |
| 4 | F-Droid AntiFeature `NonFreeNet` | **decided 2026-10-30: yes** – the app depends on planetfoss.net, whose server code is not public; drop it once that code is published |
| 5 | ~~F-Droid AntiFeature `Tracking`~~ | decided 2026-10-30: counting opened articles and seen sponsor cards is **opt-in** (Settings → Anonymous counting, off by default; renamed 2026-11-07), so no `Tracking` flag |
| 5b | D1 (2026-11-06, confirmed and built 2026-11-10) | seen sponsor cards are counted **without** opt-in, like ad views on the website (id, slot, language, no IP, no identifier); counting opened articles stays opt-in. F-Droid may read the always-on count as `Tracking`: say so in the submission and accept the flag if they set it. Play: Data safety lists app interactions (anonymous, not linked to a person), ads declaration “yes” |
| 6 | F-Droid AntiFeature `Ads` | **decided 2026-10-30:** only once ads are switched on in the planetfoss.net backend (the app shows sponsor cards and mentions them in its settings only then) |
| 7 | Screenshots | **done 2026-10-30:** `fastlane/metadata/android/*/images` (phone light and dark, ten-inch tablet), taken against `tools/demo-server.py` – only Christoph's own posts and images from linuxundich.de, made-up times and star counts; status bar in Android's demo mode |

## F-Droid recipe (draft, submitted by Christoph)

```yaml
Categories:
  - News
License: GPL-3.0-or-later
AuthorName: Christoph Langner
WebSite: https://planetfoss.net/
SourceCode: https://github.com/freiversum/planetfoss-android
IssueTracker: https://github.com/freiversum/planetfoss-android/issues
Changelog: https://github.com/freiversum/planetfoss-android/blob/main/CHANGELOG.md

AutoName: PlanetFOSS
AntiFeatures:
  NonFreeNet:
    en-US: Shows the news of planetfoss.net, whose server software is not published.
    de: Zeigt die Nachrichten von planetfoss.net, dessen Server-Software nicht veröffentlicht ist.
  # Ads: add once ads are switched on at planetfoss.net (own sponsor cards, affiliate tiles)

RepoType: git
Repo: https://github.com/freiversum/planetfoss-android.git

Builds:
  - versionName: 0.5.1
    versionCode: 98
    commit: v0.5.1
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: 164875b3462be321c125b3c07127827346afbfdf5ec54afa234697471c70caa6  # only with reproducible builds
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.5.1
CurrentVersionCode: 98
```

AntiFeatures per decisions 4–6: `NonFreeNet` now, no `Tracking`, `Ads` later. The `versionCode` comes from `git rev-list --count`,
so F-Droid needs the full history (default for its build server).

## Google Play (2026-11-10)

- Developer account exists (Christoph). New personal accounts need a closed test with at least 12 testers for 14 days
  in a row before production access.
- `./gradlew :app:bundleRelease` builds `app/build/outputs/bundle/release/app-release.aab` (8 MB, includes the baseline
  profile and the R8 mapping, all languages in one split). Upload with the existing key as the *upload key*, let Play
  create the app signing key; Play installs and the APKs in `dist/` therefore cannot update each other.
- Every release adds its entry on top of `RELEASE_NOTES` in `ui/settings/WhatsNewScreen.kt` (version, date of the
  release, a headline and at most five lines, strings in `values/` and `values-de/`): the page "What is new" in Settings.
  `WhatsNewTest` fails on a release build (version from the tag) while that version has no entry – run
  `./gradlew :app:testDebugUnitTest --tests '*WhatsNewTest*'` in the worktree of the tag before building. Written for readers, not copied from CHANGELOG.md.
- Store graphics are in `fastlane/metadata/android/*/images/`: `icon.png` (512 x 512, `icon-store-512.png` of the
  website CI: the motif fills 78 % of the tile, Play only cuts the corners) and `featureGraphic.png` (1024 x 500).
- Phone screenshots redone 2026-11-10 with the new tabs (Latest, Radar, Discover, Saved), DE + EN, light and dark, from
  `tools/demo-server.py` on the Pixel 10. Play rejects anything longer than 2:1, so they are 1220 x 2424 (sides extended from
  the edge pixels, status bar notification icons painted over). The ten-inch tablet screenshots are still from 2026-10-30.
- Phone screenshots redone 2026-11-16 for the brand refresh (same eight views, DE + EN), status bar demo mode, the
  live-update chip and notification icons painted over, sides extended to 1220 x 2424. The tablet screenshots still show the
  old colours.
- Phone screenshots redone 2026-11-22 as designed store images (`docs/store-screenshots/`, built by its `build.sh`): five
  1080 x 1920 images per language with captions on a night-sky panorama, live content from many sources through
  `tools/store-proxy.py` (article images only from linuxundich.de, OMG! Ubuntu and LinuxNews), captured in the emulator.
  Ten-inch tablet images redone the same day the same way (five 2560 x 1440 per language, `tablet.html`, Pixel Tablet
  emulator). The tablet images were uploaded with 0.6.6 on 2026-11-24 (the 10-inch slot had been empty until then).
- Privacy policy URL: https://planetfoss.net/datenschutz/ (has an Android app section; German only).
- Done 2026-11-10: report and block for fediverse replies (UGC policy), decision D1 built, website deploy of `weekly`,
  `meta`, `logbook`, v0.6.0 tagged and the AAB rebuilt (versionCode 139).
- 2026-11-16: internal test release `0.6.1 (146) – neue Marke` (new icon and colours) published from `main` (3c314f6, untagged, so the
  version name is `0.6.1-dev+6.g3c314f6e`); bundle `dist/planetfoss-0.6.1-dev-146.aab`, SHA-256
  `888e8a656e0e1d0b22aa502e1d10148abbf9ac4cd44e1b7b4cdc7432f7101baa`. Play warns about missing native debug symbols (not blocking).
- Checked 2026-11-16: all 11 app content declarations are done (10.11.), among them data safety (one data type, encrypted
  in transit, no deletion) and ads (“contains ads”). v0.6.1 (versionCode 149) is tagged; bundle `dist/planetfoss-0.6.1.aab`,
  SHA-256 `7ad18028c950ba8ca3d04416b3a34afee03cf5052bc8bbb52cbf7cbb67112180`, published to the internal test the same day
  (16:40). Store listing: new icon, feature graphics (DE/EN) and phone screenshots (DE/EN) submitted for review the same
  day (AI asset declaration: not labelled – screenshots are real, icon and graphics drawn by `ci/system/build.py`); the
  closed-test changes in the publishing overview (177 countries + rest of world, own country list instead of the production
  sync, tester list “PlanetFOSS Tester”, feedback address info@planetfoss.net) were submitted along with it (review restarted).
- Closed test (2026-11-16): testers come from the Google Group planetfoss-tester@googlegroups.com (anyone can join, member
  list visible to the owner only; https://groups.google.com/g/planetfoss-tester), set under *Testers › Google Groups* – an
  email list holding the group address does not let group members in. Release 0.6.1 (149) rolled out to the track and
  submitted for review. Opt-in link: https://play.google.com/apps/testing/net.planetfoss.app. The blog post on linuxundich.de
  asks for testers.
- 2026-11-16 (22:31): v0.6.2 (versionCode 158, new mark in the refresh spinner) tagged, built from the tag in a clean
  worktree; bundle `dist/planetfoss-0.6.2.aab`, SHA-256 `e95c22ba195701d24705f9317e95db4883cd04ff4b9b7931a981c5e3bf975859`,
  published to the internal test only. The closed track stays on 0.6.1 (149) until Google has approved it; promote 158
  afterwards, since every change to the closed track restarts the review.
- Open (Christoph): closed test “Geschlossener Test - Alpha” has a
  draft and the lists “PlanetFOSS Tester” (1) and “Tester Intern” (2) – needs 12 testers for 14 days before production;
  trader status **declared 2026-11-16: trader** (account level, public: name, postal address from the Google payments profile,
  christoph@linuxundich.de, verified phone number; app-level declaration “applies to this app”, no terms-of-service URL), submitted
  for review together with the store graphics and the closed-test settings; Google approved and published all of it the same
  day. The public developer phone number was replaced afterwards (Account details › Contact details, no new review needed).
- 2026-11-19: v0.6.4 (versionCode 182) tagged, built from the tag in a clean worktree; APK `dist/planetfoss-0.6.4.apk`
  (SHA-256 `f3c11748cd253e2b50a2b476e7e5d5c3ef5676113286d07fd2084bf3ea9ddfe4`) on the GitHub releases page, bundle
  `dist/planetfoss-0.6.4.aab` (SHA-256 `0da8bcf1ce85350af61e44dfa5a7385e68933c09c9b3c4a2a731ff7e27c1e43f`) as release
  "0.6.4 (182)" in the closed test (Alpha), sent for review together with the new store icon. Play's only warning: no
  native debug symbols. The release APK itself was not installed on a device (the QA builds of the same day were).
- 2026-11-20: v0.6.5 (versionCode 188, supernovas at ignition) tagged and built from the tag on tinytin (`werkbank gradle
  planetfoss-android --release`, unit tests in the same run); APK `dist/planetfoss-0.6.5.apk` (SHA-256
  `2758131f6e24b82cf388c4eaa0c4949a1214ba45c7b6e30f55546ffdc9f4fd0f`) on the GitHub releases page, bundle
  `dist/planetfoss-0.6.5.aab` (SHA-256 `8ab8daf43beaab19e5c08ec9905e5e2ab4c54d129ccbe411e4bf171f40211961`) for the
  closed test (Alpha) on Google Play as release "0.6.5 (188)", sent for review the same evening (uploaded per Claude in
  Chrome; Play's only warning again: no native debug symbols). Needs planetfoss.net with `pending=1` (live 2026-11-20).
- 2026-11-24: v0.6.6 (versionCode 219, tablet mode, keyboard and mouse, bookmark service, supernova posts no longer
  twice in Latest) tagged two days earlier than planned and built from the tag on tinytin (`werkbank gradle
  planetfoss-android --release`, unit tests and `lintRelease` in the same run); the signed release APK started in the
  Werkbank emulator against the live API, phone and tablet size (not on the Pixel 10). APK `dist/planetfoss-0.6.6.apk`
  (SHA-256 `58ae28126e30b329a692ba00b358dfeda812e4be3cb252b1dd3ab68792d8e7c8`) on the GitHub releases page, bundle
  `dist/planetfoss-0.6.6.aab` (SHA-256 `ee7e30ea5acf5c712adf12001fe955705bcfa04eb97d7af5b197eba62f8ad306`) as release
  "0.6.6 (219)" in the closed test (Alpha), sent for review together with the ten-inch tablet screenshots (DE + EN, AI
  declaration: not labelled) per Claude in Chrome. Play's only warning: no native debug symbols; the console asked
  nothing about the "Nearby devices" permission. Data safety was left unchanged (still to decide, see below).

## Release 0.6.6 (2026-11-24, Google Play)

Released 2026-11-24 instead of 2026-11-26; the checklist below is kept as a record. Still open from it: whether the
data safety form wants the bookmark service listed (item 3).

Collected 2026-11-21. Everything since v0.6.5 is in `CHANGELOG.md` under `[Unreleased]`; the in-app "What is new"
entry (0.6.6, dated 2026-11-26) and its strings are already in. On the day:

1. Last look at `[Unreleased]` for anything added after 2026-11-21; then heading `## [0.6.6] - 2026-11-26`
   (drop the "Planned as" line).
2. The store changelog files are named after the versionCode, which is only known at the release commit
   (commits on `main` + 140): create `fastlane/metadata/android/{de-DE,en-US}/changelogs/<versionCode>.txt` with the
   texts below in the same commit as the changelog (as for 0.6.5: "Changelog and store texts", then "Release 0.6.6"),
   and check the number with `git rev-list --count HEAD` + 140 after the release commit. If the "What is new" date
   is not 2026-11-26 any more, change it in `WhatsNewScreen.kt`.
3. Google Play, besides the bundle:
   - **Permissions:** new runtime permission "Nearby devices" (`ACCESS_LOCAL_NETWORK`, Android 17), only asked for a
     bookmark service in the home network. No Play declaration form is known for it; check what the console says.
   - **Data safety:** the bookmark service sends the address and title of saved articles to a server the user
     entered, never to planetfoss.net or the developer. Check whether the form wants it listed (Google counts data
     sent to the user's own chosen service on the user's action differently from collection); decide in the console.
4. GitHub release (first line for users, `lui:` comments for linuxundich.de), draft below.
5. Tag `v0.6.6` on `main`, build from the tag on tinytin, then `intern` gets `main` merged as usual.
6. Google Play, added 2026-11-23: upload the new tablet screenshots (`fastlane/…/tenInchScreenshots/`, tablet mode).
   Data safety is unchanged by the tablet mode: on wide screens articles open in the app's own browser by default
   (PRIVACY.md says so), the site sees the same as from any browser, counting is the same opt-in call.

Tablet mode (added after collecting, 2026-11-23): T0–T5 of `docs/11-tablet-mode.md`, checked in the Werkbank emulator
(tablet both ways, foldable size, split screen, phone landscape, dark, German, 130 % font); R8 and `lintRelease` pass
(2026-11-23, unsigned, on tinytin); the signed release APK still needs a look on the Pixel 10 on the day. Not on hardware: a real tablet, a real mouse, TalkBack by
ear.

Tested before collecting: bookmark service end to end against Christoph's own Karakeep on the Pixel 10 (also saved in
airplane mode and delivered afterwards, English UI); offline behaviour on the Pixel 10 and the emulator. Not yet on
the phone: a failed send with "Try again" (emulator only).

### Store texts (Google Play "What's new" and F-Droid, max. 500 characters each)

de-DE:

```text
- Tablets: Liste und Inhalt nebeneinander, Artikel daneben lesen, Einstellungen in zwei Bereichen
- Tastatur und Maus: Tastenkürzel (? zeigt alle), Rechtsklick-Menü, Tooltips
- Gespeichertes auf Wunsch im eigenen Lesezeichendienst: Karakeep, linkding, Linkwarden oder Readeck
- Offline: Ist das Netz wieder da, lädt die App selbst neu; der Hinweis sagt, wie alt der Stand ist
- Entdecken, Themen und Quellen zeigen offline den letzten Stand
- Einstellungen enden mit dem Nachthimmel von freiversum
```

en-US:

```text
- Tablets: list and content side by side, read articles beside the list, settings in two panes
- Keyboard and mouse: shortcuts (? shows them all), right-click menu, tooltips
- Saved articles can also go to your own bookmark service: Karakeep, linkding, Linkwarden or Readeck
- Offline: back online, the app reloads by itself; the hint says how old the shown state is
- Discover, topics and sources show the last loaded state offline
- Settings end with the night sky of freiversum
```

### GitHub release notes (draft)

```markdown
PlanetFOSS 0.6.6 brings a real tablet mode, keyboard shortcuts, your own bookmark service and copes better without a connection.

### Added
- Tablet mode: on tablets and wide windows the list stays on the left and what you open goes to the right, articles
  included (in the app's browser; Settings → Open articles). Supernova, logbook, weekly review and Discover use the
  width, Settings come in two panes, and the handle between list and content can be dragged.
- Keyboard and mouse: Ctrl+1–4, / to search, J/K from post to post, Enter, O, B, S; ? shows all shortcuts. Right click
  opens a post's menu, middle click opens it in the browser.
- Bookmark service: Karakeep, linkding, Linkwarden or Readeck (Settings → Saved → Bookmark service). Saved articles
  are sent from the phone directly to your server; planetfoss.net never sees the token or the bookmarks.
- The freiversum night sky at the end of Settings, with a few things to discover.

### Changed
- Back online, every "No connection" screen reloads by itself.
- The offline hint says how old the shown state is.
- Discover, topics and sources show the last loaded state offline.

Install: `planetfoss-0.6.6.apk` below (Android 14 or newer), or Google Play (closed test).

<!-- lui:de PlanetFOSS 0.6.6 bringt einen echten Tablet-Modus, Tastenkürzel und den eigenen Lesezeichendienst. -->
<!-- lui:en PlanetFOSS 0.6.6 brings a real tablet mode, keyboard shortcuts and your own bookmark service. -->
```
