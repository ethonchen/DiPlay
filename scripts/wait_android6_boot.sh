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
stable=0
for attempt in $(seq 1 90); do
  state=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  package_path=$(adb shell pm path android 2>/dev/null | tr -d '\r' || true)
  # API 23 adb shell may exit zero while printing "Could not access the
  # Package Manager", so inspect its actual output and require it to stay ready.
  if [[ "$state" == 1 && "$package_path" == package:* ]]; then
    stable=$((stable + 1))
    if [[ $stable -ge 4 ]]; then
      booted=true
      break
    fi
  else
    stable=0
  fi
  sleep 3
done
[[ "$booted" == true ]] || { echo "Android 6 did not finish restarting" >&2; exit 1; }
adb shell input keyevent 82
echo "Android 6 encryption restart completed."
