import io.github.kacperla.touchzoom.KeyboardAvoidance;
public class KeyboardAvoidanceTest {
 static int count;
 static void eq(float a,float b){count++;if(Math.abs(a-b)>.001)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  float preferred=800;
  eq(KeyboardAvoidance.top(preferred,60,1000,30,20,false,400,8),800);
  eq(KeyboardAvoidance.top(preferred,60,1000,30,20,true,400,8),532);
  eq(preferred,800); // automatic placement never mutates saved position
  eq(KeyboardAvoidance.top(preferred,60,1000,30,20,false,0,8),800); // close restores
  eq(KeyboardAvoidance.top(500,60,1000,30,20,true,400,8),500); // not covered
  eq(KeyboardAvoidance.top(540,60,1000,30,20,true,400,8),540); // touching edge, no unnecessary lift
  eq(KeyboardAvoidance.top(541,60,1000,30,20,true,400,8),532);
  eq(KeyboardAvoidance.top(800,60,1000,30,20,true,0,8),800); // floating/no bottom bounds
  eq(KeyboardAvoidance.top(980,60,1000,30,20,false,0,8),920); // safe bottom clamp
  eq(KeyboardAvoidance.top(-30,60,1000,30,20,true,400,8),30);
  eq(KeyboardAvoidance.top(800,60,1000,30,20,true,990,8),30); // cannot fit; top remains reachable
  eq(KeyboardAvoidance.top(800,60,1000,30,20,true,400,0),540);
  for(int height:new int[]{1000,1800,2400})for(int keyboard=100;keyboard<height-200;keyboard+=100){
   float chosen=height*.8f;
   float lifted=KeyboardAvoidance.top(chosen,60,height,30,20,true,keyboard,8);
   count++;if(lifted>chosen)throw new AssertionError("moved down");
   count++;if(lifted+60>height-keyboard && lifted>30)throw new AssertionError("still covered");
   eq(KeyboardAvoidance.top(chosen,60,height,30,20,false,0,8),chosen);
  }
  System.out.println(count+" keyboard avoidance assertions passed");
 }
}
