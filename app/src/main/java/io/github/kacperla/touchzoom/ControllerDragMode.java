package io.github.kacperla.touchzoom;
/** DOWN snapshot; first deliberate movement or hold wins permanently for this gesture. */
public final class ControllerDragMode {
    public static final int WAIT=0,PAN=1,RELOCATE=2,SCALE=3,ENDED=4;
    private boolean zoomed;private int mode=ENDED;
    public void begin(boolean alreadyZoomed,boolean secondTap){zoomed=alreadyZoomed;mode=secondTap?SCALE:WAIT;}
    public int movement(boolean pastSlop){if(mode==WAIT && pastSlop)mode=zoomed?PAN:RELOCATE;return mode;}
    public int hold(){if(mode==WAIT)mode=zoomed?RELOCATE:PAN;return mode;}
    public int mode(){return mode;}
    public static boolean stopsPreview(boolean latched,boolean alreadyZoomed){return !latched && !alreadyZoomed;}
    public static boolean ownsZoom(boolean latched,boolean alreadyZoomed,boolean preview,boolean pendingTap){
        return latched || (!alreadyZoomed && (preview || pendingTap));
    }
    public void end(){mode=ENDED;}
}
