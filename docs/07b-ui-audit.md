# UI/UX audit – design, spacing, dialogs

Status: 2026-10-30. A pass over every screen, menu and dialog for brand fit, spacing, hierarchy and
current Android patterns, with the changes made and what is left. Checked on the emulator
`Pixel_10_API_37` (Android 17, GPU acceleration) in light and dark mode, at 100 % and 160 % font,
portrait and landscape.

## Inventory

| Area | Contents |
|---|---|
| Tabs | Latest, Supernovas, Rubrics, Saved (short navigation bar; rail on wide or low windows) |
| Pushed screens | list (rubric, topic, source, videos), search, settings, internal browser |
| Menus | content language (top bar), card menu, browser menu |
| Dialogs | “open in another app” (browser), choice dialogs in the settings (new) |
| Inline notices | notification offer (Supernovas), support box (source list), error banner, empty/loading states |

Stack: Compose BOM 2026.09.00, Material 3 1.5.0-beta02 (Expressive), Navigation 2.10.2,
Activity 1.13.0, Lifecycle 2.11.0 – the newest stable (or chosen beta) releases on Google Maven
on 2026-10-30, so no dependency was changed. Edge-to-edge and predictive back (targetSdk 37)
were already in place.

## Findings and changes

| # | Finding | Change | State |
|---|---|---|---|
| 1 | The app used the system font; the website's identity lives in Space Grotesk, Inter and JetBrains Mono | the website's font subsets (SIL OFL 1.1) as app fonts: headings and titles bold Space Grotesk, text and controls Inter, meta lines (source, time, numbers) JetBrains Mono as `.meta` on the website; `ui/theme/Type.kt` | done |
| 2 | Selected tab and selected filter chips in teal (Material's `secondary`), everything else in the PlanetFOSS violet | `secondary` = accent, `secondaryContainer` = the website's `--accent-soft` (#ECE8FF / #2A2363); dark surface-2 and the light error colour taken from site.css | done |
| 3 | Every post as a card with a full-width 16:9 image – 1.5 posts per phone screen | the first post of a feed keeps the large image (lead); all others are compact with a 4:3 thumbnail beside the title and a two-line teaser, like the website's compact rows (`.cd.kr`) – 3.5 posts per screen. Search hits and saved posts are all compact | done |
| 4 | Loose spacing values (6, 10, 14, 18 dp) and four corner radii for comparable cards | one 4 dp scale (`Space`) and the website's radii (`Radius`, cards 16 dp) in `ui/theme/Dimens.kt`; all cards (post, supernova, comet, sponsor, support, notification offer) use 16 dp padding and the same radius; chips start on the text edge | done |
| 5 | Search: the period buttons cut “24 Stun…” and, once selected, “Jederz…” | “24 h” (TalkBack says “24 hours”), buttons as wide as their labels, the row scrolls at large font sizes | done |
| 6 | Card and browser menus: only some entries had an icon, the texts did not line up | icons for every entry (link, open in new, article: Material Icons, Apache-2.0) | done |
| 7 | Settings were four screens long: four radio lists (app language, content language, opening mode, interval), notifications split from supernovas, language settings apart | single choices are one row with the current value and a Material dialog with radio buttons (`ChoiceItem`, picking applies and closes, *Cancel* keeps everything); order: Supernovas (comets, notifications, interval) → Language → Open articles → Trackers → Sources → About; about split into version, privacy, licence; width capped at 720 dp; `imePadding` keeps the exception field above the keyboard. Now 2.5 screens | done |
| 8 | Rubrics: names only, the website colours its rubrics | the website's rubric colour as a dot on the name's line; hot topic chips start under the name | done |
| 9 | Phone in landscape: top bar plus bottom bar left room for one card | collapsed wide navigation rail whenever the window is low (height < 480 dp) | done |
| 10 | Empty states were plain text | saved and search intro show a quiet symbol above the text | done |
| 11 | Lists jumped when a post was unsaved or arrived on refresh | `animateItem()` on post cards (follows the system's animation scale) | done |
| 12 | Pixel 10 (font 115 %): beside the thumbnail the meta line lost the time (“GamingOnLinux, vor 1 St…”) | the meta line spans the card, the thumbnail sits beside the title only | done |
| 13 | Text buttons in the blocking section (“update lists now”) stood 4 dp right of the text edge | start padding so their label lines up with the list texts | done |

## Videos (2026-10-30)

Checked in the emulator (Android 17, YouTube app and Chrome installed) with the Videos list:
- **Custom Tab setting:** a video now goes to the app Android has for the link – the YouTube app
  there; on the Pixel 10 Android offers NewPipe Enhanced, Morphe and Firefox Beta (no default
  set). A Custom Tab of Chrome hands YouTube links to the YouTube app by itself anyway.
- **App's browser:** m.youtube.com plays (muted start, subtitles). Full screen did nothing before
  (no `onShowCustomView`); now the video fills the screen without system bars, back leaves it.
- **Chrome** without the YouTube app: the Custom Tab shows YouTube's consent page, after
  *Reject all* the video plays.
- Not checked: Firefox playing a video (only on the Pixel 10, Christoph's own browser).

## Kept as it was

- Supernova and comet cards: already in the website's style; only padding, radii and the mono font
  were aligned.
- Top bar with content language (DE + EN) and settings; notification offer card; error banner;
  pull-to-refresh with the Expressive loading indicator; the “open in another app” dialog.
- No dynamic colour: the app should look like PlanetFOSS, not like the wallpaper.
- Clearing browser data stays without a confirmation dialog: it touches only the app's own
  browser, and the row says that it was cleared.
- There is no article detail screen: articles open in the browser of choice (section 9 of the
  audit brief applies to the internal browser, which shows the original page).

## Checks

- `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug` – 96 tests green, lint without findings.
- Emulator, light and dark: Latest, Supernovas (dark), Rubrics, search with filters, card menu,
  settings (all sections), both choice dialogs, landscape with rail, 160 % font – no cut text
  except titles that are meant to end with an ellipsis, no overlaps.
- Pixel 10 (Android 17, font 115 %, light): Latest, Supernovas with comet, Rubrics, search, settings,
  interval dialog (closed with *Cancel*, setting unchanged) – findings 12 and 13 fixed and checked again.
- APK: the five font files add about 370 KB.

## Open

- [ ] TalkBack walk-through by ear (already in the backlog) – now including the choice dialogs.
- [x] Tablet: two staggered columns from 700 dp list width, together at most 1 200 dp (also phones in landscape beside the rail).
- [x] Wordmark in the top bar of Latest (CC BY-SA 4.0 since 2026-10-30), light and dark variant from planetfoss.net.
