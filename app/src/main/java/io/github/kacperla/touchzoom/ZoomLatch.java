package io.github.kacperla.touchzoom;

/** Transient latch and deferred single-tap state. Never persisted across service restarts. */
public final class ZoomLatch {
    private boolean latched, pendingTap;
    public boolean isLatched(){return latched;}
    public boolean hasPendingTap(){return pendingTap;}
    public void queueTap(){pendingTap=true;}
    public void cancelPendingTap(){pendingTap=false;}
    public boolean toggle(){latched=!latched;return latched;}
    public boolean commitTap(){if(!pendingTap)return false;pendingTap=false;toggle();return true;}
    public void clear(){latched=false;pendingTap=false;}
}
