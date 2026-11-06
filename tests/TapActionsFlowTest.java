import io.github.kacperla.touchzoom.*;

/** Fake-clock integration of shared arbitration/scale helpers, not an Android service test. */
public final class TapActionsFlowTest {
    static int assertions;
    static void check(boolean condition,String reason){assertions++;if(!condition)throw new AssertionError(reason);}
    static final class Flow {
        final TapSequence taps=new TapSequence();
        final DoubleTapScale scale=new DoubleTapScale();
        final int[] actions;
        final boolean adjust;
        boolean zoomed,scaleCandidate,sliding;
        float actual,remembered=4;
        int menus,toggles,launches,scaleWrites,dispatches;
        long token;
        Flow(int single,int twice,int triple,boolean adjust,boolean zoomed){
            actions=new int[]{single,twice,triple};this.adjust=adjust;this.zoomed=zoomed;actual=zoomed?3:1;
        }
        void down(long time){
            int displaced=taps.down(time,20,20,300,56);if(displaced!=0)dispatch(displaced);
            scaleCandidate=taps.currentTapCount()==2&&adjust;sliding=false;
            if(scaleCandidate)scale.begin(zoomed,actual); // No write merely for a candidate.
        }
        void up(long time){
            int completed=taps.up(time,20,20,300);token=taps.token();if(completed!=0)dispatch(completed);
        }
        void timeout(long now){int count=taps.take(token,now);if(count!=0)dispatch(count);}
        void dispatch(int count){
            dispatches++;int action=actions[count-1];
            if(action==ControllerSettings.MENU)menus++;
            else if(action==ControllerSettings.ZOOM){toggles++;zoomed=!zoomed;}
            else if(action==ControllerSettings.LAUNCH_APP)launches++;
        }
        void move(float selected){
            taps.cancel();
            if(scaleCandidate){sliding=true;scale.select(selected);if(scale.shouldWrite(selected)){scale.wrote();actual=selected;scaleWrites++;}}
        }
        void releaseSlide(){
            taps.cancel();
            if(sliding){zoomed=scale.shouldLatch();if(zoomed)remembered=scale.selected();actual=zoomed?remembered:1;scaleWrites++;}
        }
        void cancel(){taps.cancel();scaleCandidate=sliding=false;}
    }
    public static void main(String[] args){
        for(int single=0;single<4;single++)for(int twice=0;twice<4;twice++)for(int triple=0;triple<4;triple++)
        for(boolean adjust:new boolean[]{false,true})for(boolean initiallyZoomed:new boolean[]{false,true})
        for(int count=1;count<=3;count++){
            Flow f=new Flow(single,twice,triple,adjust,initiallyZoomed);
            for(int touch=0;touch<count;touch++){
                f.down(1000+touch*100);
                check(f.dispatches==0,"no action before highest tap UP");
                check(f.scaleWrites==0,"None/Menu candidates issue no early magnification writes");
                f.up(1040+touch*100);
                if(touch<2)check(f.dispatches==0,"lower tap waits for higher sequence");
                check(f.scaleWrites==0,"non-slide UP issues no scale write");
            }
            if(count<3){f.timeout(1040+(count-1)*100+299);check(f.dispatches==0,"timer cannot be early");}
            f.timeout(3000);
            int expected=f.actions[count-1];
            check(f.dispatches==1,"exactly one final sequence dispatch");
            check(f.menus==(expected==ControllerSettings.MENU?1:0),"only selected menu action");
            check(f.toggles==(expected==ControllerSettings.ZOOM?1:0),"only selected zoom action");
            check(f.launches==(expected==ControllerSettings.LAUNCH_APP?1:0),"only selected app launches once");
            check(f.zoomed==(expected==ControllerSettings.ZOOM?!initiallyZoomed:initiallyZoomed),"None/Menu leave zoom unchanged");
            f.timeout(10000);check(f.dispatches==1,"no later fallback or duplicate");
        }
        for(int secondAction=0;secondAction<4;secondAction++)for(boolean initiallyZoomed:new boolean[]{false,true}){
            Flow f=new Flow(ControllerSettings.MENU,secondAction,ControllerSettings.ZOOM,true,initiallyZoomed);
            f.down(0);f.up(40);f.down(100);
            check(f.scaleCandidate,"second touch can slide independent of its action");
            check(f.scale.selected()==(initiallyZoomed?3:1),"scale starts at actual zoom, not remembered4x");
            f.move(2.7f);f.releaseSlide();f.up(180);f.timeout(1000);
            check(f.dispatches==0&&f.menus==0&&f.toggles==0&&f.launches==0,"slide consumes entire tap sequence");
            check(f.zoomed&&f.remembered==2.7f&&f.actual==2.7f,"slide UP persists exact latest selection");
            f=new Flow(ControllerSettings.MENU,secondAction,ControllerSettings.ZOOM,false,initiallyZoomed);
            f.down(0);f.up(40);f.down(100);check(!f.scaleCandidate,"adjust off disables scale candidacy");
            f.move(7);f.releaseSlide();f.up(180);f.timeout(1000);
            check(f.scaleWrites==0,"adjust off never adjusts scale");
            check(f.dispatches==0,"disabled scale drag still cancels taps instead of dispatching a double");
        }
        for(int completed=1;completed<=2;completed++){
            Flow f=new Flow(1,2,2,true,false);
            f.down(0);f.up(40);if(completed==2){f.down(100);f.up(140);}
            f.cancel();f.timeout(10000);check(f.dispatches==0,"lifecycle cancellation drops completed pending taps");
        }
        System.out.println(assertions+" tap action matrix/adjust toggle/slide precedence assertions passed");
    }
}
