# Release history

This history describes source changes, not a device-compatibility certification.
See [VALIDATION.md](VALIDATION.md) for the distinction between automated checks,
historical runtime results and pending device tests.

## 0.33-repository-cleanup

- Integrates the later controller/settings/menu work into the repository branch.
- Reorganizes documentation and enables unsigned branch build artifacts.
- Retains minimum API 34, target API 36, compile SDK 37.0 and existing runtime behavior.

## 0.30–0.32

- 0.30: target API 36 and an explicitly non-debuggable release build.
- 0.31: matching launcher/adaptive/monochrome C-and-dot artwork and updated help text.
- 0.32: combines that artwork and guidance with target API 36; settings and diagnostics
  identify the built version.

## 0.24–0.29

- 0.24: native settings pages, configurable single/double/triple taps, six foreground
  colors and migration that preserves existing preferences. Fresh single taps open
  the menu; legacy single taps continue to toggle zoom.
- 0.25: display-attached, unmagnified quick menu with bounded placement and dismissal.
- 0.26: fixes visibility of the menu's inner surface root.
- 0.27: charcoal menu with controller accent, native controls and outside/accessibility
  dismissal; removes the visible Close button.
- 0.28: compact menu, transparent outside area and live slider updates with bounded
  feedback handling. With Zoom off, the slider only changes the remembered scale.
- 0.29: hides controller rendering, input and accessibility presence while the menu
  is open. New/unset idle opacity defaults to 75%; saved values are preserved.

## 0.20–0.23

- 0.20: public host-token fallback fixes the observed API 35 overlay startup failure.
  Adds startup cleanup/retry and conservative handling of externally owned
  magnification. Active window magnification is left alone.
- 0.21: API 37-guarded nonfocusable host parameters on creation and relayout.
  Adds stricter already-visible-keyboard test scenarios.
- 0.22: removes the touchpad flash while waiting to relocate existing zoom.
- 0.23: replaces recurring keyboard checks with inset-event observation and explicit
  startup/screen-on resynchronization. There is no periodic fallback.

## Earlier baseline

- 0.18: double-tap sliding starts at actual scale (1x when unzoomed), keeps the
  selected zoom on release, and returns to off at 1x. Already-zoomed drag pans;
  stationary hold selects relocation. Removes the earlier animation experiment.
- 0.19: avoids waiting for an acknowledgment of an unchanged 1x request before
  sending the first real scale change.
- The earlier prototype provided the floating controller, momentary zoom, pad
  mapping, haptics, opacity, diagnostic capture and original GPL-2.0-or-later notice.
