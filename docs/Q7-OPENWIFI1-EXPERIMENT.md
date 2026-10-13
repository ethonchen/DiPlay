# Q7 openwifi1 experiment

Base: `f4205faf57ee8a33f689210ae8545e7147a35741`, the tested Android 6 Q7
`0.2.18-android6-q7-upstream1` build.

This experiment omits the passphrase TLV from AccessoryWiFiConfiguration (`0x5703`)
and the wireless group of CarPlayStartSession (`0x4301`) when security is NONE (0).
The baseline sent a NUL-terminated empty string in both places. No other handshake
fields, hotspot settings, address policies or audio paths are changed. Secured
networks still send their password. This is an unverified iPhone compatibility
experiment, not evidence that the baseline encoding violated Apple's specification.

The diagnostic report identifies the experimental path with:
`iap2 openwifi1 experiment security=NONE passphraseField=omitted messages=0x5703+0x4301`.
It does not log the SSID, password or raw credential packets.

## Car test

1. Install the Q7 APK over the current package. No uninstall is needed.
2. Keep the car hotspot's SSID and 2.4 GHz channel fixed. Save the matching SSID
   in DiPlay, leave its password empty, and use the open security setting.
3. Start a connection and export a diagnostic report whether it succeeds or fails.
4. If it fails, use the same SSID/channel with WPA2 and a matching password in
   both the system hotspot and DiPlay, then export the successful/failing control.

The package name and cached signing certificate are unchanged. Version code stays
41 so the original code-41 upstream1 APK can be installed over this experiment
to restore baseline behavior while preserving settings. The version name ends in
`openwifi1-slim` so reports distinguish the builds. Keep the experiment isolated
until a real Q7/iPhone test establishes its result.
