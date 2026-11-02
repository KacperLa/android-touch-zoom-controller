import io.github.kacperla.touchzoom.BubbleAppearance;
public class BubbleAppearanceTest {
 static int count;
 static void check(boolean yes){count++;if(!yes)throw new AssertionError("assertion "+count);}
 public static void main(String[] args){
  BubbleAppearance a=new BubbleAppearance();
  check(a.shouldDrawArtwork()); // idle, and while activation is pending
  check(!a.update(false));check(a.shouldDrawArtwork()); // tap/canceled delay never activated
  check(a.update(true));check(!a.shouldDrawArtwork()); // actual activation hides
  check(!a.update(true));check(!a.shouldDrawArtwork()); // panning/scale changes remain hidden
  check(a.update(false));check(a.shouldDrawArtwork()); // actual release/cancel unzoom restores
  for(int i=0;i<100;i++){check(a.update(true));check(!a.shouldDrawArtwork());check(a.update(false));check(a.shouldDrawArtwork());}
  System.out.println(count+" bubble appearance assertions passed");
 }
}
