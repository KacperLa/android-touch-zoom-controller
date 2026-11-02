import Clutter from 'gi://Clutter';
import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import St from 'gi://St';

import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';

import {
    exceedsDragThreshold,
    interpolationSpanFactor,
    interpolatePoint,
    isNearbyTap,
    isTap,
    roiForMagnification,
    updateIntervalEstimate,
    zoomFactorFromVerticalDrag,
} from './gesture.js';
import {
    alignPadToCursor,
    calculatePadSize,
    movePad,
    normalizedPadCenter,
    placePad,
} from './geometry.js';
import {mapLinearPoint} from './mapping.js';

const PAD_MARGIN = 24;
const MARKER_SIZE = 22;
const TRANSITION_MS = 120;
const POINTER_POLL_MS = 250;
const DRAG_THRESHOLD = 3;
const TAP_MAX_DURATION_MS = 500;
const DOUBLE_TAP_DISTANCE = 32;
const EMULATED_BUTTON_GRACE_US = 100_000;
const MIN_TOUCH_INTERVAL_US = 4_000;
const DEFAULT_TOUCH_INTERVAL_US = 16_667;
const MAX_TOUCH_INTERVAL_US = 50_000;
const MAGNIFIER_SCHEMA = 'org.gnome.desktop.a11y.magnifier';
const MAG_FACTOR_KEY = 'mag-factor';
const MIN_MAG_FACTOR = 1;
const MAX_MAG_FACTOR = 32;
const DEFAULT_MAG_FACTOR = 2;
const ZOOM_DRAG_SPAN_FACTOR = 0.5;

function copyRect(rect) {
    return {x: rect.x, y: rect.y, width: rect.width, height: rect.height};
}

function contains(rect, x, y) {
    return x >= rect.x && x < rect.x + rect.width &&
        y >= rect.y && y < rect.y + rect.height;
}

class ZoomTouchController {
    constructor(settings) {
        this._settings = settings;
        this._gesture = null;
        this._monitor = null;
        this._pad = null;
        this._signals = [];
        this._pointerPollId = 0;
        this._cursorAvailable = null;
        this._expanded = false;
        this._pointerMotion = null;
        this._zoomTarget = null;
        this._zoomApplied = null;
        this._suppressEmulatedButtonsUntilUs = 0;
        this._longPressId = 0;
        this._pendingTap = null;
        this._pendingTapId = 0;
        const clutterSettings = Clutter.Settings.get_default();
        this._longPressDurationMs = clutterSettings.long_press_duration;
        this._doubleTapDurationMs = clutterSettings.double_click_time;
        this._magnifierSettings = new Gio.Settings({
            schema_id: MAGNIFIER_SCHEMA,
        });
        const currentMagFactor = this._magnifierSettings.get_double(
            MAG_FACTOR_KEY);
        this._lastMagFactor = currentMagFactor > MIN_MAG_FACTOR
            ? currentMagFactor
            : DEFAULT_MAG_FACTOR;
        this._themeContext = St.ThemeContext.get_for_stage(global.stage);
        this._updateScaledDimensions();

        this._actor = new St.Widget({
            name: 'zoom-touch-controller',
            style_class: 'zoom-touch-controller',
            reactive: false,
            visible: false,
        });
        this._surface = new St.Widget({
            style_class: 'zoom-touch-controller-surface idle',
            reactive: true,
        });
        this._marker = new St.Widget({
            style_class: 'zoom-touch-controller-marker',
            reactive: false,
            visible: false,
            width: this._markerSize,
            height: this._markerSize,
        });
        this._actor.add_child(this._surface);
        this._actor.add_child(this._marker);

        global.stage.add_child(this._actor);
        this._seat = global.stage.get_context().get_backend().get_default_seat();
        this._virtualPointer = this._seat.create_virtual_device(
            Clutter.InputDeviceType.POINTER_DEVICE);
        this._pointerTimeline = Clutter.Timeline.new_for_actor(
            this._actor, 1000);
        this._pointerTimeline.set_repeat_count(-1);
        this._pointerTimelineSignalId = this._pointerTimeline.connect('new-frame',
            () => {
                this._advancePointerMotion();
                this._advanceZoomAdjustment();
            });
        // Keep the visible surface in Clutter's reactive pick tree. Mutter's
        // Wayland filter runs before extension event filters, so picking a
        // Shell actor here is what prevents the touch reaching a client.

        this._signals.push([
            Main.magnifier,
            Main.magnifier.connect('active-changed',
                (_magnifier, active) => this._onMagnifierActiveChanged(active)),
        ]);
        this._signals.push([
            this._magnifierSettings,
            this._magnifierSettings.connect(`changed::${MAG_FACTOR_KEY}`,
                () => this._rememberMagnification()),
        ]);
        this._signals.push([
            Main.layoutManager,
            Main.layoutManager.connect('monitors-changed',
                () => this._onMonitorsChanged()),
        ]);
        this._signals.push([
            this._themeContext,
            this._themeContext.connect('notify::scale-factor',
                () => this._onGeometryChanged()),
        ]);
        for (const key of ['joystick-size', 'pad-size']) {
            this._signals.push([
                this._settings,
                this._settings.connect(`changed::${key}`,
                    () => this._onGeometryChanged()),
            ]);
        }
        this._signals.push([
            global.stage,
            global.stage.connect('child-added',
                (_stage, child) => this._onStageChildAdded(child)),
        ]);
        this._signals.push([
            global.stage,
            global.stage.connect('child-removed',
                (_stage, child) => this._onStageChildAdded(child)),
        ]);
        // Event filters run before signal emission and input grabs. This makes
        // an owned controller touch invisible to normal desktop processing.
        this._eventFilterId = Clutter.Event.add_filter(
            global.stage, event => this._filterEvent(event));

        this._recalculateGeometry();
        this._onMagnifierActiveChanged(Main.magnifier.isActive());
    }

