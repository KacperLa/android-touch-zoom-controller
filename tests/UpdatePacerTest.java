import io.github.kacperla.touchzoom.UpdatePacer;
public class UpdatePacerTest {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("assert "+n);}
 public static void main(String[] args){
  UpdatePacer p=new UpdatePacer();check(p.changed(2,100,100));p.sent(0,2,100,100);
  check(!p.changed(2,100,100));check(!p.changed(2.001f,100,100));check(p.changed(2,100.0001f,100));check(p.changed(2.001f,100.1f,100.1f));check(p.changed(2.02f,100,100));
  p.reset();check(p.changed(2,100,100));
  for(int t=0;t<120;t++){check(p.changed(2,t*2,100));p.sent(t*8,2,t*2,100);} // no artificial cap at120Hz
  for(int d=0;d<=2000;d++)check(UpdatePacer.holdDeadline(123,d)==123+d);
  check(UpdatePacer.holdDeadline(123,-5)==123);
  System.out.println(n+" duplicate/deadline and uncapped-frame assertions passed");
 }
}
