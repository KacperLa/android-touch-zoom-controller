package io.github.kacperla.touchzoom;

/** Resolution-independent proportions for the original floating-controller artwork. */
public final class OrbitArtwork {
    private OrbitArtwork() { }
    // Android Canvas angles run clockwise from the right. Leave an 80-degree opening.
    public static final float START_DEGREES=40f;
    public static final float SWEEP_DEGREES=280f;
    public static final float RING_RADIUS=.60f;
    public static final float RING_STROKE=.18f;
    public static final float DOT_RADIUS=.20f;
}
