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
            else if("focus".equals(scenario))focus();
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
    private void focus()throws Exception{
        onMain(()->{ZoomService.current.turnOff();return null;});
        android.accessibilityservice.AccessibilityServiceInfo info=ui.getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        ui.setServiceInfo(info);
        shell("am start -n io.github.kacperla.touchzoom.test/io.github.kacperla.touchzoom.ProbeEditorActivity");
        long editorDeadline=SystemClock.uptimeMillis()+30000;
        while(!editorFocused()&&SystemClock.uptimeMillis()<editorDeadline)SystemClock.sleep(200);
        require(editorFocused(),"test editor initially focused within 30s");boolean imeBefore=imeVisible();
        log("EDITOR BEFORE focus=true ime="+imeBefore);Rect b=bubbleBounds();
        event(MotionEvent.ACTION_DOWN,b.exactCenterX(),b.exactCenterY());
        SystemClock.sleep(25);event(MotionEvent.ACTION_UP,lastX,lastY);SystemClock.sleep(700);
        require(editorFocused(),"controller touch preserves editor focus");
        ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_A),true);
        ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_A),true);
        SystemClock.sleep(300);
        android.view.accessibility.AccessibilityNodeInfo root=ui.getRootInActiveWindow();
        android.view.accessibility.AccessibilityNodeInfo focused=root==null?null:root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
        require(focused!=null&&focused.getText()!=null&&focused.getText().toString().contains("a"),"editor receives hardware key after controller touch");
        boolean imeAfter=imeVisible();log("EDITOR AFTER focus=true typed=a ime="+imeAfter);
        require(!imeBefore||imeAfter,"visible IME remains visible");
    }
    private boolean editorFocused(){
        android.view.accessibility.AccessibilityNodeInfo root=ui.getRootInActiveWindow();
        android.view.accessibility.AccessibilityNodeInfo focused=root==null?null:root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
        return focused!=null&&"android.widget.EditText".contentEquals(focused.getClassName())
            &&"io.github.kacperla.touchzoom.test".contentEquals(focused.getPackageName());
    }
    private boolean imeVisible(){
        for(android.view.accessibility.AccessibilityWindowInfo w:ui.getWindows())
            if(w.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;
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
    private void event(int action,float x,float y){
        long now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN){down=now;fingerDown=true;}
        MotionEvent e=MotionEvent.obtain(down,now,action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{require(ui.injectInputEvent(e,true),"input injection action="+action);}finally{e.recycle();}
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
    private void log(String text){report.append(SystemClock.uptimeMillis()).append(" ").append(text).append('\n');}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static String describe(MagnificationConfig c){return c==null?"null":"active="+c.isActivated()+" mode="+c.getMode()+" scale="+c.getScale()+" center="+c.getCenterX()+","+c.getCenterY();}
}
