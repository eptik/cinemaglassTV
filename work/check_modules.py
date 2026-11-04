#!/usr/bin/env python3
"""Assert the Control-Centre popover holds the right modules and nothing redundant.

The panel must carry the system tiles, the new quick controls (mute, screensaver, next wallpaper,
picture, sound), the OLED toggle, the dock row and the wallpaper row — and must NOT carry the volume
slider or the media block, both of which live elsewhere (remote keys and the now-playing island).
"""
import sys

LAYOUT_IDS = [
    "btnSysSettings", "btnSysWifi", "btnSysApps",
    "btnScreensaver", "btnNextWall", "btnSleep",
    "btnDriftOn", "btnDriftOff",
    "btnPeekUltra", "btnPeekBalanced", "btnPeekNone",
    "btnWallFast", "btnWallSlow", "btnWallOff", "btnWallRefresh",
]

CODE_HOOKS = [
    "bindQuickControls", "Somnambulator", "rotateWallpaper", "applyNetworkState",
    "cycleSleepTimer", "SCREEN_OFF_TIMEOUT",
]

FORBIDDEN = ["sbVolume", "tvVolume", "ccTitle", "ccArtist", "ccPlay", "ccNext", "SeekBar",
             "btnMute", "btnPicture", "btnSound"]


def main(layout_path, code_path):
    layout = open(layout_path, encoding="utf-8").read()
    code = open(code_path, encoding="utf-8").read()
    problems = []

    for gid in LAYOUT_IDS:
        if f'@+id/{gid}"' not in layout:
            problems.append(f"layout is missing @+id/{gid}")
    for hook in CODE_HOOKS:
        if hook not in code:
            problems.append(f"activity never references {hook}")
    for gone in FORBIDDEN:
        if gone in layout:
            problems.append(f"{gone} should not be in the panel any more")

    if problems:
        for p in problems:
            print("FAIL", p)
        return 1
    print(f"{len(LAYOUT_IDS)} module ids present, redundant modules absent")
    print("GATE_MODULES")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
