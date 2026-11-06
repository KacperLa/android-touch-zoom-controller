package io.github.kacperla.touchzoom;

/** Main-thread tap arbitration. Actions are resolved only after a sequence is complete. */
public final class TapSequence {
    private int pending, current;
    private long lastUp, deadline, generation;
    private float lastX,lastY;
    private boolean touching;

    /**
     * Starts the next touch and invalidates any queued timeout immediately, including
     * on the third DOWN. Returns an older, unrelated completed sequence to dispatch.
     */
    public int down(long eventTime,float x,float y,long timeout,float distance) {
        boolean continues=!touching && pending>0 && eventTime>=lastUp
            && eventTime-lastUp<=timeout && Math.hypot(x-lastX,y-lastY)<distance;
        int displaced=continues?0:pending;
        current=continues?pending+1:1;
        pending=0;touching=true;generation++;
        return displaced;
    }

    /** Records a valid tap. Triple taps resolve on UP; lower counts wait for another DOWN. */
    public int up(long eventTime,float x,float y,long timeout) {
        if(!touching || current==0)return 0;
        touching=false;generation++;
        int completed=current;current=0;
        if(completed==3){pending=0;return 3;}
        pending=completed;lastUp=eventTime;lastX=x;lastY=y;
        deadline=eventTime+timeout;
        return 0;
    }

    /** A canceled, replaced, already delivered, or premature callback cannot act. */
    public int take(long token,long now) {
        if(token!=generation || touching || pending==0 || now<deadline)return 0;
        int completed=pending;pending=0;generation++;return completed;
    }

    /** A drag, hold, cancellation, relocation, settings change, or lifecycle event wins. */
    public void cancel(){pending=0;current=0;touching=false;generation++;}
    public int currentTapCount(){return current;}
    public boolean hasPendingTap(){return pending>0;}
    public long token(){return generation;}
    public long deadline(){return deadline;}
}
