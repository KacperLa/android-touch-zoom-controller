import io.github.kacperla.touchzoom.InputArrivalStats;
public class InputArrivalStatsTest {
 static int n;static void c(boolean x){n++;if(!x)throw new AssertionError("assert"+n);}
 public static void main(String[] args){
  InputArrivalStats s=new InputArrivalStats();s.reset(true);
  s.add(200,201,2,100,100,0,-1,0,0,-1,0,0);
  s.add(100,310,2,100,100,14,70,2,3,90,4,5);
  s.add(320,321,2,80,90,26,110,8,9,319,10,11);
  c(s.backsteps()==1);c(s.count()==3);c(s.report().contains("history=26"));c(s.report().contains("firstLocal=110:8.0,9.0"));
  for(int i=0;i<100000;i++)s.add(400+i,401+i,2,i,i,0,-1,0,0,-1,0,0);
  c(s.count()==96);c(s.total()==100003);c(s.report().length()<30000);
  s.reset(false);c(s.count()==0);c(s.total()==0);c(s.backsteps()==0);c(s.report().contains("requested=false"));
  System.out.println(n+" bounded input-history/reset/backstep assertions passed");
 }
}
