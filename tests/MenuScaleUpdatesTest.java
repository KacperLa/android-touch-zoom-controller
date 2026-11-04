import io.github.kacperla.touchzoom.MenuScaleUpdates;

public final class MenuScaleUpdatesTest {
    private static int assertions;
    private static void check(boolean value){assertions++;if(!value)throw new AssertionError("assert "+assertions);}
    public static void main(String[] args){
        MenuScaleUpdates q=new MenuScaleUpdates();
        check(!q.active()&&!q.hasWork());
        q.offer(1);check(Float.isNaN(q.take(1)));check(!q.waiting()&&!q.hasWork()); // 1x no-ack
        q.offer(2);check(q.take(1)==2);check(q.waiting());
        for(int i=0;i<10000;i++){q.offer(2+i/2000f);check(Float.isNaN(q.take(1)));}
        check(!q.observe(1));check(!q.observe(Float.NaN));check(q.observe(2));
        float latest=q.selected();check(q.take(2)==latest);check(!q.observe(2));check(q.waiting());
        q.offer(3);q.offer(4);check(q.observe(latest));check(q.take(latest)==4);check(q.observe(4));
        check(!q.hasWork());
        q.offer(4);check(Float.isNaN(q.take(4)));check(!q.hasWork()); // duplicate release is idempotent
        q.offer(5);check(q.take(4)==5);q.offer(6);q.cancel();
        check(!q.active()&&!q.hasWork());check(!q.observe(5));check(Float.isNaN(q.take(5))); // late ack cannot replay
        q.offer(3);check(q.take(5)==3);q.offer(3);check(q.observe(3));check(!q.hasWork());
        q.offer(7);q.cancel();check(Float.isNaN(q.take(3))); // cancellation before frame
        q.offer(8);check(q.take(3)==8);q.cancel();check(!q.waiting()); // watchdog cancels instead of retrying
        q.offer(2);check(q.take(8)==2);check(!q.observe(8));check(q.observe(2));
        System.out.println(assertions+" live menu latest-only/ack/no-op/cancel assertions passed");
    }
}
