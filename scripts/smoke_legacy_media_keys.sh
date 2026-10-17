#!/usr/bin/env bash
set -euo pipefail
mkdir -p android6-smoke
probe_dir="$(mktemp -d)"
remote=/data/local/tmp/diplay-media-key-probe
probe_pid=
cleanup() {
    if test -n "$probe_pid"; then kill "$probe_pid" 2>/dev/null || true; fi
    adb shell rm -rf "$remote" || true
    rm -rf "$probe_dir"
}
trap cleanup EXIT
mkdir -p "$probe_dir/classes" "$probe_dir/dex"
android_jar="$(find "$ANDROID_HOME/platforms" -name android.jar | sort -V | tail -1)"
d8_tool="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name d8 | sort -V | tail -1)"
test -f "$android_jar"
test -x "$d8_tool"
javac --release 8 -cp "$android_jar" \
    -d "$probe_dir/classes" scripts/probes/LegacyMediaSessionProbe.java
"$d8_tool" --min-api 23 --lib "$android_jar" \
    --output "$probe_dir/dex" "$probe_dir"/classes/*.class
adb shell mkdir -p "$remote"
adb push "$probe_dir/dex/classes.dex" "$remote/classes.dex"
adb shell "CLASSPATH=$remote/classes.dex app_process /system/bin LegacyMediaSessionProbe $remote/proceed" \
    > android6-smoke/media-key-routing.txt 2>&1 &
probe_pid=$!
wait_phase() {
    for attempt in $(seq 1 100); do
        if grep -q "$1" android6-smoke/media-key-routing.txt; then return; fi
        if ! kill -0 "$probe_pid" 2>/dev/null; then cat android6-smoke/media-key-routing.txt; return 1; fi
        sleep 0.1
    done
    cat android6-smoke/media-key-routing.txt
    return 1
}
wait_phase 'NEGATIVE_READY flags=0'
adb shell media dispatch next
sleep 1
adb shell touch "$remote/proceed"
wait_phase 'POSITIVE_READY flags=3'
adb shell media dispatch next
adb shell media dispatch previous
adb shell media dispatch play-pause
wait_phase 'media-key-probe PASS sdk=23'
wait "$probe_pid"
probe_pid=
cat android6-smoke/media-key-routing.txt
