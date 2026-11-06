package io.github.kacperla.touchzoom;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.MagnificationConfig;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** No JUnit/AndroidX dependencies. Emulator-only UI/runtime probe, not a performance benchmark. */
public final class ServiceRuntimeProbe extends Instrumentation {
    private Bundle args; private UiAutomation ui; private final StringBuilder report=new StringBuilder();
    private static final String TARGET="io.github.kacperla.touchzoom/.ZoomService";
    private static final String HELPER="io.github.kacperla.touchzoom.test/io.github.kacperla.touchzoom.ExternalMagnifier";
    private long down; private float lastX,lastY; private boolean fingerDown;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments==null?new Bundle():arguments;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int resultCode=android.app.Activity.RESULT_OK;
        try{
            ui=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            String services=TARGET+":"+HELPER;
            shell("settings put secure enabled_accessibility_services "+services);
            shell("settings put secure accessibility_enabled 1");
            waitService(30000);
            SystemClock.sleep(1000);
            log("STARTUP service connected; "+describe(state()));
            Rect b=bubbleBounds();require(b.width()>0&&b.height()>0,"bubble has usable dimensions");
            log("BUBBLE "+b);
            String scenario=args.getString("scenario","hold");
            if("startup".equals(scenario)){}
            else if("external".equals(scenario)||"window".equals(scenario))external(b,"window".equals(scenario));
            else if("hold".equals(scenario))hold(b);
            else if("focus".equals(scenario)||"focus-tap".equals(scenario))focusTrial("tap");
            else if("focus-hold".equals(scenario))focusTrial("hold");
            else if("focus-relocate".equals(scenario))focusTrial("relocate");
            else if("focus-relayout".equals(scenario))focusTrial("relayout");
            else if("focus-rotate".equals(scenario))focusTrial("rotate");
            else if("focus-all".equals(scenario)){
                for(String trial:new String[]{"tap","hold","relocate","relayout","rotate"})focusTrial(trial);
            }
            else if("ime-events".equals(scenario))imeEventsTrial("show-hide");
            else if("ime-events-retry".equals(scenario))imeEventsTrial("retry");
            else if("ime-events-rotate".equals(scenario))imeEventsTrial("rotate");
            else if("ime-events-resize".equals(scenario))imeEventsTrial("resize");
            else if("retry".equals(scenario))retry();
            else if("takeover".equals(scenario))takeover();
            else throw new IllegalArgumentException("Unknown scenario "+scenario);
            result.putString("stream","\nPASS\n"+report);
        }catch(Throwable failure){
            log("FAIL "+failure.getClass().getSimpleName()+": "+failure.getMessage());
            result.putString("stream","\nFAIL\n"+report);resultCode=android.app.Activity.RESULT_CANCELED;
        }finally{
            if(fingerDown)try{event(MotionEvent.ACTION_CANCEL,lastX,lastY);}catch(Throwable ignored){}
            // Keep target service enabled for visual/manual follow-up, remove our test-only helper.
            try{shell("settings put secure enabled_accessibility_services "+TARGET);}catch(Throwable ignored){}
        }
        finish(resultCode,result);
    }
    private void hold(Rect b)throws Exception{
        onMain(()->{ZoomService.current.turnOff();return null;});SystemClock.sleep(300);
        long hold=onMain(()->(long)ActivationSettings.totalMs(ZoomService.current.getSharedPreferences("controller",0)
            .getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS)));
        float x=b.exactCenterX(),y=b.exactCenterY();event(MotionEvent.ACTION_DOWN,x,y);
        SystemClock.sleep(hold+500);MagnificationConfig engaged=state();
        require(engaged!=null&&engaged.isActivated()&&engaged.getScale()>1.01f,"hold enables actual magnification");
        log("HOLD "+describe(engaged));
        for(int i=1;i<=12;i++){event(MotionEvent.ACTION_MOVE,x-i*3,y-i*2);SystemClock.sleep(20);}
        SystemClock.sleep(250);MagnificationConfig panned=state();log("PAN "+describe(panned));
        require(Math.abs(panned.getCenterX()-engaged.getCenterX())>1||Math.abs(panned.getCenterY()-engaged.getCenterY())>1,"viewport center changes during held pan");
        event(MotionEvent.ACTION_UP,lastX,lastY);SystemClock.sleep(500);MagnificationConfig ended=state();log("RELEASE "+describe(ended));
        require(ended!=null&&(!ended.isActivated()||ended.getScale()<=1.01f),"momentary release unzooms");
    }
    private void external(Rect b,boolean window)throws Exception{
        onMain(()->{ZoomService.current.turnOff();return null;});SystemClock.sleep(300);
        int mode=window?MagnificationConfig.MAGNIFICATION_MODE_WINDOW:MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
        log(shell("am broadcast --receiver-foreground -a "+ExternalMagnifier.ACTION+" -p io.github.kacperla.touchzoom.test --ei mode "+mode+" --ef scale 2.5 --ef cx 360 --ef cy 640"));
        SystemClock.sleep(1500);MagnificationConfig before=state();log("EXTERNAL BEFORE "+describe(before));
        require(before!=null&&before.isActivated()&&before.getMode()==mode,"independent helper enabled requested mode");
        float x=b.exactCenterX(),y=b.exactCenterY();event(MotionEvent.ACTION_DOWN,x,y);
        for(int i=1;i<=10;i++){event(MotionEvent.ACTION_MOVE,x-i*3,y-i*2);SystemClock.sleep(20);}
        event(MotionEvent.ACTION_UP,lastX,lastY);SystemClock.sleep(500);MagnificationConfig after=state();log("EXTERNAL RELEASE "+describe(after));
        require(after!=null&&after.isActivated()&&after.getMode()==mode,"release preserves external activation/mode");
        require(Math.abs(after.getScale()-before.getScale())<.02,"release preserves external scale");
        if(window)require(Math.abs(after.getCenterX()-before.getCenterX())<2&&Math.abs(after.getCenterY()-before.getCenterY())<2,"window center remains unchanged");
        b=bubbleBounds();event(MotionEvent.ACTION_DOWN,b.exactCenterX(),b.exactCenterY());
        event(MotionEvent.ACTION_MOVE,b.exactCenterX()-20,b.exactCenterY()-10);event(MotionEvent.ACTION_CANCEL,lastX,lastY);
        SystemClock.sleep(400);after=state();log("EXTERNAL CANCEL "+describe(after));
        require(after!=null&&after.isActivated()&&after.getMode()==mode,"cancel preserves external activation/mode");
        require(Math.abs(after.getScale()-before.getScale())<.02,"cancel preserves external scale");
        if(window)require(Math.abs(after.getCenterX()-before.getCenterX())<2&&Math.abs(after.getCenterY()-before.getCenterY())<2,"cancel preserves window center");
        log(shell("am broadcast --receiver-foreground -a "+ExternalMagnifier.ACTION+" -p io.github.kacperla.touchzoom.test --ez reset true"));
    }
    private void retry()throws Exception{
        onMain(()->{ZoomService.current.turnOff();return null;});
        for(int cycle=1;cycle<=2;cycle++){
            Object oldPad=onMain(()->privateField(ZoomService.current,"pad"));
            Thread oldThread=(Thread)privateField(oldPad,"renderThread");
            onMain(()->{
                java.lang.reflect.Method fail=ZoomService.class.getDeclaredMethod("failOverlayStartup",RuntimeException.class);
                fail.setAccessible(true);fail.invoke(ZoomService.current,new RuntimeException("Synthetic instrumentation startup failure"));
                require(!(Boolean)privateField(ZoomService.current,"overlayReady"),"failure clears readiness");
                require(ZoomService.current.controllerStatus().contains("could not start"),"failure exposes actionable status");
                ZoomService.current.retryStartup();return null;
            });
            waitService(30000);SystemClock.sleep(1000);Rect b=bubbleBounds();
            require(onMain(()->privateField(ZoomService.current,"pad")!=oldPad),"retry creates a distinct render owner");
            long deadline=SystemClock.uptimeMillis()+10000;
            while(oldThread.isAlive()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50);
            require(!oldThread.isAlive(),"old renderer thread terminates after retry");
            require(onMain(()->{android.view.Surface surface=(android.view.Surface)privateField(ZoomService.current,"padSurface");return surface!=null&&surface.isValid();}),"old cleanup did not release new renderer surface");
            log("RETRY cycle="+cycle+" new bubble="+b+" old renderer stopped; new surface valid");
        }
        hold(bubbleBounds());
    }
    private void takeover()throws Exception{
        // Public commands from the separate test service must supersede our held preview.
        for(boolean externalOff:new boolean[]{false,true}){
            onMain(()->{ZoomService.current.turnOff();return null;});SystemClock.sleep(300);
            Rect b=bubbleBounds();long hold=onMain(()->(long)ActivationSettings.totalMs(ZoomService.current.getSharedPreferences("controller",0)
                .getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS)));
            event(MotionEvent.ACTION_DOWN,b.exactCenterX(),b.exactCenterY());SystemClock.sleep(hold+500);
            require(state().isActivated()&&state().getScale()>1.01f,"held preview active before takeover");
            String command="am broadcast --receiver-foreground -a "+ExternalMagnifier.ACTION+" -p io.github.kacperla.touchzoom.test ";
            log(shell(command+(externalOff?"--ez reset true":"--ei mode 1 --ef scale 3.5 --ef cx 300 --ef cy 500")));
            SystemClock.sleep(800);MagnificationConfig changed=state();log("TAKEOVER state "+describe(changed));
            require(externalOff?(!changed.isActivated()||changed.getScale()<=1.01f):changed.isActivated()&&Math.abs(changed.getScale()-3.5f)<.02f,"external command applied");
            require(onMain(()->!(Boolean)privateField(ZoomService.current,"active")),"external takeover cancels controller gesture");
            require(onMain(()->!((ZoomLatch)privateField(ZoomService.current,"latch")).isLatched()),"external takeover clears controller latch");
            event(MotionEvent.ACTION_MOVE,lastX-30,lastY-20);event(MotionEvent.ACTION_UP,lastX,lastY);
            SystemClock.sleep(1200);MagnificationConfig after=state();log("TAKEOVER late input/release "+describe(after));
            require(externalOff?(!after.isActivated()||after.getScale()<=1.01f):after.isActivated()&&Math.abs(after.getScale()-3.5f)<.02f,"late input/release cannot restore or reset newer external state");
            if(!externalOff)require(Math.abs(after.getCenterX()-changed.getCenterX())<2&&Math.abs(after.getCenterY()-changed.getCenterY())<2,"late input preserves external center");
        }
    }
    private static Object privateField(Object target,String name)throws Exception{
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private void focusTrial(String trial)throws Exception{
        onMain(()->{ZoomService.current.turnOff();return null;});
        android.accessibilityservice.AccessibilityServiceInfo info=ui.getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        ui.setServiceInfo(info);
        // A fresh editor is allowed to open its IME BEFORE a trial, never after controller interaction.
        shell("am start -f 0x10008000 -n io.github.kacperla.touchzoom.test/io.github.kacperla.touchzoom.ProbeEditorActivity");
        waitVisibleEditor("before "+trial,30000);
        assertHostNonFocusable("initial "+trial);
        boolean rotated=false;
        try{
            if("relayout".equals(trial)){
                onMain(()->{
                    // Invalidate only our application's cached rotation to exercise its real relayout path.
                    Field rotation=ZoomService.class.getDeclaredField("rotation");rotation.setAccessible(true);
                    rotation.setInt(ZoomService.current,-1);
                    java.lang.reflect.Method changed=ZoomService.class.getDeclaredMethod("geometryChanged");changed.setAccessible(true);
                    changed.invoke(ZoomService.current);return null;
                });
                waitVisibleEditor("after forced service relayout",10000);
                assertHostNonFocusable("after relayout");
            }else if("rotate".equals(trial)){
                require(ui.setRotation(UiAutomation.ROTATION_FREEZE_90),"freeze emulator rotation to landscape");rotated=true;
                // Activity recreation may normally show the keyboard; it must be visibly open before touch.
                waitVisibleEditor("after rotation",30000);
                assertHostNonFocusable("after rotation");
            }
            // Keyboard avoidance may move the bubble; sample its position only after the IME settles.
            SystemClock.sleep(350);assertVisibleEditor("immediately before "+trial);
            Rect bubble=bubbleBounds();float x=bubble.exactCenterX(),y=bubble.exactCenterY();
            log("VISIBLE-IME TRIAL "+trial+" bubble="+bubble+" BEFORE focus=true ime=true");
            captureEditorPrecondition(trial);assertVisibleEditor("after precondition capture "+trial);
            event(MotionEvent.ACTION_DOWN,x,y,!"relocate".equals(trial));
            if("hold".equals(trial)){
                long hold=onMain(()->(long)ActivationSettings.totalMs(ZoomService.current.getSharedPreferences("controller",0)
                    .getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS)));
                monitorVisibleEditor("held controller",hold+300);
                MagnificationConfig engaged=state();require(engaged!=null&&engaged.isActivated()&&engaged.getScale()>1.01f,"hold trial actually zooms");
                for(int i=1;i<=8;i++){event(MotionEvent.ACTION_MOVE,x-i*3,y-i*2);assertVisibleEditor("held pan "+i);SystemClock.sleep(20);}
                log("VISIBLE-IME HOLD "+describe(state()));
            }else if("relocate".equals(trial)){
                // A single early deliberate MOVE chooses relocation before the hold timer.
                event(MotionEvent.ACTION_MOVE,x-24,y-12);
                require(onMain(()->(Boolean)privateField(ZoomService.current,"relocating")),"trial actually enters relocation");
                assertVisibleEditor("relocation begins");
                for(int i=2;i<=6;i++){event(MotionEvent.ACTION_MOVE,x-i*12,y-i*4);assertVisibleEditor("relocation "+i);SystemClock.sleep(20);}
            }else{
                require(onMain(()->(Boolean)privateField(ZoomService.current,"active")),"controller actually received DOWN for "+trial);
                log("CONTROLLER DOWN received "+trial+" elapsed="+(SystemClock.uptimeMillis()-down)+"ms");
                SystemClock.sleep(25);
            }
            event(MotionEvent.ACTION_UP,lastX,lastY);
            // Do not call showSoftInput/relaunch/requestFocus here: dismissal must remain a failure.
            monitorVisibleEditor("after "+trial,1200);
            assertHostNonFocusable("after "+trial+" interaction");
            typeAndVerifyEditor();assertVisibleEditor("after typing "+trial);
            log("VISIBLE-IME TRIAL "+trial+" AFTER focus=true ime=true typed=a");
        }finally{
            if(fingerDown){event(MotionEvent.ACTION_CANCEL,lastX,lastY);}
            if(rotated)ui.setRotation(UiAutomation.ROTATION_UNFREEZE);
        }
    }
    /** Real external IME transitions. Never inject insets or invoke the keyboard-check runnable. */
    private void imeEventsTrial(String trial)throws Exception{
        onMain(()->{ZoomService.current.turnOff();return null;});
        android.accessibilityservice.AccessibilityServiceInfo info=ui.getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        ui.setServiceInfo(info);
        shell("input keyevent KEYCODE_HOME");
        waitActualIme(false,"initial home",15000,false);
        SavedPosition saved=onMain(()->new SavedPosition(ZoomService.current));
        boolean rotated=false;
        try{
            // Fixture setup only, before the observed transition. A low saved position must be covered.
            onMain(()->{
                ZoomService service=ZoomService.current;
                service.getSharedPreferences("controller",0).edit().putFloat("x",.82f).putFloat("y",.95f).apply();
                setPrivateFloat(service,"normalizedX",.82f);setPrivateFloat(service,"normalizedY",.95f);
                invokePrivate(service,"updateGeometry");return null;
            });
            assertInsetsObserver("before external editor");
            assertKeyboardIdle("hidden baseline");
            Rect preferred=bubbleBounds();ImeSnapshot before=imeSnapshot();
            log("IME EVENTS BASELINE "+trial+" preferred="+preferred+" "+before);
            shell("am start -f 0x10008000 -n io.github.kacperla.touchzoom.test/io.github.kacperla.touchzoom.ProbeEditorActivity");
            waitVisibleEditor("initial event-driven show",30000);
            waitImeDelivery(true,before,"initial show");
            assertLiftedAboveActualIme(preferred,"initial show");
            assertSavedPosition();assertInsetsObserver("visible external editor");
            captureEditorPrecondition("ime-events-"+trial);
            assertKeyboardIdle("visible baseline");
            touchAndTypeWithIme("initial show");

            if("retry".equals(trial)){
                ImeSnapshot prior=imeSnapshot();
                Object oldObserver=onMain(()->privateField(ZoomService.current,"tokenHost"));
                onMain(()->{
                    java.lang.reflect.Method fail=ZoomService.class.getDeclaredMethod("failOverlayStartup",RuntimeException.class);
                    fail.setAccessible(true);fail.invoke(ZoomService.current,new RuntimeException("Synthetic IME observer retry"));
                    ZoomService.current.retryStartup();return null;
                });
                waitService(30000);
                // Do not reopen the IME or request editor focus after recreating the observer.
                assertVisibleEditor("retry with already-visible IME");
                waitImeDelivery(true,prior,"retry with already-visible IME");
                require(onMain(()->privateField(ZoomService.current,"tokenHost")!=oldObserver),"retry replaces the observer");
                require(onMain(()->!((View)oldObserver).isAttachedToWindow()),"retry detaches old observer");
                require(imeSnapshot().generation>prior.generation,"retry advances lifecycle generation");
                assertInsetsObserver("after retry");assertLiftedAboveActualIme(preferred,"after retry");
                monitorVisibleEditor("after retry",1200);typeAndVerifyEditor();
                assertKeyboardIdle("after retry");
            }else if("rotate".equals(trial)){
                ImeSnapshot prior=imeSnapshot();
                int oldRotation=onMain(()->(Integer)privateField(ZoomService.current,"rotation"));
                int next=oldRotation==android.view.Surface.ROTATION_90?UiAutomation.ROTATION_FREEZE_0:UiAutomation.ROTATION_FREEZE_90;
                require(ui.setRotation(next),"freeze emulator to different rotation");rotated=true;
                long until=SystemClock.uptimeMillis()+15000;
                while(onMain(()->(Integer)privateField(ZoomService.current,"rotation")==oldRotation)
                    &&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
                waitVisibleEditor("rotated external editor",30000);
                waitImeDelivery(true,prior,"rotation with visible IME");
                require(onMain(()->(Integer)privateField(ZoomService.current,"rotation")!=oldRotation),"service geometry actually rotated");
                assertInsetsObserver("after rotation");assertLiftedAboveActualIme(expectedUnobscuredBubble(),"after rotation");
                assertSavedPosition();typeAndVerifyEditor();assertKeyboardIdle("after rotation");
            }else if("resize".equals(trial)){
                // IMEs need not use different numeric/text heights. This dedicated scenario fails
                // its precondition if they do not; it never labels an unchanged keyboard a resize.
                Rect oldIme=actualImeBounds();Rect oldBubble=bubbleBounds();ImeSnapshot prior=imeSnapshot();
                imeCommand("numeric");
                long until=SystemClock.uptimeMillis()+15000;Rect resized=null;
                do{
                    assertVisibleEditor("numeric IME resize");resized=actualImeBounds();
                    if(resized!=null&&Math.abs(resized.top-oldIme.top)>2)break;
                    SystemClock.sleep(100);
                }while(SystemClock.uptimeMillis()<until);
                require(resized!=null&&Math.abs(resized.top-oldIme.top)>2,
                    "resize precondition: installed IME must change visible height between text and numeric layouts");
                waitImeDelivery(true,prior,"visible IME height change");
                until=SystemClock.uptimeMillis()+5000;
                while((imeSnapshot().bottom==prior.bottom||Math.abs(bubbleBounds().top-oldBubble.top)<=2)
                    &&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
                require(imeSnapshot().bottom!=prior.bottom,"service observes changed bottom inset while IME stays visible");
                assertLiftedAboveActualIme(expectedUnobscuredBubble(),"visible IME height change");
                require(Math.abs(bubbleBounds().top-oldBubble.top)>2,"bubble follows changed IME height while still visible");
                log("IME RESIZE independent bounds "+oldIme+" -> "+resized+" bubble="+oldBubble+" -> "+bubbleBounds());
                assertSavedPosition();assertKeyboardIdle("resized visible IME");
                imeCommand("text");waitVisibleEditor("restored text IME",15000);
                typeAndVerifyEditor();
            }

            // Repeat ordinary show/hide after initial startup, and after retry/rotation when requested.
            for(int cycle=1;cycle<=2;cycle++){
                ImeSnapshot shown=imeSnapshot();
                imeCommand("hide");waitActualIme(false,"hide cycle "+cycle,15000,true);
                waitImeDelivery(false,shown,"hide cycle "+cycle);
                require(bubbleBounds().equals(expectedUnobscuredBubble()),"hide restores exact preferred placement cycle "+cycle);
                assertSavedPosition();typeAndVerifyEditor();
                require(!imeVisible(),"hardware typing does not reopen hidden IME");
                assertKeyboardIdle("hidden cycle "+cycle);
                Rect hidden=bubbleBounds();ImeSnapshot hiddenState=imeSnapshot();
                imeCommand("show");waitVisibleEditor("show cycle "+cycle,15000);
                waitImeDelivery(true,hiddenState,"show cycle "+cycle);
                assertLiftedAboveActualIme(hidden,"show cycle "+cycle);
                assertSavedPosition();touchAndTypeWithIme("show cycle "+cycle);
                assertKeyboardIdle("visible cycle "+cycle);
            }
            log("IME EVENTS VERIFIED "+trial+": external show/hide, observer delivery, idle checks, placement, focus and typing; controllerTouch="
                +!Boolean.parseBoolean(args.getString("skipControllerTouch","false")));
        }finally{
            if(fingerDown)event(MotionEvent.ACTION_CANCEL,lastX,lastY);
            if(rotated)ui.setRotation(UiAutomation.ROTATION_UNFREEZE);
            onMain(()->{
                ZoomService service=ZoomService.current;
                if(service!=null){
                    service.turnOff();saved.restore(service);
                    if((Boolean)privateField(service,"overlayReady"))invokePrivate(service,"updateGeometry");
                }
                return null;
            });
        }
    }
    private void touchAndTypeWithIme(String phase)throws Exception{
        assertVisibleEditor(phase+" before controller touch");
        if(Boolean.parseBoolean(args.getString("skipControllerTouch","false"))){
            log("CONTROLLER TOUCH explicitly omitted "+phase+"; observer-only focus/typing assertions remain required");
            typeAndVerifyEditor();assertVisibleEditor(phase+" after observer-only typing");return;
        }
        Rect bubble=bubbleBounds();event(MotionEvent.ACTION_DOWN,bubble.exactCenterX(),bubble.exactCenterY());
        require(onMain(()->(Boolean)privateField(ZoomService.current,"active")),"controller actually receives touch "+phase);
        SystemClock.sleep(25);event(MotionEvent.ACTION_UP,lastX,lastY);
        monitorVisibleEditor(phase+" after controller touch",800);
        typeAndVerifyEditor();assertVisibleEditor(phase+" after actual typing");
        assertHostNonFocusable(phase);
        onMain(()->{ZoomService.current.turnOff();return null;});
    }
    private void imeCommand(String command)throws Exception{
        log("EXTERNAL IME COMMAND "+command+" "+shell("am broadcast --receiver-foreground -a "+ProbeEditorActivity.IME_COMMAND
            +" -p io.github.kacperla.touchzoom.test --es command "+command).trim());
    }
    private void waitActualIme(boolean visible,String phase,long timeout,boolean requireFocus)throws Exception{
        long until=SystemClock.uptimeMillis()+timeout;
        do{
            if(imeVisible()==visible&&(!requireFocus||editorFocused())){
                SystemClock.sleep(150);
                if(imeVisible()==visible&&(!requireFocus||editorFocused()))return;
            }
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<until);
        throw new AssertionError(phase+": actual IME visibility must be "+visible+"; focus="+editorFocused()+" actual="+imeVisible());
    }
    private void waitImeDelivery(boolean visible,ImeSnapshot prior,String phase)throws Exception{
        long until=SystemClock.uptimeMillis()+10000;ImeSnapshot current;
        do{
            current=imeSnapshot();
            if(current.events>prior.events&&current.checks>prior.checks&&current.visible==visible
                &&(visible?current.bottom>0:current.bottom==0)){
                log("IME DELIVERY "+phase+" before={"+prior+"} after={"+current+"} actual="+actualImeBounds());return;
            }
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<until);
        throw new AssertionError(phase+": external keyboard changed but observer event/check/state did not; before={"
            +prior+"} after={"+current+"} actual="+actualImeBounds());
    }
    private void assertKeyboardIdle(String phase)throws Exception{
        long until=SystemClock.uptimeMillis()+8000,quietSince=SystemClock.uptimeMillis();
        ImeSnapshot previous=imeSnapshot();boolean settled=false;
        do{
            SystemClock.sleep(100);ImeSnapshot now=imeSnapshot();
            if(now.checks!=previous.checks){previous=now;quietSince=SystemClock.uptimeMillis();}
            if(SystemClock.uptimeMillis()-quietSince>=750){settled=true;break;}
        }while(SystemClock.uptimeMillis()<until);
        require(settled,phase+": keyboard checks reach a quiet interval");
        ImeSnapshot before=imeSnapshot();SystemClock.sleep(1800);ImeSnapshot after=imeSnapshot();
        log("IME IDLE "+phase+" duration=1800ms before={"+before+"} after={"+after+"}");
        require(before.checks==after.checks,phase+": keyboardCheckCount remains exactly stable while idle");
    }
    private void assertLiftedAboveActualIme(Rect preferred,String phase)throws Exception{
        Rect ime=actualImeBounds();require(ime!=null&&!ime.isEmpty(),phase+": independent IME window is visible");
        require(preferred.bottom>ime.top,phase+": saved low placement is genuinely covered by keyboard");
        long until=SystemClock.uptimeMillis()+5000;Rect bubble;
        do{bubble=bubbleBounds();if(bubble.top<preferred.top&&bubble.bottom<=ime.top)break;SystemClock.sleep(100);}
        while(SystemClock.uptimeMillis()<until);
        log("IME PLACEMENT "+phase+" preferred="+preferred+" bubble="+bubble+" actualIme="+ime);
        require(bubble.top<preferred.top,phase+": actual bubble lifts above its preferred position");
        require(bubble.bottom<=ime.top,phase+": actual bubble is above independently observed IME bounds");
    }
    private Rect expectedUnobscuredBubble()throws Exception{return onMain(()->{
        ZoomService service=ZoomService.current;int width=(Integer)privateField(service,"width");
        int height=(Integer)privateField(service,"height"),size=(Integer)privateField(service,"diameter");
        android.view.WindowManager wm=(android.view.WindowManager)privateField(service,"wm");
        android.graphics.Insets safe=wm.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
        int x=Math.max(safe.left,Math.min(width-safe.right-size,Math.round(.82f*width-size/2f)));
        int y=Math.max(safe.top,Math.min(height-safe.bottom-size,Math.round(.95f*height-size/2f)));
        return new Rect(x,y,x+size,y+size);
    });}
    private void assertSavedPosition()throws Exception{
        require(onMain(()->{
            ZoomService service=ZoomService.current;android.content.SharedPreferences preferences=service.getSharedPreferences("controller",0);
            return preferences.getFloat("x",-1)==.82f&&preferences.getFloat("y",-1)==.95f
                &&(Float)privateField(service,"normalizedX")==.82f&&(Float)privateField(service,"normalizedY")==.95f;
        }),"automatic IME avoidance never mutates saved or in-memory preferred position");
    }
    private void assertInsetsObserver(String phase)throws Exception{onMain(()->{
        View observer=(View)privateField(ZoomService.current,"tokenHost");
        require(observer!=null&&observer.isAttachedToWindow(),phase+": real WindowManager observer attached");
        android.view.WindowManager.LayoutParams params=(android.view.WindowManager.LayoutParams)observer.getLayoutParams();
        int required=android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            |android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        require((params.flags&required)==required,phase+": observer cannot receive touch or key focus and participates in IME insets");
        require(params.alpha==0f,phase+": observer is transparent");
        log("IME OBSERVER "+phase+" size="+params.width+"x"+params.height+" flags="+params.flags+" focus="+observer.hasWindowFocus());
        return null;
    });}
    private Rect actualImeBounds(){
        for(android.view.accessibility.AccessibilityWindowInfo window:ui.getWindows()){
            if(window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD){
                Rect bounds=new Rect();window.getBoundsInScreen(bounds);if(!bounds.isEmpty())return bounds;
            }
        }
        return null;
    }
    private ImeSnapshot imeSnapshot()throws Exception{return onMain(()->new ImeSnapshot(ZoomService.current));}
    private static final class ImeSnapshot{
        final long events,checks;final boolean visible;final int bottom,generation;
        ImeSnapshot(ZoomService service)throws Exception{
            events=(Long)privateField(service,"insetEventCount");checks=(Long)privateField(service,"keyboardCheckCount");
            visible=(Boolean)privateField(service,"imeVisible");bottom=(Integer)privateField(service,"imeBottom");
            generation=(Integer)privateField(service,"startupGeneration");
        }
        @Override public String toString(){return "events="+events+" checks="+checks+" ime="+visible+" bottom="+bottom+" generation="+generation;}
    }
    private static final class SavedPosition{
        final boolean hasX,hasY;final float savedX,savedY,memoryX,memoryY;
        SavedPosition(ZoomService service)throws Exception{
            android.content.SharedPreferences preferences=service.getSharedPreferences("controller",0);
            hasX=preferences.contains("x");hasY=preferences.contains("y");
            savedX=preferences.getFloat("x",.82f);savedY=preferences.getFloat("y",.72f);
            memoryX=(Float)privateField(service,"normalizedX");memoryY=(Float)privateField(service,"normalizedY");
        }
        void restore(ZoomService service)throws Exception{
            android.content.SharedPreferences.Editor edit=service.getSharedPreferences("controller",0).edit();
            if(hasX)edit.putFloat("x",savedX);else edit.remove("x");
            if(hasY)edit.putFloat("y",savedY);else edit.remove("y");edit.apply();
            setPrivateFloat(service,"normalizedX",memoryX);setPrivateFloat(service,"normalizedY",memoryY);
        }
    }
    private static void setPrivateFloat(Object target,String name,float value)throws Exception{
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.setFloat(target,value);
    }
    private static void invokePrivate(Object target,String name)throws Exception{
        java.lang.reflect.Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(target);
    }
    private void captureEditorPrecondition(String trial)throws Exception{
        for(android.view.accessibility.AccessibilityWindowInfo window:ui.getWindows()){
            if(window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD){
                Rect bounds=new Rect();window.getBoundsInScreen(bounds);log("IME PRECONDITION BOUNDS "+trial+" "+bounds);
            }
        }
        if(Boolean.parseBoolean(args.getString("skipScreenshot","false"))){
            log("IME PRECONDITION SCREENSHOT explicitly disabled; IME bounds/focus assertions remain required");return;
        }
        android.graphics.Bitmap screenshot=ui.takeScreenshot();
        if(screenshot==null){log("IME PRECONDITION SCREENSHOT unavailable "+trial);return;}
        java.io.File file=new java.io.File(getTargetContext().getFilesDir(),"ime-before-"+trial+".png");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){
            require(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"precondition screenshot encoded");
        }finally{screenshot.recycle();}
        log("IME PRECONDITION SCREENSHOT "+file.getAbsolutePath()+" (fixed test editor only)");
    }
    private void waitVisibleEditor(String phase,long timeout)throws Exception{
        long deadline=SystemClock.uptimeMillis()+timeout;
        while(SystemClock.uptimeMillis()<deadline){
            if(editorFocused()&&imeVisible()){
                SystemClock.sleep(150);
                if(editorFocused()&&imeVisible()){log("EDITOR READY "+phase+" focus=true ime=true");return;}
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError(phase+": visible keyboard AND focused test editor required (focus="+editorFocused()+", ime="+imeVisible()+")");
    }
    private void assertVisibleEditor(String phase){
        boolean focus=editorFocused(),ime=imeVisible();
        if(!focus||!ime)log("EDITOR FAILURE "+phase+" focus="+focus+" ime="+ime);
        require(focus,phase+": test editor retains input focus");
        require(ime,phase+": keyboard remains visibly open");
    }
    private void monitorVisibleEditor(String phase,long duration){
        long until=SystemClock.uptimeMillis()+duration;
        do{assertVisibleEditor(phase);SystemClock.sleep(50);}while(SystemClock.uptimeMillis()<until);
    }
    private void typeAndVerifyEditor(){
        android.view.accessibility.AccessibilityNodeInfo before=focusedEditor();
        String old=before==null||before.getText()==null?"":before.getText().toString();
        require(ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_A),true),"hardware a down injected");
        require(ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_A),true),"hardware a up injected");
        SystemClock.sleep(200);
        android.view.accessibility.AccessibilityNodeInfo focused=focusedEditor();
        require(focused!=null&&focused.getText()!=null&&focused.getText().length()>old.length()
            &&focused.getText().toString().endsWith("a"),"editor receives new hardware key after controller interaction");
    }
    private void assertHostNonFocusable(String phase)throws Exception{
        if(android.os.Build.VERSION.SDK_INT>=37){
            boolean focusable=onMain(()->Api37Probe.isFocusable((android.view.SurfaceControlViewHost)privateField(ZoomService.current,"bubbleHost")));
            String expectation=args.getString("expectNonFocusable");
            boolean expect=expectation==null?getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).getLongVersionCode()>=21:Boolean.parseBoolean(expectation);
            log("HOST PARAMS "+phase+" focusable="+focusable+" expectNonFocusable="+expect+" (public API37)");
            if(expect)require(!focusable,phase+": API37 hosted root is explicitly nonfocusable");
        }
    }
    @android.annotation.TargetApi(37)
    private static final class Api37Probe {
        static boolean isFocusable(android.view.SurfaceControlViewHost host){return host.getLayoutParams().isFocusable();}
    }
    private boolean editorFocused(){return focusedEditor()!=null;}
    private android.view.accessibility.AccessibilityNodeInfo focusedEditor(){
        // Active/touched window can be the overlay during a gesture; test actual INPUT-focused window.
        for(android.view.accessibility.AccessibilityWindowInfo window:ui.getWindows()){
            if(!window.isFocused())continue;
            android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();
            android.view.accessibility.AccessibilityNodeInfo focused=root==null?null:root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if(focused!=null&&"android.widget.EditText".contentEquals(focused.getClassName())
                &&"io.github.kacperla.touchzoom.test".contentEquals(focused.getPackageName()))return focused;
        }
        return null;
    }
    private boolean imeVisible(){
        for(android.view.accessibility.AccessibilityWindowInfo w:ui.getWindows())
            if(w.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD){Rect bounds=new Rect();w.getBoundsInScreen(bounds);if(!bounds.isEmpty())return true;}
        return false;
    }
    private void waitService(long timeout)throws Exception{
        long until=SystemClock.uptimeMillis()+timeout;
        do{if(onMain(()->ZoomService.current!=null&&(Boolean)privateField(ZoomService.current,"overlayReady"))){try{bubbleBounds();return;}catch(Exception ignored){}}SystemClock.sleep(200);}while(SystemClock.uptimeMillis()<until);
        log(onMain(()->{
            ZoomService service=ZoomService.current;
            if(service==null)return "STARTUP SNAPSHOT current=null";
            Object view=privateField(service,"bubble");
            return "STARTUP SNAPSHOT status="+service.controllerStatus()+" ready="+privateField(service,"overlayReady")
                +" starting="+privateField(service,"starting")+" host="+privateField(service,"bubbleHost")
                +" tokenHost="+privateField(service,"tokenHost")+" attached="+(view instanceof View && ((View)view).isAttachedToWindow());
        }));
        for(StackTraceElement frame:android.os.Looper.getMainLooper().getThread().getStackTrace())log("MAIN "+frame);
        throw new AssertionError("Accessibility service/bubble not ready in "+timeout+"ms");
    }
    private Rect bubbleBounds()throws Exception{return onMain(()->{
        require(ZoomService.current!=null,"service current");Field f=ZoomService.class.getDeclaredField("bubble");f.setAccessible(true);
        View v=(View)f.get(ZoomService.current);require(v!=null&&v.isAttachedToWindow(),"bubble attached");
        // SurfaceControl translation is not necessarily reflected by embedded View.getLocationOnScreen().
        Field p=ZoomService.class.getDeclaredField("bubbleParams");p.setAccessible(true);
        android.view.WindowManager.LayoutParams params=(android.view.WindowManager.LayoutParams)p.get(ZoomService.current);
        return new Rect(params.x,params.y,params.x+v.getWidth(),params.y+v.getHeight());
    });}
    private MagnificationConfig state()throws Exception{return onMain(()->ZoomService.current.getMagnificationController().getMagnificationConfig());}
    private void event(int action,float x,float y){event(action,x,y,true);}
    private void event(int action,float x,float y,boolean synchronous){
        long now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN){down=now;fingerDown=true;}
        MotionEvent e=MotionEvent.obtain(down,now,action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{require(ui.injectInputEvent(e,synchronous),"input injection action="+action);}finally{e.recycle();}
        lastX=x;lastY=y;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)fingerDown=false;
    }
    interface Checked<T>{T get()throws Exception;}
    private <T>T onMain(Checked<T> call)throws Exception{
        AtomicReference<T> value=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try{value.set(call.get());}catch(Throwable t){error.set(t);}});
        if(error.get()!=null)throw new Exception(error.get());return value.get();
    }
    private String shell(String command)throws Exception{
        try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            in.transferTo(out);return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private void log(String text){report.append(SystemClock.uptimeMillis()).append(" ").append(text).append('\n');android.util.Log.i("TouchZoomProbe",text);}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static String describe(MagnificationConfig c){return c==null?"null":"active="+c.isActivated()+" mode="+c.getMode()+" scale="+c.getScale()+" center="+c.getCenterX()+","+c.getCenterY();}
}
