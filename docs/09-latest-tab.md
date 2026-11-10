# 09 – Latest as the start tab

Status: decided and built 2026-11-10 (D-L1 to D-L4); server part live since 2026-11-10. Mockups: `docs/start-concept-mockup.html`.

## Problem

The bottom bar since 2026-11-06 is Today / Radar / Discover / Saved (planetfoss.net `STRATEGIE.md` 4.2,
variant A). In daily use the split between Today and Discover is hard to follow:

- **Today** is the logbook entry of 6:00, i.e. the 24 hours *before* 6:00, plus a small block "Since 6:00"
  (four posts). A supernova that ignites during the day (KDE Plasma 6.8 on 2026-11-10, 11:42) shows up only
  in tomorrow's entry. At noon the tab shows yesterday's comets and releases.
- **Discover** is really the start page (front page, "In orbit", supernova band, weekly teaser, the whole
  stream), but its name says "browse".
- Neither tab shows *everything that is current* in one place.

Second cause, on the server: `GET /api/app/v1/supernovas?lang=de` drops a supernova with fewer than two
reports in the content language (`NovaFeed::entries`, the rule of the language feeds and Mastodon). Plasma
6.8.3 had four English reports only, so with content language German the band on Discover stayed empty.

## Idea

Split by **time** and **order** instead of two overlapping feeds:

| Role | Where | Content |
|---|---|---|
| What is happening now | tab 1 **Latest** | burning supernovas, comets, then every post by time with "new since your last visit" |
| Browse by structure | tab 3 **Discover** | search, sky, logbook archive, weekly review, videos, rubrics with hot topics, topics, sources – no stream |
| Read once a day | **logbook** as an edition, not a tab | card on Latest from 6:00 until read, then a marker at 6:00 in the stream; archive on Discover |

Radar and Saved stay as they are. No new visual parts: supernova card, comet chips, new-since bar, read line,
logbook head, tiles and rubric rows exist already.

## Decisions

Confirmed by Christoph on 2026-11-10 as proposed in the mockup:

- **D-L1 Name of tab 1:** *Aktuell* / *Latest*. "Today" is wrong as soon as the stream reaches past midnight.
- **D-L2 Supernovas without reports in the content language:** always listed, with the note "only English
  reports" / "nur deutsche Berichte" (strings `bundle_only_en` / `bundle_only_de` exist). Server change, see below.
- **D-L3 Logbook card → marker:** once the entry was opened, or from 12:00 on, whichever comes first.
- **D-L4 Several burning supernovas:** the newest as the big card, further ones as compact cards below it.

## Latest (tab 1)

Top to bottom:

1. **Sky** – every burning supernova (`supernovas`, `burning == true`), newest first; D-L4. Card as
   `NovaCard` (big) and `NovaCard(compact = true)`.
2. **Rising** ("Im Aufwind") – one chip row: comets from `supernovas.comets` (comet colour), then the
   `topics.orbit` entries that are neither a supernova nor a comet. Replaces "In orbit" and the logbook's comet
   section on this tab.
3. **Logbook card** – only while the newest entry is unread and before 12:00 (D-L3): calendar day, headline,
   numbers line (posts, sources, releases). Tap opens `LogbookRoute(day)`. Mondays to Wednesdays the weekly
   teaser takes the same rule (unread → card, else gone; it stays on Discover).
4. **New-since bar** – as now on Discover (`NewSinceBar`, "All seen").
5. **Stream** – `home` with the front page as now: lead card, then posts by time, rails (projects, videos,
   starred) and sponsor cards where `ItemListContent` puts them today. Inserted into the stream by time:
   - the **logbook marker** at 6:00 once the card is gone ("Logbook published · Firefox 160", tap opens it),
   - the **read line** ("read up to here · 9:12") as now,
   - after the read line, once: **this week's sky** – chips of the supernovas of the last 7 days that no
     longer burn; tap opens the topic, the row's end opens `SupernovasRoute`.

