# Store screenshots

Source of the Google Play phone screenshots in `fastlane/metadata/android/{de-DE,en-US}/images/phoneScreenshots/`:
five 1080 x 1920 images (9:16, the size Play wants for large-format recommendations) that form one night-sky
panorama in the PlanetFOSS CI. Captions are short, one benefit per image, and take well under 20 % of the image.

| # | DE | EN | Screen |
|---|----|----|--------|
| 1 | Was die Linux-Welt gerade bewegt | What the Linux world is talking about | Supernovas |
| 2 | Über 200 Quellen in einem Strom | 200+ sources in one stream | Latest |
| 3 | Jeden Morgen dein Logbuch | Every morning, your logbook | Logbook |
| 4 | Ein Thema, alle Quellen | One topic, every source | Topic GNOME |
| 5 | Freie Software, hell oder dunkel | Free software, light or dark | Search (light), Latest (dark) |

## Build

```sh
docs/store-screenshots/build.sh
```

Renders `index.html` and `index.html?lang=en` with headless Chromium, cuts the five images per language, replaces
the PNGs in fastlane and writes `preview-{de,en}.png` (the row as the Play Store shows it, not in Git).

## New captures

1. `tools/store-proxy.py --feed <slugs> [--no-novas]` passes the live API through. Article images stay only for
   linuxundich.de, OMG! Ubuntu and LinuxNews; `--feed` limits Latest to the chosen sources, `--no-novas` opens Latest
   on the lead instead of the supernova. Used on 2026-11-22: DE
   `linuxnews,omg-ubuntu,linux-und-ich,linux-und-ich-2,its-foss,9to5linux,this-week-in-gnome`, EN the same without
   `linuxnews` and `linux-und-ich`.
2. Debug build with `-PPLANETFOSS_DEBUG_URL=http://localhost:8099/`, emulator `Pixel_10_API_37` (1080 x 2424) cold
   booted with `-no-snapshot` (a loaded snapshot keeps an old clock), `adb reverse tcp:8099 tcp:8099`, status bar in demo
   mode (10:00, full Wi-Fi and battery, no notifications). EN: `cmd locale set-app-locales net.planetfoss.app.debug
   --locales en-US` and content language English.
3. Save the screens as `raw/<lang>-<name>.png` (not in Git) and run `build.sh`; it paints the app's status icon over
   and writes `shots/*.jpg`, which `index.html` uses.

Fonts (Space Grotesk, JetBrains Mono, Inter) are the website's copies, SIL Open Font License; `bildmarke.svg` comes
from the website CI (`ci/system/out/`).
