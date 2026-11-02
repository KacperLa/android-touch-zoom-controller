package io.github.kacperla.touchzoom;
/** Duplicate suppression only; display-frame scheduling is owned by ZoomService. */
public final class UpdatePacer {
    private float scale,x,y;
    private boolean valid;
    public boolean changed(float s,float nx,float ny){
        return !valid || Math.abs(s-scale)>=.005f || nx!=x || ny!=y;
    }
    public void sent(long now,float s,float nx,float ny){scale=s;x=nx;y=ny;valid=true;}
    public void reset(){valid=false;}
    public static long holdDeadline(long downTime,int delay){return downTime+Math.max(0,delay);}
}
