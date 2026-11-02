function clamp(value, minimum, maximum) {
    return Math.max(minimum, Math.min(maximum, value));
}

function axisBounds(start, length, padLength, margin) {
    const minimum = start + margin;
    const maximum = start + length - margin - padLength;
    if (maximum >= minimum)
        return [minimum, maximum];

    // Fall back to the full monitor on unusually small displays.
    return [start, Math.max(start, start + length - padLength)];
}

function normalizedAxis(position, start, length) {
    const span = Math.max(0, length - 1);
    if (span === 0)
        return 0;

    return clamp((position - start) / span, 0, 1);
}

export function calculatePadSize(monitor, idleSize, padLongSide) {
    let width;
    let height;

    if (monitor.width >= monitor.height) {
        width = padLongSide;
        height = padLongSide * monitor.height / monitor.width;
    } else {
        height = padLongSide;
        width = padLongSide * monitor.width / monitor.height;
    }

    return {
        width: Math.max(idleSize, Math.round(width)),
        height: Math.max(idleSize, Math.round(height)),
    };
}

/**
 * Translate a pad so the finger represents the cursor's monitor position.
 * The pad may extend beyond the monitor; clamping would change the initial
 * cursor anchor and reintroduce non-uniform motion gain.
 */
export function alignPadToCursor(monitor, size, finger, cursor) {
    const cursorX = normalizedAxis(cursor.x, monitor.x, monitor.width);
    const cursorY = normalizedAxis(cursor.y, monitor.y, monitor.height);

    return {
        x: finger.x - cursorX * size.width,
        y: finger.y - cursorY * size.height,
        width: size.width,
        height: size.height,
    };
}

export function placePad(monitor, size, margin, normalizedPosition) {
    const [minimumX, maximumX] = axisBounds(
        monitor.x, monitor.width, size.width, margin);
    const [minimumY, maximumY] = axisBounds(
        monitor.y, monitor.height, size.height, margin);
    const desiredX = monitor.x + normalizedPosition.x * monitor.width -
        size.width / 2;
    const desiredY = monitor.y + normalizedPosition.y * monitor.height -
        size.height / 2;

    return {
        x: Math.round(clamp(desiredX, minimumX, maximumX)),
        y: Math.round(clamp(desiredY, minimumY, maximumY)),
        width: size.width,
        height: size.height,
    };
}

export function movePad(monitor, pad, margin, desiredX, desiredY) {
    const [minimumX, maximumX] = axisBounds(
        monitor.x, monitor.width, pad.width, margin);
    const [minimumY, maximumY] = axisBounds(
        monitor.y, monitor.height, pad.height, margin);
    return {
        x: Math.round(clamp(desiredX, minimumX, maximumX)),
        y: Math.round(clamp(desiredY, minimumY, maximumY)),
        width: pad.width,
        height: pad.height,
    };
}

export function normalizedPadCenter(monitor, pad) {
    return {
        x: clamp((pad.x + pad.width / 2 - monitor.x) / monitor.width, 0, 1),
        y: clamp((pad.y + pad.height / 2 - monitor.y) / monitor.height, 0, 1),
    };
}
