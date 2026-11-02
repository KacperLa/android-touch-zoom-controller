package io.github.kacperla.touchzoom;
/** Pure decisions shared by the touch handler and boundary tests. */
public final class GestureRules {
    private GestureRules() { }
    public static boolean waitingForHold(boolean secondTap, boolean previewOwned) {
        return !secondTap && !previewOwned;
    }
    public static boolean tapBeforeHold(boolean moved, boolean relocating, long durationMs,
            boolean secondTap, boolean previewOwned, int totalHoldMs) {
        return quickTap(moved,relocating,durationMs) &&
            (secondTap || (!previewOwned && durationMs<totalHoldMs));
    }
    public static boolean quickTap(boolean moved, boolean relocating, long durationMs) {
        return !moved && !relocating && durationMs >= 0 && durationMs <= 250;
    }
}
