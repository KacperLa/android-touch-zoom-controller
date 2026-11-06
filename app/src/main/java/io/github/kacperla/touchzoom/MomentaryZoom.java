package io.github.kacperla.touchzoom;

/** Gesture-owned zoom lifecycle, independent of Android for regression tests. */
public final class MomentaryZoom {
    private boolean ownsPreview;
    private float selectedScale = 2;
    public void begin(float scale) {
        selectedScale = Mapping.clamp(Float.isFinite(scale) ? scale : 2, 1f, 8);
        ownsPreview = true;
    }
    public void select(float scale) {
        if (ownsPreview && Float.isFinite(scale) && scale > 1.01f)
            selectedScale = Mapping.clamp(scale, 1f, 8);
    }
    public float selectedScale() { return selectedScale; }
    public boolean ownsPreview() { return ownsPreview; }
    /** Release, cancel, reposition, interruption and destruction share this operation. */
    public boolean end() {
        boolean mustZoomOut = ownsPreview;
        ownsPreview = false;
        return mustZoomOut;
    }
}
