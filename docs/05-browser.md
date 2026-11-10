# Phase 5 – Browser

Status: 2026-10-29. How articles open, and the security decisions of the internal browser.

## Three ways to open an article

| Mode (Settings → *Open articles*) | What happens | Protection |
|---|---|---|
| **Your browser (Custom Tab)** – default | Custom Tab of the default browser; system chooser if no browser supports Custom Tabs | the browser's own settings, logins, tracking protection |
| **Inside the app** | the app's WebView (`BrowserScreen`) | the rules below; the optional tracker protection (phase 6) works only here |
| **Ask every time** | Android's app chooser for the link | whatever app the user picks |

Every card's menu offers the other way (“open in browser” / “open in the app”), and the
internal browser's menu has *Open in browser*. Only `http(s)` links leave the app
(`UrlPolicy.isWebUrl`). The app's tracker protection never covers the external browser and is
never described as system-wide.

## Internal browser: what it does

Address line with page title, host and lock (warning sign for no TLS, a certificate error
or a page that had to fall back to http), progress bar, reload/stop, forward, share, copy
link, open in browser. The system back gesture walks the WebView history first, then
closes the browser and returns to the list it was opened from. The WebView state is saved
with the screen (`saveState`/`restoreState`), so history survives rotation.

The WebView gets `MATCH_PARENT` layout parameters. Compose's `AndroidView` defaults to
`WRAP_CONTENT`, which puts WebView into content-height mode: CSS `vh` is then 0, and pages
that limit media to a share of the viewport show empty boxes (golem.de: `max-block-size:
95vh` on every image frame – found 2026-10-30, all article images were missing).

Error page for: no connection, unknown host, timeout, certificate error, blocked link,
page without encryption, anything else. Retry only where retrying can help.

## Security decisions

| Topic | Decision | Where |
|---|---|---|
| JavaScript | on by default (most sources need it), switch in settings; **no** `addJavascriptInterface` ever | `applySecureSettings` |
| File / content access | `allowFileAccess = false`, `allowContentAccess = false` | `applySecureSettings` |
| Mixed content | `MIXED_CONTENT_NEVER_ALLOW` | `applySecureSettings` |
| Plain HTTP | the app permits no cleartext traffic (network security config); main-frame `http://` is upgraded to `https://`; if that fails or redirects back to http, an explanation and *Open in browser* | `BrowserClient`, `UrlPolicy.upgrade` |
| TLS errors | `onReceivedSslError` → `handler.cancel()`; no “proceed anyway” anywhere | `BrowserClient` |
| Certificates | system store only, no user CAs (network security config) | `network_security_config.xml` |
| Safe Browsing | off – it sends URL hashes to Google; also off app-wide by manifest meta-data, so not even the first page is checked | `applySecureSettings`, `AndroidManifest.xml` |
| WebView usage metrics | opted out by `android.webkit.WebView.MetricsOptOut` (Android System WebView would otherwise send metrics if the user agreed system-wide) | `AndroidManifest.xml` |
| Cookies | first-party on (logins on sources), **third-party off** | `applySecureSettings` |
| Form data | not saved | `applySecureSettings` |
| Pop-ups, new windows | `setSupportMultipleWindows(false)`, no automatic window opening; `target=_blank` opens in the same view | `applySecureSettings`, `BrowserChrome` |
| Camera, microphone, MIDI, protected media | `onPermissionRequest` → `deny()` | `BrowserChrome` |
| Location | `setGeolocationEnabled(false)` and every prompt answered “no” | `applySecureSettings`, `BrowserChrome` |
| File uploads | refused (`onShowFileChooser` returns no file) | `BrowserChrome` |
| Downloads | handed to the user's browser, never stored by the app | `BrowserScreen` |
| Links to other apps (`mailto:`, `tel:`, `market:` …) | only after a tap **and** a confirmation dialog; script redirects get nothing | `BrowserClient.handle`, `UrlPolicy` |
| `javascript:`, `file:`, `content:`, `data:`, `blob:`, `about:` (except blank), `chrome:`, `view-source:` | blocked | `UrlPolicy` |
| `intent:` links | never started as intents; only a `browser_fallback_url` that is http(s) is loaded | `UrlPolicy.intentFallback` |
| Renderer crash | caught (`onRenderProcessGone`), error page instead of an app crash | `BrowserClient` |
| Media autoplay | needs a user gesture | `applySecureSettings` |

## What the WebView stores, and what servers see

Stored locally by the WebView (in the app's data directory, never sent anywhere by the
app): cookies of visited sites, their localStorage/IndexedDB/service-worker caches, the
HTTP cache, HTTP-auth logins. No history is persisted beyond the open screen.
*Delete browser data* in the settings removes all of it (cookies, web storage, cache,
HTTP-auth logins, site permissions); cached lists and saved articles stay.

A visited site necessarily sees the IP address, the WebView's User-Agent, the usual
request headers and – if the page loads them – its own and its embedded third parties'
requests. The app adds nothing: no identifiers, no referrer from the app, no URL is
reported to PlanetFOSS or anyone else.

## Tests

- `UrlPolicyTest` (7): web links, blocked schemes, other apps only after asking, intent
  fallbacks (including `javascript:` and `file:` as fallback), http → https.
- `BrowserClientTest` (4): web links stay inside, other apps only with a tap, blocked links
  never load, browser mode and JavaScript setting persist.

## Checked on the Pixel 10 (Android 17)

| Check | Result |
|---|---|
| Real article (linuxiac.com) in the internal browser: title, lock, host, reload, menu | ok |
| `expired.badssl.com`, `self-signed.badssl.com`, `wrong.host.badssl.com`: not loaded, explanation, no retry, warning sign | ok |
| `http://neverssl.com/` (no HTTPS at all): “no encrypted connection” + *Open in browser* | ok, after fix |
| `http://http.badssl.com/` (https redirects back to http): same message instead of a blank page | ok, after fix (redirect loop) |
| History: link inside the page, system back returns to the previous page | ok |
| Link `mailto:` on debian.org/contact: dialog “Open another app?”, cancelled | ok |
| Mode “Ask every time”: with only one browser installed (Firefox Beta) Android opens it directly without a chooser – system behaviour | ok |
| Mode “Your browser”: Firefox Custom Tab, back returns to the app | ok |
| *Delete browser data*: cookies of visited sites 10 → 0, WebView HTTP cache emptied | ok |
| Cookie banners of visited sites | not touched during the tests |
| App language English, then back to “same as the phone” (not phase 5, tested alongside) | ok |

Found and fixed on the device: WebView metrics and Safe Browsing were only off per page, now off app-wide by manifest; plain-http pages failed with a vague error (cleartext is
off app-wide); https redirecting back to http looped into a blank page; the lock stayed
on error pages; the previous page's title stayed over an error page.

## Sources and their wishes (2026-10-30)

Each source in the API carries `support` and `open_external` (set in the PlanetFOSS admin
with the publisher). `open_external`: the article opens in the user's browser whatever mode
is chosen, and the card menu leaves out “open in the app”. `support`: a box at the top of
the source's list, a last entry “Support <source>” in every card menu and in the browser
menu; the page always opens in the user's browser (Custom Tab), where they are signed in.
With `mode: forward` the source recommends another project instead (2026-10-30): the box turns
aurora teal with a hand-and-heart, “Recommended by <source>” over “Support <project> instead”,
and the menu entry reads “Support <project>” with “Recommended by <source>” below.

