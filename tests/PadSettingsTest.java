import io.github.kacperla.touchzoom.*;
public class PadSettingsTest {
 static int count;
 static void check(boolean b){count++;if(!b)throw new AssertionError("assertion "+count);}
 static void near(float a,float b){check(Math.abs(a-b)<.01);}
 public static void main(String[] args){
  check(PadSettings.normalize(-100)==80);check(PadSettings.normalize(999)==360);check(PadSettings.normalize(177)==180);
  check(PadSettings.normalize(Integer.MAX_VALUE)==360);check(PadSettings.normalize(Integer.MIN_VALUE)==80);
  for(float[] screen:new float[][]{{1080,2400},{2400,1080},{2208,1840},{500,700}}){
   for(int setting=80;setting<=360;setting+=10){
    float longer=PadSettings.longSidePixels(setting,2.75f,screen[0],screen[1]);
    float[] size=Mapping.padSize(screen[0],screen[1],longer);
    near(size[0]/size[1],screen[0]/screen[1]);check(longer<=Math.max(screen[0],screen[1])*.72f);
    near(Mapping.axis(0,screen[0]-1,20,size[0],20+size[0]),screen[0]-1);
    near(Mapping.axis(0,screen[1]-1,20,size[1],20+size[1]),screen[1]-1);
   }
  }
  float small=PadSettings.longSidePixels(100,3,1080,2400),large=PadSettings.longSidePixels(200,3,1080,2400);
  near(large,small*2);check(PadSettings.DEFAULT_DP<280);
  boolean rejected=false;try{PadSettings.longSidePixels(180,Float.NaN,1080,2400);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  System.out.println(count+" pad-size assertions passed");
 }
}
