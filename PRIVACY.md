# Privacy

PlanetFOSS for Android has no account, no ad network, no analytics or crash-report
service and no Google services. This is everything that leaves the phone.

## planetfoss.net

- **Lists and supernovas** from the app API `/api/app/v1/`: the list, page, content
  language and app language. The app identifies itself as `PlanetFOSS-Android/<version>`.
  planetfoss.net counts these requests as daily totals per list, language and app version, like page views in the
  website's access log – no IP address is stored. Requests made in the background (supernova and follow checks) carry
  `X-PlanetFOSS-Background: 1` and are not counted; the first list request after the app comes to the front (at start or
  after 30 minutes away) carries `X-PlanetFOSS-Start: 1`, an approximation of visits; requests that only fill part of
  a screen carry `X-PlanetFOSS-Part: 1`, and one request per opening of Radar carries `X-PlanetFOSS-Screen: radar`, so
  Radar counts as one view. Nothing is stored on the phone for this, and nothing in these numbers ties two requests
  together.
- **The web server's log.** Like every web server, the one behind planetfoss.net writes an access log at the hosting
  provider: the IP address, the time, the requested address (for a search this includes the search words, for Radar
  and the follow check the topics and sources asked for) and the app's identifier. PlanetFOSS reads this log only for
  the website's visitor numbers and skips the app's requests there; it stores no IP address in its own database and
  builds no profile from the log. The provider keeps the log for a limited time (at the time of writing about three
  weeks of it existed). If you do not want your address in it, a VPN or Tor hides it like for any website.
- **Counting for the sources – opt-in, off by default** (Settings → *Sources* →
  *Count for the sources*). Only if you switch it on: when you open an article, the app sends its address, the area of
  the app (list, supernova, bundle) and the content language to `/api/app-open`; planetfoss.net adds it
  to the source's daily click total – no device identifier, no time of day, nothing that
  ties two opens together. Switched off, the app reports nothing about which articles you open.
- **Seen sponsor cards – always, no opt-in** (only while PlanetFOSS has ads switched on): when half of a sponsor card is
  on screen, the app reports the card's id, place and language to `/api/ad-view`, like the website counts ad views.
  planetfoss.net adds it to the card's daily total – no IP address stored, no device
  identifier, nothing that ties two views together.
- **Sponsor cards** (only while PlanetFOSS has ads switched on): the app asks `/api/app/v1/ads` for the cards of its two slots
  (a request like the one for a list: language of the app, nothing else, no identifier) and picks one on the phone – no profile, no
  choice by what you read. A tap opens
  `planetfoss.net/a/<id>` in your browser, which counts that click and forwards – as on the
  website.
- Images come from planetfoss.net; these requests are in the same access log.

## Sites you open

Articles open in your own browser (Custom Tab, default) or in the app's browser. The site
sees what it sees from any browser: IP address, user agent, and PlanetFOSS as referrer.
The app's browser keeps cookies and site data on the phone (Settings → *Delete browser
data*), has form autofill and Safe Browsing off and sends no WebView metrics.

## Only when you tap it

- **Stars for the Sternstunden:** tapping ☆ on a card fetches a small task from planetfoss.net,
  solves it on the phone and sends the article's id, the task and the solution. planetfoss.net
  answers with a random token that takes the star back; the token stays on the phone, the server
  keeps only its hash – no account, no identifier, as on the website.

## Optional, only while switched on

- **Supernova notifications:** periodic requests to the supernova list.
- **Following topics or sources:** periodic requests for page 1 of each followed topic or source – the same public
  lists the app shows. Which topics and sources you follow stays on the phone.
- **Radar tab:** page 1 of each followed topic or source when you open Radar – the same requests as the follow check.
  When you last opened the app (for the “New” markers) stays on the phone.
- **Widgets, only while placed on the home screen:** the logbook widget asks for the newest logbook entry, the Radar
  widget for page 1 of what you follow, at most every two hours and marked as background.
- **Your bookmark service, only while it is set up** (Settings → *Saved* → *Bookmark service*): every article you
  save from then on goes from the phone straight to the address you entered (Karakeep, linkding, Linkwarden or
  Readeck) – the article's own address, its title and the tags you chose, with your API token. planetfoss.net is not
  involved and never sees the token, the address of your service or what you save there. The token is kept on the
  phone, encrypted with a key in the Android Keystore, and is not part of the export file. HTTPS only. If the address
  points into your home network, Android 17 and later ask you first for the “Nearby devices”
  permission, which covers devices in the local network; the app uses it only for this service.
- **Tracker protection, only while it is on:** the EasyPrivacy filter list from easylist.to,
  fetched weekly. Requests are checked on the phone; no visited address is sent anywhere.

## On the phone

Cached lists, saved articles and settings stay in the app's storage. Android backup is off.
