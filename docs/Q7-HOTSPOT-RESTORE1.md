# Q7 hotspot restore1 experiment

Based on the tested `f4205faf57ee8a33f689210ae8545e7147a35741` Q7 upstream1
build, preserving the existing audio and navigation fixes. The earlier openwifi1
packet-format experiment is not part of this branch.

Only Android 6 / API 23, board `sql_fyt6025`, hardware `sc8830` can invoke the
restoration path. The first launch seeds the owner-selected hotspot profile once
in app preferences and enables the existing boot/open connection options. Later
edits to credentials and startup choices are preserved. Other devices are unchanged.

Before a manual wireless connection, an incorrect running AP is stopped, the saved
WPA2 / 2.4 GHz configuration is written, read back, and enabled. Setter acceptance
alone never counts as restoration. The existing manual-hotspot readiness checks
must still observe a stable, usable tethered interface; configuration is checked
again before the CarPlay startup handoff. Failed transactions request bounded
rollback to the original AP configuration and running state. Diagnostics contain
only status/failure class and verification flags, never credentials or raw packets.

The app requests the system's Modify system settings screen from the connection
dialog when permission is absent. It does not silently grant permissions or use
ADB/root. OEM API rejection is reported as a failure, not treated as success.

## Car acceptance

1. Overlay-install the restore1 APK; open it and allow Modify system settings if asked.
2. Return and connect. Confirm the system AP is WPA2 and CarPlay opens.
3. Restart the head unit, allow its boot sequence to finish, then let DiPlay reconnect.
4. Export diagnostics whether restoration/CarPlay succeeds or fails. A successful
   configuration restoration requires `q7-restore result=READY configVerified=true`
   followed by the normal network readiness and AirPlay session evidence.

Version code and signing certificate match the code-41 upstream1 APK for overlay
installation and rollback. Real FYT firmware writability remains a car-side gate;
unit tests and an API-23 emulator do not prove that this OEM grants configuration
control or persists the restored configuration after every reboot.
