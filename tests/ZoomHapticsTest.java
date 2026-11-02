import io.github.kacperla.touchzoom.ZoomHaptics;
public class ZoomHapticsTest{
 static int n;static void c(boolean b){n++;if(!b)throw new AssertionError("assert "+n);}
 public static void main(String[] a){ZoomHaptics h=new ZoomHaptics();
  c(h.observe(true)==0);c(h.observe(false)==0); // background startup/system changes silent
  for(int i=0;i<1000;i++){
   h.requestEngage(false);c(h.observe(false)==0);c(h.observe(true)==1);c(h.observe(true)==0);
   c(h.observe(true)==0); // repeatedpan/callback no spam
   h.requestRelease(true);c(h.observe(true)==0);c(h.observe(false)==2);c(h.observe(false)==0);
   h.requestEngage(true);c(h.observe(true)==0); // alreadylatched pan silent
   h.requestEngage(false);h.cancel();c(h.observe(true)==0); // cancelbeforeactivation silent
   h.requestRelease(true);h.cancel();c(h.observe(false)==0); // background interrupt silent
   h.requestRelease(false);c(h.observe(false)==0); // nofalseoff
  }
  System.out.println(n+" user-intent haptic transition assertions passed");
 }
}
