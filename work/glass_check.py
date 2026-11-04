#!/usr/bin/env python3
"""Measure the glass highlight rim against the focused tile's true bounds."""
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = "/Users/robingrodziski/Documents/Codex/2026-10-20/the-prompt-paste-this-into-claude"
PY, ADB = f"{ROOT}/work/adb-client/bin/python", f"{ROOT}/work/tv_adb.py"


def sh(cmd):
    return subprocess.run([PY, ADB, "192.168.1.6", "--", cmd], capture_output=True, text=True).stdout


def focused_bounds():
    sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    root = ET.fromstring(sh("cat /sdcard/ui.xml"))
    for node in root.iter("node"):
        if node.get("focused") == "true" and node.get("resource-id", "").endswith("/card"):
            b = node.get("bounds")
            nums = [int(n) for n in b.replace("[", ",").replace("]", ",").split(",") if n.strip()]
            return nums
    return None


def main():
    bounds = focused_bounds()
    print("focused card bounds:", bounds)
    if not bounds:
        return
    x0, y0, x1, y1 = bounds
    sh("screencap -p /sdcard/g.png && base64 /sdcard/g.png && rm /sdcard/g.png >/dev/null")
    subprocess.run([PY, f"{ROOT}/work/tv_adb.py", "--base64-out",
                    f"{ROOT}/work/shots/glass_check.png", "192.168.1.6",
                    "screencap -p /sdcard/g.png && base64 /sdcard/g.png && rm /sdcard/g.png"],
                   capture_output=True, text=True)
    im = Image.open(f"{ROOT}/work/shots/glass_check.png").convert("RGB")
    px = im.load()

    def row_brightness(y, xs, xe):
        return sum(sum(px[x, y]) / 3 for x in range(xs, xe)) / max(1, xe - xs)

    def col_brightness(x, ys, ye):
        return sum(sum(px[x, y]) / 3 for y in range(ys, ye)) / max(1, ye - ys)

    xm = (x0 + x1) // 2
    ym = (y0 + y1) // 2
    tops = [(row_brightness(y, x0 + 40, x1 - 40), y) for y in range(y0 - 6, y0 + 7)]
    bots = [(row_brightness(y, x0 + 40, x1 - 40), y) for y in range(y1 - 6, y1 + 7)]
    lefts = [(col_brightness(x, y0 + 30, y1 - 30), x) for x in range(x0 - 6, x0 + 7)]
    rights = [(col_brightness(x, y0 + 30, y1 - 30), x) for x in range(x1 - 6, x1 + 7)]
    print("tile box           top=%d bottom=%d left=%d right=%d" % (y0, y1, x0, x1))
    print("brightest rim      top=%d bottom=%d left=%d right=%d" % (
        max(tops)[1], max(bots)[1], max(lefts)[1], max(rights)[1]))
    print("deltas (rim - tile): top=%+d bottom=%+d left=%+d right=%+d" % (
        max(tops)[1] - y0, max(bots)[1] - y1, max(lefts)[1] - x0, max(rights)[1] - x1))


if __name__ == "__main__":
    main()