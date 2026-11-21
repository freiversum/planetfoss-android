# Changelog

All notable changes to PlanetFOSS for Android. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- Settings end with a small, always dark card of the freiversum night sky instead of the quiet row "A star in the
  freiversum": a cut-out of the star map of freiversum.de with the four projects, PlanetFOSS (FV 2) marked with a
  double ring, a short text and the button "Visit freiversum.de" (opens outside the app, without tracking
  parameters). When the card comes fully into view for the first time after opening Settings, and from its play
  icon, it plays once for about four seconds: the line draws itself, the stars light up one after the other, a radar
  beam sweeps over the sky, two pulses go out from PlanetFOSS and a comet runs along the constellation. Then it
  stands still. TalkBack reads the card as one item with the link as its action.
- Two fonts of freiversum.de for the card only: Oxanium for its title and Atkinson Hyperlegible Mono for its labels
  (Latin, one weight each, 34 KB together, SIL OFL 1.1).

### Added
- The card can be played with: a tapped star flares and shows its name and role for a moment, a sideways swipe over
  the sky draws a shooting star, and tapping PlanetFOSS three times quickly calls the UFO of freiversum.de. A long
  press on the sky sends three radar pulses from the finger, and the UFO comes to that spot, hovers, blinks and leaves
  with its hyperdrive. It flies over the whole Settings screen and lets every touch through. Its hyperdrive is heard
  like on freiversum.de (1.7 s, the same synthesis), only when the phone rings normally – never in silent or vibrate
  mode.
- World UFO Day: on 2 July and on 24 June (the phone's date) the UFO comes by on its own each time Settings are opened.
- With "Remove animations" switched on in Android, the card shows its still picture right away, a tapped star shows
  its sign without flaring, and neither the shooting star nor the UFO appears.

## [0.6.5] - 2026-11-20
New supernovas show up the moment they ignite, like on planetfoss.net; the notification still waits half an hour.

### Added
- Settings → About ends with a quiet row "A star in the freiversum" ("Ein Stern im freiversum"): the constellation mark
  of freiversum, the umbrella of the independent projects around PlanetFOSS, and a link to freiversum.de that opens
  outside the app, without tracking parameters.

### Changed
- A new supernova shows up in Latest and in the Supernovas tab as soon as it ignites, like on planetfoss.net, instead of
  half an hour later. The notification still waits those 30 minutes, so a misfire hidden in that time never reaches
  the phone.

## [0.6.4] - 2026-11-19
New users follow topics during setup, so Radar is filled from the first minute; lists keep their place, and Settings
show what changed in the last versions.

### Added
- The launcher's widget picker shows a preview of the logbook and the Radar widget instead of the app icon.
- "What is new" in Settings, below the version: the last five releases as a timeline, each with a headline and a few
  short lines marked New, Better or Fixed, written for readers. The installed version is the card in the night sky.
  The texts ship with the app in German and English.
- Setup assistant: a new step "Follow topics" (step 3 of 7). The topics that rise right now and the twenty evergreen topics
  with the most posts are chips; a tap follows, another one unfollows. The chosen topics fill Radar from the first
  minute, and the closing page says how many are followed. Nothing asks for a permission on this page.
- Notifications for followed topics and sources have their own switch "News from your topics": in the assistant (next to
  logbook and supernovas, once topics are chosen) and in Settings → Following. Switched off, follows only fill Radar and
  the background check does not run. The bell on a topic or source still asks for Android's permission and switches
  them on.
### Changed
- README is a product page for users now (what the app does, install, questions); building, testing and the design documents moved to `docs/TECHNICAL.md`.
- Settings → Privacy also says that seen sponsor cards are always counted, once planetfoss.net delivers sponsor cards.
- Counting on planetfoss.net: opening Radar is one view (it counted one per followed topic or source), and a visit to
  the search is one view (it counted every pause while typing). The further requests are marked as parts of the screen.
- PRIVACY.md names the web server's access log at the hosting provider: it holds the IP address, the time and the
  requested address of every request, also the app's. The numbers PlanetFOSS keeps were and are daily totals without
  an address; the sentence "no IP address is stored" was only true for those.
- Store icon: the planet fills more of the tile (78 % instead of 62 % of its width). In Play's list of updates it looked
  smaller than the icons of other apps. The icon on the phone is unchanged.
- The line with source, time and stars on a card never wraps: a long source name is cut ("PC Games H…"), the time and
  the stars always stay whole.

### Fixed
- Latest stays where it was after reading an article in the app's own browser; it jumped back to the top.
- A list that was scrolled past its first page keeps the loaded pages when it refreshes by itself after a while away; new
  posts go on top. Before, it dropped back to its first page and the place was lost. Pull to refresh still starts the
  list again.
- Compact sponsor card (lists): the button sits below the text, so title and text keep the card's width; before, the
  title broke badly beside it. The two buttons of "Why am I seeing this ad?" wrap as whole buttons.
- Followed topics and sources: a post that planetfoss.net picks up late – with an older time than the newest post already
  announced – was never announced. The check now remembers the posts of the list's first page instead of only the newest
  time (database version 5).

## [0.6.3] - 2026-11-17
First version on GitHub: the repository is public and every release comes with a signed APK.

### Changed
- README rewritten as a product page with the ways to install (APK from GitHub, closed test on Google Play).
- The optional test against a real filter list (`-Pplanetfoss.lists=<dir>`) reads EasyPrivacy only, the one list the
  tracker protection uses.

### Fixed
- Latest: the logbook band no longer squeezes the logbook into a narrow strip when last week's review sits beside it;
  the review half now reads "Review · Week 46 →" and takes only the room it needs.
- Latest: the "new since" bar keeps the same space above and below.

## [0.6.2] - 2026-11-16
### Changed
- The pull-to-refresh spinner draws the new PlanetFOSS mark (filled planet, tilted ring); the golden spark orbits
  the planet instead of the old moon.

## [0.6.1] - 2026-11-16
### Fixed
- `versionCode` is the commit count plus 140, so builds after the history split (count starting at 1 again) install
  as updates over v0.6.0 (versionCode 139) instead of being rejected as a downgrade.

### Changed
- New app icon from the PlanetFOSS brand refresh (November 2026): filled planet with a violet-to-teal ring and a golden star
  on navy (`#050A24`), generated from the website's logo system (`web/planetfoss.net/ci/system/build.py`,
  `android-foreground.svg`). Legacy icon, Play Store icon and feature graphics (DE/EN) follow suit; the monochrome
  (themed) icon stays the star. The ring is a full ellipse that passes behind the planet, as in the brand
  template.
