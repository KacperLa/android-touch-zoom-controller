import io.github.kacperla.touchzoom.*;
public class DragVisualTest{
 static int n;static void c(boolean x){n++;if(!x)throw new AssertionError("assert"+n);}
 public static void main(String[] a){
  c(DragVisual.scale(false)==1);c(DragVisual.scale(true)==1.5f);
  for(float d:new float[]{56,168,224})for(int anchor=0;anchor<=56;anchor++){
   float grab=anchor*d/56;
   for(float scale:new float[]{1,1.5f})for(int desired=-100;desired<=1100;desired+=5){
    int logical=DragVisual.clampLogical(desired,grab,scale,d,20,1000);
    float visual=DragVisual.visualStart(logical,grab,scale);
    c(visual>=19.999);c(visual+scale*d<=1000.001);
    c(Math.abs((visual+scale*grab)-(logical+grab))<.001);
    // Shrinking around the same grab preserves its physical point, with no extra translation.
    c(Math.abs((DragVisual.visualStart(logical,grab,1)+grab)-(logical+grab))<.001);
   }
  }
  c(DragVisual.scale(false)==1); // every non-relocation state normal
  System.out.println(n+" drag scale/anchor/edge/reset assertions passed");
 }
}
