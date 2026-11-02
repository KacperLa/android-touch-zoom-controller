package io.github.kacperla.touchzoom;

/** Artwork state follows reported magnification, not touch-down or a pending timer. */
public final class BubbleAppearance {
    private boolean magnifierActivated;
    public boolean update(boolean actualActivation) {
        boolean changed=magnifierActivated!=actualActivation;
        magnifierActivated=actualActivation;
        return changed;
    }
    public boolean shouldDrawArtwork(boolean interacting,boolean repositioning) {
        return !magnifierActivated || !interacting || repositioning;
    }
    public boolean shouldDrawArtwork() { return !magnifierActivated; }
}
