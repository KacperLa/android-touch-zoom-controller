import io.github.kacperla.touchzoom.CadenceStats;
public class CadenceStatsTest {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("assert "+n);}
 public static void main(String[] args)throws Exception{
  CadenceStats s=new CadenceStats();check(s.report().contains("count=0 interval=n/a"));
  for(int i=0;i<=120;i++){s.tick(CadenceStats.INPUT_EVENT,Math.round(i*1e9/60));s.tick(CadenceStats.VSYNC,Math.round(i*1e9/120));}
  String r=s.report();check(r.contains("16.667 ms (60.00 Hz)"));check(r.contains("8.333 ms (120.00 Hz)"));check(r.contains("count=121"));
  s.reset();s.tick(0,0);s.tick(0,10_000_000);s.tick(0,1_010_000_000);
  r=s.report();check(r.contains("505.000 ms (1.98 Hz)"));check(r.contains("max=1000.000 ms"));check(r.contains("min=10.000 ms"));
  s.tick(0,1_010_000_000);s.tick(0,3);s.tick(0,-1);s.tick(-1,0);s.tick(6,0);
  r=s.report();check(r.contains("count=3"));check(r.contains("ignored=3"));
  s.reset();for(int i=0;i<6;i++){s.tick(i,0);s.tick(i,Long.MAX_VALUE);}
  check(!s.report().contains("Infinity"));check(!s.report().contains("NaN"));
  s.reset();Thread[] workers=new Thread[6];for(int j=0;j<6;j++){final int index=j;workers[j]=new Thread(()->{for(int i=0;i<10000;i++)s.tick(index,i*1000000L);});workers[j].start();}
  for(Thread t:workers)t.join();r=s.report();check(r.split("count=10000",-1).length==7);check(r.length()<2000);
  s.reset();check(!s.report().contains("mean="));check(s.report().split("count=0",-1).length==7);
  System.out.println(n+" cadence assertions passed");
 }
}
