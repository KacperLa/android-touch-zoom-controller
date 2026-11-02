import io.github.kacperla.touchzoom.GestureRules;
public class GestureRulesTest {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 public static void main(String[] args){
  check(GestureRules.waitingForHold(false,false));
  check(!GestureRules.waitingForHold(false,true)); // after hold, every motion pans
  check(!GestureRules.waitingForHold(true,false)); // second tap never relocates
  check(!GestureRules.waitingForHold(true,true));
  for(int ms=-1;ms<=2200;ms++){
   check(GestureRules.quickTap(false,false,ms)==(ms>=0&&ms<=250));
   check(!GestureRules.quickTap(true,false,ms));
   check(!GestureRules.quickTap(false,true,ms));
  }
  check(GestureRules.tapBeforeHold(false,false,99,false,false,100));
  check(!GestureRules.tapBeforeHold(false,false,100,false,false,100));
  check(!GestureRules.tapBeforeHold(false,false,150,false,true,100));
  check(!GestureRules.tapBeforeHold(false,false,80,false,true,100));
  check(GestureRules.tapBeforeHold(false,false,150,true,true,100));
  check(GestureRules.tapBeforeHold(false,false,200,false,false,1000));
  check(!GestureRules.tapBeforeHold(false,false,251,false,false,1000));
  System.out.println(n+" gesture boundary assertions passed");
 }
}
