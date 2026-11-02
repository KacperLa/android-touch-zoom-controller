import io.github.kacperla.touchzoom.LatestRenderQueue;
import java.util.concurrent.*;
public class LatestRenderQueueTest{
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("assert "+n);}
 public static void main(String[] a)throws Exception{
  LatestRenderQueue<Integer> q=new LatestRenderQueue<>();check(q.offer(1));
  CountDownLatch drawing=new CountDownLatch(1),release=new CountDownLatch(1);
  Thread worker=new Thread(()->{if(q.take()!=1)throw new AssertionError();drawing.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}});worker.start();drawing.await();
  for(int i=0;i<10000;i++)check(!q.offer(i)); // worker blocked drawing: input still never waits on it
  release.countDown();worker.join();check(q.complete());check(q.take()==9999);check(!q.complete());
  check(q.offer(5));q.stop();check(q.take()==null);check(!q.complete());check(!q.offer(6));
  System.out.println(n+" blocked-render/latest-only/shutdown assertions passed");
 }
}
