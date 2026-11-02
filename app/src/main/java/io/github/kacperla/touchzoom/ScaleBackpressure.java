package io.github.kacperla.touchzoom;
/** Observation-based backpressure: one scale request awaits matching reported state. */
public final class ScaleBackpressure {
    private boolean waiting;private float requested;
    public boolean waiting(){return waiting;}
    public void sent(float scale){if(waiting)throw new IllegalStateException("scale already in flight");requested=scale;waiting=true;}
    public boolean observe(float scale){
        if(waiting && Float.isFinite(scale) && Math.abs(scale-requested)<=.01f){waiting=false;return true;}
        return false;
    }
    public void reset(){waiting=false;}
}
