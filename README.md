# Touch Zoom Controller for Android

A floating touch controller for Android's full-screen system magnifier, inspired by
[gnome-touch-zoom-controller](https://github.com/KacperLa/gnome-touch-zoom-controller).
The controller stays outside the magnified content and provides a compact touchpad
for panning around the screen.

**Prototype version 0.16. Android 14 / API 34 or newer is required, but compatibility
varies by device and Android build.** This is not a Play Store release.

## Controls

- **Drag immediately:** move the controller. It grows to 150% while relocating,
  keeps its charcoal/white colors, and returns to normal on release.
- **Hold, then drag:** activate magnification and pan. Release to zoom out, unless
  persistent zoom was already enabled.
- **Quick tap:** toggle persistent zoom. The toggle waits for the double-tap
  confirmation interval. When extra hold delay is zero, release within 100 ms for
  a tap; a longer hold engages momentary zoom.
- **Double-tap and hold the second touch, then slide vertically:** change the
  remembered magnification scale. See the Bluetooth-mouse warning below.
- During active panning, the controller artwork hides while the touchpad remains.
  It reappears after release so persistent zoom can be switched off.

The touchpad preserves the display's aspect ratio. Smaller pad sizes reduce thumb
travel. It anchors the finger to the current magnifier center, with the full pad
mapping linearly across the screen. Near an edge, part of the pad may lie off-screen;
move the controller inward if needed. Android clamps magnification at screen edges.

A docked keyboard can temporarily lift the controller out of the covered area.
Floating keyboards may not report a usable height. Rotation, folding, multitouch,
and settings changes cancel active gestures.

## Settings

The floating controller uses original Orbit artwork: a white C-shaped ring and
central dot on charcoal, with a black/white perimeter for light and dark content.
This distinguishes it from Android's magnifying-glass accessibility shortcut.
The launcher icon is unchanged.

- Touchpad longer edge: **80–360 dp**, default **180 dp**, with preview/reset.
- Extra hold delay: **0–2000 ms**, in addition to a **100 ms** recognition pause.
  The default extra delay is 900 ms, for a total hold time of 1 second.
- Idle opacity: **10–100%**, default **40%**.
- Light engagement/release haptics follow system settings.

Position, scale, and settings are stored locally. Persistent zoom itself is not
restored after the accessibility service restarts.

## Setup and recovery

1. Build and install a development APK, or use a trusted build.
2. Open **Touch Zoom Prototype** and open Android accessibility settings.
3. Enable **Touch Zoom Controller** yourself. Android may require restricted-settings
   approval for a sideloaded application. Only grant accessibility access to code you trust.
4. Use the floating controller.

The app includes **Turn magnification off** and **Disable controller service**
controls. Android accessibility settings can also disable the service. An Android
accessibility shortcut button is separate from this app's controller and is not
needed to operate the floating controller once the service is enabled.

## Known limitations

### Bluetooth mouse can cause severe delay

Testing found delayed touch tracking after magnification activation and an input
backlog during scale adjustment while a Bluetooth mouse was connected. Disconnecting
the mouse resolved both delays. If this happens, disconnect the mouse and avoid
repeated scale-adjustment tests while the system is struggling.

A possible Android implementation lead is pointer-icon resource reloading during
magnification scale changes. AOSP source contains a shared input-manager lock in
that path, but the exact cause on the affected device has not been established.
No supported public magnification API was found that suppresses pointer rescaling.
This application does not attempt hidden-API or privileged-permission workarounds.

### Overlay compatibility

An API 35 AOSP emulator rejected the current `SurfaceControlViewHost` overlay at
service initialization with an `INTERNAL_SYSTEM_WINDOW` permission error. The
prototype works on a tested physical device, but the API minimum does not guarantee
support on every Android 14+ build. See [VALIDATION.md](VALIDATION.md).

Only the default display and full-screen magnification are targeted. Fold behavior,
floating keyboards, other accessibility services, and OEM changes require further
compatibility testing. A requested high refresh rate is a preference, not a guarantee.

## Privacy and diagnostics

The service controls magnification and displays its own overlay. It does not request
screen-content retrieval, capture screenshots, inject taps, send network traffic,
or use analytics. Android backup is disabled. Keyboard avoidance uses display/inset
information, not typed text.

Optional **Record continuous 15-second diagnostic** captures numeric touch positions,
input/arrival timing, magnification state, device model and Android version. It spans
multiple gestures and idle intervals. The bounded capture is kept in memory and
older entries can be overwritten. **Clear diagnostics** erases it; **Refresh** and
**Copy diagnostic text** read the current capture. Nothing is uploaded or copied
without the user selecting the relevant control. Review diagnostic text before sharing.

## Build

Requirements:

- JDK 17 or newer (the project targets Java 17)
- Gradle 8.13
- Android SDK platform 35 and build-tools 35
- Android Gradle Plugin 8.9.2, fetched by Gradle

Set `ANDROID_HOME` or create a local `local.properties` pointing to your SDK. Then:

```sh
gradle :app:assembleDebug :app:lintDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
A Gradle wrapper is not included; install the specified Gradle version separately.
The app has no third-party runtime dependencies.

### Signing

Ordinary development builds use the Android Gradle plugin's local debug key.
**A locally built APK will not update an existing installation signed with a
different key.** Use a separate test installation or the original matching signer.
Uninstalling an existing installation removes its local settings.

For a deliberately selected private signer, set all of these environment variables:

- `TOUCH_ZOOM_KEYSTORE`: path to your keystore outside the repository
- `TOUCH_ZOOM_STORE_PASSWORD`
- `TOUCH_ZOOM_KEY_ALIAS`
- `TOUCH_ZOOM_KEY_PASSWORD`

An explicitly configured keystore must exist and have all required values; the build
will not silently fall back to a different signer. Keep signing material and passwords
private. No signing key, private backup, password, or device capture belongs in Git.

## Tests

The pure Java helper tests do not require an Android SDK:

```sh
./scripts/test.sh
```

They cover mapping, gestures, settings, bounds, diagnostics, rendering queues,
refresh-rate decisions, and scale backpressure. These checks do not replace device
integration tests. Android compilation/lint and manual checks are described in
[VALIDATION.md](VALIDATION.md).

## Implementation

Native Java using `AccessibilityService.MagnificationController`. API 34
accessibility overlays attach a small touchable `SurfaceControlViewHost` controller
and a noninteractive canvas touchpad to the display. Panning uses latest-position
updates synchronized with display callbacks. Scale updates are bounded and await
reported state; a state acknowledgment is not proof that the compositor has finished.

## License and reference material

**GPL-2.0-or-later.** See [COPYING](COPYING) for the license notice and
[LICENSE](LICENSE) for the GNU GPL version 2 text. This retains the license declared
by the original prototype archive.

The `reference/` directory preserves the GNOME behavioral-reference snapshot supplied
with the prototype, downloaded on October 30, 2026 from
[KacperLa/gnome-touch-zoom-controller](https://github.com/KacperLa/gnome-touch-zoom-controller).
Its original notices and files are retained unchanged. The snapshot is reference
material, not a complete GNOME extension distribution or an Android runtime dependency.

Android API references:
- [MagnificationController](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.MagnificationController)
- [Accessibility display overlays](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#attachAccessibilityOverlayToDisplay(int,android.view.SurfaceControl))
- [SurfaceControlViewHost](https://developer.android.com/reference/android/view/SurfaceControlViewHost)

## GitHub Actions

Pull requests run tests, lint, and an unsigned release build without signing secrets.
A separate manual workflow can sign a trusted `main` build with the original key.
See [CI.md](CI.md) for the required protected environment and encrypted secrets setup,
artifact visibility, certificate verification, and current validation limits.

## 0.18: double-tap drag starts at actual zoom and stays zoomed

Based directly on preserved0.16, with the0.17 animation experiment removed
(including its setting). A double-tap drag while unzoomed starts at1x, even when
ordinary hold-to-pan remembers2x or another scale. When already zoomed, adjustment
starts from current actual scale. Drag upward to increase zoom; normal release
keeps the selected zoom latched and remembers that selection. A later quick tap
turns it off. Dragging back to1x releases zoom rather than leaving an invisible latch.

Release drops queued intermediate updates, cancels the gesture's scale gate and
callbacks, then commits one latest selected scale. Cancel/multitouch/interruption
never creates a new latch. Ordinary hold-to-pan remains momentary; quick taps,
relocation and the Orbit C-dot artwork/150% drag enlargement/haptics are unchanged.
This is not a Bluetooth mouse compatibility fix. Existing local preference data
for the removed experiment is ignored; no extra animation callbacks run.

Already zoomed reverses the ordinary controller gesture: drag immediately to pan;
hold stationary, then drag to relocate the bubble while keeping zoom. The zoom
state is captured at DOWN and the first deliberate movement/hold locks the mode;
a later timer cannot change a pan into relocation. Double-tap scale adjustment
has precedence. Single tap still turns zoom off. Cancel/release of a pan over
externally enabled zoom does not disable that external zoom.
