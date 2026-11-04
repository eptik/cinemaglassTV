#!/usr/bin/env python3
"""Measure how much of the now-playing pill's border is lit (progress outline)."""
import subprocess
import sys

from PIL import Image

ROOT = "/Users/robingrodziski/Documents/Codex/2026-10-20/the-prompt-paste-this-into-claude"
PY, ADB, HOST = f"{ROOT}/work/adb-client/bin/python", f"{ROOT}/work/tv_adb.py", "192.168.1.76"


def shot():
    subprocess.run([PY, ADB, "--base64-out", "/tmp/_pill.png", HOST,
                    "screencap -p /sdcard/p.png && base64 /sdcard/p.png"], capture_output=True)
    return Image.open("/tmp/_pill.png").convert("RGB")


def bounds():
    subprocess.run([PY, ADB, HOST, "--", "uiautomator dump /sdcard/u.xml >/dev/null 2>&1"], capture_output=True)
    xml = subprocess.run([PY, ADB, HOST, "--", "cat /sdcard/u.xml"], capture_output=True, text=True).stdout
    import xml.etree.ElementTree as ET
    root = ET.fromstring(xml)
    for n in root.iter("node"):
        if n.get("resource-id", "").endswith("/npRoot"):
            return [int(v) for v in n.get("bounds").replace("[", ",").replace("]", ",").split(",") if v.strip()]
    return None


def main():
    b = bounds()
    if not b:
        print("no pill on screen")
        return
    x0, y0, x1, y1 = b
    img = shot()
    px = img.load()
    lit = faint = 0
    # walk the top and bottom edges plus both sides, 2 px inside the border
    points = []
    for x in range(x0 + 8, x1 - 8, 2):
        points.append((x, y0 + 2))
        points.append((x, y1 - 3))
    for y in range(y0 + 8, y1 - 8, 2):
        points.append((x0 + 2, y))
        points.append((x1 - 3, y))
    for (x, y) in points:
        r, g, bl = px[x, y]
        luma = (r + g + bl) / 3
        if luma > 150:
            lit += 1
        elif luma > 60:
            faint += 1
    total = len(points)
    print(f"pill {b}  lit={lit/total*100:.0f}%  faint={faint/total*100:.0f}%  sampled={total}")


if __name__ == "__main__":
    main()