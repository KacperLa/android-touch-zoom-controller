import io.github.kacperla.touchzoom.InsetsEventQueue;

/** Queue policy only: actual Android insets delivery is covered by ServiceRuntimeProbe. */
public final class InsetsEventQueueTest {
    private static int assertions;
    private static void check(boolean condition,String reason) {
        assertions++;
        if(!condition)throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        InsetsEventQueue queue=new InsetsEventQueue();
        check(!queue.request(0),"inactive queue never schedules");
        check(!queue.take(0),"inactive queue has no work");
        queue.cancel();queue.stop();
        check(!queue.request(0),"cancel/stop do not activate queue");

        queue.start(7);
        check(!queue.take(7),"start alone does not create periodic work");
        check(!queue.request(6),"old observer cannot schedule");
        check(!queue.request(8),"future observer cannot schedule");
        check(queue.request(7),"first actual event schedules one callback");
        for(int i=0;i<10000;i++)check(!queue.request(7),"burst is coalesced");
        check(!queue.take(6),"stale callback cannot consume current work");
        check(queue.take(7),"current callback consumes burst");
        check(!queue.take(7),"drained queue does not reschedule itself");
        check(queue.request(7),"new event after drain schedules again");
        queue.cancel();
        check(!queue.take(7),"screen-off cancellation drops queued work");
        check(queue.request(7),"screen-on event can schedule in same generation");

        queue.start(9);
        check(!queue.take(7),"startup invalidates previous queued generation");
        check(!queue.take(9),"restart does not inherit queued work");
        check(!queue.request(7),"removed observer cannot schedule after retry");
        check(queue.request(9),"replacement observer schedules");
        check(!queue.take(7),"late old callback cannot drain new generation");
        check(!queue.request(7),"late old event cannot replace pending work");
        check(!queue.request(9),"new generation remains queued after stale activity");
        check(queue.take(9),"replacement work survives stale activity");

        check(queue.request(9),"work pending before destruction");
        queue.stop();queue.stop();queue.cancel();
        check(!queue.take(9),"destruction discards pending work");
        check(!queue.request(9),"destroyed queue ignores late events");
        queue.start(10);
        check(queue.request(10),"queue can be explicitly restarted");
        queue.start(10);
        check(!queue.take(10),"explicit start resets pending state even with same value");

        for(int generation=11;generation<1011;generation++) {
            queue.start(generation);
            check(queue.request(generation),"new lifecycle accepts first event");
            check(!queue.request(generation),"duplicate event is coalesced");
            check(!queue.take(generation-1),"previous lifecycle cannot consume");
            check(queue.take(generation),"one current check drains queue");
            check(!queue.take(generation),"no timer-like extra check appears");
            check(queue.request(generation),"second independent event accepted");
            queue.stop();
            check(!queue.take(generation),"stop cancels second event");
        }
        System.out.println(assertions+" insets event lifecycle/generation/coalescing assertions passed");
    }
}
