import io.github.kacperla.touchzoom.Mapping;
public class MappingTest {
 static int assertions;
 static void eq(float actual,float expected){assertions++;if(Math.abs(actual-expected)>.001)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
  eq(Mapping.axis(0,999,20,200,20),0);eq(Mapping.axis(0,999,20,200,220),999);
  eq(Mapping.axis(0,999,20,200,120),499.5f);eq(Mapping.axis(10,20,50,0,70),10);
  eq(Mapping.axis(10,20,50,100,-50),10);eq(Mapping.axis(10,20,50,100,500),20);
  eq(Mapping.zoom(2,100,100),4);eq(Mapping.zoom(2,-100,100),1);eq(Mapping.zoom(4,500,100),8);
  eq(Mapping.normalized(400,0,1000),.4f);
  float[] portrait=Mapping.padSize(1080,2400,280);eq(portrait[0],126);eq(portrait[1],280);
  float[] folded=Mapping.padSize(2208,1840,280);eq(folded[0],280);eq(folded[1],233.33333f);
  float anchor=600-Mapping.normalized(749.25f,0,999)*200;eq(Mapping.axis(0,999,anchor,200,600),749.25f);
  boolean threw=false;try{Mapping.axis(0,1,0,1,Float.NaN);}catch(IllegalArgumentException e){threw=true;}
  if(!threw)throw new AssertionError("NaN not rejected"); assertions++;
  java.util.Random random=new java.util.Random(20261030);
  for(int i=0;i<10000;i++){
   float span=100+random.nextFloat()*3000, len=60+random.nextFloat()*600;
   float normal=random.nextFloat(), finger=random.nextFloat()*2000;
   float left=finger-normal*len;
   float mapped=Mapping.axis(0,span,left,len,finger);
   if(Math.abs(mapped-normal*span)>.02f)throw new AssertionError("anchor round-trip"); assertions++;
   float next=Mapping.axis(0,span,left,len,finger+1);
   if(next<mapped)throw new AssertionError("nonmonotonic");assertions++;
  }
  // Even sub-slop motion must yield proportional pan, rather than wait for a dead zone.
  float start=Mapping.axis(0,2399,50,180,140);
  float tiny=Mapping.axis(0,2399,50,180,140.05f);
  if(tiny<=start)throw new AssertionError("tiny movement suppressed");assertions++;
  eq(tiny-start,2399*.05f/180);
  System.out.println(assertions+" geometry assertions passed");
 }
}
