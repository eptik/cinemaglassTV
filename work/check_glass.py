#!/usr/bin/env python3
"""Assert the settings panel is built from translucent glass drawables.

Checks that the layout and the shared styles reference the new glass materials, and that every fill
colour inside those drawables carries an alpha below 0x40, so the wallpaper reads through.
"""
import re
import sys

MAX_ALPHA = 0x40


FILL = re.compile(r'<(solid|gradient)\b[^>]*>', re.S)


def alphas(xml):
    """Alpha of fill colours only: a rim stroke is meant to be brighter than the glass it edges."""
    out = []
    for block in FILL.finditer(xml):
        for m in re.finditer(r'#[0-9A-Fa-f]{8}', block.group(0)):
            out.append(int(m.group(0)[1:3], 16))
    return out


def main(layout_path, styles_path, panel_path, tile_path):
    problems = []
    layout = open(layout_path, encoding="utf-8").read()
    styles = open(styles_path, encoding="utf-8").read()
    panel = open(panel_path, encoding="utf-8").read()
    tile = open(tile_path, encoding="utf-8").read()

    if "@drawable/glass_panel_bg" not in layout:
        problems.append("layout does not use @drawable/glass_panel_bg")
    if "@drawable/glass_tile_bg" not in styles:
        problems.append("ControlTile/ControlChoice styles do not use @drawable/glass_tile_bg")

    for name, xml in (("glass_panel_bg", panel), ("glass_tile_bg", tile)):
        a = alphas(xml)
        if not a:
            problems.append(f"{name} has no 8-digit colour with alpha")
        solid = [hex(v) for v in a if v > MAX_ALPHA]
        if solid:
            problems.append(f"{name} has opaque fills {solid} (alpha must be < 0x{MAX_ALPHA:02X})")
        if 'android:radius' not in xml and '<corners' not in xml:
            problems.append(f"{name} has no rounded corners")

    if problems:
        for p in problems:
            print("FAIL", p)
        return 1
    print("glass drawables referenced and translucent")
    print("GATE_GLASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:5]))