    destroy() {
        Clutter.Event.remove_filter(this._eventFilterId);
        this._eventFilterId = 0;
        this._cancelGesture(false);
        this._cancelPendingTap();
        this._stopLongPressWatch();
        this._stopPointerPoll();

        for (const [object, id] of this._signals)
            object.disconnect(id);
        this._signals = [];

        this._pointerTimeline.stop();
        this._pointerTimeline.disconnect(this._pointerTimelineSignalId);
        this._pointerTimelineSignalId = 0;
        this._actor.destroy();
        // Dispose immediately so no synthetic button can remain pressed.
        this._virtualPointer.run_dispose();
        this._actor = null;
        this._surface = null;
        this._marker = null;
        this._pointerTimeline = null;
        this._virtualPointer = null;
        this._seat = null;
        this._themeContext = null;
        this._settings = null;
        this._magnifierSettings = null;
    }

    _onMagnifierActiveChanged(active) {
        if (!active) {
            this._cancelGesture(false);
            this._cancelPendingTap();
            this._stopPointerPoll();
            this._actor.remove_all_transitions();
            this._surface.remove_all_transitions();
            this._actor.hide();
            return;
        }

        this._recalculateGeometry();
        if (!this._monitor)
            return;

        this._actor.show();
        this._raiseAboveMagnifier();
        this._updateCursorAvailability();
        this._startPointerPoll();
    }

    _onMonitorsChanged() {
        this._cancelGesture(false);
        this._cancelPendingTap();
        this._recalculateGeometry();

        if (Main.magnifier.isActive() && this._monitor) {
            this._actor.show();
            this._raiseAboveMagnifier();
            this._updateCursorAvailability();
        } else {
            this._actor.hide();
        }
    }

    _onGeometryChanged() {
        this._cancelGesture(false);
        this._cancelPendingTap();
        this._updateScaledDimensions();
        this._recalculateGeometry();
    }

    _updateScaledDimensions() {
        const scaleFactor = this._themeContext.scale_factor;
        this._idleSize = Math.round(
            this._settings.get_int('joystick-size') * scaleFactor);
        this._padLongSide = Math.round(
            this._settings.get_int('pad-size') * scaleFactor);
        this._padMargin = Math.round(PAD_MARGIN * scaleFactor);
        this._markerSize = Math.round(MARKER_SIZE * scaleFactor);
        this._dragThreshold = DRAG_THRESHOLD * scaleFactor;
        this._doubleTapDistance = DOUBLE_TAP_DISTANCE * scaleFactor;

        if (this._marker)
            this._marker.set_size(this._markerSize, this._markerSize);
    }

    _onStageChildAdded(child) {
        if (child !== this._actor && this._actor.visible &&
            this._actor.get_parent() === global.stage)
            this._raiseAboveMagnifier();
    }

