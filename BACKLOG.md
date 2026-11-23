# Backlog

- [x] Phase 1 – analysis (docs/01-analysis.md)
- [x] Phase 2 – architecture and UX (docs/02-*.md)
- [x] Decide: app icon / wordmark licence – CC BY-SA 4.0; the name stays with the official builds, forks rename (2026-10-30)
- [ ] Decide: offer the app for https://planetfoss.net/ links
- [x] (2026-11-20) Link to freiversum (the umbrella of the independent projects, like the website footer since 2026-11-19): one quiet row
      in Settings → About, "A star in the freiversum" / „Ein Stern im freiversum" with the constellation mark (vector
      drawable from the brand's size S), opens https://freiversum.de/ outside the app; DE + EN strings; no tracking parameters
- [x] (2026-11-21) freiversum card "Sternwarte" (concept freiversum.de/docs/app-fuss/, steps 1 + 2) replaces the row in
      Settings: intro once when fully visible, tap sign, shooting star, UFO (three taps on PlanetFOSS, long press on the
      sky, World UFO Day 2 July / 24 June), TalkBack as one unit, still with "Remove animations"
- [ ] freiversum card: move into its own library `sternwarte` (concept step 3) once it has proven itself, with a sync
      script for projekte.json; star data is copied by hand into `ui/freiversum/Sky.kt` until then
- [ ] freiversum card on the Pixel 10: haptic ticks, TalkBack by ear, the UFO over Settings in light and dark
- [x] Website parity, priority 4 (2026-11-01): weekly review (API `weekly`), hide sources/rubrics (on the phone), bundle
      languages (`bundle.langs`), donation link from the backend (API `meta`, off for now)
- [x] Release with the weekly review once planetfoss.net serves `weekly` and `meta` (website deploy first) – released with v0.6.0
- [x] Website parity, priority 1 (2026-10-31): imprint/privacy links, topic head, source directory, in orbit + topics
- [x] Website parity, priority 2 (2026-10-31): front page on top of Latest (API `front`), light/dark choice, “How it works”
- [x] Website parity, priority 3 (2026-10-31): bundle page (API `bundle/<id>`), search filter by kind of source, Mastodon
      link, following topics and sources with notifications
- [x] Decide: ads/sponsors in the app – own sponsor cards as on the website, only while ads are on there (2026-10-30)
- [x] Phase 3 – skeleton, API v1 (`home`, `supernovas`) (docs/03-skeleton.md)
- [x] App API live on planetfoss.net (2026-10-30)
- [x] Oldest supported Android (14) in the emulator, without Google services
- [x] Phase 4 – core features: rubrics, topic/source screens, search, saved items,
      background supernova check + notifications, API endpoints (docs/04-core-features.md)
- [x] Phase 4 device checks: source list, search, notification + deep link, Saved offline
- [ ] Notification test over a real night (Doze) against the live API – simulated Doze checked 2026-10-30 (docs/07-qa.md)
- [x] Export of saved articles (backup is off) – JSON file with import (2026-11-06); a QR/text transfer code is not built
- [x] Phase 5 – browser (docs/05-browser.md)
- [x] Phase 5 device checks: chooser mode, Custom Tab mode, other-app dialog, delete browser data
- [x] Phase 6 – tracker protection (docs/06-adblock.md)
- [x] Flaky unit test: `PlanetFossApp.onCreate` started WorkManager on Dispatchers.Default under Robolectric; unit tests now
      run with a plain `Application` (`app/src/test/resources/robolectric.properties`, 2026-10-30)
- [x] Phase 7 – QA (docs/07-qa.md): tests, network check, security review, licences, F-Droid scanner, measurements
- [x] Material 3 Expressive on the Pixel 10 (2026-10-30)
- [x] UI/UX audit: website fonts and colours, spacing scale, compact cards, settings dialogs (docs/07b-ui-audit.md)
- [x] New card layout and fonts on the Pixel 10 (2026-10-30)
- [ ] TalkBack walk-through by ear on a device (tree checked 2026-10-30), including the settings choice dialogs
- [x] Tablet layout: two staggered columns from 700 dp list width (emulator as 1280×800 dp tablet, 2026-10-30)
- [ ] Move to Material 3 1.5.0 stable when it is released
- [ ] Phase 8 – release
  - [x] Store texts DE/EN (fastlane), PRIVACY.md, F-Droid recipe draft (docs/08-release.md)
  - [x] Decided 2026-10-30: first release is v0.2.0; the repository stays private for now
  - [x] Decided 2026-10-30: AntiFeature `NonFreeNet` yes (server code not public), no `Tracking` (counting is opt-in),
        `Ads` only once ads are on at planetfoss.net
  - [x] Screenshots with own content (tools/demo-server.py: only linuxundich.de posts), DE + EN, phone + tablet
  - [x] v0.2.0 tagged (versionCode 67), changelogs in fastlane and CHANGELOG.md
  - [x] Release key created, v0.2.0 signed (`dist/planetfoss-0.2.0.apk`, docs/08-release.md) – back up the key file
  - [x] Public repository github.com/linuxundich/planetfoss-android with a signed APK on the releases page (2026-11-17)
  - [ ] F-Droid submission (Christoph)
  - [ ] F-Droid metadata: AntiFeature `Ads` (own sponsor cards and affiliate tiles once ads are on at planetfoss.net);
        submission by Christoph only
