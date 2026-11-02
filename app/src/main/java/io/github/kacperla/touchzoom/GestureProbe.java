package io.github.kacperla.touchzoom;
import java.util.LinkedHashMap;
/** One opt-in gesture, fixed-key bounded timestamp summary; safe from input/render threads. */
public final class GestureProbe {
 private volatile boolean armed;private volatile boolean recording;private volatile int generation;private long origin;private int extra,total;
 private final LinkedHashMap<String,long[]> phases=new LinkedHashMap<>();
 private final LinkedHashMap<String,long[]> spans=new LinkedHashMap<>();
 public synchronized void arm(int extraMs,int totalMs){generation++;armed=true;recording=false;extra=extraMs;total=totalMs;phases.clear();spans.clear();}
 public synchronized void down(long arrival,long event){if(!armed)return;armed=false;recording=true;origin=arrival;mark("DOWN arrival",arrival,event);}
 public synchronized void clear(){generation++;armed=false;recording=false;phases.clear();spans.clear();}
 public boolean recording(){return recording;}
 public boolean capturePending(){return armed || recording;}
 public int generation(){return generation;}
 public void markFor(int gen,String name,long time,long event){if(!recording)return;synchronized(this){if(gen==generation)mark(name,time,event);}}
 public void spanFor(int gen,String name,long start,long end){if(!recording)return;synchronized(this){if(gen==generation)span(name,start,end);}}
 public void mark(String name,long time,long event){if(!recording)return;synchronized(this){if(recording && time-origin>15000)recording=false;if(!recording || phases.containsKey(name) || phases.size()>=32)return;phases.put(name,new long[]{time,event});}}
 public void span(String name,long start,long end){if(!recording)return;synchronized(this){if(recording && end-origin>15000)recording=false;if(!recording)return;long[] v=spans.get(name);if(v==null){if(spans.size()>=12)return;v=new long[2];spans.put(name,v);}v[0]++;v[1]=Math.max(v[1],Math.max(0,end-start));}}
 public synchronized void stop(String reason,long time){mark(reason,time,-1);recording=false;}
 public synchronized void end(long time,long event){mark("UP/CANCEL arrival",time,event);recording=false;}
 public synchronized String report(){
  if(armed)return "Phase summary armed: make one short ordinary hold-and-pan, then release.";
  if(phases.isEmpty())return "No phase capture yet.";
  StringBuilder b=new StringBuilder("Gesture phase summary; extra="+extra+" ms; total hold="+total+" ms.\nTimes relative to DOWN arrival; lag=arrival minus input event time. No screen content.\n");
  for(java.util.Map.Entry<String,long[]> e:phases.entrySet()){long[] v=e.getValue();b.append(e.getKey()).append(": +").append(v[0]-origin).append(" ms");if(v[1]>=0)b.append("; event lag=").append(v[0]-v[1]).append(" ms");b.append('\n');}
  for(java.util.Map.Entry<String,long[]> e:spans.entrySet())b.append(e.getKey()).append(": count=").append(e.getValue()[0]).append(" max=").append(e.getValue()[1]).append(" ms\n");
  if(recording)b.append("Capture still active.\n");else b.append("Capture ended; phases missing at release did not complete during capture.\n");return b.toString();
 }
}
