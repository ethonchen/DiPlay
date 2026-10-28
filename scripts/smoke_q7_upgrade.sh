#!/usr/bin/env bash
set -euo pipefail
previous="${1:?Provide the previously released Q7 APK}"
next="${2:?Provide the new Q7 APK}"
mkdir -p android6-smoke
bash scripts/adb_install_checked.sh "$previous" android6-smoke/previous-install.txt
# This runs only on a disposable API-23 emulator. Keep a marker in private app
# data to verify a real signed update preserves that data without uninstalling.
adb root
adb wait-for-device
adb shell 'mkdir -p /data/data/com.shihab.diplay.hudtest/files; echo preserved > /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check'
# The owner authorized a one-time signer migration after the old cache was lost.
# Android must reject a direct update of the old signer instead of silently wiping data.
if bash scripts/adb_install_checked.sh "$next" android6-smoke/old-signer-update.txt; then
    echo 'The previous APK already uses the permanent signer.'
else
    if test "${3:-}" = --require-same-signer; then
        echo 'A same-signer upgrade failed; uninstalling would invalidate this test.' >&2
        exit 1
    fi
    grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' android6-smoke/old-signer-update.txt
    adb shell cat /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check | tr -d '\r' | grep -q '^preserved$'
    adb uninstall com.shihab.diplay.hudtest | tee android6-smoke/migration-uninstall.txt
    tr -d '\r' < android6-smoke/migration-uninstall.txt | grep -Eq '^[[:space:]]*Success[[:space:]]*$'
    bash scripts/adb_install_checked.sh "$next" android6-smoke/migration-install.txt
    adb shell 'mkdir -p /data/data/com.shihab.diplay.hudtest/files; echo preserved > /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check'
fi
bash scripts/adb_install_checked.sh "$next" android6-smoke/update-install.txt
adb shell cat /data/data/com.shihab.diplay.hudtest/files/q7-upgrade-check | tr -d '\r' | tee android6-smoke/update-data.txt
grep -q '^preserved$' android6-smoke/update-data.txt
adb shell dumpsys package com.shihab.diplay.hudtest > android6-smoke/update-package.txt
echo 'Q7 Android 6 signed in-place upgrade and data preservation passed.'
