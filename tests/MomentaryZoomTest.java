import io.github.kacperla.touchzoom.MomentaryZoom;
public class MomentaryZoomTest {
 static int count;
 static void check(boolean yes){count++;if(!yes)throw new AssertionError("assertion "+count);}
 public static void main(String[] args){
  MomentaryZoom z=new MomentaryZoom();
  check(!z.end());
  for(String termination:new String[]{"release","cancel","reposition","screen off","fold","interrupt","destroy","multitouch"}){
   z.begin(3);check(z.ownsPreview());check(z.selectedScale()==3);check(z.end());check(!z.ownsPreview());check(!z.end());
   z.select(6);check(z.selectedScale()==3); // late move cannot change ended session
  }
  z.begin(2);z.select(4);check(z.selectedScale()==4);check(z.end());check(z.selectedScale()==4);
  z.begin(z.selectedScale());check(z.selectedScale()==4);check(z.end());
  z.begin(2);z.select(1);check(z.selectedScale()==2);z.end(); // zoom-off preview does not erase chosen scale
  z.begin(Float.NaN);check(z.selectedScale()==2);z.end();
  z.begin(100);check(z.selectedScale()==8);z.end();
  System.out.println(count+" momentary lifecycle assertions passed");
 }
}
