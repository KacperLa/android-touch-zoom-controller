# Dedicated-emulator runtime tests

**Run only on a dedicated emulator, never a personal phone.** The runner changes
enabled accessibility services and test preferences. Its test-only magnifier,
editor, input injection and optional screenshots are not included in the production
APK. No hidden platform APIs are used.

## Build and run

Select the intended emulator explicitly with `adb -s SERIAL` if more than one device
is connected. Build and install both matching APKs:

```sh
gradle :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Run each scenario separately. Before **every** run, disable services and wait for
the previous accessibility connection to finish; do not race an existing binding:

```sh
adb shell settings put secure enabled_accessibility_services null
adb shell settings put secure accessibility_enabled 0
adb shell am instrument -w -r -e scenario startup \
  io.github.kacperla.touchzoom.test/io.github.kacperla.touchzoom.ServiceRuntimeProbe
```

Replace `startup` with a scenario below and repeat the disable preamble. Inspect
explicit PASS/FAIL output; command exit status alone is insufficient. Disable the
test services after finishing, or discard the dedicated emulator.

## Core scenarios

- `startup`: actual service startup and attached overlay.
- `hold`: magnifier activation, changed center and release.
- `external`: preserve another test service's full-screen zoom on release/cancel.
- `window`: leave another test service's window magnification unchanged.
- `takeover`: cancel stale gesture work after another service changes scale or turns off.
- `retry`: force local startup failure and verify cleanup does not destroy new surfaces.

## Already-visible keyboard and focus

Run `focus-tap` (also named `focus`), `focus-hold`, `focus-relocate`,
`focus-relayout` and `focus-rotate` separately. `focus-all` runs all five with a
fresh editor for each. Every trial requires a focused test editor and an
already-visible keyboard before controller input. A missing keyboard fails the
precondition; it is not a skipped success.

The fixture checks touch receipt, continued focus/keyboard visibility and hardware
key input. It never reopens the keyboard after controller interaction. Relayout
uses the service's actual geometry path; rotation uses public emulator controls.

On API 37+, the runner checks public hosted-window focusability. Current builds
require `false`. `-e expectNonFocusable true|false` overrides that expectation for
explicit baseline comparisons; do not weaken it for current-build acceptance.
Older-platform core tests exercise the host-token fallback instead.

## Event-based keyboard avoidance

- `ime-events`: show/hide cycles, lift/restore, saved position and idle query stability.
- `ime-events-retry`: observer replacement and resynchronization after startup retry
  with an already-visible keyboard.
- `ime-events-rotate`: geometry and keyboard event handling through rotation.
- `ime-events-resize`: an actual keyboard height change while still visible. The
  installed keyboard must change height between text and numeric layouts; an
  unchanged height fails the precondition rather than passing a no-op test.

These use a separate editor and installed keyboard. `-e skipControllerTouch true`
provides observer-only coverage and must never be reported as a touch-focus pass.

## Reporting limits

If the emulator's screenshot backend independently crashes,
`-e skipScreenshot true` omits only the supplemental precondition PNG. It retains
functional keyboard bounds, focus, touch and typing assertions. Record the omission;
do not claim visual screenshot verification.

`UiAutomation.executeShellCommand` splits arguments without shell quote removal,
so the runner's fixed component values are intentionally unquoted. Failure reports
include startup status and main-thread stack, not screen contents.

Building the test APK does **not** run instrumentation. Software-emulator results
are not physical-device latency/FPS measurements. The menu and TalkBack still need
manual checks. See [VALIDATION.md](VALIDATION.md) for actual historical coverage and
the current device checklist.
