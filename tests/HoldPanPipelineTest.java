import io.github.kacperla.touchzoom.*;
/** Deterministic fake-clock integration of shared gesture, geometry, ownership, queue and probe helpers.
 * Not an Android MotionEvent/Surface/Binder test. */
public class HoldPanPipelineTest{
 static int n;static void c(boolean x){n++;if(!x)throw new AssertionError("assert"+n);}
 public static void main(String[] args){
  for(int extra:new int[]{0,900,2000}){
   GestureProbe probe=new GestureProbe();MomentaryZoom preview=new MomentaryZoom();LatestRenderQueue<Float> render=new LatestRenderQueue<>();ScaleBackpressure scale=new ScaleBackpressure();
   probe.arm(extra,ActivationSettings.totalMs(extra));probe.down(0,0);long due=UpdatePacer.holdDeadline(0,ActivationSettings.totalMs(extra));
   c(GestureRules.waitingForHold(false,preview.ownsPreview()));probe.mark("hold",due,-1);preview.begin(2);c(!GestureRules.waitingForHold(false,preview.ownsPreview()));
   scale.sent(2); // missing scale ack must not block center/marker helpers
   float latest=0;for(int frame=0;frame<120;frame++){latest=Mapping.axis(0,1000,0,100,frame/2f);render.offer(latest);c(scale.waiting());}
   c(render.take()==latest);c(!render.complete());probe.mark("marker",due+8,due+7);
   // Offering marker precedes simulated slow Binder; render slot remains available during call.
   c(render.offer(900f));probe.mark("center start",due+9,-1);c(render.take()==900f);render.complete();probe.mark("center end",due+1009,-1);probe.span("center",due+9,due+1009);
   c(probe.report().contains("max=1000 ms"));preview.end();scale.reset();render.stop();c(!preview.ownsPreview());c(!scale.waiting());c(!render.offer(1f));probe.end(due+1010,due+1010);c(!probe.recording());
  }
  System.out.println(n+" fake-clock hold/pan/blocked-Binder/cleanup integration assertions passed");
 }
}
