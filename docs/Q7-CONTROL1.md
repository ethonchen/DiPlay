# Q7 control1 test build

Based on main `a231d0b04771c0f06cf17cb1beebaabf2b7a5446`, retaining the tested
Android 6 Q7 hotspot restoration, 2.4 GHz network discovery, audio timeline
recovery and navigation ducking. Package version code 42; optimized version
`0.2.18-android6-q7-upstream1-restore1-control1-slim`; existing update-signing key.

The 20261014 log records a received-control queue overflow at 12:40:05 and
video restoration at 12:42:40. It establishes the overflow and subsequent TCP/
Bluetooth failures, but does not establish which consumer callback/send stalled.

Wi-Fi type-130 CSM framing now has its own receive worker. Runtime callbacks and
location sends can pause without immediately stopping the link drain. Both raw
control and framed-message queues allow 256 entries, retaining their 1 MiB byte
limits. All messages remain ordered; no protocol bytes or partial metadata
updates are discarded or coalesced. Genuine overflow still terminates explicitly.
Queue diagnostics include depth, byte count, peak and oldest-frame age, without
credentials or raw frame payloads. Queue diagnosis does not replace car testing.

Runtime control sends have a five-second queue budget rather than inheriting
the unlimited drive's five-minute polling budget. Established-session recovery
caps retry pauses at eight seconds for two minutes, then returns to the normal
backoff. Initial connection and the existing startup retry limit are preserved;
the 30-second first-TCP watchdog remains unchanged for this experiment.

Validation includes a 192-message burst with the runtime consumer paused,
fragmented/concatenated CSM framing, memory limits, close/EOF wake-up and the
bounded recovery window, plus the normal unit/lint/build and API-23 smoke checks.

Car acceptance: overlay-install, keep location forwarding enabled for the first
comparison, play music with navigation for at least 30 minutes, and export a
diagnostic report. Confirm hotspot restoration and navigation ducking; if a
disconnect happens, compare queue diagnostics, the original error and time to
the next first rendered frame.
