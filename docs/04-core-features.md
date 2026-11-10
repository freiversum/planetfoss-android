# Phase 4 – Core features

Status: 2026-10-29. Builds on [03-skeleton.md](03-skeleton.md).

## Delivered

**Server (PlanetFOSS, branch `app-api`, commit `dfcef38`, not merged, not deployed)**

- Endpoints `rubrics`, `rubric/<slug>`, `topic/<key>`, `source/<slug>`, `videos`, `search?q=`
  ([02-api.md](02-api.md)), all on top of the website functions (`Site::listing`,
  `Site::search`, `Site::searchTopics`, `Site::trend`).
- `tests/AppApiTest.php` now 49 checks, all green.

**App**

| Feature | State |
|---|---|
| One list screen for Latest, rubric, topic, source, videos; cached per language, list name from the API head | done |
| Rubrics tab: rubrics in website order, hot topics as chips, entry for videos | done |
| Card: topic and rubric chips open their lists; bookmark; menu with share, copy link, “more from <source>” | done |
| Bundles: “+ N more sources” expands to the other reports | done |
| Search: debounced (350 ms, ≥ 2 characters), matching topics on top, local filters period / rubric / supernova topics | done |
| Saved: Room table, newest saved first, works offline, survives cache pruning | done |
| Supernova check: WorkManager, 15 / 30 / 60 / 180 min, network constraint, exponential backoff | done |
| First check after turning on is a silent baseline (what burns now is not announced) | done |
| Notification channel “Supernovas”, one notification per supernova, group summary from two on | done |
| Tap → `planetfoss://supernova/<key>` → topic list in the app | done |
| `POST_NOTIFICATIONS` asked only when the user turns notifications on (offer card on the Supernovas tab, switch in settings); blocked state shown with a link to Android's settings | done |
| “All reports” of a supernova opens the topic in the app (not the website) | done |
| Room v1 → v2 by auto-migration (list titles, saved items) | done, tested |

Deliberately not done: no full-text search on the phone (the server's search is used,
filters run over its at most 60 hits); no filter by source in search (a source's list is
one tap away from every card).

## Tests

- `:core` 21 tests (API client incl. lists, search, key validation; detector).
- `:app` 17 tests: repository (paging, titles, languages, errors), saved items and the
  1 → 2 migration (`MigrationTestHelper`, schemas as debug assets), navigation arguments
  → list targets (broken deep links → no list), and `NovaChecker` (off = no request,
  silent baseline, only new supernovas notify, repeated answers notify nothing, blocked
  notifications do not replay later, network errors store nothing).
- Lint: no findings (two `data` tag hints fixed).

## Checked on the Pixel 10

Against `tools/fixture-server.py`, which now also serves rubric/topic/source lists from the
website's own feeds and searches over what it loaded.

| Check | Result |
|---|---|
| Save a post, Saved tab shows it with filled bookmark | ok |
| Rubrics tab with all rubrics and the videos entry | ok |
| Rubric list with title, card menu (share, copy link, more from source) | ok |
| Source list via “more from …” | ok |
| Search “linux”: hits, filter chips (period, supernova topics, rubrics from the hits), period filter applied | ok |
| Turning notifications on: Android permission dialog, then switch on and interval choice | ok |
| First check right after turning on: silent baseline (GNOME 52 injected before: no notification) | ok, after the fix below |
| New supernova injected (`/_inject`), next check: exactly one notification “Supernova: Firefox 160” in channel “Supernovas” | ok |
| Tap on the notification opens the topic list in the app, the notification disappears | ok |
| Deep link `planetfoss://supernova/<key>` by intent: topic list, Supernovas tab marked, back goes to Supernovas | ok, after the fix below |
| Deep link with invalid key: “not found”, no retry button | ok, after the fix below |
| Saved tab without connection (adb reverse removed, app restarted) | ok |
| Turning notifications off cancels the scheduled work | ok (no job left in JobScheduler) |

Found and fixed during the device test:

- The periodic work starts only after one full interval, so the baseline came 30 minutes
  late and swallowed supernovas from that half hour. Now every scheduling call with
  notifications on also enqueues one immediate check (`supernova-check-now`, KEEP); this
  also gives a fresh check whenever the app starts.
- After a notification tap the Latest tab was marked and back led there; now the
  Supernovas tab is marked and back leads to it.
- “Not found” offered a pointless retry button.

Observed, not changed: a check within `max-age` of the previous answer is served from
OkHttp's cache (the API sends 120 s for supernovas), so a check sees data at most two
minutes old.

## Known gaps

- Fixture server: topic titles fall back to the key (the real API sends the label);
  `videos` is empty.
- Notifications were verified with the fixture server over `adb reverse`; a test over
  real background periods (Doze, hours) needs the API on the test environment.
