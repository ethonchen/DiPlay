# Q7 wheel4: legacy media input

Experimental branch only. Do not merge or mark the vehicle issue resolved until
the owner confirms operation on the HHQ Q7 Android 6 unit.

## Evidence from the latest vehicle log

The wheel3 log reports an active CarPlay session, a connected FYT MAIN module,
an initial update 0 with app ID 10, and focus requested=2 actual=2 verified=true.
There are still zero key presses and no updates 37, 60 or 133 during the test.
This confirms that the callback binder and focus write work. It does not prove
which interface this firmware uses for the steering wheel.

## Change

Register the explicit mutable media-button PendingIntent with MediaSession and
the Android 6 AudioManager legacy receiver/RemoteControlClient APIs. Mirror
playback state and tear down all registrations with the CarPlay owner.
Receive stock Android music-service and FYT external-command broadcasts through
both a live receiver and the manifest receiver. Forward only known command
strings or Android media key codes through the existing CarPlay sender. Ignore
key-up/repeat events and unknown vendor numbers. Existing sender deduplication
handles simultaneous sources.

Add a passive framework `syu` BSP module 0/update 2 subscription for diagnostics.
It never writes a BSP command, changes focus or guesses raw button mappings.
This provides additional evidence if this firmware bypasses legacy media APIs.

Protocol references (independently implemented):

- https://github.com/andreknieriem/open-headunit/blob/main/app/src/main/java/com/andrerinas/openheadunit/app/RemoteControlReceiver.kt
- https://github.com/MS-Teja/Bumblebee/blob/4d2c89640a9326ed1ca03bd9a6b9b409fe8831bb/app/src/main/java/com/teja/bumblebee/playback/Receivers.kt
- https://github.com/vasyl91/FYT-Launcher-Mod/blob/97be85d45e90d3c5af8ed2adbfdbcd7a4c955fc3/app/src/main/java/com/syu/util/UserTouches.java
- https://github.com/trywent/note/blob/f13af8abfdfe1faa90ff9e03a7a51403de1e36dd/android/modify/RemoteToolkitBsp.java

## Verification scope

API 23 unit tests cover registrations, dynamic and explicit delivery, filled-in
KeyEvents, production CarPlay sender forwarding, teardown and passive BSP
subscription. The optimized installed APK also receives next/previous broadcasts
on an Android 6 emulator. The full repository checks run in parallel with the
APK build. None of these checks establishes physical MCU delivery on the Q7;
that still requires the vehicle test.
