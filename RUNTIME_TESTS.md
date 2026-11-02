# Dedicated-emulator runtime tests

These change enabled accessibility services and must NOT be run against a personal
phone. The test APK contains a separate test-only magnifier and an empty editor;
they are not included in the normal app APK. No hidden platform APIs are used.

Build `:app:assembleDebug :app:assembleDebugAndroidTest`, install both APKs on the
same dedicated emulator, then run each scenario separately. Before each run,
disable test services and wait for the previous connection to finish, so restarting
the instrumented application does not race an existing accessibility binding:

```
adb shell settings put secure enabled_accessibility_services null
adb shell settings put secure accessibility_enabled 0
adb shell am instrument -w -r -e scenario startup io.github.kacperla.touchzoom.test/io.github.kacperla.touchzoom.ServiceRuntimeProbe
```

Repeat the clean-disable preamble for `hold`, `external`, `window`, `takeover`,
`retry`, and `focus`. Inspect explicit PASS/FAIL output; exit status alone is not
enough. `hold` checks actual magnifier activation, changed center, and release;
`external`/`window` use the independent test service; `takeover` changes another
service's scale/off midgesture; `retry` forces a local startup failure and verifies
old renderer cleanup cannot destroy new surfaces; `focus` uses only the empty
editor to check focus, hardware input and existing IME visibility.

`UiAutomation.executeShellCommand` uses raw argument splitting rather than shell
quote removal, so the runner's fixed service-component values are deliberately
unquoted. On failure, startup diagnostics include app status and its main-thread
stack, not screen contents. Software-emulator timing is not Pixel latency/FPS
validation. See VALIDATION.md for actual results and remaining coverage.

## 0.21 already-visible keyboard checks

On API 37, run `focus-tap`, `focus-hold`, `focus-relocate`, `focus-relayout`, and
`focus-rotate` separately with the same clean-disable preamble. `focus-all` runs
all five with a fresh editor for each. Every trial requires both a focused test
editor and an already-visible IME before controller input. Missing keyboard is a
failure, not a skipped/pass condition. The fixture never reopens the keyboard
after controller input; it checks continued editor focus, IME presence and new
hardware-key text. Relayout uses the actual service geometry-change path; rotation
uses the emulator's public rotation control and waits for the editor/IME again.

The runner logs the public API 37 hosted-window focusability. Version 21 requires
`false`; baseline version 20 merely logs it so the actual keyboard symptom can be
observed. `-e expectNonFocusable true|false` explicitly overrides that expectation.
The older-platform startup/hold tests still exercise the unchanged fallback.

If an emulator's graphics-capture backend crashes independently of the app,
`-e skipScreenshot true` omits only the supplementary precondition PNG. It logs
that omission and retains every IME-bounds, focused-editor, touch-receipt and typing
assertion. Do not report visual screenshot verification when using this option.