    _raiseAboveMagnifier() {
        global.stage.set_child_above_sibling(this._actor, null);
    }

    _recalculateGeometry() {
        const primary = Main.layoutManager.primaryMonitor;
        if (!primary) {
            this._monitor = null;
            this._pad = null;
            this._actor.hide();
            return;
        }

        this._monitor = copyRect(primary);
        const size = calculatePadSize(
            this._monitor, this._idleSize, this._padLongSide);
        this._pad = placePad(this._monitor, size, this._padMargin, {
            x: this._settings.get_double('position-x'),
            y: this._settings.get_double('position-y'),
        });
        this._applyPadGeometry(this._pad);
        this._setExpanded(false, false);
    }

    _idleCircle() {
        return {
            x: this._pad.x + (this._pad.width - this._idleSize) / 2,
            y: this._pad.y + (this._pad.height - this._idleSize) / 2,
            width: this._idleSize,
            height: this._idleSize,
        };
    }

    _isInsideIdleCircle(x, y) {
        const circle = this._idleCircle();
        const centerX = circle.x + circle.width / 2;
        const centerY = circle.y + circle.height / 2;
        const offsetX = x - centerX;
        const offsetY = y - centerY;
        const radius = circle.width / 2;
        return offsetX * offsetX + offsetY * offsetY <= radius * radius;
    }

    _setExpanded(expanded, animate = true) {
        if (!this._pad)
            return;

        this._expanded = expanded;
        this._surface.remove_all_transitions();
        this._surface.set_style_class_name(
            `zoom-touch-controller-surface ${expanded ? 'expanded' : 'idle'}`);

        const target = expanded
            ? {x: 0, y: 0, width: this._pad.width, height: this._pad.height,
                opacity: 238}
            : {x: (this._pad.width - this._idleSize) / 2,
                y: (this._pad.height - this._idleSize) / 2,
                width: this._idleSize, height: this._idleSize, opacity: 210};

        if (animate) {
            this._surface.ease({
                ...target,
                duration: TRANSITION_MS,
                mode: Clutter.AnimationMode.EASE_OUT_QUAD,
            });
        } else {
            this._surface.set_position(target.x, target.y);
            this._surface.set_size(target.width, target.height);
            this._surface.opacity = target.opacity;
        }

        if (!expanded)
            this._marker.hide();
    }

    _startPointerPoll() {
        if (this._pointerPollId)
            return;

        this._pointerPollId = GLib.timeout_add(
            GLib.PRIORITY_DEFAULT, POINTER_POLL_MS, () => {
                this._updateCursorAvailability();
                return GLib.SOURCE_CONTINUE;
            });
    }

    _stopPointerPoll() {
        if (!this._pointerPollId)
            return;

        GLib.source_remove(this._pointerPollId);
        this._pointerPollId = 0;
    }

    _updateCursorAvailability() {
        if (!this._monitor)
            return;

        const [x, y] = global.get_pointer();
        const available = contains(this._monitor, x, y);
        if (available === this._cursorAvailable)
            return;

        this._cursorAvailable = available;
        this._surface.reactive = available;
        if (available)
            this._actor.remove_style_pseudo_class('unavailable');
        else
            this._actor.add_style_pseudo_class('unavailable');
    }

