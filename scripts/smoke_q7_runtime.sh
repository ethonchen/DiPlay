#!/usr/bin/env bash
set -euo pipefail
apk="${1:?Provide the Q7 optimized APK}"
mkdir -p android6-smoke
# Inputs are the same already-public test identity bundled in the deliverable.
probe_dir="$(mktemp -d)"
trap 'rm -rf "$probe_dir"; adb shell rm -rf /data/local/tmp/diplay-q7-probe' EXIT
python3 - "$apk" "$probe_dir" <<'PY'
import sys
import zipfile
from pathlib import Path
with zipfile.ZipFile(sys.argv[1]) as archive:
    for name in ("identity.pk8", "certificate.p7b"):
        Path(sys.argv[2], name).write_bytes(archive.read("assets/offline-mfi/" + name))
PY
adb shell mkdir -p /data/local/tmp/diplay-q7-probe
adb push "$apk" /data/local/tmp/diplay-q7-probe/app.apk
adb push "$probe_dir/identity.pk8" /data/local/tmp/diplay-q7-probe/identity.pk8
adb push "$probe_dir/certificate.p7b" /data/local/tmp/diplay-q7-probe/certificate.p7b
adb shell 'CLASSPATH=/data/local/tmp/diplay-q7-probe/app.apk app_process /system/bin com.shilapi.xcertplay.Q7RuntimeProbe /data/local/tmp/diplay-q7-probe' | tee android6-smoke/runtime-probe.txt
grep -q 'q7-runtime PASS sdk=23 pairing=true authenticatedPackets=true' android6-smoke/runtime-probe.txt

# Exercise the actual optimized manifest receiver on Android 6. No CarPlay owner
# is attached on the emulator; forwarding with an owner is covered by unit tests.
adb logcat -c
adb shell am broadcast -n com.shihab.diplay.hudtest/com.shilapi.xcertplay.FytLegacyMediaReceiver -a com.android.music.musicservicecommand --es command next
adb shell am broadcast -n com.shihab.diplay.hudtest/com.shilapi.xcertplay.FytLegacyMediaReceiver -a com.android.music.previous
adb logcat -d -s DiPlay-FytLegacy:I '*:S' > android6-smoke/legacy-media-receiver.txt
grep -q 'source=manifest action=com.android.music.musicservicecommand mapped=4 active=false' android6-smoke/legacy-media-receiver.txt
grep -q 'source=manifest action=com.android.music.previous mapped=5 active=false' android6-smoke/legacy-media-receiver.txt
