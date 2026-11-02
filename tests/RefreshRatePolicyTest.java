import io.github.kacperla.touchzoom.RefreshRatePolicy;
public class RefreshRatePolicyTest {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("assert "+n);}
 public static void main(String[] args){
  int[] w={1080,1080,1080,1440},h={2400,2400,2400,3200};float[] r={60,90,120,144};
  check(RefreshRatePolicy.select(1080,2400,w,h,r)==120);check(RefreshRatePolicy.select(1440,3200,w,h,r)==144);
  check(RefreshRatePolicy.select(2400,1080,w,h,r)==0);check(RefreshRatePolicy.select(0,0,w,h,r)==0);
  check(RefreshRatePolicy.select(1080,2400,null,h,r)==0);check(RefreshRatePolicy.select(1080,2400,w,null,r)==0);check(RefreshRatePolicy.select(1080,2400,w,h,null)==0);
  check(RefreshRatePolicy.select(1080,2400,new int[0],new int[0],new float[0])==0);
  check(RefreshRatePolicy.select(1080,2400,w,new int[1],r)==0);
  for(float hz:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-60,0,1001,Float.MAX_VALUE})check(RefreshRatePolicy.select(1,1,new int[]{1},new int[]{1},new float[]{hz})==0);
  for(int hz=1;hz<=1000;hz++)check(RefreshRatePolicy.select(1,1,new int[]{1,1},new int[]{1,1},new float[]{hz,1})==hz);
  check(RefreshRatePolicy.select(1,1,new int[]{1},new int[]{1},new float[]{119.88f})==119.88f);
  System.out.println(n+" refresh preference assertions passed");
 }
}