    _onTouchEvent(event) {
        if (!this._actor.visible || !this._monitor || !this._pad)
            return Clutter.EVENT_PROPAGATE;

        const type = event.type();
        const sequence = event.get_event_sequence();
        const slot = sequence.get_slot();

        if (type === Clutter.EventType.TOUCH_BEGIN) {
            if (this._gesture)
                return Clutter.EVENT_PROPAGATE;

            const [fingerX, fingerY] = event.get_coords();
            if (!this._isInsideIdleCircle(fingerX, fingerY))
                return Clutter.EVENT_PROPAGATE;

            const [cursorX, cursorY] = global.get_pointer();
            if (!contains(this._monitor, cursorX, cursorY)) {
                this._updateCursorAvailability();
                return Clutter.EVENT_PROPAGATE;
            }

            const finger = {x: fingerX, y: fingerY};
            if (this._pendingTap && isNearbyTap(
                this._pendingTap.position, finger, this._doubleTapDistance)) {
                this._cancelPendingTap();
                this._gesture = {
                    slot,
                    monitor: copyRect(this._monitor),
                    pad: copyRect(this._pad),
                    idlePad: copyRect(this._pad),
                    initialFinger: finger,
                    lastFinger: finger,
                    initialMagFactor: Math.max(MIN_MAG_FACTOR,
                        this._magnifierSettings.get_double(MAG_FACTOR_KEY)),
                    startedAtUs: GLib.get_monotonic_time(),
                    tapEligible: true,
                    dragging: false,
                    mode: 'zoom',
                };
                this._actor.add_style_pseudo_class('zooming');
                return Clutter.EVENT_STOP;
            }

            if (this._pendingTap)
                this._commitPendingTap();

            const idlePad = copyRect(this._pad);
            const expandedPad = alignPadToCursor(
                this._monitor,
                {width: idlePad.width, height: idlePad.height},
                {x: fingerX, y: fingerY},
                {x: cursorX, y: cursorY});
            this._gesture = {
                slot,
                monitor: copyRect(this._monitor),
                pad: copyRect(expandedPad),
                idlePad,
                initialFinger: {x: fingerX, y: fingerY},
                initialCursor: {x: cursorX, y: cursorY},
                startedAtUs: GLib.get_monotonic_time(),
                tapEligible: true,
                dragging: false,
                mode: 'pointer',
                lastFinger: {x: fingerX, y: fingerY},
            };
            this._startLongPressWatch();
            this._applyPadGeometry(expandedPad);
            this._surface.set_position(
                fingerX - expandedPad.x - this._idleSize / 2,
                fingerY - expandedPad.y - this._idleSize / 2);
            this._setExpanded(true);
            this._updateMarker(fingerX, fingerY);
            return Clutter.EVENT_STOP;
        }

        if (!this._gesture || this._gesture.slot !== slot)
            return Clutter.EVENT_PROPAGATE;

        if (type === Clutter.EventType.TOUCH_UPDATE) {
            const [fingerX, fingerY] = event.get_coords();
            const currentFinger = {x: fingerX, y: fingerY};
            this._gesture.lastFinger = currentFinger;

            if (this._gesture.mode === 'reposition') {
                this._updateReposition(currentFinger);
                return Clutter.EVENT_STOP;
            }

            if (this._gesture.mode === 'zoom') {
                this._updateGestureClassification(currentFinger);
                if (this._gesture.dragging) {
                    const factor = zoomFactorFromVerticalDrag(
                        this._gesture.initialMagFactor,
                        this._gesture.initialFinger.y,
                        currentFinger.y,
                        this._padLongSide * ZOOM_DRAG_SPAN_FACTOR,
                        MIN_MAG_FACTOR,
                        MAX_MAG_FACTOR);
                    this._setZoomTarget(factor);
                }
                return Clutter.EVENT_STOP;
            }

            this._updateGestureClassification(currentFinger);
            this._updateMarker(fingerX, fingerY);

            // Ignore natural touchscreen jitter until this is clearly a drag.
            if (!this._gesture.dragging)
                return Clutter.EVENT_STOP;

            const cursor = mapLinearPoint(
                this._gesture.monitor, this._gesture.pad, currentFinger);
            this._setPointerTarget(cursor, event.get_time_us());
            return Clutter.EVENT_STOP;
        }

        if (type === Clutter.EventType.TOUCH_END ||
            type === Clutter.EventType.TOUCH_CANCEL) {
            const gesture = this._gesture;
            this._stopLongPressWatch();

            if (gesture.mode === 'reposition') {
                if (type === Clutter.EventType.TOUCH_END) {
                    this._updateReposition(gesture.lastFinger);
                    this._saveControllerPosition();
                    gesture.repositionCommitted = true;
                }

                this._suppressEmulatedButtonsUntilUs =
                    GLib.get_monotonic_time() + EMULATED_BUTTON_GRACE_US;
                this._cancelGesture(false);
                return Clutter.EVENT_STOP;
            }
            if (gesture.mode === 'zoom') {
                if (gesture.dragging) {
                    this._finishZoomAdjustment(
                        type === Clutter.EventType.TOUCH_END);
                } else if (type === Clutter.EventType.TOUCH_END) {
                    this._toggleMagnification();
                }

                this._suppressEmulatedButtonsUntilUs =
                    GLib.get_monotonic_time() + EMULATED_BUTTON_GRACE_US;
                this._cancelGesture(false);
                return Clutter.EVENT_STOP;
            }

            const elapsedMs =
                (GLib.get_monotonic_time() - gesture.startedAtUs) / 1000;
            const shouldClick = type === Clutter.EventType.TOUCH_END &&
                isTap(gesture.tapEligible, elapsedMs, TAP_MAX_DURATION_MS);

            // TOUCH_END coordinates often shift as the contact area shrinks.
            // Ignore that lift-off sample and keep the last rendered position
            // rather than snapping across the smoothing remainder.
            if (gesture.dragging)
                this._stopPointerMotion();

            this._suppressEmulatedButtonsUntilUs =
                GLib.get_monotonic_time() + EMULATED_BUTTON_GRACE_US;
            this._cancelGesture(true);
            if (shouldClick)
                this._scheduleTap(gesture.lastFinger);
            return Clutter.EVENT_STOP;
        }

        return Clutter.EVENT_PROPAGATE;
    }

