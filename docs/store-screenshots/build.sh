#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Christoph Langner
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds the Play Store screenshots (DE + EN):
#   raw/*.png -> shots/*.jpg  (phone: app icon in the status bar painted over; tablet: status bar cut off)
#   index.html  (?lang=en) -> five 1080 x 1920 PNGs per language in fastlane/…/phoneScreenshots/
#   tablet.html (?lang=en) -> five 2560 x 1440 PNGs per language in fastlane/…/tenInchScreenshots/
#   preview-{phone,tablet}-{de,en}.png -> the row as the Play Store shows it (for checking)
# Needs chromium and python3-pillow.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=../..
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

python3 - <<'PY'
from PIL import Image, ImageDraw
import glob, os
os.makedirs("shots", exist_ok=True)
for f in sorted(glob.glob("raw/*.png")):
    im = Image.open(f).convert("RGB")
    if os.path.basename(f).startswith("tab-"):
        # tablet: no status bar at all (its clock is white on the light rail in some views)
        im = im.crop((0, 52, im.width, im.height))
    else:
        # phone: the app's own status icon (✦) next to the clock, filled with the bar colour
        ImageDraw.Draw(im).rectangle((140, 30, 215, 110), fill=im.getpixel((330, 70)))
    im.save("shots/" + os.path.basename(f)[:-4] + ".jpg", quality=92)
PY

shot() {  # page, width, height, output
  chromium --headless=new --disable-gpu --hide-scrollbars --virtual-time-budget=3000 \
    --window-size="$2,$3" --screenshot="$4" "file://$PWD/$1" 2>/dev/null
}
for lang in de en; do
  shot "index.html?lang=$lang" 5400 1920 "$TMP/phone-$lang.png"
  shot "tablet.html?lang=$lang" 12800 1440 "$TMP/tablet-$lang.png"
done

python3 - "$TMP" "$ROOT" <<'PY'
import os, sys
from PIL import Image, ImageDraw
tmp, root = sys.argv[1:]
KINDS = (("phone", "phoneScreenshots", 1080, 1920, 600), ("tablet", "tenInchScreenshots", 2560, 1440, 420))
for kind, target, w, h, th in KINDS:
    for lang, folder in (("de", "de-DE"), ("en", "en-US")):
        strip = Image.open(f"{tmp}/{kind}-{lang}.png").convert("RGB")
        out = f"{root}/fastlane/metadata/android/{folder}/images/{target}"
        os.makedirs(out, exist_ok=True)
        for old in os.listdir(out):
            os.remove(os.path.join(out, old))
        slides = [strip.crop((i * w, 0, (i + 1) * w, h)) for i in range(5)]
        for i, s in enumerate(slides, 1):
            s.save(f"{out}/{i}.png", optimize=True)  # 24-bit PNG without alpha, as Play wants
        gap, pad = 36, 60
        tw = w * th // h
        row = Image.new("RGB", (pad * 2 + 5 * tw + 4 * gap, th + 2 * pad), "#1b1b1f")
        for i, s in enumerate(slides):
            mask = Image.new("L", (tw, th), 0)
            ImageDraw.Draw(mask).rounded_rectangle((0, 0, tw - 1, th - 1), 32, fill=255)
            row.paste(s.resize((tw, th), Image.LANCZOS), (pad + i * (tw + gap), pad), mask)
        row.save(f"preview-{kind}-{lang}.png")
        print(kind, lang, "->", out)
PY
