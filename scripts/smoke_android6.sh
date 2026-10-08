#!/usr/bin/env bash
set -euo pipefail
apk="${1:?Provide the Android 6 APK}"
package=com.shihab.diplay.hudtest
mkdir -p android6-smoke
adb shell getprop ro.build.version.sdk > android6-smoke/sdk.txt
test "$(tr -d '\r\n' < android6-smoke/sdk.txt)" = 23
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$package/com.shilapi.xcertplay.DiPlayActivity" --es page settings > android6-smoke/start.txt
# Allow asynchronous startup work to run before checking process health and the screen.
sleep 8
adb logcat -d > android6-smoke/logcat.txt
adb shell dumpsys activity activities > android6-smoke/activities.txt
adb shell uiautomator dump /sdcard/diplay-settings.xml
adb pull /sdcard/diplay-settings.xml android6-smoke/settings.xml
adb exec-out screencap -p > android6-smoke/settings.png
grep "Status: ok" android6-smoke/start.txt
grep "mResumedActivity.*com.shihab.diplay.hudtest.*DiPlayActivity" android6-smoke/activities.txt
if grep -E 'FATAL EXCEPTION|NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError' android6-smoke/logcat.txt; then
    exit 1
fi
echo "Android 6 install and Settings startup smoke passed"
