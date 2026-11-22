#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Christoph Langner
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds the Play Store phone screenshots (DE + EN) from index.html:
#   raw/*.png  -> shots/*.jpg   (app icon in the status bar painted over)
#   index.html, index.html?lang=en -> five 1080 x 1920 PNGs per language in fastlane/…/phoneScreenshots/
#   preview-de.png, preview-en.png -> the row as the Play Store shows it (for checking)
# Needs chromium and python3-pillow.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=../..
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

python3 - <<'EOF'
from PIL import Image, ImageDraw
import glob, os
os.makedirs("shots", exist_ok=True)
for f in sorted(glob.glob("raw/*.png")):
    im = Image.open(f).convert("RGB")
    # the app's own status icon (✦) next to the clock: fill with the bar colour
    ImageDraw.Draw(im).rectangle((140, 30, 215, 110), fill=im.getpixel((330, 70)))
    im.save("shots/" + os.path.basename(f)[:-4] + ".jpg", quality=92)
EOF

for lang in de en; do
  chromium --headless=new --disable-gpu --hide-scrollbars --virtual-time-budget=3000 \
    --window-size=5400,1920 --screenshot="$TMP/strip-$lang.png" "file://$PWD/index.html?lang=$lang" 2>/dev/null
done

python3 - "$TMP" "$ROOT" <<'EOF'
import os, sys
from PIL import Image, ImageDraw
tmp, root = sys.argv[1:]
for lang, folder in (("de", "de-DE"), ("en", "en-US")):
    strip = Image.open(f"{tmp}/strip-{lang}.png").convert("RGB")
    out = f"{root}/fastlane/metadata/android/{folder}/images/phoneScreenshots"
    os.makedirs(out, exist_ok=True)
    for old in os.listdir(out):
        os.remove(os.path.join(out, old))
    slides = [strip.crop((i * 1080, 0, (i + 1) * 1080, 1920)) for i in range(5)]
    for i, s in enumerate(slides, 1):
        s.save(f"{out}/{i}.png", optimize=True)  # 24-bit PNG without alpha, as Play wants
    th, gap, pad = 600, 36, 60
    tw = 1080 * th // 1920
    row = Image.new("RGB", (pad * 2 + 5 * tw + 4 * gap, th + 2 * pad), "#1b1b1f")
    for i, s in enumerate(slides):
        mask = Image.new("L", (tw, th), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, tw - 1, th - 1), 32, fill=255)
        row.paste(s.resize((tw, th), Image.LANCZOS), (pad + i * (tw + gap), pad), mask)
    row.save(f"preview-{lang}.png")
    print(lang, "->", out)
EOF
