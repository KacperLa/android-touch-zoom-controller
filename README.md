# Touch Zoom Controller for Android

A floating touch controller for Android's full-screen system magnifier, inspired by
[gnome-touch-zoom-controller](https://github.com/KacperLa/gnome-touch-zoom-controller).
Use one finger to zoom and pan while the controller and its quick menu stay outside
the magnified content.

**Android 14 / API 34 or newer.** Compatibility depends on the device and Android
build. This repository contains version **0.33-repository-cleanup**, targeting API 36.
See [validation and known test gaps](VALIDATION.md) before relying on a build.

## Controls

- **Unzoomed:** drag immediately to relocate the bubble. Hold, then drag to zoom
  and pan; releasing ends this temporary zoom.
- **Already zoomed:** drag immediately to pan. Hold, then drag to relocate the
  bubble without turning zoom off.
- **Single, double and triple tap:** choose None, Show Menu or Zoom In-Out for each.
  Fresh installations default to **Show Menu**, **Zoom In-Out**, and **None**,
  respectively. Upgrades preserve existing choices, including the legacy
  single-tap zoom toggle.
- **Double-tap and slide:** with Adjust Zoom Level enabled (the default), slide up
  to increase zoom or down to reduce it. An unzoomed start begins at 1x; release
  keeps the selected zoom. Returning to 1x turns zoom off. Disabling this gesture
  does not disable the configured double-tap action.

Single and double taps wait for the next-tap interval. A third tap cancels the
pending double action, even when Triple Tap is None. Movement during double-tap
and slide takes precedence over the tap action.

The bubble grows to 150% only while relocating. It hides during panning; the
expanded touchpad appears only after panning is selected, not while waiting to
relocate existing zoom. The pad preserves the display's aspect ratio and maps
linearly across the screen. Near an edge it can extend off-screen; move the bubble
inward if needed. Android clamps magnification at screen edges.

### Quick menu

Show Menu opens an unmagnified charcoal panel with a zoom slider, Zoom switch and
Settings shortcut. The bubble and its touch input stay hidden while the menu is
open. With Zoom on, the slider adjusts magnification live from **1.1–8x**. With
Zoom off, it selects the remembered level without enabling zoom.

Tap outside the panel within its content area, or use the accessibility Dismiss
menu action, to close it. There is no visible Close button. System Back and hardware
keys go to the underlying app because the menu does not take key focus. Rotation,
keyboard-bound changes and lifecycle interruptions dismiss the menu. Dismissal
cancels unsent slider updates; it does not undo changes already applied.

## Settings

- **Color:** white (default), blue, red, green, yellow or orange for the controller
  outline, C-shaped ring and dot. The charcoal background stays unchanged; the
  menu uses the selected accent too.
- **Idle opacity:** 10–100%, default **75%** for new or unset preferences.
- **Touchpad longer edge:** 80–360 dp, default **180 dp**.
- **Extra hold delay:** 0–2000 ms, default **900 ms**, plus a fixed 100 ms recognition
  pause. The default total hold time is one second.

Existing position, scale, opacity, pad size, hold delay and action choices survive
updates. Settings are stored locally. Persistent zoom is not restored after the
accessibility service restarts. Light engagement/release haptics follow system settings.

## Enable and recover

1. Install a trusted APK or [build your own](#build).
2. Open **Touch Zoom**, then open Android accessibility settings from the app.
3. Enable **Touch Zoom Controller** yourself. Android may require restricted-settings
   approval for sideloaded apps. Only grant accessibility access to code you trust.
4. Use the floating controller.

Advanced settings include **Turn magnification off**, **Disable controller service**
and diagnostics. If overlay startup fails, the app reports its status and offers
**Retry controller startup**. Android accessibility settings can also disable the
service. Android's separate accessibility shortcut button is not required to use
this controller.

## Compatibility and limitations

- Only the default display and **full-screen magnification** are supported. Active
  window magnification is left unchanged. Existing full-screen zoom is preserved
  after ordinary pan/release, but public callbacks cannot reliably identify every
  handoff between accessibility services.
- A known API 35 overlay startup failure was fixed in **0.20** using a public
  accessibility-host token fallback. API 35 startup and hold/pan/release passed on
  that version and 0.21. These historical results do not certify the current build
  on every device.
- On **API 37 and newer**, explicit nonfocusable embedded-host parameters protect
  the underlying editor's key focus. The API call is guarded; older Android versions
  use the existing host-token path. Equivalent keyboard behavior across versions
  has not been established by runtime testing.
- Keyboard avoidance is **event-based**, with no periodic polling fallback. Docked
  keyboard insets can lift the bubble and restore its preferred position after
  closing. Missing inset events or floating keyboards can prevent this. Keyboard,
  fold, TalkBack and OEM-specific behavior still need device testing.
- Testing found severe input delays with a **Bluetooth mouse connected**. Disconnecting
  it resolved the observed delays. The platform cause remains unconfirmed. Stop
  testing if the system becomes unresponsive; no hidden-API workaround is used.
- Requested refresh rates and Android state acknowledgments do not establish
  presented frame rate or physical-device responsiveness.

## Privacy and diagnostics

The production service controls magnification and displays its own overlays. It
cannot retrieve window content and does not capture screenshots, inject taps,
request internet access or use analytics. Backup and device-transfer exclusions
are configured. Keyboard avoidance reads display/inset geometry, not typed text.

Optional **Record 15-second diagnostic** records numeric touch positions,
input/arrival timing, magnification state, device model and Android version. The
bounded in-memory capture spans gestures and idle intervals; old entries can be
overwritten. The 15 seconds limits recording, not retention. Clear diagnostics
erases the app capture, not text already copied or shared. View reads the capture and Copy
copies it only when selected. Nothing is uploaded. Review diagnostic text before
sharing it. The separate test APK has additional test-only capabilities; see
[RUNTIME_TESTS.md](RUNTIME_TESTS.md).

## Build

Requirements: a complete **JDK 17+**, **Python 3**, **Gradle 9.3.1**, Android SDK
platform **37.0**, and build-tools **36.0.0**. Gradle fetches Android Gradle Plugin
**9.1.1**. The project targets Java 17 and declares no third-party runtime dependencies;
build tooling can add implicit libraries.
A Gradle wrapper is not included.

Set `ANDROID_HOME` or an SDK path in an untracked `local.properties`, then run:

```sh
bash scripts/test.sh
gradle :app:assembleDebug :app:lintDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For an unsigned,
non-debuggable release APK:

```sh
gradle :app:assembleRelease :app:lintRelease
```

Output: `app/build/outputs/apk/release/app-release-unsigned.apk`.

### Signing and updates

Ordinary debug builds use a local debug key. **An APK signed with a different key
cannot update an existing installation.** Keep the original signer for in-place
updates; uninstalling removes local settings. Do not replace a key just to make
installation succeed.

For a debug build with an explicitly selected private signer, set
`TOUCH_ZOOM_KEYSTORE`, `TOUCH_ZOOM_STORE_PASSWORD`, `TOUCH_ZOOM_KEY_ALIAS` and
`TOUCH_ZOOM_KEY_PASSWORD` privately. Invalid or incomplete configuration fails
rather than falling back to another signer. These settings do not automatically
sign release APKs. Keep keys, passwords, private backups and diagnostic captures
out of Git.

[CI.md](CI.md) covers branch builds, downloading an unsigned artifact for local
original-key signing, and the separate trusted-main signing workflow. Branch
integration does not imply a merge to `main` or a change of signing identity.

## Tests and development

The helper suite covers gesture policy, mapping, settings migration, keyboard
geometry, magnification ownership, diagnostic buffers, rendering queues and scale
backpressure. Source-wiring checks cover menu/controller visibility. Neither
proves Android compositor, input routing or accessibility behavior.

- [Validation status and device checklist](VALIDATION.md)
- [Dedicated-emulator runtime tests](RUNTIME_TESTS.md)
- [Build and signing workflows](CI.md)
- [Release history](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)

Implementation uses native Java and
[AccessibilityService.MagnificationController](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.MagnificationController),
with display-attached `SurfaceControlViewHost` overlays. Panning coalesces the
latest position on display callbacks. Scale updates are bounded while awaiting
Android feedback rather than replaying intermediate values.

## License and reference material

**GPL-2.0-or-later.** See [COPYING](COPYING) and [LICENSE](LICENSE).

`reference/` preserves the GNOME behavioral-reference snapshot obtained from
[KacperLa/gnome-touch-zoom-controller](https://github.com/KacperLa/gnome-touch-zoom-controller)
on October 30, 2026, with its original notices. It is reference material, not an
Android runtime dependency or a complete GNOME extension distribution.