    _filterEvent(event) {
        const type = event.type();
        if (type === Clutter.EventType.TOUCH_BEGIN ||
            type === Clutter.EventType.TOUCH_UPDATE ||
            type === Clutter.EventType.TOUCH_END ||
            type === Clutter.EventType.TOUCH_CANCEL)
            return this._onTouchEvent(event);

        const isTouchEmulated =
            (event.get_flags() &
                Clutter.EventFlags.FLAG_POINTER_EMULATED) !== 0;
        if (!isTouchEmulated)
            return Clutter.EVENT_PROPAGATE;

        // Some backends emit the emulated press before TOUCH_BEGIN. Catch it
        // by location; after ownership begins, consume the entire emulated
        // pointer sequence, not merely its button events.
        let beginsOnIdleCircle = false;
        if (type === Clutter.EventType.BUTTON_PRESS &&
            this._actor.visible && this._cursorAvailable && !this._expanded &&
            this._monitor && this._pad) {
            const [x, y] = event.get_coords();
            beginsOnIdleCircle = this._isInsideIdleCircle(x, y);
        }

        if (this._gesture || beginsOnIdleCircle ||
            GLib.get_monotonic_time() < this._suppressEmulatedButtonsUntilUs)
            return Clutter.EVENT_STOP;

        return Clutter.EVENT_PROPAGATE;
    }

    _updateGestureClassification(currentFinger) {
        if (!this._gesture.tapEligible)
            return;

        if (exceedsDragThreshold(
            this._gesture.initialFinger, currentFinger, this._dragThreshold)) {
            // This is latched: returning to the deadzone can never restore a tap.
            this._gesture.tapEligible = false;
            this._gesture.dragging = true;
            this._stopLongPressWatch();
        }
    }

    _startLongPressWatch() {
        this._stopLongPressWatch();
        this._longPressId = GLib.timeout_add(
            GLib.PRIORITY_DEFAULT,
            Math.max(1, this._longPressDurationMs),
            () => {
                this._longPressId = 0;
                this._beginReposition();
                return GLib.SOURCE_REMOVE;
            });
    }

    _stopLongPressWatch() {
        if (!this._longPressId)
            return;

        GLib.source_remove(this._longPressId);
        this._longPressId = 0;
    }

    _beginReposition() {
        if (!this._gesture || !this._gesture.tapEligible ||
            this._gesture.mode !== 'pointer')
            return;

        this._gesture.mode = 'reposition';
        this._gesture.tapEligible = false;
        this._gesture.dragging = false;
        this._applyPadGeometry(this._gesture.idlePad);
        this._gesture.originalPad = copyRect(this._gesture.idlePad);
        this._gesture.grabOffset = {
            x: this._gesture.lastFinger.x - this._pad.x,
            y: this._gesture.lastFinger.y - this._pad.y,
        };
        this._actor.add_style_pseudo_class('repositioning');
        this._setExpanded(false, true);
    }

    _updateReposition(finger) {
        const pad = movePad(
            this._gesture.monitor,
            this._pad,
            this._padMargin,
            finger.x - this._gesture.grabOffset.x,
            finger.y - this._gesture.grabOffset.y);
        this._applyPadGeometry(pad);
    }

    _saveControllerPosition() {
        const position = normalizedPadCenter(this._monitor, this._pad);
        this._settings.set_double('position-x', position.x);
        this._settings.set_double('position-y', position.y);
    }