- The wordmark in the top bar and the setup screen is the new one from the brand refresh (rendered from the website's
  `wortmarke-dark.svg` / `wortmarke-light.svg`), 30 dp high instead of 26 so the name keeps its size next to the planet.
- Phone screenshots for Google Play and F-Droid redone with the new icon, wordmark and colours (DE + EN, light and dark,
  `tools/demo-server.py` on the Pixel 10).
- App colours follow the refreshed website palette: accent `#5A45E6`, violet `#6E5BEB`, star `#FCC954`, teal in dark mode
  `#5EE4DF`, header navy `#050A24` (theme, spinner, setup, cards, radar, widgets).

## [0.6.0] - 2026-11-10
### Changed
- Seen sponsor cards are now counted without the opt-in, like ad views on the website (card id, place and language, no IP address,
  no identifier). Counting opened articles stays opt-in. Privacy text, settings and setup assistant say so.

### Added
- Replies from the fediverse can be reported and their accounts blocked (Google Play's policy for user-generated content): a
  menu on every reply (not PlanetFOSS's own) opens the mail app with a report to info@planetfoss.net – the reply's link,
  the account and the service already filled in – or blocks the account. Blocked accounts are left out of the replies on
  the phone only; they are listed in Settings under *Hidden* and can be unblocked there. German and English.
- Store graphics for Google Play and F-Droid: `icon.png` (512 x 512) and `featureGraphic.png` (1024 x 500) in `fastlane/`.
- Setup assistant (mockup v2 approved 2026-11-07, `docs/setup-assistant-mockup.html`): eight pages that open by themselves at
  the first start (and once for everyone who already uses the app) and later from Settings → About → Setup assistant. The
  welcome and the closing page are the website's night sky with the wordmark and the supernova star; the six pages between
  have a night sky head (step, title, symbol) and their choices in cards: app language, content language and design; how
  articles open (option cards, "Recommended" on your browser); notifications (logbook, supernovas – both off, Android asks
  when switched on); anonymous counting (off, with a note what is counted and what never is); a tour of the four tabs with the
  navigation bar in miniature; the handles (search, follow, save and star, supernova, settings). Every choice is saved at once,
  so leaving on any page keeps it; skip and Back close it for good. It replaces the small intro hint on Discover. German and
  English.
- Today shows a block "Since 6:00" above the logbook entry: the newest post as a card and three more as rows, with a link
  to all of them in Discover. The logbook is written once at 6:00, so at noon Today was six hours behind. Before 6:00 the
  block reads "Since yesterday 6:00". It is left out for archive days, quiet mornings and hidden sources, and a failed
  load keeps the last block. It uses Latest's cached list (a part of the screen, not a counted view).

### Fixed
- Following a topic opened from a link (supernova notification, widget) stored it without a name – Radar and its widget
  showed empty rows. The page takes its title from the topic head now, and a follow never gets a blank name.
- The empty Radar widget's hint was cut off; shorter text, one more line.
- The Radar widget kept showing old follows after following or unfollowing: every change now marks its data stale and
  redraws it, the widget then counts itself.

### Changed
- **Latest is the start tab again, the logbook is an edition** (concept `docs/09-latest-tab.md`, mockups
  `docs/start-concept-mockup.html`, decisions D-L1 to D-L4 of 2026-11-10). The tabs are now Latest / Radar / Discover /
  Saved. Latest shows what is happening now: every burning supernova on top (the newest in full, further ones compact,
  with the note "so far only English sources" when none reports in the content language), "Rising" with the comets of
  the last 24 hours and the orbit topics, then every post by time with "new since your last visit". The newest logbook
  entry stands on top as a card in the morning until it is opened or until noon; after that it is a marker at its 6:00
  between the posts. Supernovas of the week that no longer burn follow the read line as a chip row. Discover has no
  stream any more: search, the sky, the logbook archive, the weekly review, Sternstunden, videos, the rubrics with their
  hot topics, topics and sources. The Today tab and its "Since 6:00" block are gone; `planetfoss://today` opens Latest,
  the logbook widget opens the entry itself. Latest has its own tab icon: the four-pointed star of the PlanetFOSS mark above a stream of lines. Setup assistant
  and help follow. German and English.
- Counting wording: the explanation names publishers, bloggers and content creators and says that the sources only get a
  total per day, never who read what; the switch is "Count anonymously for the sources", the section "Anonymous counting"
  (was "Sources", which read as if the single reader's habits were sent); the privacy note follows.
- Pull-to-refresh uses the planet "o" of the logo instead of the generic loading shape (mockup variant A, 2026-11-07):
  the yellow moon follows the finger around the planet, orbits on its own once the pull is far enough and while loading,
  and the spinner stays at least 1.2 s so a quick refresh is still answered.
- Widgets redesigned (mockup variant C, 2026-11-06): the app's night sky with stars and Venus instead of a flat dark area;
  the logbook widget puts the headline on top and one line each for supernova (sources, languages), comet and new versions
  at the bottom, a smaller layout at 2 × 2; the Radar widget in the same style with thin lines between the rows and burning
  topics in supernova orange.
- Logbook (Today) more airy: more room between and inside the cards, no explanation lines under the section heads,
  reports with the source above the title (the fixed source column cut names like “BleepingCo…”), at most two reports
  per supernova and comet, quotes up to three lines, and the biggest story only when it is not the headline itself.

### Added
- Sponsor cards in two formats and with a rotation (ads plan, phase 4; planetfoss.net `ANZEIGEN-APP.md`): the app asks `ads` for the
  candidates of its two slots – `app_feed` (Discover, standard card with image) and `app_list` (rubric, topic, source and
  video lists, compact card with logo, title, one line and button) – and rolls weighted dice once per screen opening
  (new on pull to refresh), like the website's rotation; a card bound to topics shows only on those topic lists. If the request fails,
  the card the list answer carries is used. Every card has an (i): a sheet says who paid, that there is no profile, advertising ID or
  third-party script, links to the privacy policy and to the imprint to report an ad. No ads on Today, Radar, Saved, Sternstunden,
  bundles, supernovas, notifications or widgets. Seen cards are still reported only with *Count for the sources* (opt-in);
  that changes with the first public release. German and English texts, tests for the picker, the API, the view model and the card.
- `tools/fixture-server.py --ads` serves invented sponsor cards ("Musterhost (Demo)"); the fixture server now answers `front` and `logbook`
  with minimal stand-ins (Latest failed with “not found” against it before).
- Four new tabs (STRATEGIE.md on planetfoss.net, variant A, decided 2026-11-06): **Today** (the logbook as the first
  page, the morning briefing), **Radar**, **Discover** (the former Latest) and **Saved**. Supernovas and rubrics are no
  tabs any more but buttons on top of Discover, together with topics and the source directory; they open with a back
  arrow. The logbook band on Latest is gone (Today shows the entry); the weekly review hint stays.
- New since the last visit: the app keeps the time it last went to the background (on the phone only, nothing is sent)
  and marks later posts with a small “New” pill. Discover shows “23 new since yesterday, 21:40” with “All seen” and a line
  “read up to here” between new and known posts. A visit only ends after 30 seconds away, so a look at an article and back
  keeps the markers. Settings → Radar → “Mark new posts” (on by default).
- Radar: everything you follow with the number of new posts since your last visit, supernovas and comets on top, below
  the newest posts of all of them without doubles (“New for you”, or “Latest” when nothing is new). Without follows a
  short explanation and suggestions from the sky right now. Uses the same public page-1 requests as the follow check.
- Home screen widgets (Jetpack Glance 1.2): **Logbook** (4 × 2: headline, supernova with sources, comets, number of new
  versions) and **Radar** (2 × 2: up to four followed topics or sources with their new posts). Data from the app's last
  load; older than two hours, the widget asks the API itself as background (no view). A tap opens Today or Radar
  (planetfoss://today, planetfoss://radar).
- Export and import of saved articles (Settings → Saved): a JSON file chosen with the system file dialog, for a backup or
  another phone – Android backup stays off. Import keeps what is already saved and refuses other files.
- “Pass it on” next to “Discussed on”: the share sheet hands PlanetFOSS's own Mastodon post to the user's Mastodon or
  Bluesky app for boosting or quoting – no account in the app.
- “Discussed on” in the head of bundles and topics: the biggest thread per service as outlined pills – Hacker News and
  Lobsters with points and comments (from one comment on), PlanetFOSS's own posts on Mastodon and Bluesky with replies or
  “join in”. A tap opens the thread in the chosen browser mode; the app itself never talks to these services. API
  `bundle.discussions`, `topic.discussions`.
- Replies from the fediverse below a supernova, as on the website: public replies to PlanetFOSS's own post with name,
  handle, service, time and text, no avatars; replies to replies indented, PlanetFOSS's own ones tinted, “Join the
  conversation on Mastodon →” below. Placed right before “More on …” (topics without a story split: after six posts).
  API `topic.replies`.
- Logbook (website: /logbuch/): every morning at 6:00 Berlin time one entry with what mattered in the 24 hours before –
  biggest story, supernovas (newly ignited or still shining), comets, new releases, “also worth knowing” and Sternstunden,
  with the neighbouring days and the archive. On Mondays a band to the week that just ended. Entries: Rubrics → Logbook,
  a slim band on top of Latest (Monday to Wednesday together with the weekly review in one band), and “The week in the
  logbook” at the end of the weekly review. API `logbook`, `logbook/<day>`; `home.logbook`, `weekly.logbook`.
- Morning notification for the logbook (Settings → Logbook, off by default): an hourly background check announces a new
  entry once, from 6:00; the first check after turning it on only remembers the current entry, quiet days stay silent.
  Own channel; a tap opens the entry (explicit intent, no new link the outside can start).
- Reach numbers without any extra report: planetfoss.net counts the list requests the app makes anyway, like page views
  on the website. List requests in the background (supernova and follow checks) carry `X-PlanetFOSS-Background: 1`, the
  first one after the app comes to the front (start, or after 30 minutes away) `X-PlanetFOSS-Start: 1`, and requests that
  only fill part of Latest (Sternstunden, orbit, supernova band) `X-PlanetFOSS-Part: 1`. Nothing is
  stored on the phone; the privacy note and PRIVACY.md say so. The supernova and follow checks always mark themselves as
  background, also when they run while the app is open (first test on the phone counted them as views).

### Fixed
- Posts without a picture (Kuketz-Blog, Phoronix and other blogs that send none) no longer show an empty tile in the
  Sternstunden, the rubric, project and video rows, the front page lead and the weekly review's story list: they get the
  website's motif instead – a glow in the source's colour, the source logo, its name and the rubric.

### Changed
- Tab bar as in the Reddit app: it slides away while scrolling down a list and comes back when scrolling up. Tapping
  the tab that is already open goes back to the top of its list (the top bar comes back too), or from a pushed list
  back to the tab's page. The rail on wide screens and in landscape stays.
- Weekly review: the list below the cover story is now "More big stories" and is numbered from 1 instead of 2.
- Topic of a burning supernova: its story comes first and the rest of the topic follows under "Other coverage of …",
  as on the website (the Raspberry Pi 5 supernova is the price rise, not every Pi 5 add-on). The numbers in the head
  count only the story. Uses the new `story` field of the topic head.

### Added
- Weekly review (Rubrics → Weekly review), as the website's /rueckblick/: supernovas, the biggest stories (bundles of at
  least three sources, with German/English sources), topics and Sternstunden of the last finished week, and the
  earlier weeks to switch to. API `weekly`, `weekly/<YYYY-WW>`.
- Weekly review as a small magazine issue, like the website: night-blue masthead with issue, opening paragraph and key
  figures (posts against the week before, sources, supernovas, videos, DE/EN), cover story with picture, teaser and how
  other sources covered it, supernovas as cards with picture and quote, further stories with thumbnails, new releases,
  one post per section, the week in numbers (posts per day, sections, most active sources) and videos of the week.
- Latest, Monday to Wednesday: a hint to last week's review with picture, numbers and cover story (API `home.weekly`).
- Latest, Monday to Wednesday: a hint to last week's review with picture, numbers and cover story (API `home.weekly`).
- Hide a source or rubric from the lists (eye button in its title bar), as on the website – stored on the phone only.
  Its own list keeps showing everything; Latest says how many posts are hidden; Settings → Hidden shows them again.
- Bundle page: how many sources report and in which language (“DE 4 · EN 10”, “so far only English sources”). API
  `bundle.langs`.
- “Support PlanetFOSS” in Settings → About, only while the donation link is switched on in PlanetFOSS's backend
  (API `meta`; off at the moment, so nothing shows).

### Fixed
- Latest loads page 1 once per app session: the logbook band and the weekly review hint live in memory only and were
  missing after a restart or update until the next pull to refresh. The logbook band keeps the same space below as
  “In orbit” above.

## [0.5.1] - 2026-11-01

### Fixed
- The back gesture now uses the new page motion too: the page follows the finger the short way and fades, instead of
  Navigation's default that shrinks it to 70 % (0.5.0 changed only the back arrow and the back button).
- The tab bar is there as soon as the page below shows, also while a full-screen page is swiped away, so that page
  never changes size in view.

## [0.5.0] - 2026-11-01

### Changed
- Quicker, calmer page motion after Moshidon: a new page fades in over a short 100 dp slide and covers the one
  below, which stays still; going back it fades and slides off in 200 ms (the predictive back gesture scrubs it).
  Tabs swap with a short fade instead of the slow 700 ms crossfade. The tab bar no longer jumps: it leaves only once
  a full-screen page (internal browser, settings, help) has faded in and is back in the same frame on return.
- The Custom Tab asks the browser for the same short slide in and out (whether it is used is up to the browser).
- Faster cold start: the app ships a baseline profile, so Android compiles the start and Latest ahead of time
  (−20 % to the first frame on a Pixel 10, 338 → 270 ms); `profileinstaller` applies it on F-Droid and sideloaded installs.

### Added
- Tests for 0.4.0: the supernova band takes the first burning supernova, the intro hint stays closed and belongs to Latest
  only, comet and supernova chips in *In orbit*.
- `:baselineprofile` module (profile generator and startup benchmark) and the `benchmark` build type.

## [0.4.0] - 2026-10-31

### Changed
- More of planetfoss.net's look:
  - Latest has the website's night-sky header with the light wordmark, in light and dark mode alike;
  - *In orbit* as the website's trend bar: pulsing live dot, mono label in aurora, comets as ☄ chips in the light star colour;
  - section heads as on the start page (big title, *All →*); rubric modules tinted in the rubric's colour with a line on top;
  - a bundle's *N more sources* as the website's aurora tag;
  - splash screen in the night sky with the PlanetFOSS wordmark.

### Added
- A burning supernova as a band on top of Latest, like the website's start page.
- An intro hint for new users on top of Latest with the way to *How PlanetFOSS works*; gone once closed or once the help
  was opened.

## [0.3.0] - 2026-10-31

### Added
- What the website offers readers, now in the app as well (API endpoints `topics`, `sources` and the extended topic
  head, live on planetfoss.net since 2026-10-31):
  - *In orbit* on top of Latest – the topics being talked about right now, as the website's trend bar;
  - topic lists start with the topic's head: status (supernova, afterglow, comet), posts, sources and since when, the
    introduction, rubric, parent topic and related topics;
  - *Topics* (Rubrics tab): in orbit with status, and every topic with its own page;
  - *The solar system* (Rubrics tab): every source by kind with its posts of the week, filter *Can be supported*;
  - imprint and privacy policy of PlanetFOSS under Settings → About.
- Latest as a front page, like the website's start page (API endpoint `front`): the lead story by weight – sources, stars,
  freshness, picked by nobody – with three side stories on top, then *Latest* in time order; between the posts the
  Sternstunden, *From the projects*, the busiest rubrics and the videos as rows that scroll sideways.
- *Display → Design*: same as the phone, light or dark (Android's per-app night mode, so the app's browser follows too).
- Follow a topic or source (bell in its title bar): new posts are announced in their own notification channel, one
  notification per topic or source, a tap opens its list – the app's answer to the website's RSS feeds. The app asks
  page 1 of each followed list in the supernova interval; the list of follows stays on the phone (Settings → Following).
- A bundle's reports as cards of their own (*All reports as cards* under the opened sources; API endpoint `bundle/<id>`).
- Search: filter by kind of source (publishers, magazines, blogs, projects, associations, tutorials, videos).
- *PlanetFOSS on Mastodon* under Settings → About.
- *How PlanetFOSS works* (Settings → About): supernova, comet, afterglow, Sternstunden, bundles, sources and your data,
  after the website's about page.
- Star an article for the Sternstunden, as on the website: ☆ on every card (beside the bookmark). The app fetches the
  website's small task, solves it on the phone (under two seconds) and keeps the token that takes the star back – only
  on the phone, no account. The card shows the new count at once.

## [0.2.3] - 2026-10-30

### Changed
- Friendlier support texts: “Enjoy the articles? A subscription or a donation helps right where they are written –
  and keeps them coming.” Recommendations read “… recommends <project> to you instead. You support it directly on its
  own site.” (same wording as the website).

## [0.2.2] - 2026-10-30

### Added
- Recommendations: a source that needs no support itself can name another project instead. Its list then starts
  with a teal box “Recommended by <source> – Support <project> instead”, and the last entry of the card and browser
  menus reads “Support <project>” with “Recommended by <source>” below. Follows the API's new `support` object
  (`url`, `mode` self/forward, `name`), which replaces `support_url`.
- `tools/fixture-server.py --support SLUG=URL[|PROJECT]` gives a source a support link or a recommendation.

### Fixed
- Card action row: topic and rubric chips stay on one line beside star, bookmark and menu; what does not fit is left
  out (the video chip first, then the rubric).

## [0.2.1] - 2026-10-30

### Fixed
- Long source names no longer cut the time off the meta line (“Digitale Gesellschaft Schweiz, vor 31…”): the line wraps
  to a second one when needed.

## [0.2.0] - 2026-10-30

First release for F-Droid (once the repository is public).

### Added
- Sternstunden: what readers starred on planetfoss.net in the last 7 days, most stars first. Latest shows the top five
  as a row after the 7th post with the rank as a small star-coloured badge on the image (as the website's start page),
  the Rubrics tab leads to the full list, and every card shows its stars (“★ 3”, TalkBack: “3 stars”). Needs the new
  API endpoint `starred`.
- Videos: with the Custom Tab setting a video opens in the app Android has for the link (YouTube, NewPipe …, else the
  browser); the card and browser menus offer *Open in video app*; the app's browser plays videos in full screen
  (system bars hidden, back leaves it); video images carry a play sign as on the website.
- The PlanetFOSS wordmark in the top bar of Latest, as in the website's header (light and dark variant; TalkBack says
  “PlanetFOSS, Latest”). Logo, wordmark and icon are licensed CC BY-SA 4.0; the name stays with the official builds.
- Tablets and phones in landscape show two columns of posts (staggered, so cards of different heights leave no gaps).
- Store texts for F-Droid (German and English) and `PRIVACY.md` listing everything that leaves the phone.
- *Count for the sources* (Settings → Sources, opt-in, off by default): opened articles count towards the source's
  reach on planetfoss.net – the app posts the article's address, the area (list, supernova, bundle) and the content
  language to `/api/app-open`, no identifier, a daily total – and seen sponsor cards are reported. Off, the app
  reports nothing about what you read.
- PlanetFOSS's own sponsor cards (and affiliate tiles) in Latest after the 4th post and in the lists after the 8th, as on
  the website and only while ads are on there: labelled, sponsor colour, image, text, note for affiliate links. A tap
  opens the offer in the user's browser through planetfoss.net (which counts the click); with *Count for the sources*
  on, the app reports a view once half the card was on screen – no identifier, no ad network, not stored, none offline.
- Support the sources: where PlanetFOSS has a support link (subscription, donation), a source's list starts with a
  box “Support <source>” (as on the website, variant A), every card menu ends with “Support <source>”, and so does the
  browser menu. The link always opens in the user's own browser.
- Sources can ask for the user's own browser: their articles then never open in the app's browser, and the card
  menu offers no “open in the app”.

### Changed
- PlanetFOSS look (UI audit, `docs/07b-ui-audit.md`): the website's fonts – Space Grotesk for headings and titles,
  Inter for text, JetBrains Mono for source and time; selected tab and filter chips in the PlanetFOSS violet instead of
  teal; one spacing scale and one corner radius for all cards.
- Lists are denser: the first post of a feed keeps its large image, every other post shows source and time across the
  card, a thumbnail beside the title and a two-line teaser – about 3.5 posts per phone screen instead of 1.5. Search hits and saved posts are all compact.
- Settings are shorter and grouped as people look for them: supernovas with notifications, language (app and content),
  opening articles, trackers, sources, about. App language, content language, opening mode and check interval are one
  row each and open a choice dialog.
- Rubrics show the website's rubric colour; empty saved list and search show a symbol.
- Phones in landscape get the navigation rail at the side instead of the bar at the bottom.
- Fair to the sources: the browser's protection is now *Block trackers* – EasyPrivacy for third-party requests only, so
  the sources keep their ads and their own visitor statistics.
- The source sees where its reader came from: the internal browser sends `Referer: https://planetfoss.net/`, the
  Custom Tab passes the app as referrer.

### Fixed
- Settings → Sources: the counting text mentions sponsor cards only once planetfoss.net delivers them (ads switched on
  in its backend); until then it speaks of opened articles only.
- Search: the period buttons no longer cut their labels (“24 Stun…”, “Jederz…”).
- Card and browser menus: every entry has an icon, so the texts line up.
- TalkBack: the supernova, afterglow and comet badges are read as the plain word instead of the star sign and
  spelled-out capitals; supernova topic chips say “Supernova” (the star icon alone said nothing).
- Compact supernova cards: the time no longer gets cut beside the image (“vor 17 Stun…”), it moves under the badge.
- Settings: the internal browser's description names the tracker protection.

## [0.1.0] - 2026-10-30

First version. Android 14 or newer, Material 3 Expressive, all texts in German and English.

### Added
- Project skeleton: Gradle with version catalog, pure Kotlin module `:core`, app module `:app`.
- Client for the PlanetFOSS app API v1 (`home`, `supernovas`) with typed errors (network, HTTP, malformed answer).
- Supernova detection (`NovaDetector`): idempotent, no repeated notifications, nothing older than 48 hours on first run.
- Latest posts with images, source, time and rubric; pull-to-refresh, paging, offline banner over the cached list.
- Content language All / German / English, stored across restarts.
- PlanetFOSS colours in light and dark mode, adaptive navigation (bar on phones, rail on wide screens).
- Articles open in a Custom Tab or the system app chooser.
- Development fixture server (`tools/fixture-server.py`) that serves the public feeds in the API format.
- Rubrics tab with hot topics and videos; lists for rubric, topic, source and videos, cached per language.
- Topic and rubric chips on every card; bundles expand to their other reports.
- Card menu: share, copy link, more from the source.
- Search over PlanetFOSS with matching topics and filters for period, rubric and supernova topics.
- Saved articles, stored locally and available offline.
- Supernova notifications: periodic check (15 minutes to 3 hours), own channel, grouped,
  tap opens the topic; off by default, the first check after turning on stays silent.
- With notifications on, a check also runs right away when they are turned on and when the app starts.
- Choice how articles open: your browser as Custom Tab (default), the app's own browser, or Android's app chooser.
- Internal browser with address line, lock, progress, history, reload, share, copy link, open in browser and error pages;
  certificate errors are never accepted, plain http is upgraded to https, other apps open only after asking.
- Settings: JavaScript in the app's browser, delete browser data.
- WebView usage metrics and Safe Browsing are switched off for the whole app.
- App language German or English, independent of the phone and of the content language.
- Optional tracker blocker for the app's browser (EasyPrivacy): downloaded only while on, updated weekly,
  checked on the phone; site exceptions in the browser menu and the settings; blocked count per page, not stored.
- Supernovas: the card takes up the website's supernova look – night blue with nova glow and stars, status badge in
  the burst gradient, numbers in monospace, quote with a star-coloured bar, reports with round source logos and the
  source in star colour – and shows one lead image (the first report photo) instead of the website's share image,
  which was unreadable at phone size. Reports have no thumbnails, as they mostly repeated the lead image.
- Supernovas: only the first supernova gets the full card; the others are compact (name with a square image, two
  reports by title, “All N reports”), night blue while burning and a light card with a burst-gradient stripe in the
  afterglow, so the list no longer stacks equally heavy blocks.
- Comets (rising topics) in the Supernovas tab: one after each supernova, as the lightest card – outlined, no image,
  a comet tail in the website's comet gradient, posts and sources of the last 24 hours, two reports and “Open topic”.
  Without supernovas the tab says so and still shows the comets.
  Settings → Supernovas → *Show comets* turns them off (on by default).
- Supernovas: each report shows language and time, a longer title and its teaser; the quote is left out when it only
  repeats a teaser shown below.
- Android 14 or newer only (the versions that still get security updates from Google).
- Material 3 Expressive: expressive theme and motion, loading indicators, wavy progress, button shapes, connected button
  group for the search period, short navigation bar and wide rail.
- Filter rules take 14 MB in memory; first and third party are told apart by the Public Suffix List.
- Only `planetfoss://` links reach the app's navigation from outside; no foreground service.
