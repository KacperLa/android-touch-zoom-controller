import io.github.kacperla.touchzoom.*;
public class DoubleTapScaleTest {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("assertion "+n);}
 public static void main(String[]a){
  DoubleTapScale scale=new DoubleTapScale();ZoomLatch latch=new ZoomLatch();ScaleBackpressure gate=new ScaleBackpressure();
  for(float remembered:new float[]{2,4,8}){
   float start=scale.begin(false,remembered);ok(start==1);ok(!scale.shouldLatch());
   float prev=1;for(int y=0;y<=100;y++){
    float selected=Mapping.zoom(start,y,100);scale.select(selected);ok(scale.selected()==selected);ok(selected>=prev);prev=selected;
   }
   ok(scale.selected()==2);latch.setLatched(scale.shouldLatch());ok(latch.isLatched());
   float saved=scale.selected();ok(saved==2);ok(!latch.toggle()); // next quick tap off
  }
  ok(scale.begin(true,3)==3);scale.select(4.25f);ok(scale.selected()==4.25f);ok(scale.shouldLatch());
  scale.begin(false,8);scale.select(1.03f);ok(scale.selected()==1.03f);ok(scale.shouldLatch());
  MomentaryZoom preview=new MomentaryZoom();preview.begin(scale.selected());ok(preview.selectedScale()==1.03f);preview.end();
  scale.select(1);latch.setLatched(scale.shouldLatch());ok(!latch.isLatched());
  scale.begin(false,Float.NaN);ok(scale.selected()==1);scale.select(Float.NaN);ok(scale.selected()==1);
  // Release clears intermediate pending ownership before one final commit. Stale callbacks cannot write.
  gate.sent(1.5f);scale.select(2.7f);gate.reset();latch.setLatched(scale.shouldLatch());
  ok(!gate.waiting());ok(!gate.observe(1.5f));ok(latch.isLatched());ok(scale.selected()==2.7f);
  // Cancel does not execute setLatched: an unlatched preview remains unlatched.
  latch.clear();scale.begin(false,2);scale.select(3);gate.sent(3);gate.reset();ok(!latch.isLatched());
  ok(!gate.observe(3));latch.queueTap();latch.setLatched(true);ok(!latch.hasPendingTap());
  System.out.println(n+" actual-scale/double-tap selection/latch/cancel assertions passed");
 }
}
