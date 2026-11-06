# Validation and compatibility

## Current scope

The repository cleanup integrates the later settings, keyboard observer and quick
menu implementation into the source branch. Version 0.33 targets API 36, requires
API 34 and compiles against SDK 37.0. Repository integration is not a `main` merge,
a device test pass or a signing-identity change.

Use the exact commit and workflow run when reporting a build result. Helper tests,
lint and APK assembly validate different things; none establishes smoothness,
keyboard retention, TalkBack discovery or correct compositor/input behavior.
Current-build results should be taken from its completed CI run, not inferred from
the historical results below.

Local cleanup verification passed 42 Java helper suites with 478,570 assertions
and source guards, plus five Python menu-surface checks (478,575 checks total).
Android build/lint and device results must be verified separately.

## Automated checks

```sh
bash scripts/test.sh
gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
gradle :app:assembleRelease :app:lintRelease
```

The helper script compiles with `javac --release 17` and runs the pure Java suites
and Python source-wiring checks. Coverage includes gesture/tap policy, settings
migration, keyboard geometry and event coalescing, menu bounds/style, visibility,
magnification ownership, diagnostics, latest-position rendering and scale
backpressure. Source checks are not substitutes for runtime surface/input tests.

Previous builds reported `UseRequiresApi` for the explicitly guarded API 37 helper
and a SeekBar touch-listener accessibility advisory. Assess the actual lint report
for each build instead of assuming these are the only findings.

## Historical runtime evidence

### API 35 overlay startup and magnification

A dedicated Android 15 / API 35 AOSP software emulator reproduced the old null-host
`INTERNAL_SYSTEM_WINDOW` failure with 0.19. **The 0.20 fallback fixed that failure**;
it does not request privileged permission. Actual 0.20 service tests passed:

- Overlay startup and visible controller.
- Hold, pan and release: activation, changed center, then inactive 1x.
- Preservation of another service's full-screen zoom after release/cancel.
- Cancellation on external scale takeover or off, without stale-state restoration.
- Preservation of another service's active window magnification.
- Forced startup failures followed by successful retry and renderer cleanup.

An empty editor retained focus and accepted hardware input after a controller tap,
but the keyboard was not already visible. That trial is **not** keyboard-retention
coverage. Version 0.21 also passed API 35 startup and hold/pan/release. These older
results exercise the fallback; they do not validate the later menu and observer.
Software-emulator timing is not a physical-device latency or frame-rate measurement.

### API 37 keyboard focus

Source and compiled-bytecode inspection of 0.21 confirmed that embedded-host
creation and relayout use public `focusable=false` parameters behind API 37 guards.
This preserves the older-platform path and does not use hidden APIs.

API 37 emulator trials did **not** establish keyboard retention. They failed at
readiness or system graphics/capture stages before a valid completed interaction.
These failures are not app test passes or proof of an app crash. The test fixture
now requires a focused editor and an already-visible keyboard, actual controller
input and successful typing afterward, without reopening the keyboard after input.

### Later keyboard and menu changes

Versions 0.23 onward have helper/source coverage and recorded Android build/lint
checks. Software-emulator boot failures prevented usable new runtime coverage for
the event-based observer and later menu changes. There is no completed runtime
pass recorded here for current keyboard avoidance, menu input routing or TalkBack.

The observer depends on inset events; it has no periodic polling fallback. Floating
keyboards may not supply a usable inset. Menu/controller hiding checks verify source
wiring, not whether every Android build suppresses hidden input correctly.

### Bluetooth mouse delay

Device testing observed severe delayed touch tracking and scale-adjustment input
backlogs while a Bluetooth mouse was connected. Disconnecting it resolved those
observations. The underlying platform cause remains unconfirmed; no universal fix
is claimed. Stop rather than repeatedly stressing an unresponsive system.

## Current device checklist

Test an update signed by the original key, retaining existing preferences. Use a
separate clean installation when checking defaults; do not erase a working setup
just to exercise migration.

1. **Startup and recovery:** enable/disable/re-enable the service, retry startup if
   needed, and verify magnification-off and service-disable controls remain reachable.
2. **Gestures:** unzoomed immediate relocation and hold/pan/release; zoomed immediate
   pan and hold/relocation. Confirm 150% relocation growth and no pre-relocation pad
   flash. Cancel via multitouch, rotation and screen off/on.
3. **Actions and migration:** fresh defaults are menu/zoom/none. Upgrades retain
   previous action, position, scale, opacity, pad and hold choices. Test all tap
   actions, triple-None cancellation and double-slide enabled/disabled behavior.
4. **Menu:** open while zoomed, check unmagnified size and accurate touch targets,
   and confirm the bubble cannot appear or intercept input. Test live slider updates,
   final scale, Zoom-off remembered level, outside dismissal, reopening and Settings.
   After dismissal, touches must reach the underlying app again.
5. **Keyboard:** start with a docked keyboard already visible. Tap, pan, relocate
   and use the menu without losing editor focus or typing. Test show/hide, keyboard
   height changes, startup/retry while open, rotation/folding and screen off/on.
   The bubble should avoid the keyboard and return to its saved position.
6. **Layout and accessibility:** check large text, landscape, fold/unfold, system
   bars and both navigation modes. With TalkBack, check control discovery, slider
   range/value, Dismiss menu, and absence of hidden controller/menu nodes. System
   Back belongs to the underlying app while the nonfocusable menu is open.
7. **Magnification coexistence:** test externally enabled full-screen zoom, takeover
   during a gesture and active window magnification. Public callbacks cannot identify
   identical-state ownership handoffs or every center-only change.
8. **Settings and diagnostics:** verify six colors, saved idle opacity, pad/hold
   settings, system-respecting haptics and bounded 15-second capture, Clear and Copy.

Record Android/API version, app version, connection state and exact reproduction
steps. Review diagnostics before sharing; do not commit private device captures.
Dedicated-emulator instructions are in [RUNTIME_TESTS.md](RUNTIME_TESTS.md).
