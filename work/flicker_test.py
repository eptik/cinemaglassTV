#!/usr/bin/env python3
"""Press play/pause and check the island's artwork does not swap to a different image."""
import subprocess
import time
import xml.etree.ElementTree as ET
from io import BytesIO

from PIL import Image, ImageChops

ROOT = "/Users/robingrodziski/Documents/Codex/2026-10-20/the-prompt-paste-this-into-claude"
PY, ADB = f"{ROOT}/work/adb-client/bin/python", f"{ROOT}/work/tv_adb.py"


def sh(cmd):
    return subprocess.run([PY, ADB, "192.168.1.6", "--", cmd], capture_output=True, text=True).stdout


def shot():
    out = subprocess.run([PY, ADB, "--base64-out", "/tmp/_s.png", "192.168.1.6",
                          "screencap -p /sdcard/s.png && base64 /sdcard/s.png && rm /sdcard/s.png"],
                         capture_output=True, text=True)
    return Image.open("/tmp/_s.png").convert("RGB")


def art_bounds():
    sh("uiautomator dump /sdcard/u.xml >/dev/null 2>&1")
    root = ET.fromstring(sh("cat /sdcard/u.xml"))
    for n in root.iter("node"):
        if n.get("resource-id", "").endswith("/npArt"):
            b = n.get("bounds")
            return [int(v) for v in b.replace("[", ",").replace("]", ",").split(",") if v.strip()]
    return None


def main():
    b = art_bounds()
    if not b:
        print("no npArt on screen — is something playing?")
        return
    x0, y0, x1, y1 = b
    print("art bounds", b)
    samples = []
    for i in range(4):
        sh("input keyevent KEYCODE_DPAD_CENTER")
        time.sleep(0.25)
        img = shot().crop((x0, y0, x1, y1))
        samples.append(img)
        time.sleep(0.6)
    base = samples[0]
    for i, s in enumerate(samples[1:], 1):
        diff = ImageChops.difference(base, s)
        mean = sum(sum(p) for p in diff.getdata()) / (diff.size[0] * diff.size[1] * 3)
        print(f"sample {i}: mean pixel difference vs first = {mean:.2f} (0 = identical artwork)")


if __name__ == "__main__":
    main()