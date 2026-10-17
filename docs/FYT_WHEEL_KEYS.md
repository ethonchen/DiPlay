# Legacy FYT steering-wheel track keys

The Q7 `FYT6025` / `sql_fyt6025` Android 6 report from wheel1 shows an active
MediaSession with flags 3 and granted focus, but no media-key callbacks. This
establishes the missing Android input path; it does not identify the MCU event
actually emitted by this vehicle. The new adapter subscribes to FYT's separate
MCU/player callbacks and records their numeric payload and CarPlay send result.

## Protocol evidence

The 2015 FYT SDK sources in [FYTUSBDVR](https://github.com/liyunkuai/FYTUSBDVR/tree/6496bc00f416d1da3e4794f09a61ffe7f6c7c4c1/src/com/syu)
contain `module/MsToolkitConnection.java`, `module/main/ConnectionMain.java`,
`module/main/FinalMain.java`, and `ipc/FinalMainServer.java`:

- Explicit service: `com.syu.ms/app.ToolkitService`.
- Toolkit MAIN module number: **0**. `FinalMain.MODULE_MAIN = 1` is a different enum.
- MAIN update **37**, `U_PLAYER_CMD`: `[APP_ID, PLAYER_CMD]`.
- Audio player app 8 / third-party player app 10; previous command 4 / next command 5.
- Register argument 0 subscribes without replaying the current value; 1 replays it.
- Update 60 is firmware-specific direction navigation; it is diagnostic only.

[open-headunit's FYT receiver](https://github.com/andreknieriem/open-headunit/blob/main/app/src/main/java/com/andrerinas/openheadunit/connection/carkey/fyt/CarFYTReceiver.kt)
documents newer MAIN update **133** with an Android key code. It normally needs a
separate focus property on newer units. DiPlay passively subscribes when supported;
it does not set that property. [FytBt's device findings](https://github.com/PimpinPumpkin/FytBt/blob/main/FINDINGS.md)
document empty replies for void module transactions on some FYT firmware.
The implementation is a fresh minimal Binder client, with no vendor library or
copied external implementation.

## Behavior and limits

The adapter runs only on FYT Android 6 while a CarPlay controller is attached.
Car Bluetooth audio disables it to leave AVRCP in charge. It forwards only explicit
next/previous from the documented player/raw-key callbacks. Radio, Bluetooth,
unknown commands and unverified direction codes are never translated into skips.
No audio-source command, root command, system-property write or accessibility
permission is involved. Standard Android media-key handling remains enabled.

The service is unbound and callbacks unregistered on session stop. Binder work
runs outside the UI thread; delayed callbacks are fenced by connection generation
and controller identity. Different input paths reporting the same skip within
150 ms are suppressed; repeated taps on the same path remain distinct.

`FytWheelKeysTest` exercises real Parcel/Binder layouts on Robolectric API 23,
empty replies, unsupported newer callbacks, forwarding into the production
CarPlay media-key owner, invalid payloads, stop/reconnect races, and deduplication.
The build also runs the full repository checks and installed Android 6 APK smoke
tests. Actual FYT MCU delivery still requires a vehicle check. Exported diagnostics
now include FYT bind/registration state, numeric events, and `sent=true/false`.
