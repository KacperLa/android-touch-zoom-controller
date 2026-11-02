package io.github.kacperla.touchzoom;
import java.util.Locale;
/** Fixed-size cadence summary. Counts observations, never claims display presentation. */
public final class CadenceStats {
    public static final int INPUT_EVENT=0, INPUT_ARRIVAL=1, VSYNC=2, CENTER=3, PAD_POST=4, TRACKING_FRAME=5;
    public static final int SERIES_COUNT=6;
    private static final String[] NAMES={"Input event", "Input arrival", "Vsync callback", "Center request", "Pad post", "Tracking frame"};
    private final long[] count=new long[SERIES_COUNT], rejected=new long[SERIES_COUNT], last=new long[SERIES_COUNT], min=new long[SERIES_COUNT], max=new long[SERIES_COUNT];
    private final double[] sum=new double[SERIES_COUNT];
    public synchronized void tick(int index,long nanos){
        if(index<0 || index>=SERIES_COUNT)return;
        if(nanos<0 || (count[index]>0 && nanos<=last[index])){rejected[index]++;return;}
        if(count[index]>0){
            long delta=nanos-last[index];
            sum[index]+=delta;
            if(count[index]==1 || delta<min[index])min[index]=delta;
            if(delta>max[index])max[index]=delta;
        }
        last[index]=nanos;count[index]++;
    }
    public synchronized void reset(){
        for(int i=0;i<SERIES_COUNT;i++){count[i]=rejected[i]=last[i]=min[i]=max[i]=0;sum[i]=0;}
    }
    public synchronized String report(){
        StringBuilder out=new StringBuilder("Cadence observations; idle gaps included. Hz = inverse mean interval, not rendered FPS.\n");
        for(int i=0;i<SERIES_COUNT;i++){
            out.append(NAMES[i]).append(": count=").append(count[i]);
            if(count[i]>1){
                double mean=sum[i]/(count[i]-1);
                out.append(String.format(Locale.ROOT," mean=%.3f ms (%.2f Hz) min=%.3f ms max=%.3f ms",mean/1e6,1e9/mean,min[i]/1e6,max[i]/1e6));
            }else out.append(" interval=n/a");
            out.append(" ignored=").append(rejected[i]).append('\n');
        }
        return out.toString();
    }
}