- [x] Fair to the sources, phases 1–3 (2026-10-30): tracker protection for third-party trackers only,
      referer; support links and "open in the user's browser" per source; own sponsor cards
- [x] Fair to the sources, phase 4: publishers section on planetfoss.net/ueber/#betreiber covers the app, support links, reach on request
- [x] Count article opens from the app per source (daily totals), combined and as their own value (2026-10-30)
- [x] Logbook: screen, entries on Latest/Rubrics/weekly review, morning notification (2026-11-05)
- [x] Release with the logbook once planetfoss.net serves `logbook` and `home.logbook` (website deploy first) – released with v0.6.0
- [x] Logbook home screen widget (newest entry; followed topics as a second, small widget) (2026-11-06)
- [x] Decide: bottom bar Today / Radar / Discover (STRATEGIE.md on planetfoss.net) – mockup first: variant A, built 2026-11-06
- [x] “New since the last visit” markers and Radar tab (2026-11-06)
- [x] Today: block "Since 6:00" above the logbook entry (2026-11-07; built, on the Pixel)
- [ ] Logbook notification over a real morning (Doze) on the phone
- [x] “Discussed on” and replies from the fediverse (bundle and topic head, below the supernova, 2026-11-05)
- [x] Release once planetfoss.net serves `discussions` and `replies` (live since the deploy of 2026-11-05) – released with v0.6.0
- [ ] Widgets on the phone over a few days (redraw every 3 h, data older than 2 h fetched by the widget, launcher previews)
- [x] Widget preview images for the launcher's picker: `android:previewLayout` with sample lines for both widgets
      (2026-11-19) – not looked at in the launcher's picker yet
- [x] Cold start from planetfoss://radar opened Today once (2026-11-06) – not reproducible: emulator cold start, restored task, widget flags and a cold start on the Pixel all open Radar; most likely the test ran while Android redrew the widgets after the update

- [x] Ads in the app, phase 4 (app side, 2026-11-06): candidates, rotation, compact card, info sheet. Phases 1–3 are on the server side.
- [x] Decision D1 built (2026-11-10): seen sponsor cards are reported without the opt-in; PRIVACY.md, settings and setup texts, docs/08-release.md updated
- [x] Google Play internal test, v0.6.0 from the Play Store on the Pixel 10 (2026-11-10): setup assistant, Latest, Radar, Discover, Saved, logbook, weekly review, supernovas, search, article in the Custom Tab, save, follow (asks for the notification permission), settings; no crash in logcat
- [x] (2026-11-19, `settings_privacy_note_ads`, shown once sponsor cards arrive) Settings → Privacy text (`settings_privacy_note`) did not yet say that seen sponsor cards are counted without opt-in; add it for when ads are switched on (the explain text under *Count for the sources* already does)
- [ ] F-Droid text: AntiFeature `Ads` on, mention the always-on sponsor count (may be read as `Tracking`); Play: Data safety "app interactions", ads "yes"
- [x] Ads in the app: own placements `app_feed`/`app_list` and `/api/app/v1/ads` are built and live (server, lui-ads, app; plan in
      planetfoss.net `ANZEIGEN-APP.md`); no ad is booked for the app yet
- [x] Ads in the app seen on the Pixel 10 against the test environment with two demo ads (2026-11-19): standard card on
      Latest after the fourth post, compact card in a rubric after the eighth, info sheet, tap opens `/a/<id>` in the
      browser and is counted for `app_list`, pull to refresh picks another card, English version ("Sponsored"), views are
      reported to `/api/ad-view`
