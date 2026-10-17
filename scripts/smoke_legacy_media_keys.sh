#!/usr/bin/env bash
set -euo pipefail
mkdir -p android6-smoke
adb shell getprop ro.build.version.sdk > android6-smoke/media-key-sdk.txt
test "$(tr -d '\r\n' < android6-smoke/media-key-sdk.txt)" = 23
probe_dir="$(mktemp -d)"
package=com.diplay.mediakeyprobe
cleanup() {
    adb shell am force-stop "$package" || true
    adb uninstall "$package" >/dev/null 2>&1 || true
    rm -rf "$probe_dir"
}
trap cleanup EXIT
mkdir -p "$probe_dir/classes" "$probe_dir/dex"
android_jar="$(find "$ANDROID_HOME/platforms" -name android.jar | sort -V | tail -1)"
d8_tool="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name d8 | sort -V | tail -1)"
build_tools="$(dirname "$d8_tool")"
test -f "$android_jar"
test -x "$d8_tool"
javac --release 8 -cp "$android_jar" -d "$probe_dir/classes" scripts/probes/LegacyMediaSessionProbe.java
"$d8_tool" --min-api 23 --lib "$android_jar" --output "$probe_dir/dex" \
    "$probe_dir"/classes/com/diplay/mediakeyprobe/*.class
cat > "$probe_dir/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.diplay.mediakeyprobe">
    <uses-sdk android:minSdkVersion="23" android:targetSdkVersion="23"/>
    <application android:label="DiPlay media-key test" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".LegacyMediaSessionProbe" android:exported="true"/>
    </application>
</manifest>
XML
"$build_tools/aapt" package -f -M "$probe_dir/AndroidManifest.xml" -I "$android_jar" -F "$probe_dir/unsigned.apk"
python3 - "$probe_dir" <<'PY'
from pathlib import Path
import sys, zipfile
root=Path(sys.argv[1])
with zipfile.ZipFile(root/'unsigned.apk','a') as archive:
    archive.write(root/'dex/classes.dex','classes.dex')
PY
keytool -genkeypair -keystore "$probe_dir/probe.jks" -storepass android -keypass android \
    -alias probe -dname CN=DiPlayMediaKeyTest -keyalg RSA -keysize 2048 -validity 1 -noprompt
"$build_tools/apksigner" sign --ks "$probe_dir/probe.jks" --ks-pass pass:android --key-pass pass:android \
    --min-sdk-version 23 --v1-signing-enabled true --out "$probe_dir/probe.apk" "$probe_dir/unsigned.apk"
adb install -r "$probe_dir/probe.apk"
adb logcat -c
component="$package/.LegacyMediaSessionProbe"
adb shell am start -W -n "$component" --ei flags 0
adb shell media dispatch next
sleep 1
adb logcat -d -s DiPlayMediaProbe:I > android6-smoke/media-key-negative.txt
grep -q 'READY flags=0' android6-smoke/media-key-negative.txt
if grep -q 'KEY flags=0' android6-smoke/media-key-negative.txt; then
    cat android6-smoke/media-key-negative.txt
    exit 1
fi
adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$component" --ei flags 3
adb shell media dispatch next
adb shell media dispatch previous
adb shell media dispatch play-pause
sleep 1
adb logcat -d -s DiPlayMediaProbe:I > android6-smoke/media-key-positive.txt
cat android6-smoke/media-key-negative.txt android6-smoke/media-key-positive.txt > android6-smoke/media-key-routing.txt
grep -q 'READY flags=3' android6-smoke/media-key-positive.txt
grep -q 'next=1 previous=1 toggle=1 presses=3' android6-smoke/media-key-positive.txt
test "$(grep -c 'KEY flags=3' android6-smoke/media-key-positive.txt)" = 3
printf '\nmedia-key-probe PASS sdk=23 noFlagsReceived=0 flags=3 next=1 previous=1 toggle=1\n' | tee -a android6-smoke/media-key-routing.txt
cat android6-smoke/media-key-routing.txt
