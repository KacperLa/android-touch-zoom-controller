export function exceedsDragThreshold(initialPoint, currentPoint, threshold) {
    const deltaX = currentPoint.x - initialPoint.x;
    const deltaY = currentPoint.y - initialPoint.y;
    return deltaX * deltaX + deltaY * deltaY > threshold * threshold;
}

export function isTap(tapEligible, elapsedMs, maximumDurationMs) {
    return tapEligible && elapsedMs >= 0 && elapsedMs <= maximumDurationMs;
}

export function isNearbyTap(firstPoint, secondPoint, maximumDistance) {
    const deltaX = secondPoint.x - firstPoint.x;
    const deltaY = secondPoint.y - firstPoint.y;
    return deltaX * deltaX + deltaY * deltaY <=
        maximumDistance * maximumDistance;
}

export function zoomFactorFromVerticalDrag(
    initialFactor, initialY, currentY, distancePerDoubling,
    minimumFactor, maximumFactor) {
    const factor = initialFactor *
        2 ** ((initialY - currentY) / distancePerDoubling);
    const clamped = Math.max(minimumFactor, Math.min(maximumFactor, factor));

    // GNOME's magnifier stores and applies the factor to two decimal places.
    return Math.round(clamped * 100) / 100;
}

export function roiForMagnification(
    roi, currentXFactor, currentYFactor, targetFactor) {
    const centerX = roi.x + roi.width / 2;
    const centerY = roi.y + roi.height / 2;
    const width = roi.width * currentXFactor / targetFactor;
    const height = roi.height * currentYFactor / targetFactor;

    return {
        x: centerX - width / 2,
        y: centerY - height / 2,
        width,
        height,
    };
}

export function interpolatePoint(
    startPoint, targetPoint, elapsedUs, durationUs) {
    if (durationUs <= 0)
        return {...targetPoint};

    const amount = Math.max(0, Math.min(1, elapsedUs / durationUs));
    return {
        x: startPoint.x + (targetPoint.x - startPoint.x) * amount,
        y: startPoint.y + (targetPoint.y - startPoint.y) * amount,
    };
}

export function updateIntervalEstimate(
    previousUs, observedUs, minimumUs, maximumUs, weight = 0.35) {
    // Event delivery can be delayed by Shell work. Do not let that scheduling
    // stall inflate the estimated hardware sampling interval.
    if (!Number.isFinite(observedUs) ||
        observedUs < minimumUs || observedUs > maximumUs)
        return previousUs;

    return previousUs + (observedUs - previousUs) * weight;
}

export function interpolationSpanFactor(
    mappingGain, magnification, minimumFactor = 1.1,
    maximumFactor = 1.8) {
    const effectiveGain = Math.max(1, mappingGain) *
        Math.max(1, magnification);
    const extra = Math.max(0, Math.log2(effectiveGain / 4)) * 0.12;
    return Math.min(maximumFactor, minimumFactor + extra);
}
