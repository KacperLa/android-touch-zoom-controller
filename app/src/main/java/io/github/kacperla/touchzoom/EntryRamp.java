package io.github.kacperla.touchzoom;
/** Elapsed-time entry easing, one current value only. No queued interpolation samples. */
public final class EntryRamp {
    public static final String KEY="experimental_hold_entry_easing";
    public static final long DURATION_MS=120, INTERVAL_MS=17;
    private boolean running, finalSent;
    private long started, next;
    private float from, target;
    public boolean start(long now,float actual,float selected,boolean enabled){
        cancel();
        if(!enabled || !Float.isFinite(actual) || !Float.isFinite(selected) || selected<=actual+.01f)return false;
        from=Math.max(1,actual);target=selected;started=next=now;running=true;return true;
    }
    public boolean running(){return running;}
    public long nextAt(){return next;}
    public boolean ready(long now){return running && !finalSent && now>=next;}
    public float value(long now){
        float t=Math.max(0,Math.min(1,(now-started)/(float)DURATION_MS));
        return from+(target-from)*(1-(1-t)*(1-t));
    }
    public void sent(long now){finalSent=now-started>=DURATION_MS;next=now+INTERVAL_MS;}
    public void acknowledged(){if(finalSent)cancel();}
    public void cancel(){running=false;finalSent=false;}
}