    _applyPadGeometry(pad) {
        this._pad = copyRect(pad);
        this._actor.set_position(this._pad.x, this._pad.y);
        this._actor.set_size(this._pad.width, this._pad.height);
    }

    _updateMarker(stageX, stageY) {
        const localX = Math.max(this._markerSize / 2,
            Math.min(this._pad.width - this._markerSize / 2,
                stageX - this._pad.x));
        const localY = Math.max(this._markerSize / 2,
            Math.min(this._pad.height - this._markerSize / 2,
                stageY - this._pad.y));
        this._marker.set_position(
            Math.round(localX - this._markerSize / 2),
            Math.round(localY - this._markerSize / 2));
        this._marker.show();
    }

    _clickPrimary() {
        const timeUs = GLib.get_monotonic_time();
        this._virtualPointer.notify_button(
            timeUs, Clutter.BUTTON_PRIMARY, Clutter.ButtonState.PRESSED);
        this._virtualPointer.notify_button(
            timeUs, Clutter.BUTTON_PRIMARY, Clutter.ButtonState.RELEASED);
    }

    _scheduleTap(position) {
        this._cancelPendingTap();
        this._pendingTap = {position: {...position}};
        this._pendingTapId = GLib.timeout_add(
            GLib.PRIORITY_DEFAULT,
            Math.max(1, this._doubleTapDurationMs),
            () => {
                this._pendingTapId = 0;
                this._pendingTap = null;
                this._clickPrimary();
                return GLib.SOURCE_REMOVE;
            });
    }

    _commitPendingTap() {
        if (!this._pendingTap)
            return;

        this._cancelPendingTap();
        this._clickPrimary();
    }

    _cancelPendingTap() {
        if (this._pendingTapId)
            GLib.source_remove(this._pendingTapId);
        this._pendingTapId = 0;
        this._pendingTap = null;
    }

    _rememberMagnification() {
        const factor = this._magnifierSettings.get_double(MAG_FACTOR_KEY);
        if (factor > MIN_MAG_FACTOR)
            this._lastMagFactor = factor;
    }

    _setMagnification(factor) {
        if (factor === this._magnifierSettings.get_double(MAG_FACTOR_KEY))
            return;

        this._magnifierSettings.set_double(MAG_FACTOR_KEY, factor);
    }

    _toggleMagnification() {
        const factor = this._magnifierSettings.get_double(MAG_FACTOR_KEY);
        if (factor > MIN_MAG_FACTOR) {
            this._lastMagFactor = factor;
            this._setMagnification(MIN_MAG_FACTOR);
        } else {
            this._setMagnification(Math.max(
                MIN_MAG_FACTOR, this._lastMagFactor));
        }
    }

    _setZoomTarget(factor) {
        this._zoomTarget = factor;
        if (!this._pointerTimeline.is_playing())
            this._pointerTimeline.start();
    }

    _advanceZoomAdjustment() {
        if (this._zoomTarget === null ||
            this._zoomTarget === this._zoomApplied)
            return;

        const [zoomRegion] = Main.magnifier.getZoomRegions();
        if (!zoomRegion)
            return;

        // setMagFactor() always starts a 100 ms Shell animation. Updating the
        // public ROI instead makes the live gesture immediate. Cursor tracking
        // is restored once when the gesture finishes.
        this._applyLiveMagnification(zoomRegion, this._zoomTarget);
        this._zoomApplied = this._zoomTarget;
    }

    _applyLiveMagnification(zoomRegion, factor) {
        const [xFactor, yFactor] = zoomRegion.getMagFactor();
        const [x, y, width, height] = zoomRegion.getROI();
        zoomRegion.setROI(roiForMagnification(
            {x, y, width, height}, xFactor, yFactor, factor));
    }

    _finishZoomAdjustment(commit) {
        if (this._zoomTarget === null)
            return;

        const finalFactor = this._zoomTarget;
        if (commit) {
            this._advanceZoomAdjustment();
            this._setMagnification(finalFactor);
        } else {
            const initialFactor = this._gesture.initialMagFactor;
            const [zoomRegion] = Main.magnifier.getZoomRegions();
            if (zoomRegion)
                this._applyLiveMagnification(zoomRegion, initialFactor);
        }

        const [zoomRegion] = Main.magnifier.getZoomRegions();
        zoomRegion?.scrollToMousePos();

        this._zoomTarget = null;
        this._zoomApplied = null;
        this._pointerTimeline.stop();
    }

