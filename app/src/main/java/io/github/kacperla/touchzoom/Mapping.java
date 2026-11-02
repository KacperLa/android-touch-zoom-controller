package io.github.kacperla.touchzoom;

/** Pure geometry. Screen coordinates are unscaled display pixels. */
public final class Mapping {
    private Mapping() {}
    public static float clamp(float n, float lo, float hi) { return Math.max(lo, Math.min(hi, n)); }
    public static float axis(float start, float end, float padStart, float padLength, float finger) {
        if (!Float.isFinite(start) || !Float.isFinite(end) || !Float.isFinite(padStart)
                || !Float.isFinite(padLength) || !Float.isFinite(finger) || end < start || padLength < 0)
            throw new IllegalArgumentException("Invalid mapping geometry");
        return padLength == 0 ? start : start + clamp((finger - padStart) / padLength, 0, 1) * (end - start);
    }
    public static float normalized(float position, float start, float end) {
        return end <= start ? .5f : clamp((position - start) / (end - start), 0, 1);
    }
    public static float zoom(float initial, float deltaUp, float halfPadHeight) {
        return clamp((float) (initial * Math.pow(2, deltaUp / Math.max(1, halfPadHeight))), 1, 8);
    }
    public static float[] padSize(float width, float height, float longSide) {
        if (width <= 0 || height <= 0 || longSide <= 0) throw new IllegalArgumentException("Invalid screen size");
        return width >= height ? new float[]{longSide, longSide * height / width}
                : new float[]{longSide * width / height, longSide};
    }
}
