#!/usr/bin/env python3
"""Assert the panel materials match einui's liquid-glass recipe.

einui builds glass as `bg-white/10` + `border-white/20` + `rounded-4xl` (32px) with a
`from-white/20 to-transparent` top highlight. This checks the native port keeps those alphas and radius.
"""
import re
import sys

WHITE_10 = (0x19, 0x1A)          # 10% white, tolerate 0x19/0x1A rounding
WHITE_20 = (0x33, 0x34)
RADIUS_MIN = 28                   # dp; einui uses 32px which maps to ~28-32dp here


def parse(path):
    return open(path, encoding="utf-8").read()


def radius(xml):
    m = re.search(r'android:radius="(\d+)dp"', xml)
    return int(m.group(1)) if m else None


def has_alpha(xml, rng):
    for m in re.finditer(r'#([0-9A-Fa-f]{2})[0-9A-Fa-f]{6}', xml):
        if int(m.group(1), 16) in rng:
            return True
    return False


def main(panel_path, tile_path):
    problems = []
    panel = parse(panel_path)
    tile = parse(tile_path)

    r = radius(panel)
    if r is None or r < RADIUS_MIN:
        problems.append(f"panel radius {r}dp is below the {RADIUS_MIN}dp glass radius")
    if not has_alpha(panel, WHITE_10):
        problems.append("panel has no white-10% fill (einui bg-white/10)")
    if not has_alpha(panel, WHITE_20):
        problems.append("panel has no white-20% rim or highlight (einui border-white/20)")
    if "gradient" not in panel:
        problems.append("panel has no vertical specular gradient")

    if "gradient" not in tile:
        problems.append("tile has no gradient fill")
    if "state_focused" not in tile:
        problems.append("tile has no focused state")
    if "state_selected" not in tile:
        problems.append("tile has no selected state")

    if problems:
        for p in problems:
            print("FAIL", p)
        return 1
    print(f"panel radius {r}dp, white-10 fill, white-20 rim/highlight, tile states present")
    print("GATE_EINUI")
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
