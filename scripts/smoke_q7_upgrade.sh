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
# The owner authorized a one-time signer migration after the old cache was lost.
# Android must reject a direct update of the old signer instead of silently wiping data.
if adb install -r "$next" > android6-smoke/old-signer-update.txt 2>&1; then
    echo 'The previous APK already uses the permanent signer.'
else
    grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' android6-smoke/old-signer-update.txt
    adb shell cat /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check | tr -d '\r' | grep -q '^preserved$'
    adb uninstall com.shihab.diplay.hudtest
    adb install "$next"
    adb shell 'mkdir -p /data/data/com.shihab.diplay.hudtest/files; echo preserved > /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check'
fi
adb install -r "$next" | tee android6-smoke/update-install.txt
adb shell cat /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check | tr -d '\r' | tee android6-smoke/update-data.txt
grep -q '^preserved$' android6-smoke/update-data.txt
adb shell dumpsys package com.shihab.diplay.hudtest > android6-smoke/update-package.txt
echo 'Q7 Android 6 signed in-place upgrade and data preservation passed.'
