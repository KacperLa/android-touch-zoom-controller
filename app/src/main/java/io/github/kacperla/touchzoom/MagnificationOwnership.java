package io.github.kacperla.touchzoom;

/**
 * Conservative app-side lease, not proof of Android's magnifier owner. Public callbacks have no
 * origin/request IDs. Centers are deliberately ignored because Android clamps them near edges.
 * Only recent app-requested scales may explain delayed callbacks; fresh reset checks use latest.
 */
public final class MagnificationOwnership {
    public static final int FULLSCREEN=1;
    private static final int CAPACITY=8;
    private static final float EPSILON=.011f;
    private final float[] recent=new float[CAPACITY];
    private int count,next,expectedOff;
    private int mode=FULLSCREEN;
    private float latest=Float.NaN;
    private boolean owned,observing,expectedActive;

    /** Snapshot at DOWN. Existing active zoom is observed, never newly claimed by this operation. */
    public void beginInteraction(int actualMode,boolean activated,float scale){
        expectedOff=0; // Fresh snapshot is a boundary; unused/no-op reset expectations cannot leak into it.
        if(owned && !canReset(actualMode,activated,scale))relinquish();
        observing=true;mode=actualMode;expectedActive=activated;
        if(activated && Float.isFinite(scale))remember(scale);
        else if(!activated){latest=Float.NaN;count=next=0;}
    }

    /** Record before issuing a command, so an immediate callback cannot precede expectations. */
    public void command(int requestedMode,float scale,boolean claimOwnership){
        observing=true;mode=requestedMode;expectedActive=true;
        if(Float.isFinite(scale))remember(scale);
        if(claimOwnership && requestedMode==FULLSCREEN)owned=true;
    }

    /** Record an explicit app reset before sending it. A delayed own-off callback isn't a takeover. */
    public void commandOff(){
        expectedOff=Math.min(CAPACITY,expectedOff+1);
        owned=false;expectedActive=false;
    }

    /** True means incompatible/unexpected state: caller must cancel queued writes WITHOUT reset. */
    public boolean observe(int actualMode,boolean activated,float scale){
        if(!observing && !owned)return false;
        if(!activated && expectedOff>0){expectedOff--;return false;}
        if(!activated){
            if(!expectedActive)return false; // Unzoomed baseline has not been changed by anyone.
            relinquish();return true;
        }
        if(!expectedActive || actualMode!=FULLSCREEN || actualMode!=mode){relinquish();return true;}
        if(!Float.isFinite(scale)){relinquish();return true;}
        if(count==0){remember(scale);return false;}
        for(int i=0;i<count;i++)if(close(recent[i],scale)){
            if(close(latest,scale))expectedOff=0; // Latest activation observed: earlier own-off grace expires.
            return false;
        }
        relinquish();return true;
    }

    /** Fresh state must match the latest requested scale; old acknowledged scales are insufficient. */
    public boolean canReset(int actualMode,boolean activated,float scale){
        return owned && activated && actualMode==FULLSCREEN && mode==FULLSCREEN && close(latest,scale);
    }
    public boolean isOwned(){return owned;}
    public int retainedScaleCount(){return count;}
    public void relinquish(){owned=false;observing=false;expectedActive=false;latest=Float.NaN;count=next=expectedOff=0;}
    private void remember(float scale){
        latest=scale;
        // Consecutive duplicate center-only or no-op writes need no extra history slot.
        int previous=(next+CAPACITY-1)%CAPACITY;
        if(count>0 && close(recent[previous],scale))return;
        recent[next]=scale;next=(next+1)%CAPACITY;count=Math.min(CAPACITY,count+1);
    }
    private static boolean close(float a,float b){return Float.isFinite(a)&&Float.isFinite(b)&&Math.abs(a-b)<=EPSILON;}
}
