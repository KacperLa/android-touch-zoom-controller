# Validation and compatibility

## 0.22 visibility-only relocation polish

Based on 0.21 commit `4af3fb9f6078631cafc07e26a1222f6753d922f7`.
The cause was source-confirmed: already-zoomed DOWN adopted a preview and showed
the pad while the drag mode was still WAIT; the relocation timer then hid it.
Pad visibility now requires committed PAN, and WAIT keeps the normal bubble
artwork visible. Existing gesture-state transitions, timers, magnification writes,
haptics, 150% relocation transform and API37 keyboard-focus handling are unchanged.

The pure Java suite passes 445,278 assertions, including 17,002 new policy and
latest-render-queue checks for WAIT/jitter, hold-to-relocate, immediate pan,
ordinary hold-to-pan, scale gestures, cleanup and in-flight stale render data.
Android debug assembly and lint passed (zero errors, the unchanged API37
annotation advisory). Independent review and a separate full helper-suite rerun
passed. These checks model visibility policy/queue behavior, not Android compositor
presentation. Source review confirms only the input thread changes pad surface
visibility; an old worker draw cannot re-show a hidden pad. No new emulator or
physical-phone visual test is claimed for this release. Verify on the phone that
long-hold relocation while zoomed never flashes the pad and immediate panning
still begins normally. Earlier-version runtime results below remain historical.


## 0.21 keyboard-focus fix: current validation

Based on 0.20 commit `8c8067827c78e7235e5969cfc3c69d4467979e26`.
Compilation now uses the official API 37.0 SDK, Android Gradle Plugin 9.1.1,
Gradle 9.3.1 and build-tools 36.0.0. Minimum API 34 and target API 35 are unchanged.
The original APK signer is retained; no new production permissions were added.
All 428,276 pure Java assertions pass. Assembly and lint pass with zero errors;
lint reports one `UseRequiresApi` advisory for the isolated `@TargetApi(37)`
helper. Independent source and compiled-bytecode review confirmed both creation
paths and every relayout use `focusable=false` behind explicit API 37 checks.
No AndroidX annotation dependency or hidden API workaround was added.

API 37 software-emulator testing did **not** verify keyboard retention. The
baseline service eventually started, but a trial failed before interaction because
the keyboard was not visible within its readiness window. A justified warm retry
then timed out before service readiness. Patched 0.21 installed and its service
bound manually, but the final startup probe aborted with “System has crashed.”
The captured system-server native stack was in task-snapshot/ImageReader graphics
code; independent `screencap` also aborted in the emulator graphics backend.
This is not an app keyboard test pass or an established app crash. No repeated
API 37 trials are counted as successful. The emulator was stopped afterward.

The same patched 0.21 APK passed actual API 35 service startup with an attached
bubble, then hold/pan/release: active 2.5x, changed viewport center, and inactive
1x after release. This exercises the older-platform branch and API37 class
isolation; it does not validate API37 keyboard retention.

The strengthened test fixture requires a focused editor and already-visible IME,
confirms the controller actually receives input, checks focus/IME throughout the
interaction, and verifies typing afterward. It never reopens the keyboard after
controller input. Supplemental screenshots can be explicitly disabled when the
emulator capture backend is broken, without disabling any functional assertions.
Pixel verification is still required: with a keyboard already visible, test a
controller tap, hold-and-pan, relocation, and interaction after rotation/unfolding;
the keyboard should stay open and typing should remain in the original editor.


## 0.20 overlay startup and external magnification ownership

Based on 0.19 commit `985e50389dcb8aeddafb6ca5e15f759c325dc08b`.
Pure Java regression checks passed 428,276 assertions, including 10,046 ownership
checks. Android debug assembly, test-APK assembly, and Android lint passed.
The normal APK adds no requested permissions. Independent source review covered
startup generation invalidation, partial cleanup, renderer resource ownership,
and external magnification transitions. Helper tests are not Android runtime tests.

Actual service tests ran on a dedicated Android 15 / API 35 AOSP x86_64 emulator
with software CPU acceleration and SwiftShader (no KVM), two cores and 2 GiB RAM.
After slow framework startup/recovery, display geometry was reduced to 480x800 at
160 dpi and system animation scales were disabled. These conditions validate
functionality, not Pixel smoothness, timing, or refresh rate.

- Baseline 0.19 reproduced the null-host `INTERNAL_SYSTEM_WINDOW` startup crash.
- Patched 0.20 enabled and bound the real accessibility service; its bubble appeared.
- Injected hold, move and release activated magnification, changed viewport center,
  and returned to inactive 1x on release.
- A separate test service's 2.5x fullscreen zoom survived controller release/cancel.
- External scale takeover and external off canceled the controller gesture;
  subsequent stale move/release did not restore the controller's old state.
- A separate test service's active window magnification retained mode, scale and
  center across controller release/cancel.
- The empty editor retained input focus after a controller tap and received a
  hardware key. The IME was not yet visible before that gesture (visible afterward),
  so this run does not establish preservation of an already-visible keyboard.
- Two forced local startup failures followed by retry stopped old renderer threads,
  retained the new surface, and permitted another successful hold/pan/release.

Initial harness failures (literal shell quotes and a fixed two-second editor launch
wait) were corrected; they are not counted as production failures. The emulator's
initial framework restart and ANRs predated these tests. The test APK and its
independent magnifier/editor are not included in the normal app APK.

Remaining checks on a physical Pixel: service disable/re-enable, normal hold and
sticky/double-tap gestures, keyboard/fold transitions, and coexistence with zoom
enabled outside this app. Public callbacks do not identify the owning service;
identical scale/mode handoffs and center-only changes cannot be reliably attributed.
Active window magnification is intentionally not controlled by this fullscreen app.
This does not claim to fix the separate Bluetooth-mouse input delay or all focus
behavior across Android versions. See `RUNTIME_TESTS.md` for dedicated-emulator steps.


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
