package io.github.kacperla.touchzoom;
/** Actual start and exact selected scale for a double-tap drag, never remembered preview scale. */
public final class DoubleTapScale {
    private float selected=1;
    public float begin(boolean activated,float actual){
        selected=activated && Float.isFinite(actual)?Mapping.clamp(actual,1,8):1;
        return selected;
    }
    public void select(float scale){if(Float.isFinite(scale))selected=Mapping.clamp(scale,1,8);}
    public float selected(){return selected;}
    public boolean shouldLatch(){return selected>1.01f;}
}
