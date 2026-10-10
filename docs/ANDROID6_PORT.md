# Android 6.0 / API 23 port

This branch tracks upstream through `09399b14805195bb027bfbc1ef2f1482e59625f5`
(0.2.16 plus the subsequent connection, microphone, update and diagnostic fixes), targeting
Android 6.0, retaining the current UI and protocol implementation. It is an
experimental compatibility target, not a claim of vehicle validation.

## Scope

- `mobile`, `common`, and `shared` use minSdk 23; JNI libraries also build for API 23.
- The existing `armeabi-v7a`, `arm64-v8a`, and `x86_64` native targets remain available.
  Cortex-A7 head units use `armeabi-v7a`.
- Language selection and interface scaling use a single locale on Android 6 and
  LocaleList on Android 7+. Arabic layout direction is retained.
- Audio playback never calls `AudioTrack.getUnderrunCount` on Android 6. It uses
  PCM written/played accounting to detect an empty track; unknown hardware
  underruns are reported as unavailable, rather than a measured zero.
- USB Lockdown TLS uses the platform default key-manager algorithm and avoids the
  API-24 endpoint-identification setter on Android 6. Microphone timing uses the
  existing latency fallback without calling the API-24 timestamp method.
- Audio focus, configured audio attributes, API-23 AudioTrack/MediaCodec, and
  media/navigation stream overrides retain upstream compatibility behavior.
- Java-8 collection/future calls on connection and decoder lifecycle paths are
  replaced with older equivalents. Core library desugaring handles transitive
  Java-library compatibility without lowering targetSdk or compileSdk.
- Optional Android Automotive, Home, and map-host application minimums remain
  unchanged. Home/map embedding requires Android 11 and is not part of API-23 use.

The AIxBits Android-4.3 adaptation was inspected at commit
`73b4750` (experimental 0.21). Its separate API-18 UI and stripped native codec
build are not imported: API 23 can use the upstream UI, AudioTrack.Builder,
MediaCodec surface changes, and native echo cancellation directly. Its useful
principles are retained: independent stream routing, preserved audio diagnostics,
and explicit experimental/hardware validation limits.

## Build and authentication

Use [BUILD.md](BUILD.md) with JDK 25, SDK 37, NDK 28.2.13676358 and the checked-in
Gradle wrapper. Compilation against SDK 37 does not set the installation minimum.

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest \
  :shared:lintDebug :common:lintDebug :mobile:lintDebug :home:lintDebug :maphost:lintDebug \
  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

CI uploads `DiPlay-Android6-source-only` only after all checks pass. The APK is
`mobile/build/outputs/apk/debug/mobile-debug.apk`, package
`com.shihab.diplay.hudtest`, and version `0.2.16-android6-q7-hud-test`.
Do not install the Home/map-host APK on Android 6.

**The CI/source APK does not contain an accessory authentication identity.** It
can exercise installation, UI, permissions, and audio previews, but cannot
complete standalone iPhone accessory authentication. Provision intended runtime
assets explicitly according to BUILD.md, then run:

```sh
export DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets
./gradlew :mobile:assembleStandaloneDebug
```

The directory contains `offline-mfi/identity.pk8` and
`offline-mfi/certificate.p7b`. Neither authentication material nor signing keys
belong in this public repository. Ordinary source builds do not import APK credentials. For this fork's car-test build, the owner explicitly selected the public experimental identity from upstream `DiPlay-0.2.14.apk` (SHA-256 `62b31f79db32bc7c85013ae830460b697a5952fad571ed0030b97341dde0b2e3`). The dedicated push workflow extracts only the two named files into a temporary external directory, builds `DiPlay-Android6-standalone-experimental`, verifies API-23/v1/ARMv7 packaging, and removes the temporary inputs. Pull-request and ordinary source builds stay identity-free. This is the upstream public experiment, not Apple certification; future iOS acceptance is unresolved. The APK necessarily makes that public test identity extractable.
Keep the same signing key for updates. Legacy 0.21 has a different package and
does not share settings with this app; run one projection receiver at a time.

## Validation and first vehicle run

