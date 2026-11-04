#!/usr/bin/env python3
"""Reject wallpapers that cannot look cinematic on a 16:9 panel.

Two failure modes this catches:
  * portrait / near-square art => wrong aspect
  * portrait art pasted onto a 16:9 canvas with black bars => pillarboxing
Both look like "cheap fan art squeezed into a frame" on the TV.
"""
import sys, os
from PIL import Image, ImageStat

MIN_RATIO, MAX_RATIO = 1.72, 1.83


def bar_frac(im, side):
    """Width fraction of a uniform dark column hugging the left/right edge."""
    w, h = im.size
    probe = max(1, int(w * 0.18))
    box = (0, 0, probe, h) if side == "left" else (w - probe, 0, w, h)
    col = im.crop(box).convert("L").resize((probe, 40))
    px = col.load()
    uniform = 0
    for x in range(col.size[0]):
        vals = [px[x, y] for y in range(col.size[1])]
        if max(vals) < 26 and (max(vals) - min(vals)) < 12:
            uniform += 1
        else:
            break
    return uniform / w


def verdict(path):
    try:
        im = Image.open(path)
        im.load()
    except Exception as e:
        return None, f"unreadable ({e})"
    w, h = im.size
    ratio = w / h
    if w < 3400:
        return None, f"too small {w}x{h}"
    if not (MIN_RATIO <= ratio <= MAX_RATIO):
        return None, f"aspect {ratio:.2f} ({w}x{h})"
    lb, rb = bar_frac(im, "left"), bar_frac(im, "right")
    if lb > 0.06 or rb > 0.06:
        return None, f"pillarboxed L={lb:.2f} R={rb:.2f}"
    stat = ImageStat.Stat(im.convert("L").resize((320, 180)))
    return (round(stat.mean[0], 1), w, h, round(ratio, 2)), "ok"


def main():
    root = sys.argv[1]
    bad = []
    for name in sorted(os.listdir(root)):
        if not name.lower().endswith((".jpg", ".jpeg", ".png")):
            continue
        p = os.path.join(root, name)
        info, why = verdict(p)
        if info is None:
            print(f"REJECT {name:38} {why}")
            bad.append(name)
        else:
            print(f"ok     {name:38} {info[1]}x{info[2]} lum={info[0]}")
    print(f"\n{len(bad)} rejected of {len([n for n in os.listdir(root) if n.lower().endswith(('.jpg','.jpeg','.png'))])}")


if __name__ == "__main__":
    main()
