#!/usr/bin/env python3
"""Assert the now-playing pill sits on the panel's centre line.

Reads the launcher's own geometry log (`IslandGeom: bar=<w> left=<l> right=<r>`) and checks that the
pill's padded band is symmetric and centred on 1920 px. uiautomator cannot be used here: the OLED drift
tick keeps the window from ever reporting idle.
"""
import re
import sys

SCREEN_W = 1920
TOL = 20


def main(path):
    text = open(path, encoding="utf-8", errors="replace").read()
    matches = re.findall(r"bar=(\d+) left=(\d+) right=(\d+) host=(\d+) hostw=(\d+)", text)
    if not matches:
        print("FAIL no IslandGeom line in log (launcher not resumed since install?)")
        return 1
    bar, left, right, host, hostw = map(int, matches[-1])
    centre = host + hostw / 2
    delta = centre - SCREEN_W / 2
    print(f"bar={bar} padding {left}/{right} host x={host} w={hostw} -> pill centre {centre:.1f} "
          f"(screen {SCREEN_W/2}) delta {delta:+.1f}")
    if abs(left - right) > 2:
        print(f"FAIL asymmetric padding: left {left} vs right {right}")
        return 1
    if hostw <= 0:
        print("FAIL degenerate pill width")
        return 1
    if abs(delta) > TOL:
        print(f"FAIL off centre by {delta:+.1f}px (tolerance {TOL})")
        return 1
    print("GATE_CENTRED")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
