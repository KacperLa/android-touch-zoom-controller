import io.github.kacperla.touchzoom.OpacitySettings;
public class OpacitySettingsTest {
 private static int assertions;
 private static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("assertion "+assertions);}
 public static void main(String[] args){
  int[][] cases={{Integer.MIN_VALUE,10},{0,10},{10,10},{12,10},{13,15},{39,40},{42,40},{43,45},{98,100},{100,100},{101,100},{Integer.MAX_VALUE,100}};
  for(int[] pair:cases)check(OpacitySettings.normalize(pair[0])==pair[1]);
  check(OpacitySettings.DEFAULT_PERCENT==75);check(OpacitySettings.KEY.equals("idle_opacity_percent"));
  check(Math.abs(OpacitySettings.alpha(40)-0.4f)<0.00001f);
  java.util.Map<String,Integer> stored=new java.util.HashMap<>();
  check(stored.getOrDefault(OpacitySettings.KEY,OpacitySettings.DEFAULT_PERCENT)==75);
  for(int old=10;old<=100;old+=5){stored.put(OpacitySettings.KEY,old);check(stored.getOrDefault(OpacitySettings.KEY,OpacitySettings.DEFAULT_PERCENT)==old);}
  check(Math.abs(OpacitySettings.alpha(OpacitySettings.DEFAULT_PERCENT)-.75f)<.00001f);
  int previous=10;
  for(int input=-100;input<=200;input++){
   int value=OpacitySettings.normalize(input);
   check(value>=10&&value<=100);check(value%5==0);check(value>=previous);
   check(OpacitySettings.normalize(value)==value);
   check(Math.abs(OpacitySettings.alpha(input)-value/100f)<0.00001f);previous=value;
  }
  System.out.println(assertions+" opacity assertions passed");
 }
}
