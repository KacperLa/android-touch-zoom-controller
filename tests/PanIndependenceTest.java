import io.github.kacperla.touchzoom.*;
public class PanIndependenceTest{
 public static void main(String[] a){ScaleBackpressure gate=new ScaleBackpressure();UpdatePacer center=new UpdatePacer();gate.sent(4);int centers=0;
 for(int i=0;i<120;i++){if(center.changed(4,i*2,i*3)){center.sent(i*8,4,i*2,i*3);centers++;}if(!gate.waiting())throw new AssertionError();}
 if(centers!=120)throw new AssertionError();if(gate.observe(2))throw new AssertionError();if(!gate.observe(4))throw new AssertionError();
 System.out.println("123 delayed-scale/independent-center assertions passed");}
}
