# Zoom Touch Controller

Zoom Touch Controller is a local GNOME Shell 50 extension for Wayland. When
the system magnifier is active, it places an unmagnified circular touch target
near the primary monitor's bottom-right corner. Tap the idle circle to click the
primary mouse button, or touch and drag on the expanded pad to move the pointer
without clicking. Lifting the finger collapses the pad.

Double-tap the circle to switch between the current zoom level and 1x (zoom
off). The previous zoom level is remembered, so another double tap restores it.
On the second tap, keep the finger down and drag upward to zoom in or downward
to zoom out; roughly half the configured pad height doubles or halves the zoom.
Live changes are synchronized to display frames and the final level is saved on
release. A single tap waits for the system double-click interval before its
primary click is sent.

When the pad opens, its position reflects the current pointer position: if the
pointer is at the top-right of the monitor, the initial finger position is the
top-right of the pad. This gives the entire pad one constant movement scale
instead of changing sensitivity on either side of the initial touch.

Only touchscreen sequences are captured. Mouse, touchpad, and stylus input is
left alone. The target is dimmed and unavailable while the pointer is outside
the primary monitor. More than three scaled UI pixels of touch movement commits
the gesture to cursor movement and permanently suppresses its click. A touch
that begins on the controller is filtered before normal desktop event
processing, including any pointer events emulated from that touch. Its visible
surface remains in Shell's reactive pick tree, so Mutter targets the Shell
rather than a Wayland application beneath it.

Hold the circle without moving until it turns blue, then drag it to reposition
the controller. The position is stored as normalized primary-monitor
coordinates, so it persists and adapts to monitor geometry changes.

Cursor output is driven by the controller actor's Clutter frame clock, matching
the refresh rate of its monitor. The extension estimates the touchscreen sample
interval and interpolates intermediate pointer positions on display frames;
the interpolation window adapts to pad gain and magnification, and fractional
absolute pointer events avoid magnifying whole-pixel rounding into visible
steps. Cursor release ignores the unstable lift-off sample and preserves the
last rendered pointer position instead of snapping across the remaining
interpolation.

## Build and install

Requirements are GNOME Shell 50, `gnome-extensions`, `gjs`, GLib's
`glib-compile-schemas`, GNU Make, and a Wayland session.

```sh
make test
make pack
make install
```

Log out and back in after the first installation, then enable the extension:

```sh
gnome-extensions enable zoom-touch-controller@kacperla.github.io
```

Use the extension's preferences window to adjust the collapsed joystick diameter
and expanded pad size. Values are UI pixels and follow GNOME's current UI scale;
changes apply immediately. `make install` overwrites a previous local
installation; restart the Shell session to load changed code.

## Nested GNOME Shell development session

GNOME Shell 50's Wayland devkit mode allows integration testing without
replacing the current desktop. Install the extension first, then launch a
nested session from a terminal in the existing graphical session:

```sh
dbus-run-session -- gnome-shell --devkit --wayland
```

Inside the nested session, enable the extension and turn on Accessibility >
Zoom. Test at 1x, 2x, and higher zoom factors, monitor layout changes, rotation,
scaling, hot-plugging, repeated magnifier toggles, and extension disable/enable.
Close the nested Shell and launch it again after rebuilding the extension.

Useful diagnostics from the host are:

```sh
journalctl --user -f -o cat /usr/bin/gnome-shell
gnome-extensions info zoom-touch-controller@kacperla.github.io
```

## Design notes

The controller is attached directly to `global.stage` above magnifier zoom
regions, rather than to `Main.uiGroup`, whose clone is magnified. Controller
dimensions are user-configurable UI pixels multiplied by Shell's live theme
scale factor. Mapping logic lives in `mapping.js` and can be tested without
constructing Shell actors.

## License

GPL-2.0-or-later. See `COPYING`.
