import io.github.kacperla.touchzoom.ActivationSettings;
public class ActivationSettingsTest {
 private static int assertions;
 private static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("assertion "+assertions);}
 public static void main(String[] args){
  int[][] cases={{-50,0},{0,0},{1,0},{24,0},{25,50},{49,50},{50,50},{74,50},{75,100},{299,300},{300,300},{324,300},{325,350},{999,1000},{1025,1050},{1999,2000},{2001,2000},{Integer.MAX_VALUE,2000},{Integer.MIN_VALUE,0}};
  for(int[] pair:cases)check(ActivationSettings.normalize(pair[0])==pair[1]);
  check(ActivationSettings.DEFAULT_MS==900);check(ActivationSettings.BASE_MS==100);
  check(ActivationSettings.KEY.equals("hold_extra_delay_ms"));
  check(ActivationSettings.LEGACY_KEY.equals("hold_to_zoom_ms"));
  check(ActivationSettings.totalMs(0)==100);check(ActivationSettings.totalMs(900)==1000);
  int[][] migrations={{Integer.MIN_VALUE,0},{0,0},{50,0},{100,0},{125,50},{300,200},{1000,900},{2000,1900},{Integer.MAX_VALUE,2000}};
  for(int[] pair:migrations)check(ActivationSettings.migratedExtra(pair[0])==pair[1]);
  int previous=0;
  for(int input=-1000;input<=3000;input++){
   int value=ActivationSettings.normalize(input);
   check(value>=0&&value<=2000);check(value%50==0);
   check(value>=previous);check(ActivationSettings.normalize(value)==value);previous=value;
   check(ActivationSettings.totalMs(input)==100+value);
  }
  System.out.println(assertions+" extra hold delay assertions passed");
 }
}
