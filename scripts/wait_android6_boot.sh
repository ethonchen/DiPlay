#!/usr/bin/env bash
set -euo pipefail
# The stock API-23 Google image reports its first boot complete before its
# initial data encryption has restarted Android's framework. Wait for that
# restart before installing the APK or taking UI screenshots.
seen_restart=false
for attempt in $(seq 1 120); do
  output=$(adb logcat -d -s 'Cryptfs:D' '*:S' 2>/dev/null || true)
  if [[ "$output" == *"Just triggered restart_framework"* ]]; then
    seen_restart=true
    break
  fi
  sleep 2
done
if [[ "$seen_restart" != true ]]; then
  echo "Android 6 initial encryption/framework restart was not observed" >&2
  exit 1
fi
adb wait-for-device
booted=false
for attempt in $(seq 1 90); do
  state=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [[ "$state" == 1 ]] && adb shell pm path android >/dev/null 2>&1; then
    booted=true
    break
  fi
  sleep 2
done
[[ "$booted" == true ]] || { echo "Android 6 did not finish restarting" >&2; exit 1; }
adb shell input keyevent 82
echo "Android 6 encryption restart completed."
