package io.github.kacperla.touchzoom;
/** Fixed-size first-input capture. No sample replay, filtering, or input changes. Main thread only. */
public final class InputArrivalStats {
 public static final int LIMIT=96;
 private final double[][] rows=new double[LIMIT][12];
 private int count,total,backsteps,historical;private long previous=-1;private boolean unbuffered;
 public void reset(boolean requested){count=total=backsteps=historical=0;previous=-1;unbuffered=requested;}
 public void add(long event,long arrival,int action,float rawX,float rawY,int history,long firstTime,float firstX,float firstY,long lastTime,float lastX,float lastY){
  total++;if(previous>=0 && event<previous)backsteps++;previous=event;historical+=history;
  if(count>=LIMIT)return;
  double[] r=rows[count++];r[0]=event;r[1]=arrival;r[2]=action;r[3]=rawX;r[4]=rawY;r[5]=history;
  r[6]=firstTime;r[7]=firstX;r[8]=firstY;r[9]=lastTime;r[10]=lastX;r[11]=lastY;
 }
 public int count(){return count;}public int total(){return total;}public int backsteps(){return backsteps;}
 public String report(){
  StringBuilder b=new StringBuilder("Input delivery experiment: unbuffered requested="+unbuffered+"; events="+total+"; timestamp backsteps="+backsteps+"; historical samples="+historical+". First "+count+" events retained (max96); history coordinates are LOCAL, current coordinates RAW. No resampling flag is exposed here.\n");
  for(int i=0;i<count;i++){double[] r=rows[i];b.append("input event=").append((long)r[0]).append(" arrival=").append((long)r[1]).append(" action=").append((int)r[2]).append(" raw=").append(r[3]).append(',').append(r[4]).append(" history=").append((int)r[5]);
   if(r[5]>0)b.append(" firstLocal=").append((long)r[6]).append(':').append(r[7]).append(',').append(r[8]).append(" lastLocal=").append((long)r[9]).append(':').append(r[10]).append(',').append(r[11]);b.append('\n');}
  return b.toString();
 }
}
