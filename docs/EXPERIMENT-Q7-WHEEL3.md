# Q7 wheel3: request FYT key focus (experimental)

The wheel2 vehicle report confirms a successful ToolkitService bind and registrations
for 37/60/133, but zero callbacks. Registering alone does not establish key ownership.
The FYT receiver in open-headunit also requests `sys.carlink.type=2` after subscribing.
This build requests that property through the legacy SDK's MAIN command 41
(`C_SYSTEM_PROPERTIES`, strings `[key,value]`), then reads the actual value back.
Raw-key registration uses the stock receiver's update=1 flag, with startup replay
filtered for 350 ms after the focus request. It also subscribes to source-state
update 0 with initial replay, so the next export
can distinguish a working callback transport from missing key ownership.

Only the temporary `sys.carlink.type` property is changed, only during the active
legacy FYT CarPlay controller. The original value is restored on normal teardown
if the property is still 2. A changed value belonging to another owner is preserved.
No audio-source switch, permanent firmware change, root shell or accessibility
permission is introduced. Request acceptance is not presented as proof: diagnostics
include the before/requested/actual values and a verified flag. Device delivery still
needs confirmation on the FYT6025; this branch is intentionally unmerged.

The focused API-23 Binder tests model a service that emits update 133 only when the
focus property is 2, and verify restoring the exact original value. The complete
repository check command runs in parallel with APK construction to shorten the wait
for the experimental APK. Android 6 installed APK smoke tests use that same artifact.
