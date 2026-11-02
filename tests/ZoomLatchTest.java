import io.github.kacperla.touchzoom.*;
public class ZoomLatchTest {
 static int count;static void check(boolean x){count++;if(!x)throw new AssertionError("assertion "+count);}
 public static void main(String[] args){
  ZoomLatch l=new ZoomLatch();check(!l.isLatched());check(!l.hasPendingTap());
  l.queueTap();check(!l.isLatched());check(l.hasPendingTap());check(l.commitTap());check(l.isLatched());check(!l.hasPendingTap());
  l.queueTap();l.cancelPendingTap();check(l.isLatched());check(!l.commitTap()); // second tap + drag preserves latch
  l.queueTap();check(l.commitTap());check(!l.isLatched());
  l.queueTap();l.cancelPendingTap();check(!l.isLatched()); // canceled momentary gesture doesn't latch
  l.toggle();l.queueTap();l.clear();check(!l.isLatched());check(!l.hasPendingTap());
  BubbleAppearance a=new BubbleAppearance();a.update(true);check(a.shouldDrawArtwork(false,false));check(!a.shouldDrawArtwork(true,false));check(a.shouldDrawArtwork(true,true));
  a.update(false);check(a.shouldDrawArtwork(true,false));
  for(int i=0;i<100;i++){l.queueTap();check(l.commitTap());check(l.isLatched());l.queueTap();check(l.commitTap());check(!l.isLatched());}
  System.out.println(count+" latch and usable-off-target assertions passed");
 }
}
