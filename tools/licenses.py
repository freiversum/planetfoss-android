#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Christoph Langner
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Licence report of everything that ends up in the release APK.

    tools/licenses.py > /tmp/deps.md       (needs a finished Gradle dependency resolution)

Runs `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, takes every
resolved group:artifact:version, reads its POM from the Gradle cache (following <parent>
POMs when the licence is inherited) and prints a Markdown table. Exits with 1 if a
dependency has no licence or one that is not on the free list below.
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
NS = "{http://maven.apache.org/POM/4.0.0}"

# licence names/URLs as they appear in POMs → SPDX id
FREE = [
    (r"apache.*2|apache-2|www\.apache\.org/licenses/license-2", "Apache-2.0"),
    (r"\bmit\b|opensource\.org/licenses/mit", "MIT"),
    (r"bsd.?3|new bsd|bsd-3|revised bsd", "BSD-3-Clause"),
    (r"bsd.?2|simplified bsd|freebsd", "BSD-2-Clause"),
    (r"eclipse public license.*2|epl-2", "EPL-2.0"),
    (r"lgpl|lesser general public", "LGPL"),
    (r"gnu general public license|gpl", "GPL"),
    (r"public domain|cc0", "CC0/Public domain"),
    (r"mozilla public license|mpl", "MPL-2.0"),
]


# POMs without a <licenses> element, checked by hand
KNOWN = {
    "javax.inject:javax.inject": "Apache-2.0",  # JSR-330, github.com/javax-inject/javax-inject, LICENSE = Apache 2.0
}


def resolve():
    out = subprocess.run(
        ["./gradlew", "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout
    deps = set()
    for line in out.splitlines():
        m = re.search(r"--- ([\w.\-]+):([\w.\-]+):([\w.\-]+)(?: -> ([\w.\-]+))?", line)
        if m and not line.rstrip().endswith("(n)"):
            g, a, v, v2 = m.groups()
            deps.add((g, a, v2 or v))
        m = re.search(r"--- ([\w.\-]+):([\w.\-]+) -> ([\w.\-]+)", line)  # BOM-managed: group:artifact -> version
        if m:
            deps.add(m.groups())
    return sorted(deps)


def pom(g, a, v):
    d = CACHE / g / a / v
    for p in d.glob("*/*.pom") if d.exists() else []:
        return p
    return None


def licences(g, a, v, depth=0):
    p = pom(g, a, v)
    if p is None or depth > 5:
        return [], None
    root = ET.parse(p).getroot()
    found = []
    for lic in root.iter(f"{NS}license"):
        name = (lic.findtext(f"{NS}name") or "").strip()
        url = (lic.findtext(f"{NS}url") or "").strip()
        found.append((name, url))
    url = (root.findtext(f"{NS}url") or "").strip() or None
    if not found:
        parent = root.find(f"{NS}parent")
        if parent is not None:
            pg, pa, pv = (parent.findtext(f"{NS}{k}") for k in ("groupId", "artifactId", "version"))
            found, purl = licences(pg, pa, pv, depth + 1)
            url = url or purl
    return found, url


def spdx(found):
    ids = []
    for name, url in found:
        text = f"{name} {url}".lower()
        ids.append(next((sid for rx, sid in FREE if re.search(rx, text)), None) or f"UNKNOWN ({name or url})")
    return " / ".join(dict.fromkeys(ids)) if ids else "NONE"


def main():
    bad = []
    rows = []
    for g, a, v in resolve():
        found, url = licences(g, a, v)
        lic = KNOWN.get(f"{g}:{a}") or spdx(found)
        if lic == "NONE" or "UNKNOWN" in lic:
            bad.append(f"{g}:{a}:{v} – {lic}")
        rows.append(f"| `{g}:{a}` | {v} | {lic} |")
    print("| Library | Version | Licence |\n|---|---|---|")
    print("\n".join(rows))
    if bad:
        print("\nNeeds a look:\n" + "\n".join(f"- {b}" for b in bad), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    os.chdir(ROOT)
    main()