- [x] Compact sponsor card: the title wrapped badly beside the button ("Linux-Notebooks" / ", vorinstalliert") and the text
      got one cut line; "Anzeige melden" on the info sheet wrapped inside its button. Fixed 2026-11-19: button below the
      text, two lines each for title and text, whole buttons wrap on the sheet; seen on the Pixel 10
- [ ] First real ad in the app after the customers agreed (Christoph)
- [x] Setup assistant: seven pages at first start, repeatable from Settings (2026-11-07)
- [x] Setup assistant checked on the Android 14 emulator (2026-11-07): all seven pages in German, language switch keeps the page, finish/restart, repeat from Settings; notification permission dialog and the dark theme not looked at
- [x] Latest as the start tab (built 2026-11-10; concept `docs/09-latest-tab.md`, mockups `docs/start-concept-mockup.html`, 2026-11-10):
      Today/Discover overlap; tab 1 becomes a live stream with supernovas and comets on top, the logbook an edition,
      Discover a browse screen. Decisions D-L1 to D-L4 confirmed 2026-11-10
- [ ] Latest over one real morning on the Pixel: card at 6:00, marker after opening or from noon, several burning supernovas
- [x] Latest and Discover on the emulator (2026-11-10): English dark, German light at 130 % font, against the test
      environment: Plasma 6.8 (English reports only) shows with content language German; logbook marker at 6:00 opens the entry
- [x] Own tab icon for Latest: `ic_latest`, the four-pointed star above a stream of lines (2026-11-10)
- [x] Server: supernovas in the app API also with fewer than two reports in the content language (planetfoss.net
      29163bc, live since 2026-11-10) (Plasma 6.8.3 on
      2026-11-10 was missing with `lang=de`), see `docs/09-latest-tab.md`
- [x] Feedback round 2026-11-19, checked on the Pixel 10: Latest keeps its place after the app's browser; the source line of a
      card stays on one line; the follow check announces posts that arrive late (ids of the first page, database version 5)
- [x] Follow notification for the English sister of a linuxundich.de post instead of the German original (2026-11-18): not
      the app – the German feed was served stale, so planetfoss.net never had the original. The content language
      (All / German / English) already applies to every notification
- [x] First session (feedback 2026-11-19: who sets nothing up on day one rarely comes back). Fresh install on the Pixel 10:
      welcome, six steps, "All set", then Latest with content – but nothing could be followed on the way and Radar started
      empty. Built 2026-11-19 after `docs/setup-follow-mockup.html`: step 3 "Follow topics" (chips), own switch for the
      follow notifications; checked on the Pixel 10 with a fresh install (three topics chosen, no permission dialog, Radar
      lists them)
- [x] Follow topics step in the light design, in English, at 130 % font and without a reachable server (the retry text): checked on the Pixel 10, 2026-11-19
- [x] A list scrolled past its first page dropped back to page 1 when it refreshed itself after five minutes away (page 1
      replaced the list). Built 2026-11-19: an automatic refresh merges page 1 (new posts on top, loaded pages stay); a
      whole page of new posts or a pull to refresh still replaces the list
- [x] Flaky unit test: `ItemListViewModelTest.theSponsorCardComesFromTheCandidates` failed once with "Dispatchers.Main is
      used concurrently with setting it" (2026-11-19): Main is now set once for the class; four runs in a row green
- [x] New store icon uploaded in the Play Console and sent for review (2026-11-19): default listing de-DE, en-US inherits
      it; AI assets declaration "do not label". Managed publishing is off, so it goes live once Google approved it
- [ ] Check in the Play Store that the new icon is live (review usually takes up to 7 days)
- [x] "What is new" in Settings (2026-11-19, mockup `docs/whats-new-mockup.html`): the last five releases, checked on the
      Pixel 10 in the dark design, German
- [x] "What is new" in the light design, in English and at 130 % font: checked on the Pixel 10, 2026-11-19 (found and
      fixed: dark status bar icons on the night sky head in the light design)
- [x] v0.6.4 released 2026-11-19 (versionCode 182): GitHub release with APK, closed test on Google Play sent for review together with the new store icon
- [x] Check of the counting and of the privacy promises with real users (2026-11-19): the numbers arrive, the database
      holds daily totals only. Fixed: Radar and search inflated the views (app 0.6.4), development builds were counted and
      a start could get lost (server, planetfoss.net 6817ef4). PRIVACY.md now names the hosting provider's access log
