import io.github.kacperla.touchzoom.*;
/** Service-like callbacks with a backend that never acknowledges redundant1x enable. */
public class NoopScaleEntryTest {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 static final class Flow {
  final DoubleTapScale selection=new DoubleTapScale();final ScaleBackpressure gate=new ScaleBackpressure();
  boolean active,pending;float desired,last;int writes;long firstWrite=-1;
  void down(){active=true;pending=false;gate.reset();selection.begin(false,2);apply(1,0);}
  void apply(float scale,long now){
   if(!selection.shouldWrite(scale))return;
   selection.wrote();gate.sent(scale);last=scale;writes++;if(firstWrite<0)firstWrite=now;
   // Fake backend accepts requests. Unchanged1x emits no callback; test explicitly reports actual changes.
  }
  void move(float scale){if(active){desired=scale;selection.select(scale);pending=true;}}
  void frame(long now){if(active&&pending&&!gate.waiting()){pending=false;apply(desired,now);}}
  void ack(float scale){gate.observe(scale);}
  void cancel(){active=false;pending=false;gate.reset();}
  float release(){float target=selection.selected();cancel();return target;}
 }
 public static void main(String[]a){
  Flow f=new Flow();f.down();ok(f.writes==0);ok(!f.gate.waiting());
  // DOWN and downward moves at1x never arm a timeout, even with no callback ever.
  for(int t=1;t<100;t++){f.move(1);f.frame(t);ok(f.writes==0);ok(!f.gate.waiting());}
  f.move(1.2f);f.frame(108);ok(f.writes==1);ok(f.firstWrite==108);ok(f.gate.waiting());
  for(int t=109;t<140;t++){f.move(1.3f+(t-109)/100f);f.frame(t);ok(f.writes==1);}
  f.ack(1.2f);f.frame(141);ok(f.writes==2);ok(Math.abs(f.last-1.6f)<.001f); // newest only
  f.ack(f.last);f.move(1);f.frame(150);ok(f.writes==3);ok(f.last==1); // returning fromzoom must write1x
  // Real changed return-to1x reports active1x; unlike redundant initial1x, it is a state change.
  ok(f.gate.observe(1));f.move(2);f.frame(158);ok(f.writes==4);ok(f.last==2);
  ok(!f.gate.observe(1));ok(f.gate.waiting()); // stale1x report cannot acknowledge2x
  float chosen=f.release();ok(chosen==2);int old=f.writes;f.ack(1);f.frame(2000);ok(f.writes==old);ok(!f.gate.waiting());
  Flow release=new Flow();release.down();release.move(2.5f);release.frame(8);release.move(3.2f);
  chosen=release.release();ok(chosen==3.2f);ok(!release.pending&&!release.gate.waiting());
  ZoomLatch latch=new ZoomLatch();latch.setLatched(chosen>1.01f);ok(latch.isLatched());ok(!latch.toggle());
  Flow cancel=new Flow();cancel.down();cancel.move(4);cancel.cancel();cancel.frame(8);ok(cancel.writes==0);
  DoubleTapScale already=new DoubleTapScale();already.begin(true,3);ok(already.shouldWrite(3));
  System.out.println(n+" no-ack1x/service-flow/first-frame/latest-only/release/cancel assertions passed");
 }
}
