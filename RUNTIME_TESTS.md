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