- [x] planetfoss.net: counting changes rolled out (commit 6817ef4, live 2026-11-19); a development build is no longer counted
- [ ] Hosting provider: "store no IP addresses" is set for planetfoss.net but has no effect since the move of 2026-10-27;
      Christoph decided on 2026-11-19 to leave it (the website's bot filter and country numbers need the address)
- [x] Save to the reader's bookmark service when tapping the bookmark icon, phase 1 (2026-11-21): Karakeep, linkding,
      Linkwarden, Readeck; checked against local containers of all four (`docs/10-bookmark-services.md`, "Testing")
- [x] Bookmark service on the Pixel 10 with Christoph's own Karakeep (2026-11-21, debug build from intern): connected,
      a saved article arrived with the tag "planetfoss" within seconds
- [x] Bookmark service on the Pixel 10, 2026-11-21: saved in airplane mode → "on its way" note, delivered 9 s after
      airplane mode ended; English UI in the dark design checked
- [ ] Bookmark service on the Pixel 10: failed + "Try again" (needs a broken token or server; checked in the emulator)
- [x] Offline behaviour (2026-11-21, found on the Pixel 10 in airplane mode): screens reload by themselves when the
      connection is back (`NetworkWatch`), the hint says "as of 14:05", Discover/topics/sources show the cached answer
      (OkHttp cache, `getOrCached`) instead of an empty page
- [ ] Bookmark service phase 2: wallabag (OAuth2) and Nextcloud Bookmarks (Login Flow v2)
- [ ] Bookmark service: plain HTTP for LAN instances (network security config allows HTTPS only today) and
      self-signed certificates – only if readers ask
- [ ] Idea (2026-11-22): a full tablet mode. Today only Latest uses the width (rail plus two staggered columns from
      700 dp); seen on the Pixel Tablet emulator while making the store images: Supernovas, Logbook and topic pages sit
      in one centred column with wide empty margins, a supernova's lead image takes most of the screen and its reports
      start below the fold. Ideas: list and detail side by side (open an article, supernova or topic in a right-hand
      pane instead of a new screen), supernova reports next to the image, Logbook and Weekly review in two columns,
      Discover as a grid, landscape and portrait both thought through, keyboard and mouse (shortcuts, hover)
      Concept and mockups 2026-11-23: `docs/11-tablet-mode.md`, `docs/tablet-mode-mockup.html`; phases T0–T5
  - [ ] Decisions D-T1 to D-T5 (Christoph)
  - [x] T0 wide forms of Supernova, Logbook, Weekly review, Discover (2026-11-23; emulator as Pixel Tablet both ways and as phone)
  - [x] T1 list and detail side by side (Latest, Radar, Saved, search, lists), situation pane (2026-11-23; D-T2 and D-T3
        as proposed, not confirmed yet; emulator as Pixel Tablet both ways, rotation with two pages open, phone)
  - [x] T1 rest (2026-11-23): mark what is open on the right (Discover, Radar, topic and source lists); logbook and weekly
        review in the situation pane; links and notifications open on the right (also on a cold start); search checked
  - [ ] Tablet: mark the open topic among chips and the open story on its card (only rows are marked so far); hide the
        on-screen keyboard when a search result opens on the right
- [x] Flaky unit test: `ItemListViewModelTest` failed twice (2026-11-23) with “uncaught exceptions before the test
      started” – the view models of a test outlived it and a refresh hit the closed in-memory database on Room's disk
      thread. Fixed 2026-11-23: `tearDown` cancels and joins their scopes before closing the database; eight runs of the
      class green, no “already-closed” errors in the output any more
  - [x] T2 article in the detail pane (app's own browser) (2026-11-23; D-T1, D-T4, D-T5 confirmed as proposed)
  - [ ] T3 keyboard shortcuts, hover, right click, focus ring; menus/dialogs instead of sheets
  - [ ] T4 settings in two panes, topic side column, draggable divider, foldable hinge
  - [ ] T5 device checks (tablet both ways, Fold, split screen, TalkBack) and new tablet store images

## GitHub presentation

- [ ] Social preview image with the next update: a screenshot or a designed card of the app, 1280×640 PNG (2:1), under 1 MB, keep text and key content in the centre with ~40 px margin. Keep the source in the repo (`docs/social-preview.png`). Upload by hand under *Settings* | *General* | *Social preview* (there is no API for it). GitHub shows it on topic pages (github.com/topics/…), in Explore, and as the link card on Mastodon, Bluesky and in chats.
