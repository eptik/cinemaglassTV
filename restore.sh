#!/usr/bin/env bash
# Undo everything this project changed on the TV.
#
#   TV_HOST=192.168.1.6 ./restore.sh            # re-enable packages, stock launcher becomes home
#   TV_HOST=192.168.1.6 ./restore.sh --uninstall-launcher   # also remove the custom launcher
#
# Safe to run twice.
set -uo pipefail
cd "$(dirname "$0")"

TV_HOST="${TV_HOST:?set TV_HOST to the TV IP, e.g. TV_HOST=192.168.1.6 $0}"
PY="$PWD/work/adb-client/bin/python"
ADB="$PWD/work/tv_adb.py"
PKG=com.gathin.tvhome
STOCK_LAUNCHER=com.google.android.tvlauncher

[ -x "$PY" ] || { echo "missing $PY"; exit 1; }

echo "==> re-enabling packages"
# Every package in work/disabled-packages.txt, one command per shell call (pm aborts chains).
while read -r pkg; do
  [ -z "$pkg" ] && continue
  printf '  %-42s ' "$pkg"
  "$PY" "$ADB" "$TV_HOST" -- "pm enable --user 0 $pkg" | tr -d '\r'
done < work/disabled-packages.txt

echo "==> restoring the stock home screen"
"$PY" "$ADB" "$TV_HOST" -- "cmd package set-home-activity $STOCK_LAUNCHER/com.google.android.tvlauncher.MainActivity" || true

echo "==> restoring network + locale settings touched by the project"
"$PY" "$ADB" "$TV_HOST" -- "settings put global private_dns_mode opportunistic"
"$PY" "$ADB" "$TV_HOST" -- "settings delete global private_dns_specifier" || true

if [ "${1:-}" = "--uninstall-launcher" ]; then
  echo "==> removing the custom launcher"
  "$PY" "$ADB" "$TV_HOST" -- "settings put secure enabled_notification_listeners \
    $( "$PY" "$ADB" "$TV_HOST" -- 'settings get secure enabled_notification_listeners' \
        | tr -d '\r' | sed "s#:$PKG/$PKG.NowPlayingService##" )" || true
  "$PY" "$ADB" "$TV_HOST" -- "pm uninstall $PKG"
fi

"$PY" "$ADB" "$TV_HOST" -- "input keyevent KEYCODE_HOME"
echo "Done. Reboot the TV if the old launcher does not come back immediately."