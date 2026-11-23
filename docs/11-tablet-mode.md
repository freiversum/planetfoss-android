# 11 – Tablet mode

Status: concept, 2026-11-23. T2 built 2026-11-23 (article as `BrowserRoute` in the pane, setting `articlesBeside`). T1 built 2026-11-23 with D-T2 and D-T3 as proposed (`AppNavigation.kt`: a second NavHost for the pane, `SituationRoute`, handover on narrowing). T0 built 2026-11-23 (shared limits `TwoColumnsFrom`, `WideMaxWidth`, `SideBySideFrom` in `ui/theme/Dimens.kt`). Mockups: `docs/tablet-mode-mockup.html`.
Decisions D-T1 to D-T5 confirmed 2026-11-23 as proposed.

## Problem

Seen on the Pixel Tablet emulator (1280×800 dp) while making the store images on 2026-11-22
(`docs/store-screenshots/raw/tab-*.png`):

| Screen | Today on a tablet | Effect |
|---|---|---|
| Latest, rubric, topic, source, Saved | rail + two staggered columns from 700 dp list width (`TwoColumnsFrom`), at most 1200 dp | good; the only screen that uses the width |
| Supernovas | one centred column of 720 dp (`ContentMaxWidth`) | the lead image takes ~ 330 dp height, the first report starts at the bottom edge, the second below the fold |
| Logbook, weekly review | one column of 720 dp | the head box, then a 16:9 image of the supernova fills the whole screen; ~ 280 dp of empty margin left and right |
| Discover | rows of 720 dp in the middle | a phone list with wide margins |
| Settings, help, What is new | 720 dp column, no rail | readable, but one long list of 13 sections |
| Search | 720 dp column | results one below the other, half the screen empty |
| Opening anything | a new full screen (Navigation push) | the list is gone; back, next card, back … |
| Keyboard, mouse | only what Compose does by default | no shortcuts, no hover, no right click; Tab focus works but is not designed |

So the app is a phone app that stretches. The width is wasted where it would help most: reading a
supernova, the logbook, and moving between list and content.

What is already there and stays: `NavigationSuiteScaffold` (rail on large screens and on phones in
landscape), the staggered grid with `FullLine` spans, the window size class from
`currentWindowAdaptiveInfo()`, `ContentMaxWidth` as the line-length limit.

## Goals

1. **List and content side by side** on wide windows: what opens today as a new screen opens in a
   pane to the right; the list stays where it was.
2. **Every screen designed for the width**, not centred: supernova image beside its reports, logbook
   and weekly review in two columns, Discover as a grid, settings in two panes.
3. **Portrait and landscape both thought through**, and a window that changes size (rotation,
   foldable, split screen, freeform window on a ChromeOS or desktop-mode device) keeps its place.
4. **Keyboard and mouse** as first-class input: shortcuts, hover, right click, visible focus.
5. Phones stay exactly as they are. No feature exists only on tablets.

## Window classes

Decided by the **window**, not the device (split screen on a tablet is a phone-sized window).
Material 3 width classes:

| Class | Width | Typical | Layout |
|---|---|---|---|
| Compact | < 600 dp | phone portrait, half of a split screen | as today |
| Medium | 600–839 dp | tablet portrait (Pixel Tablet 800 dp), foldable open portrait, phone landscape | one pane; two stream columns (as today from 700 dp); detail screens in their wide form |
| Expanded | 840–1199 dp | small tablet landscape, foldable open landscape | **two panes**: list 360 dp, detail the rest |
| Large / XL | ≥ 1200 dp | Pixel Tablet landscape (1280 dp), desktop window | two panes: list 400 dp, detail up to 880 dp, rest as margin evenly around both |

Height: below 480 dp (phone landscape) the rail stays as today, panes do not open.

## Layout per screen

### List–detail (Expanded and wider)

`ListDetailPaneScaffold` from `material3-adaptive` (`adaptive-layout`, `adaptive-navigation`; the
`adaptive` 1.3.0 artifact is already in the build through the navigation suite).

- **List pane** (left): Latest, Radar, Saved, a rubric/topic/source list, search results. One column
  of compact cards. The card that is open in the detail pane is marked (tonal background,
  `selected` for TalkBack).
