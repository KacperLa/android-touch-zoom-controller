import io.github.kacperla.touchzoom.MagnificationOwnership;
public class MagnificationOwnershipTest {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 public static void main(String[]args){
  MagnificationOwnership o=new MagnificationOwnership();
  o.beginInteraction(1,true,3);o.command(1,Float.NaN,false);
  ok(!o.isOwned());ok(!o.observe(1,true,3));ok(!o.canReset(1,true,3));
  o.relinquish();o.beginInteraction(1,false,1);o.command(1,2,true);
  ok(o.isOwned());ok(!o.observe(1,true,2));ok(o.canReset(1,true,2));
  o.command(1,3,true);ok(!o.observe(1,true,2));ok(!o.canReset(1,true,2));
  ok(!o.observe(1,true,3));ok(o.canReset(1,true,3));
  ok(o.observe(1,true,5));ok(!o.isOwned());ok(!o.canReset(1,true,5));
  o.beginInteraction(1,true,2);o.command(1,2,true);ok(o.observe(2,true,2));ok(!o.isOwned());
  o.beginInteraction(2,true,4);ok(!o.isOwned());ok(!o.canReset(2,true,4));ok(o.observe(2,true,4));
  o.beginInteraction(1,false,1);o.command(1,4,true);ok(o.observe(1,false,1));ok(!o.isOwned());
  o.beginInteraction(1,false,1);o.command(1,2,true);o.commandOff();
  ok(!o.isOwned());ok(!o.observe(1,false,1));
  // A queued own-off report may arrive after the next active command, without canceling that command.
  o.beginInteraction(1,false,1);o.command(1,2,true);o.commandOff();o.command(1,3,true);
  ok(!o.observe(1,false,1));ok(o.isOwned());ok(!o.observe(1,true,3));ok(o.canReset(1,true,3));
  ok(o.observe(1,false,1));ok(!o.isOwned()); // second unaccounted off is not ignored indefinitely
  o.beginInteraction(1,false,1);o.command(1,2,true);o.beginInteraction(1,true,5);
  ok(!o.isOwned());ok(!o.canReset(1,true,5));ok(!o.observe(1,true,5));
  // No-op resets may never report off. A fresh DOWN or matching latest active report expires that allowance.
  o.commandOff();o.beginInteraction(1,true,5);ok(o.observe(1,false,1));
  o.beginInteraction(1,true,2);o.command(1,2,true);o.commandOff();o.command(1,3,true);
  ok(!o.observe(1,true,3));ok(o.observe(1,false,1));ok(!o.isOwned());
  // External activation while our hold is still pending must cancel, not be adopted as our future preview.
  o.relinquish();o.beginInteraction(0,false,1);ok(!o.observe(0,false,1));ok(!o.observe(1,false,1));
  ok(o.observe(1,true,3));ok(!o.isOwned());
  o.beginInteraction(1,false,1);o.command(1,2,true);ok(!o.observe(1,true,2));ok(o.isOwned());
  // Bounded history for delayed own acknowledgments; never stores centers or unbounded commands.
  o.relinquish();o.beginInteraction(1,false,1);
  for(int i=0;i<10000;i++){float scale=1.1f+(i%500)*.01f;o.command(1,scale,true);ok(o.retainedScaleCount()<=8);}
  o.relinquish();ok(o.retainedScaleCount()==0);ok(!o.observe(1,true,2));ok(!o.isOwned());
  o.beginInteraction(1,true,2);o.command(1,2,true);ok(o.observe(1,true,Float.NaN));ok(!o.isOwned());
  System.out.println(n+" conservative magnification ownership/history/takeover/reset assertions passed");
 }
}
