#!/usr/bin/env python3
"""Print the focused node, and whether the now-playing controls are visible.

Host: $TV_HOST, else the first of the known addresses that answers on 5555 (the TV has both an
Ethernet and a Wi-Fi address, and only one of them is always up).
"""
import os
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PY = f"{ROOT}/work/adb-client/bin/python"
ADB = f"{ROOT}/work/tv_adb.py"
CANDIDATES = ["192.168.1.76", "192.168.1.6"]


def host():
    if os.environ.get("TV_HOST"):
        return os.environ["TV_HOST"]
    for ip in CANDIDATES:
        with socket.socket() as s:
            s.settimeout(1.5)
            try:
                s.connect((ip, 5555))
                return ip
            except OSError:
                continue
    return CANDIDATES[0]


HOST = host()


def sh(cmd):
    return subprocess.run([PY, ADB, HOST, "--", cmd],
                          capture_output=True, text=True).stdout


def dump_tree(attempts=3):
    for _ in range(attempts):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
        xml = sh("cat /sdcard/ui.xml")
        if xml.strip().startswith("<?xml"):
            try:
                return ET.fromstring(xml)
            except ET.ParseError:
                pass
        time.sleep(0.8)          # a dropped link or a slow dump; retry
    return None


def report(tag=""):
    root = dump_tree()
    if root is None:
        print(f"{tag:12s} focus=? | uiautomator dump unavailable (host {HOST})")
        return
    focused = "none"
    controls = "missing"
    for node in root.iter("node"):
        rid = node.get("resource-id", "")
        if node.get("focused") == "true":
            label = node.get("text") or node.get("content-desc") or rid.split("/")[-1]
            focused = f"{rid.split('/')[-1]} '{label}' {node.get('bounds')}"
        if rid.endswith("npControls"):
            controls = f"visible={node.get('bounds')}"
    print(f"{tag:12s} focus={focused} | controls {controls}")


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        if arg.startswith("key:"):
            sh(f"input keyevent {arg[4:]}")
            time.sleep(0.9)
            report(arg[4:])
        elif arg == "report":
            report("now")
