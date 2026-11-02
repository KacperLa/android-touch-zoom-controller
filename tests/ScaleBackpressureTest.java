import io.github.kacperla.touchzoom.ScaleBackpressure;
public class ScaleBackpressureTest{
 static int n;static void check(boolean v){n++;if(!v)throw new AssertionError("assert "+n);}
 public static void main(String[] args){
  ScaleBackpressure p=new ScaleBackpressure();check(!p.waiting());p.sent(2);check(p.waiting());check(!p.observe(1));check(!p.observe(Float.NaN));
  float latest=2;int sends=1;
  for(int i=0;i<10000;i++){latest=2+i/2000f;check(p.waiting());} // blocked backend: no extra sends
  check(sends==1);check(p.observe(2));check(!p.waiting());p.sent(latest);sends++;check(sends==2);
  check(!p.observe(2));check(p.observe(latest)); // obsolete ack cannot release newer scale
  p.sent(3);p.reset();check(!p.waiting());p.sent(3);check(p.waiting());p.reset(); // second-tap release clears old gate before independent toggle
  p.sent(4);boolean threw=false;try{p.sent(5);}catch(IllegalStateException e){threw=true;}check(threw);
  p.reset();check(!p.waiting());check(!p.observe(4)); // late callback after cancel does nothing
  for(int i=0;i<1000;i++){float s=1+(i%700)/100f;p.sent(s);check(p.observe(s));check(!p.waiting());}
  System.out.println(n+" delayed-backend/latest-value/cancel assertions passed");
 }
}
