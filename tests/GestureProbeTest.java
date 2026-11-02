import io.github.kacperla.touchzoom.GestureProbe;
public class GestureProbeTest{
 static int n;static void c(boolean x){n++;if(!x)throw new AssertionError("assert"+n);}
 public static void main(String[] args){GestureProbe p=new GestureProbe();p.mark("ignored",0,0);c(p.report().contains("No phase"));p.arm(0,100);int gen=p.generation();c(!p.recording());p.down(1000,900);p.mark("hold deadline",1000,-1);p.mark("hold timer",1010,-1);p.mark("MOVE",2010,1020);p.span("center",1020,2020);p.mark("MOVE",3000,3000);p.end(3100,3090);String s=p.report();c(s.contains("event lag=100 ms"));c(s.contains("event lag=990 ms"));c(s.contains("max=1000 ms"));c(!s.contains("+2000 ms"));c(!p.recording());p.arm(900,1000);p.down(4000,4000);p.markFor(gen,"stale worker",4100,-1);c(!p.report().contains("stale worker"));
 for(int i=0;i<10000;i++){p.mark("key"+i,4001,-1);p.span("span"+i,4000,4001);}c(!p.report().contains("key32:"));c(!p.report().contains("span12:"));p.mark("timeout",20000,-1);c(!p.recording());System.out.println(n+" bounded phase/lifecycle/stale-render assertions passed");}
}
