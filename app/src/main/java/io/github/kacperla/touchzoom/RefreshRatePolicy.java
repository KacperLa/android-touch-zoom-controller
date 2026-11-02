package io.github.kacperla.touchzoom;
/** Selects a preference only; the system still decides the actual refresh rate. */
public final class RefreshRatePolicy {
    public static final float MAX_SANE_HZ=1000f;
    private RefreshRatePolicy() { }
    public static float select(int width,int height,int[] widths,int[] heights,float[] rates){
        if(width<=0 || height<=0 || widths==null || heights==null || rates==null
                || widths.length!=heights.length || widths.length!=rates.length)return 0;
        float best=0;
        for(int i=0;i<rates.length;i++){
            float hz=rates[i];
            if(widths[i]==width && heights[i]==height && Float.isFinite(hz)
                    && hz>best && hz<=MAX_SANE_HZ)best=hz;
        }
        return best;
    }
}