Automated coverage includes API 23/25 settings startup, all settings categories,
language/scaling, API-23 PCM AudioTrack startup/write, audio starvation accounting,
audio focus and attribute compatibility, synthetic USB TLS setup, API-23 microphone
latency fallback, and bounded asynchronous URL responses.
Existing upstream unit tests and the complete mobile/home/map-host lint suite run as well. Extra library lint focuses on NewApi/InlinedApi; pre-existing shared-library permission-lint findings are outside this API compatibility audit. Pushes to the fork main and port branches both build the standalone APK. The dedicated fork push workflow also installs the standalone APK in an API-23 x86_64 emulator, opens Settings, checks the resumed activity and startup errors, and retains a screenshot/logcat artifact. This emulator does not validate ARMv7 execution or real-car Bluetooth/audio. Passing these checks cannot
prove physical speaker output, microphone capture, codecs, Bluetooth handoff,
USB drivers, or iPhone authentication on a particular OEM firmware.

On the HHQ Q7 Android-6/Cortex-A7 head unit:

1. Install the main APK, open Settings, grant requested microphone/location
   permissions, and leave the old receiver stopped.
2. Start with H.264, moderate resolution and 30 fps. HEVC/Opus depend on installed
   platform codecs and are not guaranteed for Cortex-A7 firmware.
3. Use the existing car hotspot or same-LAN mode first. Android 6 has no Android-8
   local-only hotspot API; Wi-Fi Direct and USB remain firmware dependent.
4. Run the existing media/navigation audio previews before projection. A successful
   beep proves that selected output path, not reception/decoding of iPhone audio.
5. With an explicitly provisioned standalone build, test music, navigation and Siri
   separately. If automatic routing is silent, try media/navigation stream 3
   (`STREAM_MUSIC`) through the existing routing settings and reconnect.
6. Export diagnostics during a failure. Check received packets, decoder output,
   write errors, and playback-head advancement. API-23 hardware underruns being
   unavailable is expected. Verify reconnection and a sustained session before
   treating the build as vehicle-ready.

No HHQ Q7 physical-device result is claimed by this port.

## Q7 optimized build (2026-10-08)

The owner confirmed speaker output in the code-34 APK from main commit
`53be782f5f7f423fa8791372a2bf16c7f0aad1c0`. The supplied FYT6025/sc8830 log
also records active AAC-LC/48-kHz playback with advancing PCM accounting and
no AudioTrack write errors. This observation does not establish call/Siri
or navigation-mixing behavior.

The same log shows H.264 hardware decoding at 1024x600/30 fps, but the Java
ChaCha20-Poly1305 fallback takes about 32–59 ms per frame in many active
windows, with larger spikes of 200–280 ms. Received/rendered rates commonly
fall to 20–28 fps. Wi-Fi P2P uses 2.4 GHz with reported 5-GHz support false;
later audio windows show sequence gaps and up to roughly 1.2 s receive gaps.
Low frame rates while the TextureView is destroyed are background activity,
and should not be interpreted as foreground decoder failure.

The `q7` build uses release dependencies and AGP 9.3's code/resource optimizer,
without Compose debug tooling or the debuggable flag. It retains all existing
languages, ARMv7/ARM64/x86_64 native targets, settings, diagnostic exports and
CarPlay audio/connection features. The small extra native targets allow the
**exact delivered APK** to run in the API-23 emulator. No audio routing, codec,
buffer, video-resolution or Wi-Fi defaults are changed. The Java fallback
decryptor is reused separately on each receive thread, and resets its key,
nonce, AAD and authentication state on every packet. Encryption and native
platform selection retain the previous behavior.