    _setPointerTarget(target, sampleTimeUs) {
        const nowUs = GLib.get_monotonic_time();
        if (!this._pointerMotion) {
            const initial = this._gesture.initialCursor;
            const sampleIntervalUs = DEFAULT_TOUCH_INTERVAL_US;
            const mappingGain = Math.max(
                (this._gesture.monitor.width - 1) / this._gesture.pad.width,
                (this._gesture.monitor.height - 1) / this._gesture.pad.height);
            const [zoomRegion] = Main.magnifier.getZoomRegions();
            const [magnification = MIN_MAG_FACTOR] =
                zoomRegion?.getMagFactor() ?? [];
            const spanFactor = interpolationSpanFactor(
                mappingGain, magnification);
            this._pointerMotion = {
                current: {x: initial.x, y: initial.y},
                start: {x: initial.x, y: initial.y},
                target: {...target},
                segmentStartUs: nowUs,
                segmentDurationUs: sampleIntervalUs * spanFactor,
                lastSampleUs: sampleTimeUs,
                sampleIntervalUs,
                sampleCount: 1,
                interpolationSpanFactor: spanFactor,
                lastPosition: {x: initial.x, y: initial.y},
            };
            this._pointerTimeline.start();
        } else {
            const motion = this._pointerMotion;
            motion.current = interpolatePoint(
                motion.start,
                motion.target,
                nowUs - motion.segmentStartUs,
                motion.segmentDurationUs);

            const observedIntervalUs = sampleTimeUs - motion.lastSampleUs;
            if (motion.sampleCount === 1) {
                motion.sampleIntervalUs = updateIntervalEstimate(
                    motion.sampleIntervalUs,
                    observedIntervalUs,
                    MIN_TOUCH_INTERVAL_US,
                    MAX_TOUCH_INTERVAL_US,
                    1);
            } else {
                motion.sampleIntervalUs = updateIntervalEstimate(
                    motion.sampleIntervalUs,
                    observedIntervalUs,
                    MIN_TOUCH_INTERVAL_US,
                    MAX_TOUCH_INTERVAL_US);
            }

            motion.start = {...motion.current};
            this._pointerMotion.target = {...target};
            motion.segmentStartUs = nowUs;
            motion.segmentDurationUs =
                motion.sampleIntervalUs * motion.interpolationSpanFactor;
            motion.lastSampleUs = sampleTimeUs;
            motion.sampleCount++;
        }
    }

    _advancePointerMotion() {
        if (!this._pointerMotion)
            return;

        const nowUs = GLib.get_monotonic_time();
        this._pointerMotion.current = interpolatePoint(
            this._pointerMotion.start,
            this._pointerMotion.target,
            nowUs - this._pointerMotion.segmentStartUs,
            this._pointerMotion.segmentDurationUs);

        const next = this._pointerMotion.current;
        const deltaX = next.x - this._pointerMotion.lastPosition.x;
        const deltaY = next.y - this._pointerMotion.lastPosition.y;
        if (deltaX * deltaX + deltaY * deltaY < 0.000001)
            return;

        this._virtualPointer.notify_absolute_motion(
            nowUs, next.x, next.y);
        this._pointerMotion.lastPosition = {...next};
    }

    _stopPointerMotion() {
        if (!this._pointerMotion)
            return;

        this._pointerMotion = null;
        this._pointerTimeline.stop();
    }

    _cancelGesture(animate) {
        if (!this._gesture && !this._expanded)
            return;

        this._stopLongPressWatch();
        this._stopPointerMotion();
        this._finishZoomAdjustment(false);
        if (this._gesture?.mode === 'reposition') {
            if (!this._gesture.repositionCommitted)
                this._applyPadGeometry(this._gesture.originalPad);
        } else if (this._gesture) {
            this._applyPadGeometry(this._gesture.idlePad);
        }
        this._actor.remove_style_pseudo_class('repositioning');
        this._actor.remove_style_pseudo_class('zooming');
        this._gesture = null;
        this._setExpanded(false, animate);
    }
}

export default class ZoomTouchControllerExtension extends Extension {
    enable() {
        this._controller = new ZoomTouchController(this.getSettings());
    }

    disable() {
        this._controller?.destroy();
        this._controller = null;
    }
}
