# Android 6.0 / API 23 port

This branch ports upstream commit `75628b298378060698cb60b328fcef100d1e497d`
to Android 6.0, retaining the current UI and protocol implementation. It is an
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
`com.shihab.diplay.hudtest`, and version `0.2.14-android6-q7-hud-test`.
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
signing key. Version code 34 updates the earlier code-33 test APK.
