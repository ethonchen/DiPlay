#!/usr/bin/env bash
# API 23's package manager can print Failure while adb still exits zero.
# Require an explicit success and compare the installed bytes with the candidate.
set -euo pipefail
apk="${1:?Provide the Q7 APK}"
log="${2:?Provide the install log path}"
package=com.shihab.diplay.hudtest
mkdir -p "$(dirname "$log")"
status=0
adb install -r "$apk" > "$log" 2>&1 || status=$?
cat "$log"
if test "$status" -ne 0 ||
   ! tr -d '\r' < "$log" | grep -Eq '^[[:space:]]*Success[[:space:]]*$' ||
   grep -Eq 'Failure|INSTALL_FAILED|^Error' "$log"; then
    echo 'APK installation did not succeed.' >&2
    exit 1
fi
installed_path=$(adb shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p')
test -n "$installed_path"
test "$(printf '%s\n' "$installed_path" | wc -l)" -eq 1
installed_copy=$(mktemp)
trap 'rm -f "$installed_copy"' EXIT
adb pull "$installed_path" "$installed_copy"
if ! cmp -s "$apk" "$installed_copy"; then
    echo 'Installed APK does not match the candidate.' >&2
    exit 1
fi
digest=$(sha256sum "$installed_copy" | cut -d ' ' -f 1)
printf 'Installed Q7 APK bytes verified: %s\n' "$digest" | tee -a "$log"