- **Detail pane** (right): whatever opens from the list – supernova, bundle, topic, source,
  logbook entry, weekly review, and (D-T1) the article itself. Inside the pane its own back stack:
  a topic chip in a supernova opens the topic in the same pane, back goes back in the pane.
- **Nothing selected**: the detail pane shows the **situation** (D-T2): burning supernovas and
  the logbook entry of today, the same parts as on top of Latest. On Radar: the followed topics with
  counts. On Saved: the export/bookmark service hint. Never an empty grey area.
- **Divider**: draggable between 320 and 520 dp list width, remembered per window class
  (`adaptive` 1.3 has the drag handle).
- **Growing / shrinking**: going from two panes to one keeps the open detail as a full screen
  (scaffold navigator `rememberSaveable`); going from one to two puts the list back on the left.

### Latest on Medium (tablet portrait)

One pane as today, two staggered columns. Cards open a full screen. The front page block gets the
lead card over both columns and the three side stories in a row of three, as on the website.

### Supernova (detail)

- From 600 dp pane width: **image left** (5 of 12 columns, at most 16:9 and at most 40 % of the
  window height), **head and reports right**. The first three reports are visible without scrolling.
- Compact supernovas and comets below as a two-column grid.
- Below 600 dp: as today.

### Logbook and weekly review

- Head box over the full width, image as a band of at most 220 dp height (cropped, not a
  16:9 block).
- Sections in **two columns** from 840 dp: supernovas | comets and releases, then
  "most read" | "new sources". Staggered, so short sections do not leave holes.
- Day/week switch (‹ 21.11.) moves into the top bar as two buttons; ← / → on a keyboard.

### Discover

- Tiles (Sky, Logbook, Weekly review, Sternstunden, Videos) as a **grid** of 3–5 per row.
- Rubrics as **cards** in 2–3 columns, each with its colour, count and 3 hot topics as chips.
- Topics and sources as two tiles at the end. With two panes: a rubric opens in the detail pane.

### Topic, source, bundle

- In a full pane ≥ 840 dp: the topic head (description, related, follow, support box) as a
  **side column** of 320 dp that stays in view; posts in two columns beside it.
- In the detail pane (narrower): head on top, one column, as today.

### Search

Two panes: search field + filters + results on the left, the opened result on the right.
Filters (kind of source, language) as chips under the field instead of a sheet.

### Settings

Two panes as Android's own settings: **sections left** (Radar, Supernovas, Logbook, Follows, Hidden,
Saved, Display, Language, Browser, Tracker protection, Sources, About, freiversum), the chosen section
right. Help, What is new and the bookmark service open in the right pane. Compact/Medium: one list as
today. Settings keep the rail on tablets (today they hide it), so the place never jumps.

### Dialogs and sheets

Bottom sheets (item menu, sponsor info, language) become **dialogs or menus at the pointer** on
Expanded+: the ⋮ menu as a dropdown, the sponsor info as a dialog of 560 dp.

## Article in the detail pane (D-T1)

Today an article opens by the browser mode: Custom Tab (default), the app's own browser
(`BrowserScreen`, WebView with tracker protection), or the chooser. A Custom Tab cannot be embedded.

Proposal: on Expanded+ an article opens **in the detail pane with the app's own browser**, whatever
the browser mode – a new switch *Artikel neben der Liste öffnen* / *Open articles beside the list*
(on by default, only shown on wide windows). Off: as today, full screen by the browser mode.

- Pane toolbar: source name and support link (as in `BrowserScreen`), save, star, share,
  *Im Browser öffnen*, *Vollbild* (pane over the list, Esc/back returns), close.
- `openExternal` sources and videos keep going outside, as today.
- Counting (`appVm.opened`) is the same call as now: one open = one count. The pane never opens an
  article by itself (see D-T2), so no count without a tap.
- JavaScript setting and tracker protection apply as in the full-screen browser.

## Keyboard and mouse

Shortcuts (Activity `onProvideKeyboardShortcuts`, so Meta + / shows them in Android's shortcut
helper; plus `onPreviewKeyEvent` on the scaffold):

