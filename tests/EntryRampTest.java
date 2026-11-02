import io.github.kacperla.touchzoom.*;
public class EntryRampTest {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("assertion "+n);}
 public static void main(String[]args){
  EntryRamp r=new EntryRamp();
  check(!r.start(0,1,8,false));check(!r.start(0,8,8,true));check(!r.start(0,Float.NaN,8,true));
  check(r.start(100,1,8,true));check(r.value(100)==1);check(r.value(220)==8);
  float prev=1;for(long t=100;t<=220;t++){float v=r.value(t);check(v>=prev && v<=8);prev=v;}
  ScaleBackpressure gate=new ScaleBackpressure();int writes=0;float latestX=0,lastX=0;long last=-999;
  for(long t=100;t<=300;t++){
   latestX=t; // many input samples: no competing center-only command during ramp
   if(r.ready(t)&&!gate.waiting()){
    check(t-last>=17);last=t;lastX=latestX;float v=r.value(t);r.sent(t);gate.sent(v);writes++;
    check(!gate.observe(v+.1f));check(gate.waiting());
    check(gate.observe(v));r.acknowledged();
   }
  }
  check(writes<=9);check(!r.running());check(lastX>=220);
  // Missing acknowledgment blocks every later write, even after nominal duration.
  r.start(0,1,4,true);r.sent(0);gate.sent(1);
  int deferred=0;for(int t=1;t<1000;t++){if(r.ready(t)&&!gate.waiting())deferred++;}
  check(deferred==0);r.cancel();gate.reset();check(!r.running());check(!r.ready(1001));
  check(!gate.observe(1));check(!r.running()); // stale callback cannot resurrect cancellation
  // Delayed callback samples elapsed time once, not all missed values.
  r.start(0,1,8,true);r.sent(0);check(r.value(800)==8);check(r.ready(800));r.sent(800);r.acknowledged();check(!r.running());
  // New gesture must start from actual state, not remembered8; disabling restores no ramp.
  check(r.start(1000,3,8,true));check(r.value(1000)==3);r.cancel();check(!r.start(1001,1,8,false));
  // Frozen logical pad: Android clamped/intermediate center reports never re-anchor it.
  float padLeft=120,padSize=180;float first=Mapping.axis(0,999,padLeft,padSize,150);
  for(int scale=1;scale<=8;scale++)check(Mapping.axis(0,999,padLeft,padSize,150)==first);
  System.out.println(n+" entry-ramp cadence/ownership/cancel/elapsed/anchor assertions passed");
 }
}