Build with the same runtime inputs and cached fork signing key:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets \
DIPLAY_DEBUG_KEYSTORE_PATH=/absolute/path/to/the-existing/debug.keystore \
./gradlew :mobile:lintQ7 :mobile:assembleStandaloneQ7
```

Output: `mobile/build/outputs/apk/q7/mobile-q7.apk`, package
`com.shihab.diplay.hudtest`, code 37, version `0.2.16-android6-q7-slim`.
The required signer SHA-256 is
`5113b2d373d54973ac2fe837ab1d0f4132dba3e4fba64d4027bd4d41823d163f`.
The dedicated workflow refuses a missing signing cache or changed certificate,
checks API-23/v1/ARMv7/identity packaging and a 12-MiB size budget, and archives
R8 mappings. It runs the full required debug test/lint/build command and Q7
lint, then installs the optimized APK and opens Settings in Android 6.
An explicit ADB probe runs the **optimized code** to check local MFi signing,
X25519, Ed25519, varied-size authenticated packets, tampered-tag rejection and
recovery after rejection. It never runs during ordinary app startup.

Use an in-place APK update to keep the known-working audio settings. A smoothness
improvement must be measured on the physical Q7; emulator crypto/UI checks cannot
establish Cortex-A7 performance or remove 2.4-GHz RF interference.

## HHQ Q7 completion (2026-10-08)

The Q7 build retains H.264, 30 fps, automatic media/navigation routing, and
unbuffered CarPlay audio as the fresh-install defaults. The real-car log shows
1024x600 video; API-23/25 Settings/category/language tests now use that screen
size. Audio-channel persistence and focus-control tests also run on API 23.
There is no attempt to force OEM volume or guess proprietary MCU/steering-wheel
interfaces. The car-test APK contains ARMv7 native libraries.

The old Legacy report's `Android SDK18` header is a fixed string in
`adaptation/receiver/src/local/airuize/receiver/ReceiverActivity.kt` in the
AIxBits reference; it is not a runtime SDK measurement. The Q7 screenshot says
Android 6.0. This build reports `Build.VERSION.SDK_INT` and the actual firmware.
It requires API 23 or newer; an installation failure must be diagnosed from
the system-reported SDK and installer result rather than the old header.

The old report showed no audio SETUP/RTP/PCM despite working local test tones.
This port uses the complete current upstream receiver, not the stripped API-18
receiver: matching discovery/info features, audio formats and latencies,
resource declarations, SETUP handling, decoders and PCM playback are retained.
A source review cannot prove iPhone media/navigation/Siri playback in this car.
Export this build's diagnostics while music is playing if any of them is silent.

The in-app update feed now points to `ethonchen/DiPlay`; upstream's newer Android
minimum and signing identity must not be offered as updates to this port. If
this fork has no release with an APK and checksums, no update is offered.
For subsequent APK builds use the fork Actions artifact and the same cached
signing key. Fork push builds generate/restore an explicit cached debug keystore
and pass its path to Gradle; AGP's default location is not assumed. The final
main-branch APK establishes the update-signing baseline. Earlier unpublished
code-33/34 test artifacts used temporary signing keys: uninstall that test app
once if Android reports a signature conflict. The separate Legacy app does not
conflict with this package.

## Upstream synchronization: 0.2.15 (2026-10-09)

The previous Q7 build already descended from upstream
`75628b298378060698cb60b328fcef100d1e497d`, which includes all functional
changes shipped in 0.2.15: audio rebuffer recovery, the decoder operating-rate
hint, appearance/layout updates, setup guide, update checks and optional vehicle
features. The remaining three upstream commits only change release metadata,
documentation, the website and issue templates. This merge adds those commits
with their ancestry intact; it does not add a new audio-stutter fix.

Fork version code advances from 35 to 36 (upstream's code 34 cannot update the
installed Q7 app). The optimized version is `0.2.15-android6-q7-slim`. API 23,
ARMv7 packaging, the `com.shihab.diplay.hudtest` package, cached update signer,
fork update feed, explicit 0.2.14 test-identity source and receive-decryptor
reuse remain unchanged. Upstream's API-25 minimum and signing key do not replace
the fork's. The merge branch runs the required tests/lint/debug builds plus the
optimized Q7 APK checks and API-23 installation/runtime smoke. CI evidence is
linked in the synchronization pull request; physical Q7 smoothness remains a
separate device check.


## Upstream synchronization: 0.2.16 and subsequent fixes (2026-10-10)

The merge preserves all upstream main ancestry through `09399b14805195bb027bfbc1ef2f1482e59625f5`.
It includes the 0.2.16 release and all later commits available at synchronization:
software Opus microphone encoding, wired USB framing/read/retry fixes, wireless
address recovery and scan-pause lifecycle fixes, bounded video recovery,
optional low-latency/direct video output, revised settings/search, background
update checks, visible diagnostic exports, and opt-in BYD/HID features.

API 23, ARMv7, the existing `.hudtest` application ID, update certificate and
receive-decryptor reuse are retained. The added locale accesses use the API-23
locale helper; the new call-speaker behavior keeps concurrent microphone
insertion on `getOrPut` rather than the API-24 `computeIfAbsent`. Both the
existing desugaring dependency and WorkManager **2.11.2** are included.
WorkManager 2.12 requires API 24; the last API-23-compatible maintenance line
retains the same background-update APIs used here. The shared ambient-light consumer rules are retained for R8.

Fork version code is **37**, optimized version **0.2.16-android6-q7-slim**.
The fork update feed and explicitly selected runtime identity stay in place.
The optimized workflow also runs on future main and upstream-sync branches,
using the existing signing cache and API-23 runtime smoke checks. Verification
results are recorded in the synchronization pull request. Physical Q7 audio,
Siri/calls, USB and video performance remain device checks.
