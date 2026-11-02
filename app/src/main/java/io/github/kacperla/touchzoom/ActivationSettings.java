package io.github.kacperla.touchzoom;
/** User-selected extra delay follows a short fixed hold/drag distinction pause. */
public final class ActivationSettings {
    public static final String KEY="hold_extra_delay_ms", LEGACY_KEY="hold_to_zoom_ms";
    public static final int BASE_MS=100, DEFAULT_MS=900, MIN_MS=0, MAX_MS=2000, STEP_MS=50;
    private ActivationSettings() { }
    public static int normalize(int ms){return Math.round(Math.max(MIN_MS,Math.min(MAX_MS,ms))/(float)STEP_MS)*STEP_MS;}
    public static int totalMs(int extra){return BASE_MS+normalize(extra);}
    public static int migratedExtra(int oldTotal){return normalize((int)Math.max(0L,Math.min(MAX_MS,(long)oldTotal-BASE_MS)));}
}
