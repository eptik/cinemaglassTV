#!/usr/bin/env python3
"""Port react-ios-icons glyphs into Android VectorDrawables.

The pack is React components (MIT, (c) 2025 Walidoux) drawing SVG paths on a 30x30 grid. Android cannot
run React, so this pulls the path data out of each component and emits the equivalent vector XML.
"""
import re
import sys
import pathlib

SRC = pathlib.Path("/tmp/react-ios-icons/src/icons")
OUT = pathlib.Path(sys.argv[1])
PACK = ["Wifi", "Speaker", "Play", "Video", "Folder", "Gear", "Battery", "LightBulb",
        "XMark", "Loop", "Chevron", "Cellular", "Microphone", "Globe"]

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- iOS glyph from react-ios-icons (MIT, (c) 2025 Walidoux), ported to a VectorDrawable. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="30dp"
    android:height="30dp"
    android:viewportWidth="30"
    android:viewportHeight="30">
{paths}
</vector>
"""


def vector(name):
    text = (SRC / f"{name}.tsx").read_text(encoding="utf-8")
    paths = []
    for m in re.finditer(r"<path\b(.*?)/>", text, re.S):
        block = m.group(1)
        # `d` is often a ternary of two path strings; take the first one that starts with a move command
        # ternary renders `filled ? 'solid' : 'outline'`; the outline (last) is the cleaner glyph
        cands = re.findall(r"['\"](M[^'\"]{20,})['\"]", block)
        if not cands:
            continue
        d = cands[-1]
        opacity = re.search(r"opacity=\{([^}]*)\}", block)
        alpha = ""
        if opacity:
            alpha = '\n        android:fillAlpha="0.35"' if "0.2" in opacity.group(1) else ""
        paths.append(
            '    <path\n'
            f'        android:fillColor="#FFFFFFFF"{alpha}\n'
            f'        android:pathData="{d.strip()}" />'
        )
    if not paths:
        return None
    return TEMPLATE.format(paths="\n".join(paths))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    made = []
    for name in PACK:
        xml = vector(name)
        if not xml:
            print("skip", name)
            continue
        slug = "ios_" + re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
        (OUT / f"{slug}.xml").write_text(xml, encoding="utf-8")
        made.append(f"{slug} <- {name} ({xml.count('<path')} paths)")
    print("\n".join(made))
    print(f"{len(made)} glyphs")


if __name__ == "__main__":
    main()
