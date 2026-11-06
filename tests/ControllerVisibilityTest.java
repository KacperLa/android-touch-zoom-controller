import io.github.kacperla.touchzoom.*;
/** Policy/queue regression checks; Android compositor presentation is not simulated. */
public class ControllerVisibilityTest {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 static void hidden(ControllerDragMode m){ok(!m.showsPad(true));ok(!m.showsPad(false));}
 public static void main(String[] args){
  ControllerDragMode m=new ControllerDragMode();hidden(m);
  for(int iteration=0;iteration<100;iteration++){
   m.begin(true,false);hidden(m);ok(!m.hidesBubble(true));
   // Existing magnification callbacks/preview adoption and subslop MOVE cannot reveal a pad.
   for(int jitter=0;jitter<20;jitter++){ok(m.movement(false)==ControllerDragMode.WAIT);hidden(m);}
   ok(m.hold()==ControllerDragMode.RELOCATE);hidden(m);ok(!m.hidesBubble(true));
   for(int move=0;move<20;move++){ok(m.movement(true)==ControllerDragMode.RELOCATE);hidden(m);}
   m.end();hidden(m);ok(!m.hidesBubble(true));
   m.begin(true,false);ok(m.movement(true)==ControllerDragMode.PAN);
   ok(m.showsPad(true));ok(m.hidesBubble(true));ok(!m.showsPad(false));
   ok(m.hold()==ControllerDragMode.PAN);ok(m.showsPad(true)); // Late timer cannot relocate.
   m.end();hidden(m);
   m.begin(false,false);hidden(m);ok(m.hold()==ControllerDragMode.PAN);ok(m.showsPad(true));
   m.end();m.begin(false,false);ok(m.movement(true)==ControllerDragMode.RELOCATE);hidden(m);
   for(boolean zoomed:new boolean[]{false,true}){
    m.begin(zoomed,true);hidden(m);ok(m.hidesBubble(true)); // Scale artwork unchanged; never pad.
    m.end();hidden(m);ok(!m.hidesBubble(true));
   }
   // Latest-only render data can contain an old visible frame. Surface visibility remains
   // main-thread policy, not a side effect of consuming/drawing that immutable snapshot.
   LatestRenderQueue<Boolean> q=new LatestRenderQueue<>();m.begin(true,false);m.movement(true);
   boolean surfaceVisible=m.showsPad(true);ok(q.offer(surfaceVisible));Boolean inFlight=q.take();
   m.end();surfaceVisible=m.showsPad(true);ok(!q.offer(surfaceVisible));
   m.begin(true,false);surfaceVisible=m.showsPad(true);ok(!surfaceVisible);ok(!q.offer(surfaceVisible));
   ok(inFlight);ok(!surfaceVisible);ok(q.complete());ok(!q.take());ok(!q.complete());
   m.hold();hidden(m);q.stop();ok(!q.offer(true));ok(q.take()==null);
  }
  System.out.println(n+" committed-pan visibility, relocation, cleanup and render-queue assertions passed");
 }
}