Pull to refresh reloads `supernovas`, `topics`, `front` and `home`. Tapping the tab again scrolls to the top
(as now).

## Discover (tab 3)

No stream any more. One screen (`LazyColumn`, max content width as elsewhere):

1. Search field (opens `SearchRoute`; the search icon leaves this tab's top bar).
2. Tiles 2×2: **Sky** (supernovas in 7 days, how many burn), **Logbook** (archive of entries),
   **Weekly review** (last week), **Videos**.
3. **Rubrics** inline: name + its hot topics (`rubrics.hot_topics`, ✦ for supernovas); tap opens the rubric
   list. Replaces the separate `RubricsRoute` as an entry point (the route stays for deep links).
4. Chips: all topics, all sources.

`RubricsScreen` already has most of this (rubrics, videos, starred, topics, sources, weekly, logbook links);
the new Discover is close to `RubricsScreen` without its back arrow. Check whether one screen can serve both.

## Logbook

- No tab. Reached from the card / marker on Latest, the Logbook tile on Discover, the widget, the morning
  notification and `planetfoss://logbook/{day}`.
- The "Since 6:00" block (`SinceBlock`, `SinceCard`) is dropped: Latest itself is that block.
- "Read" = the newest entry was shown in `LogbookScreen`. Stored on the device only (day string in
  `SettingsRepository` or `SeenRepository`), like the other seen markers.

## Navigation and links

- `Tab.Today` → `Tab.Latest` with `HomeRoute` as its route; `Tab.Discover` gets a new route (`DiscoverRoute`).
- `planetfoss://today` keeps working and opens Latest (old widgets and notifications).
- Logbook widget and morning notification open `LogbookRoute` of the newest day instead of the Today tab.
- Start destination: Latest.
- Setup assistant, tour page: new texts for the four tabs (`setup_tab_today` → `setup_tab_latest`,
  `setup_tab_discover` new), DE + EN. Help screen accordingly.

## Server (planetfoss.net)

- `AppApi` supernovas: for the app, stop dropping supernovas with fewer than two reports in `lang`; keep the
  reports in the content language first. The reports already carry `lang`, so the app can derive the note
  (D-L2) itself; a `langs: {de, en}` count per entry would only be needed if the list is cut. The RSS/Mastodon language feeds
  keep their two-report rule. Same for the `burning` band data used by Latest.
- No new endpoint otherwise: Latest uses `supernovas`, `topics`, `front`, `home`, `logbook` (for the card).

## Affected code (app)

| File | Change |
|---|---|
| `ui/AppNavigation.kt` | tabs, routes, start destination, deep links |
| `ui/list/ItemListScreen.kt`, `ItemListViewModel.kt` | Home header: sky (all burning), rising row, logbook card, week's sky after the read line; drop `DiscoverChips` |
| `ui/logbook/LogbookScreen.kt`, `SinceBlock.kt` | only the entry view with back arrow; drop the tab variant and `SinceCard` |
| `ui/rubrics/RubricsScreen.kt` | becomes (or feeds) the new Discover |
| `ui/setup/SetupScreen.kt`, `ui/settings/HelpScreen.kt` | texts and tour |
| widget / notification code | target `LogbookRoute` |
| `res/values*/strings.xml` | new strings DE + EN (`tab_latest`, `rising`, `logbook_card_*`, `logbook_marker`, `week_sky`, `discover_search_hint`, …) |

## Order of work

1. Server: supernovas for the app across languages with `langs` (planetfoss.net, deploy test, then live).
2. Latest: header parts on `HomeRoute`, logbook card/marker, week's sky; Today tab removed.
3. Discover as the browse screen.
4. Links, widget, notification, setup assistant, help.
5. Tests: unit tests for the card/marker rule (D-L3) and the rising row (dedupe against supernovas); emulator
   run DE + EN, light + dark, large font; on the Pixel over one morning (card at 6:00, marker after reading).
