package io.github.kacperla.touchzoom;
/** Opt-in, memory-only bounded numeric/event trace. No screen content or transmission. */
public final class DiagnosticTrace {
    public static final int CAPACITY=8192;
    private final String[] entries=new String[CAPACITY];
    private static final int MAX_CHARS=196608;
    private int count,next,chars;private long until;private boolean started,tracking;
    public void start(long now){java.util.Arrays.fill(entries,null);count=next=chars=0;until=now+15000;started=true;tracking=true;}
    public void clear(){java.util.Arrays.fill(entries,null);count=next=chars=0;started=tracking=false;}
    public void stop(){tracking=false;}
    public boolean mayRecord(){return tracking;}
    public boolean recording(long now){if(tracking && now>=until)tracking=false;return tracking;}
    public void add(String event){
        if(event.length()>512)event=event.substring(0,498)+" [truncated]";
        if(count==CAPACITY)chars-=entries[next].length();
        entries[next]=event;chars+=event.length();next=(next+1)%CAPACITY;count=Math.min(CAPACITY,count+1);
        while(chars>MAX_CHARS && count>0){int oldest=(next-count+CAPACITY)%CAPACITY;chars-=entries[oldest].length();entries[oldest]=null;count--;}
    }
    public String report(){
        if(!started)return "No recording. Optional diagnostics record only this controller’s touch coordinates, requested scale and magnifier state, in memory for15 seconds. Nothing is uploaded.";
        StringBuilder b=new StringBuilder("Touch Zoom0.22; latest "+count+" events (max8192). Local only; 196608-character budget; older entries overwritten.\n");
        int start=(next-count+CAPACITY)%CAPACITY;for(int i=0;i<count;i++)b.append(entries[(start+i)%CAPACITY]).append('\n');return b.toString();
    }
}
