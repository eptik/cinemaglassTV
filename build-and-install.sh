#!/usr/bin/env bash
# Build the launcher and install it on the TV as the default home screen.
#
#   TV_HOST=192.168.1.6 ./build-and-install.sh
#
# Uses the Python adb-shell client in work/ (macOS Local Network privacy blocks the stock adb binary).
set -euo pipefail
cd "$(dirname "$0")"

TV_HOST="${TV_HOST:?set TV_HOST to the TV IP, e.g. TV_HOST=192.168.1.6 $0}"
PY="$PWD/work/adb-client/bin/python"
ADB="$PWD/work/tv_adb.py"
LAUNCHER="$PWD/work/tv-vibehack-source/launcher"
APK="$LAUNCHER/app/build/outputs/apk/debug/app-debug.apk"
PKG=com.gathin.tvhome
STOCK_LAUNCHER=com.google.android.tvlauncher

[ -x "$PY" ] || { echo "missing $PY (python venv with adb-shell)"; exit 1; }

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export ANDROID_USER_HOME="$LAUNCHER/work/android-user-home"   # pins the debug signing key
export GRADLE_USER_HOME="$PWD/work/gradle-cache"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> building"
(cd "$LAUNCHER" && ./gradlew assembleDebug --console=plain --no-daemon -q)

echo "==> pushing APK"
"$PY" "$ADB" --push "$APK" /data/local/tmp/tvhome.apk "$TV_HOST"

echo "==> installing"
"$PY" "$ADB" "$TV_HOST" -- "pm install -r /data/local/tmp/tvhome.apk"

echo "==> granting media-session access (now-playing island)"
"$PY" "$ADB" "$TV_HOST" -- "settings put secure enabled_notification_listeners \
$( "$PY" "$ADB" "$TV_HOST" -- 'settings get secure enabled_notification_listeners' | tr -d '\r' | sed 's/^null$//' ):$PKG/$PKG.NowPlayingService"

echo "==> default home + disable stock launcher"
"$PY" "$ADB" "$TV_HOST" -- "cmd package set-home-activity $PKG/.MainActivity"
"$PY" "$ADB" "$TV_HOST" -- "pm disable-user --user 0 $STOCK_LAUNCHER" || true

"$PY" "$ADB" "$TV_HOST" -- "input keyevent KEYCODE_HOME"
echo "Installed. Press Home on the remote."