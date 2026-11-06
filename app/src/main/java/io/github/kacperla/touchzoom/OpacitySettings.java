package io.github.kacperla.touchzoom;
/** Idle bubble opacity; active interaction remains fully visible. */
public final class OpacitySettings {
    public static final String KEY="idle_opacity_percent";
    public static final int DEFAULT_PERCENT=75, MIN_PERCENT=10, MAX_PERCENT=100, STEP_PERCENT=5;
    private OpacitySettings() { }
    public static int normalize(int percent){return Math.round(Math.max(MIN_PERCENT,Math.min(MAX_PERCENT,percent))/(float)STEP_PERCENT)*STEP_PERCENT;}
    public static float alpha(int percent){return normalize(percent)/100f;}
}
