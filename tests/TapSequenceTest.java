import io.github.kacperla.touchzoom.TapSequence;

/** Fake event time and callback tokens; no Android touch delivery is claimed here. */
public final class TapSequenceTest {
    private static int assertions;
    private static void check(boolean condition,String reason){
        assertions++;if(!condition)throw new AssertionError(reason);
    }
    private static void down(TapSequence taps,long now){check(taps.down(now,20,20,300,56)==0,"related DOWN never dispatches early");}
    private static void up(TapSequence taps,long now){check(taps.up(now,20,20,300)==0,"single/double UP waits");}
    public static void main(String[] args){
        TapSequence taps=new TapSequence();
        down(taps,0);check(taps.currentTapCount()==1,"first DOWN");up(taps,40);
        long one=taps.token();check(taps.deadline()==340,"deadline uses event time");
        check(taps.take(one,339)==0,"single cannot fire early");
        check(taps.take(one,340)==1,"single resolves at deadline");
        check(taps.take(one,1000)==0,"single fires once");

        down(taps,1000);up(taps,1040);one=taps.token();
        down(taps,1200);check(taps.currentTapCount()==2,"second DOWN");
        check(taps.take(one,2000)==0,"second DOWN invalidates first timeout");
        up(taps,1240);long two=taps.token();
        check(taps.take(two,1539)==0,"double waits for possible triple even if triple is None");
        down(taps,1500);check(taps.currentTapCount()==3,"third DOWN");
        check(taps.take(two,1540)==0,"third DOWN cancels double before third UP");
        check(taps.take(one,2000)==0,"very stale single cannot act during third touch");
        check(taps.up(1570,20,20,300)==3,"triple resolves only on its own UP");
        check(!taps.hasPendingTap(),"triple leaves no lower fallback");
        check(taps.take(two,2000)==0,"double cannot fire after triple");
        down(taps,1580);check(taps.currentTapCount()==1,"fourth tap begins a new sequence");taps.cancel();

        down(taps,2000);up(taps,2040);down(taps,2200);up(taps,2240);two=taps.token();
        check(taps.take(two,2540)==2,"double fires exactly once after triple interval");
        check(taps.take(two,2541)==0,"duplicate double callback ignored");

        for(int count=1;count<=3;count++)for(int reason=0;reason<8;reason++){
            long time=3000+count*1000+reason*100;
            taps.cancel();down(taps,time);up(taps,time+10);long stale=taps.token();
            if(count>=2){down(taps,time+20);if(count==3){up(taps,time+30);stale=taps.token();down(taps,time+40);}}
            taps.cancel(); // drag/hold/cancel/multitouch/relocation/rotation/settings/destroy
            check(taps.currentTapCount()==0 && !taps.hasPendingTap(),"cancellation clears both states");
            check(taps.take(stale,time+5000)==0,"cancellation invalidates queued action");
            check(taps.up(time+60,20,20,300)==0,"late UP after cancel cannot resurrect taps");
        }

        taps.cancel();down(taps,10000);up(taps,10040);one=taps.token();
        check(taps.down(10341,20,20,300,56)==1,"expired single is resolved before a new sequence");
        check(taps.currentTapCount()==1,"expired tap does not become double");
        check(taps.take(one,20000)==0,"displaced single timeout invalidated");taps.cancel();
        down(taps,20000);up(taps,20040);
        check(taps.down(20100,100,100,300,56)==1,"distant touch starts independent sequence");
        check(taps.currentTapCount()==1,"distant touch is first tap");taps.cancel();
        down(taps,30000);up(taps,30040);down(taps,30100);up(taps,30140);
        check(taps.down(30441,20,20,300,56)==2,"expired double resolves before new sequence");taps.cancel();
        down(taps,40000);up(taps,40040);
        check(taps.down(40000,20,20,300,56)==1,"out-of-order event cannot continue sequence");taps.cancel();
        down(taps,50000);up(taps,50040);down(taps,50340);
        check(taps.currentTapCount()==2,"DOWN at exact timeout boundary continues if handled first");
        System.out.println(assertions+" tap sequence/deadline/triple suppression/cancellation assertions passed");
    }
}
