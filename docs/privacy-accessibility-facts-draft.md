# Privacy and accessibility implementation facts

This is a source-based summary, not a published privacy policy, legal opinion or
completed store declaration. Recheck the final built manifest and dependencies
before using it in a release policy.

## Production capabilities

The [manifest](../app/src/main/AndroidManifest.xml) declares no `uses-permission`
entries. The exported service is protected by `BIND_ACCESSIBILITY_SERVICE`.
Its [configuration](../app/src/main/res/xml/accessibility_service.xml) enables
magnification control, disables window-content retrieval and identifies it as an
accessibility tool. It declares no screenshot or gesture-injection capability.

The app controls full-screen magnification and its own overlays. Keyboard avoidance
uses display/window-inset geometry, not editor text. Production code does not
capture screenshots, inject taps or upload data. There is no account, advertising,
analytics or remote crash-reporting feature. Test instrumentation is separate and
has additional test-only capabilities described in [RUNTIME_TESTS.md](../RUNTIME_TESTS.md).

The project declares no third-party runtime dependencies. Build tooling may add
implicit libraries: an earlier resolved release classpath included Kotlin standard
library and JetBrains annotations. Do not equate an empty explicit dependency block
with an empty generated classpath.

## Local settings and diagnostics

Private preferences store position, remembered scale, pad size, hold delay, opacity,
tap actions, zoom-adjustment toggle, color and migration version. Disabling the
service does not erase preferences. Clearing app storage or uninstalling removes
these local settings. Backup is disabled and cloud/device-transfer exclusions are
configured; this is not a guarantee about every OEM or user-operated backup mechanism.

Optional diagnostics record numeric controller touch coordinates, timing, input
flags/history counts, magnification scale/center, frame-cadence observations,
device manufacturer/model, Android/API version and uptime. Coordinates are screen
positions, not geographic location. No typed text or screenshot pixels are recorded.

Fifteen seconds is the **recording window**, not a deletion deadline. The bounded
report stays in service memory for viewing/copying. Starting another capture replaces
it; Clear diagnostics clears the app's report. Disabling the service is not a promise
of immediate secure memory erasure.

Copy writes a report to Android's clipboard only when selected. Clearing diagnostics
does not clear text already copied, pasted or shared elsewhere. Clipboard retention
and receiving apps are outside the report buffer's control. Review reports before
sharing. Error paths can write exception details to Android system logs; do not
claim that the app never logs anything.

## Before publishing a privacy policy

Confirm the developer identity, privacy contact, policy destination and any handling
or retention of support reports received outside the app. Describe the final release
rather than assuming this inventory covers every variant. Check current platform
requirements separately and do not infer acceptance from build or signing success.
