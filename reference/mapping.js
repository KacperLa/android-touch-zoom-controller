/**
 * Map one pad axis linearly onto one monitor axis.
 *
 * The pad endpoints map to the monitor's first and last valid coordinates.
 * Values outside the pad are clamped to the monitor.
 */
export function mapLinearAxis(
    monitorStart, monitorEnd, padStart, padEnd, currentFinger) {
    if (![monitorStart, monitorEnd, padStart, padEnd,
        currentFinger].every(Number.isFinite))
        throw new TypeError('All mapping coordinates must be finite numbers');

    if (monitorEnd < monitorStart || padEnd < padStart)
        throw new RangeError('Bounds must be ordered');

    const padSpan = padEnd - padStart;
    if (padSpan === 0)
        return monitorStart;

    const ratio = (currentFinger - padStart) / padSpan;
    const mapped = monitorStart + ratio * (monitorEnd - monitorStart);
    return Math.max(monitorStart, Math.min(monitorEnd, mapped));
}

/**
 * @param {{x: number, y: number, width: number, height: number}} monitor
 * @param {{x: number, y: number, width: number, height: number}} pad
 * @param {{x: number, y: number}} currentFinger
 * @returns {{x: number, y: number}}
 */
export function mapLinearPoint(monitor, pad, currentFinger) {
    return {
        x: mapLinearAxis(
            monitor.x, monitor.x + monitor.width - 1,
            pad.x, pad.x + pad.width, currentFinger.x),
        y: mapLinearAxis(
            monitor.y, monitor.y + monitor.height - 1,
            pad.y, pad.y + pad.height, currentFinger.y),
    };
}
