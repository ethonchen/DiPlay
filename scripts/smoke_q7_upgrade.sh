#!/usr/bin/env bash
set -euo pipefail
previous="${1:?Provide the previously released Q7 APK}"
next="${2:?Provide the new Q7 APK}"
mkdir -p android6-smoke
adb install -r "$previous"
# This runs only on a disposable API-23 emulator. Keep a marker in private app
# data to verify a real signed update preserves that data without uninstalling.
adb root
adb wait-for-device
adb shell 'mkdir -p /data/data/com.shihab.diplay.hudtest/files; echo preserved > /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check'
adb install -r "$next" | tee android6-smoke/update-install.txt
adb shell cat /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check | tr -d '\r' | tee android6-smoke/update-data.txt
grep -q '^preserved$' android6-smoke/update-data.txt
adb shell dumpsys package com.shihab.diplay.hudtest > android6-smoke/update-package.txt
echo 'Q7 Android 6 signed in-place upgrade and data preservation passed.'
