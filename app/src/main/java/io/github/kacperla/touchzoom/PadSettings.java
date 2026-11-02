package io.github.kacperla.touchzoom;

/** Expanded pad size only. Mapping still spans the entire screen. */
public final class PadSettings {
    public static final String KEY = "pad_size_dp";
    public static final int MIN_DP=80, MAX_DP=360, DEFAULT_DP=180, STEP_DP=10;
    private PadSettings() { }
    public static int normalize(int dp) {
        int bounded=Math.max(MIN_DP,Math.min(MAX_DP,dp));
        return Math.round(bounded/(float)STEP_DP)*STEP_DP;
    }
    public static float longSidePixels(int dp,float density,float width,float height) {
        if(!Float.isFinite(density) || !Float.isFinite(width) || !Float.isFinite(height)
                || density<=0 || width<=0 || height<=0) throw new IllegalArgumentException("Invalid display geometry");
        return Math.min(normalize(dp)*density,Math.max(width,height)*.72f);
    }
}
