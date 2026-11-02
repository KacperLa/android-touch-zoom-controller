# Validation and compatibility

## Existing 0.15 application baseline

The application sources in this repository come from the 0.15 source archive.
That baseline passed its Java helper/regression suite, Android debug assembly,
and Android lint during development. These are not device integration tests.

An API 35 AOSP emulator installed the APK and rendered its activity, but rejected
accessibility-service startup at `SurfaceControlViewHost.setView` with an
`INTERNAL_SYSTEM_WINDOW` permission error. Magnification gestures were therefore
not exercised in that emulator. No privileged permission workaround is included.
Android 14 is the API minimum, not a claim of universal device compatibility.

Real-device testing reported severe delays with a Bluetooth mouse connected.
Disconnecting the mouse resolved both activation and scale-adjustment delays.
The underlying Android implementation cause remains unconfirmed; see the README.

## Repository preparation

Runtime Java, resources, and manifest are unchanged from 0.15. Repository preparation
updates documentation, ignores, test tooling, license text, and build-time signing
configuration. Ordinary local builds now use the Android Gradle plugin's debug key;
a private matching signer can be selected explicitly through environment variables.
This repository preparation does not create a new application release.

The 24 pure Java test programs were rerun during repository preparation: 416,483
assertions passed. The available Java 21 runtime included the compiler module but
not the `javac` launcher or Java 17 API signature archive. This rerun used the
compiler module with `-source 17 -target 17`; it verifies Java 17 syntax/bytecode,
not Java 17 standard-library API compatibility. The documented test script uses
`javac --release 17` on a complete JDK. Android assembly and lint were not rerun
for these repository-only changes because the Android build toolchain was unavailable.

Run `./scripts/test.sh` for pure Java checks and
`gradle :app:assembleDebug :app:lintDebug` for Android checks. Test reports and build
outputs are intentionally not committed. Verify behavior on a compatible device
before relying on a new build.

## Manual checks

- Immediate drag repositions the controller; hold then drag pans magnification.
- Tap toggles persistent zoom; release ends momentary zoom.
- The controller grows to 150% during relocation without changing color.
- Keyboard avoidance restores the chosen position when the keyboard closes.
- Rotation, folding, cancellation, multitouch, and service disable end active gestures safely.
- Idle opacity, pad size, and extra hold delay update and persist.
- Haptics respect system settings; no repeated vibration occurs during movement.
- Diagnostic capture ends after 15 seconds across multiple gestures; Clear and Copy work.
- Emergency magnification-off and service-disable controls remain reachable.

Stop testing if the system becomes unresponsive. Do not repeatedly reproduce the
Bluetooth-mouse delay on a device where it disrupts other applications.
