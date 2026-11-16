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
| 2 | Repository | `gh:linuxundich/planetfoss-android`, public – **stays private for now** (2026-10-30); F-Droid needs it public |
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
SourceCode: https://github.com/linuxundich/planetfoss-android
IssueTracker: https://github.com/linuxundich/planetfoss-android/issues
Changelog: https://github.com/linuxundich/planetfoss-android/blob/main/CHANGELOG.md

AutoName: PlanetFOSS
AntiFeatures:
  NonFreeNet:
    en-US: Shows the news of planetfoss.net, whose server software is not published.
    de: Zeigt die Nachrichten von planetfoss.net, dessen Server-Software nicht veröffentlicht ist.
  # Ads: add once ads are switched on at planetfoss.net (own sponsor cards, affiliate tiles)

RepoType: git
Repo: https://github.com/linuxundich/planetfoss-android.git

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
- Store graphics are in `fastlane/metadata/android/*/images/`: `icon.png` (512 x 512, from `icon-randlos.svg` of the
  website CI) and `featureGraphic.png` (1024 x 500).
- Phone screenshots redone 2026-11-10 with the new tabs (Latest, Radar, Discover, Saved), DE + EN, light and dark, from
  `tools/demo-server.py` on the Pixel 10. Play rejects anything longer than 2:1, so they are 1220 x 2424 (sides extended from
  the edge pixels, status bar notification icons painted over). The ten-inch tablet screenshots are still from 2026-10-30.
- Phone screenshots redone 2026-11-16 for the brand refresh (same eight views, DE + EN), status bar demo mode, the
  live-update chip and notification icons painted over, sides extended to 1220 x 2424. The tablet screenshots still show the
  old colours.
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
- Open (Christoph): closed test “Geschlossener Test - Alpha” has a
  draft and the lists “PlanetFOSS Tester” (1) and “Tester Intern” (2) – needs 12 testers for 14 days before production;
  trader status **declared 2026-11-16: trader** (account level, public: name, postal address from the Google payments profile,
  christoph@linuxundich.de, verified phone number; app-level declaration “applies to this app”, no terms-of-service URL), submitted
  for review together with the store graphics and the closed-test settings; Google approved and published all of it the same
  day. The public developer phone number was replaced afterwards (Account details › Contact details, no new review needed).
