#!/usr/bin/env python3
"""Drive the TV with injected D-pad keys and report the focused node after each step."""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TV = ROOT / "work/adb-client/bin/python"
ADB = ROOT / "work/tv_adb.py"
HOST = "192.168.1.6"


def sh(cmd):
    out = subprocess.run([str(TV), str(ADB), HOST, "--", cmd], capture_output=True, text=True)
    return out.stdout


def dump():
    sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    xml = sh("cat /sdcard/ui.xml")
    return xml


def focused(xml):
    if not xml.strip().startswith("<?xml"):
        return "dump-failed"
    root = ET.fromstring(xml)
    for node in root.iter("node"):
        if node.get("focused") == "true":
            label = node.get("text") or node.get("content-desc") or node.get("resource-id") or ""
            if not label:
                # look at descendant/child text
                texts = [n.get("text") for n in node.iter("node") if n.get("text")]
                label = "|".join(t for t in texts if t)
            return f"{node.get('class').split('.')[-1]} '{label}' {node.get('bounds')}"
    return "none"


def main():
    for arg in sys.argv[1:]:
        if arg.startswith("key:"):
            sh(f"input keyevent {arg[4:]}")
            import time
            time.sleep(0.9)
            print(f"{arg[4:]:24s} -> {focused(dump())}")
        else:
            print(f"note: {arg}")


if __name__ == "__main__":
    main()