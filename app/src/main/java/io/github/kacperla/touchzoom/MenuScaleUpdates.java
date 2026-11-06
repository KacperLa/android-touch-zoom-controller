package io.github.kacperla.touchzoom;

/** Latest-only menu requests. No FIFO, retries, Android dependencies, or gesture-rate limits. */
public final class MenuScaleUpdates {
    private boolean active,pending,waiting;
    private float selected,requested;
    public void begin(){active=true;}
    public boolean active(){return active;}
    public boolean waiting(){return waiting;}
    public boolean hasWork(){return pending || waiting;}
    public boolean ready(){return pending && !waiting;}
    public float selected(){return selected;}
    public void offer(float scale){
        if(!Float.isFinite(scale))throw new IllegalArgumentException("finite scale required");
        active=true;selected=scale;pending=true;
    }
    /** Called once per frame. Marks the request before the backend can acknowledge synchronously. */
    public float take(float actual){
        if(!ready())return Float.NaN;
        pending=false;
        if(matches(selected,actual))return Float.NaN; // No state change means no callback is required.
        requested=selected;waiting=true;return requested;
    }
    public boolean observe(float actual){
        if(!waiting || !matches(requested,actual))return false;
        waiting=false;
        if(pending && matches(selected,actual))pending=false;
        return true;
    }
    public void cancel(){active=pending=waiting=false;}
    private static boolean matches(float a,float b){return Float.isFinite(b)&&Math.abs(a-b)<=.01f;}
}
