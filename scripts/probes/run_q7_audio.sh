#!/usr/bin/env bash
set -euo pipefail
mkdir -p q7-audio-evidence
adb shell wm size 1024x600
adb shell getprop > q7-audio-evidence/getprop.txt
adb shell cat /proc/cpuinfo > q7-audio-evidence/cpuinfo.txt
adb shell cat /proc/meminfo > q7-audio-evidence/meminfo.txt
adb shell dumpsys media.audio_flinger > q7-audio-evidence/audio-flinger-before.txt
cp "$HOME/.android/avd/test.avd/config.ini" q7-audio-evidence/avd-config.ini
# Check the unchanged deliverable first; the following probe APK is a distinct test build.
echo 'b9a746bcfb218043f629508430421db5664e827fc77e5b6397e968418424a82d  probe-build/tested-release.apk' | sha256sum --check
bash scripts/smoke_android6.sh probe-build/tested-release.apk
adb push probe-build/audio-probe.apk /data/local/tmp/q7-audio-probe.apk
adb push fixture.aac /data/local/tmp/q7-audio-fixture.aac
adb logcat -c
adb shell 'CLASSPATH=/data/local/tmp/q7-audio-probe.apk app_process /system/bin com.shilapi.xcertplay.Q7AudioProbe /data/local/tmp/q7-audio-fixture.aac' | tee q7-audio-evidence/probe.txt
adb logcat -d -v threadtime > q7-audio-evidence/logcat.txt
adb shell dumpsys media.audio_flinger > q7-audio-evidence/audio-flinger-after.txt
grep -q 'Q7_AUDIO_PROBE PASS scenarios=6' q7-audio-evidence/probe.txt