| Key | Action |
|---|---|
| Ctrl + 1 … 4 | Latest, Radar, Discover, Saved |
| / or Ctrl + F | search |
| J / K or ↓ / ↑ | next / previous card in the list (focus + scroll) |
| Enter | open the focused card (in the pane) |
| O | open the focused article in the outside browser |
| B | save / unsave (bookmark) |
| S | star (Sternstunde), see D-T5 |
| Ctrl + R or F5 | refresh |
| Esc | close the detail pane / leave full pane |
| ← / → in logbook, weekly | previous / next day or week |
| Ctrl + , | settings |
| ? | the shortcut list as a dialog |

Mouse and touchpad:

- Hover: cards lift (tonal overlay), chips and icon buttons get their state layer; tooltips on
  icon buttons (`TooltipBox`).
- Right click / long press on a card: the item menu at the pointer.
- Middle click or Ctrl + click on a card: open in the outside browser.
- Scroll wheel in the stream, horizontal scroll on chip rows with Shift.
- Visible focus ring (2 dp, aurora) on every focusable part; Tab order list → pane toolbar → pane.

## Phases

| Phase | Content | Risk |
|---|---|---|
| T0 | wide forms of Supernova, Logbook, Weekly, Discover (built 2026-11-23); the `LayoutSize` helper and settings keeping the rail move to T1/T4 | low – no navigation change; fixes what the store images show |
| T1 | List–detail for Latest, Radar, Saved, search, rubric/topic/source lists; detail pane with its own back stack for supernova, bundle, topic, source, logbook, weekly; situation pane (D-T2) | medium – navigation; deep links and notifications must open the right pane |
| T2 | Article in the detail pane (D-T1), pane toolbar | medium – WebView in a pane, state on resize |
| T3 | Keyboard shortcuts, shortcut helper, hover, right-click menus, focus ring; menus/dialogs instead of sheets | low |
| T4 | Settings in two panes; topic side column; draggable divider; foldable posture (no content under the hinge) | low |
| T5 | Checks and new store images: Pixel Tablet portrait + landscape, Pixel Fold open/closed, split screen 50/50 and 1/3, phone landscape; TalkBack in both panes; dark + light, DE + EN, 130 % font | – |

Each phase is a release of its own; phones must look and behave unchanged after every phase
(check on the Pixel 10 and the Android 14 emulator).

## Notes on the code

- `AppNavigation.kt`: the `NavHost` stays the outer navigation (tabs, settings, setup). The
  list–detail screens get a scaffold navigator; the detail content is a small nested `NavHost` with
  its own routes (`NovaPane`, `BundlePane`, `TopicPane`, `SourcePane`, `LogbookPane`, `WeeklyPane`,
  `ArticlePane`), so `LocalItemActions` decides by `LayoutSize` whether to push a full screen or
  navigate the pane.
- Deep links (`planetfoss://logbook/{day}`, `…/radar`, notifications): on Expanded+ open the tab
  with the target in the pane.
- `ItemListScreen`: `columns` from the pane width as today (`BoxWithConstraints`), so the list pane
  gets one column automatically; the selected id comes in as a parameter.
- `ContentMaxWidth` stays for text columns; new `PaneMaxWidth = 880.dp` for the detail pane.
- Tests: Robolectric with `@Config(qualifiers = "w1280dp-h800dp")` and `"w800dp-h1280dp"` for the
  pane decisions; device checks as in T5.

## Decisions

Confirmed by Christoph on 2026-11-23: D-T1, D-T2 (as built in T1), D-T3, D-T4 and D-T5 as proposed below.

- **D-T1 Article beside the list:** in the detail pane with the app's own browser by default, switch to
  turn it off (proposal) – or always by the browser mode (Custom Tab over the whole screen).
- **D-T2 Detail pane with nothing selected:** the situation (burning supernovas + today's logbook
  entry) (proposal) – or open the first card by itself, as mail apps do. Against the second: it would
  count article opens nobody made and load a page on every start.
- **D-T3 Tablet portrait (800 dp):** one pane with two stream columns (proposal) – or two panes with
  a narrow list (320 dp list, 480 dp detail).
- **D-T4 Settings on tablets:** two panes and the rail stays visible (proposal) – or as today.
- **D-T5 Shortcut letters:** B (bookmark) = save, S (star/Stern) = star, as in the table (proposal) –
  or the Feedly letters (S = save, M = mark read)?
