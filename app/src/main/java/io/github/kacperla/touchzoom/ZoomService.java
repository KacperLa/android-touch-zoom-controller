package io.github.kacperla.touchzoom;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.MagnificationConfig;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** Display-attached surface hosts keep controls outside app-window magnification. No screen capture. */
public final class ZoomService extends AccessibilityService implements DisplayManager.DisplayListener {
    // Process-local service handle for settings; cleared in onDestroy, never an Activity context.
    @android.annotation.SuppressLint("StaticFieldLeak")
    static ZoomService current;
    private WindowManager wm;
    private SurfaceControlViewHost bubbleHost,menuHost;
    private SurfaceControlViewHost.SurfacePackage menuPackage;
    private SurfaceControl menuControl;
    private IBinder overlayHostToken;
    private SurfaceView tokenHost;
    private final InsetsEventQueue insetQueue=new InsetsEventQueue();
    private long insetEventCount,keyboardCheckCount;
    private boolean overlayReady,starting;
    private int startupGeneration;
    private String startupStatus="Controller is starting.";
    public String controllerStatus(){return startupStatus;}
    public boolean controllerReady(){return overlayReady && !destroyed;}
    public boolean controllerStarting(){return starting && !destroyed;}
    public void retryStartup(){if(!overlayReady && !starting && !destroyed)onServiceConnected();}
    private SurfaceControl padControl;
    private Surface padSurface;
    private SurfaceControlViewHost.SurfacePackage bubblePackage;
    private Insets lastInsets = Insets.NONE;
    private DisplayManager displays;
    private MagnificationController magnifier;
    private PowerManager power;
    private MagnificationConfig lastReportedConfig;
    private boolean imeVisible;
    private int imeBottom;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Bubble bubble;
    private Pad pad;
    private WindowManager.LayoutParams bubbleParams;
    private SharedPreferences prefs;
    private int width, height, diameter, rotation, pointerId = -1;
    private float density, normalizedX = .82f, normalizedY = .72f, rememberedScale = 2f;
    private float initialCenterX, initialCenterY;
    private float downX, downY, initialScale, padLeft, padTop, padWidth, padHeight;
    private float moveStartX, moveStartY, pendingX, pendingY, pendingScale;
    private float grabX,grabY;
    private long downTime;
    private boolean panStarted;
    private boolean moved, zoomGesture, relocating, active, frameQueued, hasPending, destroyed, failureShown;
    private final UpdatePacer pacer=new UpdatePacer();
    private final ZoomHaptics zoomHaptics=new ZoomHaptics();
    private final DoubleTapScale doubleTapScale=new DoubleTapScale();
    private final MagnificationOwnership ownership=new MagnificationOwnership();
    private final ControllerDragMode dragMode=new ControllerDragMode();
    private final TapSequence taps=new TapSequence();
    private Runnable pendingTap;
    private ControllerMenu controllerMenu;
    private boolean scaleGestureClaimed;
    private boolean zoomedAtDown,preserveZoomAtDown,pendingTapWasZoomed;
    private final Runnable relocateZoomed=this::startZoomedRelocation;
    private void startZoomedRelocation(){
        if(!active || destroyed || zoomGesture || !zoomedAtDown || moved)return;
        if(dragMode.hold()!=ControllerDragMode.RELOCATE)return;
        relocating=true;cancelTapSequence();hasPending=false;momentary.end();
        Choreographer.getInstance().removeFrameCallback(frame);frameQueued=false;
        pad.visible=false;pad.invalidate();applyBubbleTransform();bubble.invalidate();
    }
    private final MenuScaleUpdates menuScale=new MenuScaleUpdates();
    private boolean menuScaleFrameQueued,menuScalePreferenceDirty;
    private final Runnable persistMenuScale=this::persistMenuScale;
    private void persistMenuScale(){
        handler.removeCallbacks(persistMenuScale);
        if(menuScalePreferenceDirty && prefs!=null){
            menuScalePreferenceDirty=false;prefs.edit().putFloat("scale",rememberedScale).apply();
        }
    }
    private final Choreographer.FrameCallback menuScaleFrame=nanos->{
        menuScaleFrameQueued=false;
        dispatchMenuScale();
    };
    private final Runnable menuScaleTimeout=()->{
        if(!menuScale.waiting())return;
        MagnificationConfig actual=config();
        if(actual!=null)refreshBubbleArtwork(actual);
        if(menuScale.waiting()){
            // Missing acknowledgments never authorize another write or a replay after dismissal.
            cancelMenuScaleUpdates();
            if(menuIsShowing())controllerMenu.refresh();
            Toast.makeText(this,"Zoom update not confirmed. Try adjusting again.",Toast.LENGTH_SHORT).show();
        }
    };
    private void queueMenuScaleFrame(){
        if(!menuScale.ready() || menuScaleFrameQueued || !menuIsShowing() || destroyed)return;
        menuScaleFrameQueued=true;Choreographer.getInstance().postFrameCallback(menuScaleFrame);
    }
    private void cancelMenuScaleUpdates(){
        persistMenuScale();
        menuScale.cancel();handler.removeCallbacks(menuScaleTimeout);
        Choreographer.getInstance().removeFrameCallback(menuScaleFrame);menuScaleFrameQueued=false;
    }
    private void observeMenuScale(MagnificationConfig state){
        if(state.getMode()!=MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN || !state.isActivated())return;
        if(menuScale.observe(state.getScale())){
            handler.removeCallbacks(menuScaleTimeout);queueMenuScaleFrame();
        }
    }
    private void dispatchMenuScale(){
        if(!menuScale.ready())return;
        if(destroyed || !overlayReady || !menuIsShowing()){cancelMenuScaleUpdates();return;}
        MagnificationConfig fresh=config();
        if(fresh==null){cancelMenuScaleUpdates();return;}
        // Reconcile before writing: a different owner, mode, or external reset cancels our queue.
        refreshBubbleArtwork(fresh);
        if(!menuScale.ready())return;
        if(windowZoomActive(fresh) || !fresh.isActivated() || fresh.getScale()<=1.01f){
            cancelMenuScaleUpdates();return;
        }
        float target=menuScale.take(fresh.getScale());
        if(!Float.isFinite(target))return;
        latch.setLatched(true);preserveZoomAtDown=false;
        float x=Float.isFinite(fresh.getCenterX())?fresh.getCenterX():width/2f;
        float y=Float.isFinite(fresh.getCenterY())?fresh.getCenterY():height/2f;
        boolean accepted=apply(target,x,y,false);
        if(!accepted){
            MagnificationConfig actual=config();
            if(actual!=null)refreshBubbleArtwork(actual);
            if(menuScale.waiting())cancelMenuScaleUpdates();
        }
        if(menuScale.waiting())handler.postDelayed(menuScaleTimeout,1000);
        else queueMenuScaleFrame();
    }
    private final ScaleBackpressure scaleGate=new ScaleBackpressure();
    private final Runnable scaleTimeout=()->{
        if(active && zoomGesture && scaleGate.waiting()){
            MagnificationConfig actual=config();
            if(actual!=null)observeScale(actual);
            if(scaleGate.waiting()){
                trace("scale awaiting report: no retry queued");
                Toast.makeText(this,"Zoom update not confirmed. Updates paused; release to stop.",Toast.LENGTH_SHORT).show();
            }
        }
    };
    private void observeScale(MagnificationConfig state){
        if(state.getMode()!=MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN || !state.isActivated())return;
        if(scaleGate.observe(state.getScale())){
            handler.removeCallbacks(scaleTimeout);
            if(active && zoomGesture && hasPending)queueFrame();
        }
    }

    private final CadenceStats cadence=new CadenceStats();
    private float peakRefreshRate,appliedRefreshVote;
    private float reportedMin=Float.NaN,reportedMax=Float.NaN;
    private boolean sampledDisplay;
    private String cadenceDisplayInfo="No cadence capture yet.";
    private final Choreographer.FrameCallback cadenceFrame=new Choreographer.FrameCallback(){
        public void doFrame(long nanos){
            if(!probe.recording())return;
            cadence.tick(CadenceStats.VSYNC,nanos);
            if(!sampledDisplay){sampledDisplay=true;sampleReportedRefresh();}
            Choreographer.getInstance().postFrameCallback(this);
        }
    };
    private void sampleReportedRefresh(){
        Display d=displays.getDisplay(Display.DEFAULT_DISPLAY);if(d==null)return;float hz=d.getRefreshRate();
        reportedMin=Float.isNaN(reportedMin)?hz:Math.min(reportedMin,hz);reportedMax=Float.isNaN(reportedMax)?hz:Math.max(reportedMax,hz);
    }
    private void setRefreshVote(boolean interacting){
        float wanted=interacting?peakRefreshRate:0;
        if(wanted==appliedRefreshVote)return;
        if(padControl!=null && bubblePackage!=null){
            transact(t->t.setFrameRate(padControl,wanted,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
                .setFrameRate(bubblePackage.getSurfaceControl(),wanted,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS));
            if(Build.VERSION.SDK_INT>=35)bubble.setRequestedFrameRate(interacting?View.REQUESTED_FRAME_RATE_CATEGORY_HIGH:View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT);
            appliedRefreshVote=wanted;
        }
    }
    private boolean diagnosticEnabled(){return diagnostic.mayRecord() && diagnostic.recording(SystemClock.uptimeMillis());}
    private final DiagnosticTrace diagnostic=new DiagnosticTrace();
    private final GestureProbe probe=new GestureProbe();
    private final InputArrivalStats inputArrival=new InputArrivalStats();
    private long heartbeatDue,frameScheduledAt;
    private final Runnable probeHeartbeat=new Runnable(){public void run(){
        if(!captureActive)return;
        long now=SystemClock.uptimeMillis();captureSession.heartbeat(heartbeatDue,now);
        if(!captureSession.recording(now)){endDiagnostic();return;}
        probe.span("main-looper heartbeat lateness",heartbeatDue,now);
        heartbeatDue=now+50;handler.postAtTime(this,heartbeatDue);
    }};
    private final CaptureSession captureSession=new CaptureSession();
    private boolean captureActive,phaseOpen;
    private final Runnable stopCapture=()->endDiagnostic();
    public void startDiagnostic(){
        clearDiagnostic();
        long now=SystemClock.uptimeMillis();captureSession.start(now);captureActive=true;
        diagnostic.start(now);inputArrival.reset(false);
        sampledDisplay=false;reportedMin=reportedMax=Float.NaN;
        Display display=displays.getDisplay(Display.DEFAULT_DISPLAY);
        cadenceDisplayInfo="Frame-rate preference="+peakRefreshRate+" Hz (not guaranteed); reported at arm="+(display==null?"unknown":display.getRefreshRate())+" Hz.";
        heartbeatDue=now+50;handler.postAtTime(probeHeartbeat,heartbeatDue);
        handler.postAtTime(stopCapture,captureSession.deadline());
    }
    public void clearDiagnostic(){
        captureActive=false;phaseOpen=false;handler.removeCallbacks(stopCapture);handler.removeCallbacks(probeHeartbeat);
        Choreographer.getInstance().removeFrameCallback(cadenceFrame);
        synchronized(probe){probe.clear();cadence.reset();}
        captureSession.clear();diagnostic.clear();inputArrival.reset(false);
        sampledDisplay=false;reportedMin=reportedMax=Float.NaN;cadenceDisplayInfo="No capture armed.";
    }
    private void finishPhase(String reason,long now,long event){
        if(!phaseOpen)return;
        probe.mark(reason,now,event);probe.stop(reason,now);
        captureSession.addGesture(probe.report());phaseOpen=false;
    }
    private void endDiagnostic(){
        long now=SystemClock.uptimeMillis();
        if(captureActive && heartbeatDue<captureSession.deadline() && heartbeatDue<=now)
            captureSession.heartbeat(heartbeatDue,now);
        finishPhase("capture ended",now,-1);
        captureSession.recording(now);captureSession.stop();captureActive=false;diagnostic.stop();
        handler.removeCallbacks(stopCapture);handler.removeCallbacks(probeHeartbeat);
        Choreographer.getInstance().removeFrameCallback(cadenceFrame);
    }
    public String diagnosticReport(){
        long now=SystemClock.uptimeMillis();
        if(captureActive && now>=captureSession.deadline())endDiagnostic();
        return "Touch Zoom "+BuildConfig.VERSION_NAME+"\nAndroid "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"; "+Build.MANUFACTURER+" "+Build.MODEL+"\nSnapshot uptime="+now+" ms. Refresh or Copy reads the current capture.\n"+captureSession.report(now)+"\n"+cadenceDisplayInfo+"\nReported during capture="+reportedMin+"–"+reportedMax+" Hz (not presented FPS).\n"+cadence.report()+"\n"+(phaseOpen?"Current gesture:\n"+probe.report():"")+"\n"+inputArrival.report()+"\nContinuous detailed events (bounded latest 8192; all gestures within the capture window):\n"+diagnostic.report();
    }
    private void trace(String event){if(diagnosticEnabled())diagnostic.add(event);}

    private final Rect region = new Rect();
    private final MomentaryZoom momentary = new MomentaryZoom();
    private final ZoomLatch latch=new ZoomLatch();
    private final BubbleAppearance bubbleAppearance = new BubbleAppearance();
    private final MagnificationController.OnMagnificationChangedListener magnificationChanged =
        new MagnificationController.OnMagnificationChangedListener() {
            @Override public void onMagnificationChanged(MagnificationController controller, Region region,
                    float scale, float centerX, float centerY) {
                if(!destroyed && bubbleAppearance.update(scale>1.01f) && bubble!=null)bubble.invalidate();
            }
            @Override public void onMagnificationChanged(MagnificationController controller, Region region,
                    MagnificationConfig state) {
                refreshBubbleArtwork(state);
            }
        };
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (preferences,key) -> {
        if(OpacitySettings.KEY.equals(key) && bubble!=null && !active) {
            bubble.setAlpha(OpacitySettings.normalize(prefs.getInt(OpacitySettings.KEY,OpacitySettings.DEFAULT_PERCENT))/100f);
        }
        if((PadSettings.KEY.equals(key) || ActivationSettings.KEY.equals(key)) && wm!=null && !destroyed) {
            cancelGesture(); updateGeometry(); if(pad!=null)pad.invalidate();
        }
        if(ControllerSettings.isControllerPreference(key) && !destroyed){
            cancelGesture();
            if(bubble!=null){bubble.updateDescription();bubble.invalidate();}
        }
    };
    private final Runnable activatePreview = () -> {
        if(!active || relocating || destroyed)return;
        MagnificationConfig fresh=config();
        if(fresh!=null){
            lastReportedConfig=fresh;
            if(windowZoomActive(fresh) || ownership.observe(fresh.getMode(),fresh.isActivated(),fresh.getScale())){
                abandonZoomControl();return;
            }
        }
        if(!zoomGesture && dragMode.hold()!=ControllerDragMode.PAN)return;
        cancelTapSequence(); // A hold owns this sequence; no earlier tap may fire later.
        probe.mark("hold timer / activation entry",SystemClock.uptimeMillis(),-1);
        pad.visible=!zoomGesture;
        momentary.begin(initialScale);
        if(!zoomGesture)anchorPad(initialCenterX,initialCenterY);
        boolean redrawPending=hasPending;
        float targetScale=hasPending ? pendingScale : initialScale;
        momentary.select(targetScale);
        zoomHaptics.requestEngage(isReportedZoomed());
        probe.mark("activation call start",SystemClock.uptimeMillis(),-1);
        if(!zoomGesture || doubleTapScale.shouldWrite(targetScale))
            apply(targetScale,hasPending ? pendingX : initialCenterX,hasPending ? pendingY : initialCenterY,false);
        else trace("second-tap1x logical entry: no system request or acknowledgment needed");
        probe.mark("activation call end",SystemClock.uptimeMillis(),-1);
        hasPending=false;
        // No synchronous read-back or canvas lock between activation and the next input event.
        if(pad!=null && (pad.visible || redrawPending))pad.invalidate();
        bubble.invalidate();
    };
    private final Runnable fade = () -> {
        if (!active && bubble != null) bubble.setAlpha(OpacitySettings.normalize(
            prefs.getInt(OpacitySettings.KEY,OpacitySettings.DEFAULT_PERCENT))/100f);
    };
    private final Choreographer.FrameCallback frame = nanos -> {
        frameQueued=false;
        if(probe.recording()){probe.span("frame scheduling wait",frameScheduledAt,SystemClock.uptimeMillis());cadence.tick(CadenceStats.TRACKING_FRAME,nanos);}
        if(active && momentary.ownsPreview() && hasPending && !destroyed){
            if(zoomGesture && scaleGate.waiting())return; // Latest desired values stay; callback resumes.
            hasPending=false;
            if(probe.recording())probe.mark("tracking frame entry",SystemClock.uptimeMillis(),-1);
            // Marker submission is independent of duplicate suppression and precedes Binder work.
            if(pad!=null && pad.visible)pad.invalidate();
            if(pacer.changed(pendingScale,pendingX,pendingY)){
                if(zoomGesture){if(doubleTapScale.shouldWrite(pendingScale))apply(pendingScale,pendingX,pendingY,false);}
                else applyCenter(pendingX,pendingY);
            }
        }
    };
    private void queueFrame(){
        if(!frameQueued){frameQueued=true;if(probe.recording()){frameScheduledAt=SystemClock.uptimeMillis();probe.mark("tracking frame scheduled",frameScheduledAt,-1);}Choreographer.getInstance().postFrameCallback(frame);}
    }
    private final Runnable checkKeyboard = new Runnable() {
        @Override public void run() {
            if(!insetQueue.take(startupGeneration) || destroyed || !overlayReady || wm==null || power==null || !power.isInteractive())return;
            keyboardCheckCount++;
            long metricsStart=SystemClock.uptimeMillis();
            WindowInsets insets=wm.getCurrentWindowMetrics().getWindowInsets();
            probe.span("keyboard metrics query",metricsStart,SystemClock.uptimeMillis());
            boolean visible=insets.isVisible(WindowInsets.Type.ime());
            int bottom=insets.getInsets(WindowInsets.Type.ime()).bottom;
            if(visible!=imeVisible || bottom!=imeBottom){
                imeVisible=visible;imeBottom=bottom;
                // An IME resize cancels a menu drag before moving its touch targets.
                if(menuIsShowing())dismissControllerMenu();
                Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                float desiredX=relocating ? bubbleParams.x : normalizedX*width-diameter/2f;
                float desiredY=relocating ? bubbleParams.y : normalizedY*height-diameter/2f;
                float scale=DragVisual.scale(relocating),offset=DragVisual.offset(grabY,scale);
                float targetY=KeyboardAvoidance.top(desiredY-offset,diameter*scale,height,
                    safe.top,safe.bottom,visible,bottom,8*density)+offset;
                // Moving the touch target under an active finger would invalidate raw coordinates.
                if(Math.round(targetY)!=bubbleParams.y){
                    if(active || taps.hasPendingTap())cancelGesture();
                    place(desiredX,desiredY);
                }
            }

        }
    };
    private void requestKeyboardCheck(int generation){
        if(destroyed || !overlayReady || power==null || !power.isInteractive())return;
        if(insetQueue.request(generation))handler.post(checkKeyboard);
    }
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(checkKeyboard);insetQueue.cancel();
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))releaseAllZoom();
            else if(Intent.ACTION_SCREEN_ON.equals(intent.getAction())){
                if(tokenHost!=null)tokenHost.requestApplyInsets();
                requestKeyboardCheck(startupGeneration);
            }
        }
    };

    @Override public void onServiceConnected() {
        if (overlayReady || starting) return;
        current = this; destroyed = false;starting=true;startupGeneration++;
        startupStatus="Controller is starting.";insetQueue.start(startupGeneration);
        try {
        prefs = getSharedPreferences("controller", MODE_PRIVATE);
        ControllerPreferences.ensure(prefs);
        if(!prefs.contains(ActivationSettings.KEY))prefs.edit().putInt(ActivationSettings.KEY,
            ActivationSettings.migratedExtra(prefs.getInt(ActivationSettings.LEGACY_KEY,1000))).apply();
        normalizedX = prefs.getFloat("x", .82f); normalizedY = prefs.getFloat("y", .72f);
        rememberedScale = Mapping.clamp(prefs.getFloat("scale",2),1.01f,8);
        wm = getSystemService(WindowManager.class); displays = getSystemService(DisplayManager.class);
        power=getSystemService(PowerManager.class);
        magnifier = getMagnificationController(); density = getResources().getDisplayMetrics().density;
        diameter = Math.round(56 * density);
        bubble = new Bubble();
        bubbleParams = params(diameter,diameter);
        updateGeometry();
        Display display = displays.getDisplay(Display.DEFAULT_DISPLAY);
        createInsetsHost(startupGeneration);
        padControl = new SurfaceControl.Builder().setName("Touch Zoom Pad").setBufferSize(Math.max(1,(int)Math.ceil(padWidth)),Math.max(1,(int)Math.ceil(padHeight)))
            .setFormat(PixelFormat.RGBA_8888).setHidden(false).build();
        padSurface = new Surface(padControl);pad=new Pad(padSurface,padControl);
        attachAccessibilityOverlayToDisplay(Display.DEFAULT_DISPLAY,padControl);
        overlayHostToken=null;
        try {
            bubbleHost = new SurfaceControlViewHost(this, display, (IBinder)null);
            setBubbleView();bubblePackage=bubbleHost.getSurfacePackage();
        }catch(RuntimeException denied){
            if(bubbleHost!=null){bubbleHost.release();bubbleHost=null;}
            if(!hostTokenDenied(denied))throw denied;
            startTokenHost(display,startupGeneration);
            return;
        }
        finishOverlayStartup();
        }catch(RuntimeException failure){failOverlayStartup(failure);}
    }
    private static boolean hostTokenDenied(Throwable failure){
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())
            if(cause instanceof SecurityException && String.valueOf(cause.getMessage()).contains("INTERNAL_SYSTEM_WINDOW"))return true;
        return false;
    }
    private void createInsetsHost(int generation){
        // A display-attached SCVH alone is not a registered inset recipient. This
        // non-interactive window observes source changes, then queries display
        // metrics: a 1px window's local IME inset is not the keyboard height.
        tokenHost=new SurfaceView(this);tokenHost.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tokenHost.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        tokenHost.setOnApplyWindowInsetsListener((view,insets)->{
            if(generation==startupGeneration && !destroyed){insetEventCount++;requestKeyboardCheck(generation);}
            return insets; // Observe, never consume or control the editor's IME.
        });
        WindowManager.LayoutParams hostParams=params(1,1);
        hostParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        hostParams.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        hostParams.setTitle("Touch Zoom inset host");hostParams.alpha=0;
        wm.addView(tokenHost,hostParams);tokenHost.requestApplyInsets();
    }
    private void startTokenHost(Display display,int generation){
        startupStatus="Starting a compatible controller host.";
        final long deadline=SystemClock.uptimeMillis()+1500;
        handler.post(new Runnable(){public void run(){
            if(destroyed || generation!=startupGeneration || !starting)return;
            try {
                IBinder token=tokenHost.getHostToken();
                if(token==null){if(SystemClock.uptimeMillis()<deadline){handler.postDelayed(this,50);return;}
                    throw new IllegalStateException("Compatible input host did not attach");}
                overlayHostToken=token;
                bubbleHost=new SurfaceControlViewHost(ZoomService.this,display,token);
                setBubbleView();bubblePackage=bubbleHost.getSurfacePackage();
                finishOverlayStartup();
            }catch(RuntimeException failure){failOverlayStartup(failure);}
        }});
    }
    private void finishOverlayStartup(){
        createMenuOverlay();
        attachAccessibilityOverlayToDisplay(Display.DEFAULT_DISPLAY,bubblePackage.getSurfaceControl());
        transact(t -> t.setLayer(padControl,0).setVisibility(padControl,true)
            .setLayer(bubblePackage.getSurfaceControl(),1).setVisibility(bubblePackage.getSurfaceControl(),true));
        place(bubbleParams.x,bubbleParams.y); pad.invalidate();
        magnifier.addListener(magnificationChanged,handler);
        refreshBubbleArtwork(config());
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged);
        displays.registerDisplayListener(this,handler);
        IntentFilter screenEvents=new IntentFilter(Intent.ACTION_SCREEN_OFF);screenEvents.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOff,screenEvents,Context.RECEIVER_NOT_EXPORTED);
        handler.postDelayed(fade,1800);
        overlayReady=true;starting=false;
        requestKeyboardCheck(startupGeneration);
        startupStatus="Service connected. The floating controller is available.";
    }
    private void failOverlayStartup(RuntimeException failure){
        starting=false;overlayReady=false;startupGeneration++;
        cancelGesture();
        startupStatus="Controller could not start. In Touch Zoom, open Advanced and tap Retry controller startup. If it still fails, turn its accessibility service off and on again.";
        android.util.Log.e("TouchZoom","Overlay startup failed",failure);
        insetQueue.stop();handler.removeCallbacks(checkKeyboard);handler.removeCallbacks(fade);
        if(magnifier!=null)magnifier.removeListener(magnificationChanged);
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        if(displays!=null)displays.unregisterDisplayListener(this);
        try{unregisterReceiver(screenOff);}catch(IllegalArgumentException ignored){}
        cleanupOverlayResources();
        Toast.makeText(this,startupStatus,Toast.LENGTH_LONG).show();
    }
    private void cleanupOverlayResources(){
        // Hide first so neither a stale frame nor an input window survives partial cleanup.
        if(controllerMenu!=null){try{controllerMenu.dismiss();}catch(RuntimeException ignored){}controllerMenu=null;}
        if(menuControl!=null){try{transact(t->t.setVisibility(menuControl,false).reparent(menuControl,null));}catch(RuntimeException ignored){}}
        if(menuPackage!=null){try{transact(t->t.reparent(menuPackage.getSurfaceControl(),null));}catch(RuntimeException ignored){}menuPackage.release();menuPackage=null;}
        if(menuHost!=null){try{menuHost.release();}catch(RuntimeException ignored){}menuHost=null;}
        if(menuControl!=null){menuControl.release();menuControl=null;}
        overlayHostToken=null;
        if(bubblePackage!=null){try{transact(t->t.reparent(bubblePackage.getSurfaceControl(),null));}catch(RuntimeException ignored){}bubblePackage.release();bubblePackage=null;}
        if(bubbleHost!=null){try{bubbleHost.release();}catch(RuntimeException ignored){}bubbleHost=null;}
        if(tokenHost!=null){tokenHost.setOnApplyWindowInsetsListener(null);try{wm.removeViewImmediate(tokenHost);}catch(RuntimeException ignored){}tokenHost=null;}
        if(pad!=null){pad.shutdown();pad=null;}
        else {if(padSurface!=null){padSurface.release();padSurface=null;}if(padControl!=null){padControl.release();padControl=null;}}
    }
    private void setBubbleView(){
        setHostedView(bubbleHost,bubble,diameter,diameter);
    }
    private static void setHostedView(SurfaceControlViewHost host,View view,int width,int height){
        if(Build.VERSION.SDK_INT>=37)Api37Host.setView(host,view,width,height);
        else host.setView(view,width,height);
    }
    private static void relayoutHost(SurfaceControlViewHost host,int width,int height){
        if(Build.VERSION.SDK_INT>=37)Api37Host.relayout(host,width,height);
        else host.relayout(width,height);
    }
    /** Isolate API37 types from older platform class loading; keep touch, never take key focus. */
    @android.annotation.TargetApi(37)
    private static final class Api37Host {
        static void setView(SurfaceControlViewHost host,View view,int width,int height){
            host.setView(view,new SurfaceControlViewHost.LayoutParams(width,height,false));
        }
        static void relayout(SurfaceControlViewHost host,int width,int height){
            host.relayout(new SurfaceControlViewHost.LayoutParams(width,height,false));
        }
    }
    private void transact(java.util.function.Consumer<SurfaceControl.Transaction> action) {
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) { action.accept(transaction); transaction.apply(); }
    }
    private WindowManager.LayoutParams params(int w,int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START; p.setFitInsetsTypes(0);
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setTitle("Touch Zoom Controller"); return p;
    }
    private void updateGeometry() {
        Rect bounds = wm.getCurrentWindowMetrics().getBounds(); width = bounds.width(); height = bounds.height();
        Display display=displays.getDisplay(Display.DEFAULT_DISPLAY);
        rotation=display.getRotation();
        Display.Mode mode=display.getMode();Display.Mode[] modes=display.getSupportedModes();
        int[] widths=new int[modes.length],heights=new int[modes.length];float[] rates=new float[modes.length];
        for(int i=0;i<modes.length;i++){widths[i]=modes[i].getPhysicalWidth();heights[i]=modes[i].getPhysicalHeight();rates[i]=modes[i].getRefreshRate();}
        peakRefreshRate=RefreshRatePolicy.select(mode.getPhysicalWidth(),mode.getPhysicalHeight(),widths,heights,rates);
        float longSide = PadSettings.longSidePixels(prefs.getInt(PadSettings.KEY,PadSettings.DEFAULT_DP),density,width,height);
        float[] size = Mapping.padSize(width,height,longSide); padWidth = size[0]; padHeight = size[1];
        place(normalizedX*width-diameter/2f,normalizedY*height-diameter/2f);
    }
    private void place(float x,float y) {
        // Keep clear of cutouts and system gesture bars where possible.
        WindowInsets windowInsets=wm.getCurrentWindowMetrics().getWindowInsets();
        Insets insets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        imeVisible=windowInsets.isVisible(WindowInsets.Type.ime());
        imeBottom=windowInsets.getInsets(WindowInsets.Type.ime()).bottom;
        float visualScale=DragVisual.scale(relocating);
        float visualDiameter=diameter*visualScale;
        float visualY=DragVisual.visualStart(y,grabY,visualScale);
        float avoidedY=KeyboardAvoidance.top(visualY,visualDiameter,height,insets.top,insets.bottom,imeVisible,imeBottom,8*density);
        float safeBottom=height-insets.bottom;
        if(imeVisible && imeBottom>0)safeBottom=Math.min(safeBottom,height-imeBottom);
        bubbleParams.x=DragVisual.clampLogical(x,grabX,visualScale,diameter,insets.left,width-insets.right);
        bubbleParams.y=DragVisual.clampLogical(avoidedY+DragVisual.offset(grabY,visualScale),grabY,visualScale,diameter,insets.top,safeBottom);
        lastInsets=insets;
        applyBubbleTransform();
    }
    private void applyBubbleTransform(){
        if(bubblePackage==null)return;
        float scale=DragVisual.scale(relocating);
        float x=DragVisual.visualStart(bubbleParams.x,grabX,scale),y=DragVisual.visualStart(bubbleParams.y,grabY,scale);
        // One atomic parent transform scales artwork and its input subtree, with no crop/relayout.
        transact(t->t.setScale(bubblePackage.getSurfaceControl(),scale,scale).setPosition(bubblePackage.getSurfaceControl(),x,y));
    }
    private void refreshBubbleArtwork(MagnificationConfig state) {
        // Unknown state does not prove zoom ended; wait for a valid system callback.
        if(destroyed || state==null)return;
        lastReportedConfig=state;
        if(ownership.observe(state.getMode(),state.isActivated(),state.getScale()))abandonZoomControl();
        int feedback=zoomHaptics.observe(isReportedZoomed());
        if(bubble!=null && feedback!=ZoomHaptics.NONE)bubble.performHapticFeedback(
            feedback==ZoomHaptics.ENGAGE?HapticFeedbackConstants.GESTURE_START:HapticFeedbackConstants.GESTURE_END);
        if(active && !zoomGesture && momentary.ownsPreview() && !panStarted && state.isActivated()
                && Float.isFinite(state.getCenterX()) && Float.isFinite(state.getCenterY())){
            anchorPad(state.getCenterX(),state.getCenterY());if(pad!=null && pad.visible)pad.invalidate();
        }
        observeScale(state);
        observeMenuScale(state);
        if(diagnosticEnabled())trace("state t="+SystemClock.uptimeMillis()+" active="+state.isActivated()+" scale="+state.getScale()+" cx="+state.getCenterX()+" cy="+state.getCenterY());
        if(bubbleAppearance.update(state.isActivated()) && bubble!=null)bubble.invalidate();
        if(menuIsShowing())controllerMenu.refresh();
    }
    private boolean isReportedZoomed(){
        return lastReportedConfig!=null && lastReportedConfig.isActivated()
            && lastReportedConfig.getMode()==MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN
            && lastReportedConfig.getScale()>1.01f;
    }
    private void anchorPad(float x,float y){
        padLeft=pad.fingerX-Mapping.normalized(x,region.left,region.right-1)*padWidth;
        padTop=pad.fingerY-Mapping.normalized(y,region.top,region.bottom-1)*padHeight;
    }
    private MagnificationConfig config() { return magnifier.getMagnificationConfig(); }
    private float scale() { MagnificationConfig c=config(); return c != null && c.isActivated() && Float.isFinite(c.getScale()) ? Math.max(1,c.getScale()) : 1; }
    private float centerX() { MagnificationConfig c=config(); return c != null && Float.isFinite(c.getCenterX()) ? c.getCenterX() : width/2f; }
    private float centerY() { MagnificationConfig c=config(); return c != null && Float.isFinite(c.getCenterY()) ? c.getCenterY() : height/2f; }
    private boolean windowZoomActive(MagnificationConfig state){
        return state!=null && state.isActivated() && state.getMode()!=MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
    }
    private void explainWindowMode(){Toast.makeText(this,"Window magnification is on. Switch Android magnification to full-screen mode to use Touch Zoom.",Toast.LENGTH_LONG).show();}
    private void abandonZoomControl(){
        // Relinquish without resetting or restoring a stale snapshot over someone else's zoom.
        ownership.relinquish();latch.clear();momentary.end();preserveZoomAtDown=true;cancelGesture();
    }
    private void applyCenter(float x,float y){
        if(windowZoomActive(lastReportedConfig)){abandonZoomControl();return;}
        ownership.command(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN,Float.NaN,false);
        // Explicit active fullscreen mode; unspecified (NaN) scale preserves Android's current scale.
        // Center-only pan is never held behind scale acknowledgment and never repeats a scale target.
        boolean measuring=probe.recording() || diagnosticEnabled();
        long started=measuring?SystemClock.uptimeMillis():0;if(probe.recording())probe.mark("center call start",started,-1);
        magnifier.setMagnificationConfig(new MagnificationConfig.Builder().setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN).setActivated(true).setCenterX(x).setCenterY(y).build(),false);
        if(probe.recording()){long ended=SystemClock.uptimeMillis();probe.mark("center call end",ended,-1);probe.span("center call duration",started,ended);cadence.tick(CadenceStats.CENTER,System.nanoTime());}
        pacer.sent(0,pendingScale,x,y);
        if(diagnosticEnabled())trace("center t="+SystemClock.uptimeMillis()+" callMs="+(SystemClock.uptimeMillis()-started)+" cx="+x+" cy="+y);
    }
    private boolean apply(float scale,float x,float y,boolean animate) {
        if(windowZoomActive(lastReportedConfig)){abandonZoomControl();return false;}
        ownership.command(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN,scale,!preserveZoomAtDown || latch.isLatched());
        if(diagnosticEnabled())trace("request t="+SystemClock.uptimeMillis()+" ON scale="+scale+" cx="+x+" cy="+y);
        boolean gated=active && zoomGesture;
        if(gated){doubleTapScale.wrote();scaleGate.sent(scale);}
        long controlStart=SystemClock.uptimeMillis();
        boolean ok = magnifier.setMagnificationConfig(new MagnificationConfig.Builder()
            .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN).setActivated(true).setScale(scale).setCenterX(x).setCenterY(y).build(),animate);
        // Android also returns false for an unchanged specification, especially at edges.
        // Do not cancel a healthy gesture merely because a request was a no-op.
        probe.span("scale/control call duration",controlStart,SystemClock.uptimeMillis());
        if(gated){
            if(!ok)scaleGate.reset();
            else {
                // An already-reported matching scale needs no new state-change callback.
                if(lastReportedConfig!=null)observeScale(lastReportedConfig);
                if(scaleGate.waiting())handler.postDelayed(scaleTimeout,1000);
            }
        }
        pacer.sent(SystemClock.uptimeMillis(),scale,x,y);
        MagnificationConfig after=ok ? null : config();
        if(after!=null)refreshBubbleArtwork(after);
        if (!ok && !failureShown && scale>1.01f && (after==null || !after.isActivated())) {
            failureShown=true;
            Toast.makeText(this,"Magnification did not activate. Check accessibility settings.",Toast.LENGTH_SHORT).show();
        }
        return ok;
    }
    public void turnOff() {
        if(magnifier==null)return;
        MagnificationConfig fresh=config();
        if(windowZoomActive(fresh)){abandonZoomControl();explainWindowMode();return;}
        abandonZoomControl();ownership.commandOff();magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());
    }
    private void releaseAllZoom(){
        MagnificationConfig fresh=magnifier==null?null:config();
        boolean reset=fresh!=null && ownership.canReset(fresh.getMode(),fresh.isActivated(),fresh.getScale());
        abandonZoomControl();
        if(reset){ownership.commandOff();magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());}
    }
    private void toggleLatch(){
        MagnificationConfig fresh=config();lastReportedConfig=fresh;
        if(windowZoomActive(fresh)){abandonZoomControl();explainWindowMode();return;}
        // A quick tap on externally-owned zoom is still an explicit user request to turn it off.
        if(pendingTapWasZoomed && !latch.isLatched())latch.setLatched(true);
        pendingTapWasZoomed=false;
        if(momentary.ownsPreview()){rememberedScale=momentary.selectedScale();momentary.end();prefs.edit().putFloat("scale",rememberedScale).apply();}
        if(latch.toggle()){
            zoomHaptics.requestEngage(isReportedZoomed());apply(rememberedScale,centerX(),centerY(),false);
        }else if(magnifier!=null){
            zoomHaptics.requestRelease(isReportedZoomed());ownership.commandOff();magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());
        }
        if(bubble!=null)bubble.invalidate();
    }
    private void stopPreview() {
        if (!momentary.end()) return;
        trace("end preview latched="+latch.isLatched());
        rememberedScale=zoomGesture ? (doubleTapScale.shouldLatch()?doubleTapScale.selected():rememberedScale) : momentary.selectedScale();
        if(prefs!=null)prefs.edit().putFloat("scale",rememberedScale).apply();
        if(magnifier!=null && ControllerDragMode.stopsPreview(latch.isLatched(),preserveZoomAtDown)){
            MagnificationConfig fresh=config();
            if(fresh==null || !ownership.canReset(fresh.getMode(),fresh.isActivated(),fresh.getScale())){
                ownership.relinquish();return;
            }
            ownership.commandOff();
            magnifier.setMagnificationConfig(new MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN).setActivated(false).setScale(1).build(),false);
            refreshBubbleArtwork(config());
        }
    }
    private void reconcileIdleLatch(){
        // Read fresh state only before a new interaction, not during async enable callbacks.
        if(!active && !taps.hasPendingTap() && !momentary.ownsPreview() && latch.isLatched()){
            MagnificationConfig currentState=config();
            if(currentState!=null && !ownership.canReset(currentState.getMode(),currentState.isActivated(),currentState.getScale())){latch.clear();ownership.relinquish();}
        }
    }
    private void cancelTapSequence(){
        if(pendingTap!=null)handler.removeCallbacks(pendingTap);
        pendingTap=null;taps.cancel();pendingTapWasZoomed=false;
    }
    private void scheduleTapAction(){
        if(!taps.hasPendingTap())return;
        final long token=taps.token();
        pendingTap=()->{
            if(destroyed || !overlayReady || active)return;
            int count=taps.take(token,SystemClock.uptimeMillis());
            if(count!=0){pendingTap=null;dispatchTapAction(count);}
        };
        handler.postAtTime(pendingTap,taps.deadline());
    }
    private void dispatchTapAction(int count){
        if(destroyed || !overlayReady)return;
        int action=ControllerPreferences.action(prefs,count);
        if(action==ControllerSettings.ZOOM)toggleLatch();
        else {
            pendingTapWasZoomed=false;
            if(action==ControllerSettings.MENU)showControllerMenu();
        }
        // None consumes the complete sequence, including a triple. It never falls back.
    }
    private void createMenuOverlay(){
        // Attach exactly once per startup, not per open: Android retains attached overlay handles.
        // A separate parent owns visibility, independent of the ViewRoot's child transactions.
        menuControl=new SurfaceControl.Builder().setName("Touch Zoom Menu").setHidden(true).build();
        controllerMenu=new ControllerMenu(this,menuActions(),this::hideMenuSurface);
        menuHost=new SurfaceControlViewHost(this,displays.getDisplay(Display.DEFAULT_DISPLAY),overlayHostToken);
        setHostedView(menuHost,controllerMenu.view(),1,1);
        menuPackage=menuHost.getSurfacePackage();
        if(menuPackage==null)throw new IllegalStateException("Menu surface unavailable");
        // SCVH creates this package root hidden. Showing only its parent is insufficient.
        // Keep the package visible underneath the separate parent visibility/input gate.
        transact(t->t.reparent(menuPackage.getSurfaceControl(),menuControl)
            .setVisibility(menuPackage.getSurfaceControl(),true)
            .setLayer(menuControl,2).setVisibility(menuControl,false));
        attachAccessibilityOverlayToDisplay(Display.DEFAULT_DISPLAY,menuControl);
    }
    private void hideMenuSurface(){
        cancelMenuScaleUpdates();
        // SurfaceFlinger propagates a parent's hidden policy to descendant input windows.
        // Alpha=0 alone would leave the scrim intercepting touches, so never use it to dismiss.
        boolean restore=overlayReady && !destroyed;
        if(bubble!=null)bubble.setImportantForAccessibility(restore
            ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        transact(t->{
            if(menuControl!=null)t.setVisibility(menuControl,false);
            if(bubblePackage!=null)t.setVisibility(bubblePackage.getSurfaceControl(),restore);
        });
        // Preserve the current view alpha and fade deadline; no host recreation or zoom reset.
    }
    private void dismissControllerMenu(){
        if(controllerMenu!=null)controllerMenu.dismiss();
    }
    private boolean menuIsShowing(){return controllerMenu!=null && controllerMenu.isShowing();}
    private void showControllerMenu(){
        cancelGesture();
        if(destroyed || !overlayReady || controllerMenu==null)return;
        try {
            WindowMetrics metrics=wm.getCurrentWindowMetrics();
            Rect display=metrics.getBounds();WindowInsets insets=metrics.getWindowInsets();
            Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            MenuBounds bounds=MenuBounds.calculate(display.width(),display.height(),safe.left,safe.top,safe.right,safe.bottom,
                insets.isVisible(WindowInsets.Type.ime()),insets.getInsets(WindowInsets.Type.ime()).bottom);
            if(!bounds.usable())throw new IllegalStateException("No safe space for controller menu");
            controllerMenu.setAvailableWidth(bounds.width);
            relayoutHost(menuHost,bounds.width,bounds.height);
            controllerMenu.show();
            bubble.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            // Hide artwork and its input subtree atomically with showing the menu.
            transact(t->t.setVisibility(bubblePackage.getSurfaceControl(),false)
                .setPosition(menuControl,bounds.left,bounds.top).setVisibility(menuControl,true));
        }catch(RuntimeException failure){
            try{dismissControllerMenu();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            android.util.Log.e("TouchZoom","Could not open controller menu",failure);
            Toast.makeText(this,"Menu unavailable. Open Touch Zoom from your apps",Toast.LENGTH_SHORT).show();
        }
    }
    private ControllerMenu.Actions menuActions(){
        return new ControllerMenu.Actions(){
            @Override public float scale(){
                if(menuScale.hasWork())return menuScale.selected();
                MagnificationConfig fresh=config();
                return fresh!=null && fresh.isActivated() && Float.isFinite(fresh.getScale())
                    ? Mapping.clamp(fresh.getScale(),1.01f,8):rememberedScale;
            }
            @Override public boolean zoomed(){
                MagnificationConfig fresh=config();
                return fresh!=null && !windowZoomActive(fresh) && fresh.isActivated() && fresh.getScale()>1.01f;
            }
            @Override public boolean setScale(float value){return setMenuScale(value);}
            @Override public void cancelScaleUpdates(){cancelMenuScaleUpdates();}
            @Override public boolean setZoomed(boolean enabled){return setMenuZoomed(enabled);}
            @Override public void openSettings(){
                cancelGesture();
                try{startActivity(new Intent(ZoomService.this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
                catch(ActivityNotFoundException | SecurityException failure){
                    android.util.Log.e("TouchZoom","Could not open controller settings",failure);
                    Toast.makeText(ZoomService.this,"Open Touch Zoom from your apps",Toast.LENGTH_SHORT).show();
                }
            }
        };
    }
    private boolean prepareMenuCommand(){
        if(destroyed || !overlayReady || magnifier==null)return false;
        cancelGesture(false);
        MagnificationConfig fresh=config();lastReportedConfig=fresh;
        if(windowZoomActive(fresh)){abandonZoomControl();explainWindowMode();return false;}
        if(fresh!=null)ownership.beginInteraction(fresh.getMode(),fresh.isActivated(),fresh.getScale());
        return true;
    }
    private boolean setMenuScale(float value){
        if(!Float.isFinite(value) || destroyed || !overlayReady || magnifier==null || !menuIsShowing())return false;
        if(!menuScale.active()){
            if(!prepareMenuCommand())return false;
            menuScale.begin();
        }
        rememberedScale=Mapping.clamp(value,1.01f,8);
        menuScalePreferenceDirty=true;
        handler.removeCallbacks(persistMenuScale);handler.postDelayed(persistMenuScale,250);
        if(!isReportedZoomed() && !menuScale.hasWork())return true; // Remember only; never implicitly enable.
        menuScale.offer(rememberedScale);queueMenuScaleFrame();
        return true;
    }
    private boolean setMenuZoomed(boolean enabled){
        if(!prepareMenuCommand())return false;
        preserveZoomAtDown=false;latch.setLatched(enabled);
        if(enabled){
            zoomHaptics.requestEngage(isReportedZoomed());
            boolean changed=apply(rememberedScale,centerX(),centerY(),false);
            MagnificationConfig fresh=config();
            boolean accepted=changed || (fresh!=null && !windowZoomActive(fresh) && fresh.isActivated()
                && Math.abs(fresh.getScale()-rememberedScale)<.02f);
            if(!accepted){latch.clear();ownership.relinquish();zoomHaptics.cancel();}
            return accepted;
        }
        zoomHaptics.requestRelease(isReportedZoomed());ownership.commandOff();
        boolean changed=magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());
        return changed || !isReportedZoomed();
    }
    private void begin(MotionEvent e,long arrival) {
        if(!overlayReady || menuIsShowing())return;
        if(active)cancelGesture();
        MagnificationConfig before=config();lastReportedConfig=before;
        if(windowZoomActive(before)){abandonZoomControl();explainWindowMode();return;}
        if(pendingTap!=null){handler.removeCallbacks(pendingTap);pendingTap=null;}
        int displaced=taps.down(e.getEventTime(),e.getRawX(),e.getRawY(),
            ViewConfiguration.getDoubleTapTimeout(),diameter);
        if(displaced!=0){
            dispatchTapAction(displaced); // Finish a genuinely older or distant sequence first.
            if(menuIsShowing() || destroyed || !overlayReady)return;
        }
        if(captureActive && arrival>=captureSession.deadline())endDiagnostic();
        if(captureActive){
            int extra=prefs.getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS);
            probe.arm(extra,ActivationSettings.totalMs(extra));probe.down(arrival,e.getEventTime());phaseOpen=true;
        }
        captureInput(e,arrival);
        if(probe.recording()){Choreographer.getInstance().removeFrameCallback(cadenceFrame);Choreographer.getInstance().postFrameCallback(cadenceFrame);}
        reconcileIdleLatch();
        panStarted=false;pacer.reset();scaleGate.reset();handler.removeCallbacks(scaleTimeout);
        handler.removeCallbacks(fade); bubble.setAlpha(1);
        active=true;setRefreshVote(true); bubble.invalidate(); failureShown=false; pointerId=e.getPointerId(0); moved=false; relocating=false; hasPending=false;scaleGestureClaimed=false;
        downX=e.getRawX(); downY=e.getRawY(); downTime=e.getEventTime();
        grabX=Mapping.clamp(downX-bubbleParams.x,0,diameter);grabY=Mapping.clamp(downY-bubbleParams.y,0,diameter);
        zoomGesture=taps.currentTapCount()==2 && ControllerPreferences.adjustZoom(prefs);
        if(probe.recording())probe.mark(zoomGesture?"second-tap scale candidate":"tap sequence touch "+taps.currentTapCount(),SystemClock.uptimeMillis(),-1);
        MagnificationConfig initial=config();lastReportedConfig=initial;
        if(windowZoomActive(initial)){abandonZoomControl();explainWindowMode();return;}
        if(initial!=null)ownership.beginInteraction(initial.getMode(),initial.isActivated(),initial.getScale());
        zoomedAtDown=initial!=null && initial.isActivated() && Float.isFinite(initial.getScale()) && initial.getScale()>1.01f;
        preserveZoomAtDown=zoomedAtDown;
        dragMode.begin(zoomedAtDown,zoomGesture);
        initialScale=initial!=null && initial.isActivated() && Float.isFinite(initial.getScale()) && initial.getScale()>1.01f ? initial.getScale() : rememberedScale;
        if(zoomGesture)initialScale=doubleTapScale.begin(initial!=null && initial.isActivated(),
            initial==null?1:initial.getScale());
        initialCenterX=initial!=null && Float.isFinite(initial.getCenterX()) ? initial.getCenterX() : width/2f;
        initialCenterY=initial!=null && Float.isFinite(initial.getCenterY()) ? initial.getCenterY() : height/2f;
        moveStartX=bubbleParams.x; moveStartY=bubbleParams.y;
        int delay=ActivationSettings.totalMs(prefs.getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS));
        // Include preparation in the deadline; no extra platform long-press timeout.
        // Second DOWN only prepares scaling. A real slide owns the sequence; a
        // quick double tap must not change zoom before a possible third tap.
        if(zoomGesture){pad.visible=false;momentary.begin(initialScale);}
        else if(zoomedAtDown){
            // Already magnified: adopt only a gesture preview, never send a new scale or take ownership.
            momentary.begin(initialScale);
            handler.postAtTime(relocateZoomed,UpdatePacer.holdDeadline(downTime,delay));
        }
        else {probe.mark("hold deadline",UpdatePacer.holdDeadline(downTime,delay),-1);handler.postAtTime(activatePreview,UpdatePacer.holdDeadline(downTime,delay));}
        region.set(magnifier.getMagnificationRegion().getBounds());
        if(region.isEmpty()) region.set(0,0,width,height);
        float nx=Mapping.normalized(initialCenterX,region.left,region.right-1);
        float ny=Mapping.normalized(initialCenterY,region.top,region.bottom-1);
        padLeft=downX-nx*padWidth; padTop=downY-ny*padHeight;
        pad.fingerX=downX; pad.fingerY=downY; pad.visible=dragMode.showsPad(momentary.ownsPreview());pad.invalidate();
    }
    private void captureInput(MotionEvent e,long arrival){
        if(!captureActive)return;
        if(arrival>=captureSession.deadline()){endDiagnostic();return;}
        int n=e.getHistorySize(),last=n-1;
        inputArrival.add(e.getEventTime(),arrival,e.getActionMasked(),e.getRawX(),e.getRawY(),n,
            n>0?e.getHistoricalEventTime(0):-1,n>0?e.getHistoricalX(0):0,n>0?e.getHistoricalY(0):0,
            n>0?e.getHistoricalEventTime(last):-1,n>0?e.getHistoricalX(last):0,n>0?e.getHistoricalY(last):0);
    }
    private void move(MotionEvent e,long arrival) {
        if (!active || e.getPointerId(0)!=pointerId) return;
        if(probe.recording()){
            probe.mark("first MOVE arrival",arrival,e.getEventTime());probe.span("MOVE event-to-arrival lag",e.getEventTime(),arrival);
            if(momentary.ownsPreview()){
                probe.mark("first MOVE after activation",arrival,e.getEventTime());
                if(!zoomGesture){cadence.tick(CadenceStats.INPUT_EVENT,e.getEventTime()*1000000L);cadence.tick(CadenceStats.INPUT_ARRIVAL,System.nanoTime());}
            }
        }
        float x=e.getRawX(),y=e.getRawY();
        if(!moved && Math.hypot(x-downX,y-downY)>3*density)moved=true;
        if(moved && taps.currentTapCount()!=0)cancelTapSequence();
        if(zoomGesture && moved && !scaleGestureClaimed){
            scaleGestureClaimed=true;zoomHaptics.requestEngage(isReportedZoomed());bubble.invalidate();
        }
        int chosen=dragMode.movement(moved);
        if(zoomedAtDown && chosen==ControllerDragMode.PAN)handler.removeCallbacks(relocateZoomed);
        if(zoomedAtDown && chosen==ControllerDragMode.WAIT)return; // Jitter must not pan before mode locks.
        if(relocating){place(moveStartX+x-downX,moveStartY+y-downY);return;}
        if(GestureRules.waitingForHold(zoomGesture,momentary.ownsPreview())){
            // Anchor zoom to the latest stationary finger, not stale DOWN jitter.
            pad.fingerX=x;pad.fingerY=y;
            if(chosen==ControllerDragMode.RELOCATE){
                relocating=true;cancelTapSequence();handler.removeCallbacks(activatePreview);
                place(moveStartX+x-downX,moveStartY+y-downY);bubble.invalidate();
            }
            return; // No pre-hold pan. A relocation can never turn into zoom later.
        }
        // Once the hold engages zoom, every move pans; slop only classifies taps.
        if(zoomGesture && !moved)return;
        if(zoomGesture) {
            pendingScale=Mapping.zoom(initialScale,downY-y,padHeight/2); doubleTapScale.select(pendingScale); momentary.select(pendingScale); pendingX=initialCenterX; pendingY=initialCenterY;
        } else {
            panStarted=true;
            if(!pad.visible && dragMode.showsPad(momentary.ownsPreview())){pad.visible=true;bubble.invalidate();}
            pendingScale=initialScale>1.01f ? initialScale : rememberedScale;
            pendingX=Mapping.axis(region.left,region.right-1,padLeft,padWidth,x);
            pendingY=Mapping.axis(region.top,region.bottom-1,padTop,padHeight,y);
            pad.fingerX=x; pad.fingerY=y;
            if(probe.recording())probe.mark("tracking marker coordinates updated",SystemClock.uptimeMillis(),e.getEventTime());
        }
        hasPending=true;
        if(momentary.ownsPreview() && !(zoomGesture && scaleGate.waiting()))queueFrame();
    }
    private boolean isTap(MotionEvent e){
        if(zoomedAtDown && !zoomGesture)return !moved && !relocating && e.getEventTime()-downTime<Math.min(250,ActivationSettings.totalMs(prefs.getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS)));
        return GestureRules.tapBeforeHold(moved,relocating,e.getEventTime()-downTime,
            zoomGesture,momentary.ownsPreview(),ActivationSettings.totalMs(
                prefs.getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS)));
    }
    private void finish(MotionEvent e) {
        if (!active) return;
        // UP is not a hot path: inspect current state before committing/resetting a scale selection.
        MagnificationConfig fresh=config();
        if(fresh!=null){
            lastReportedConfig=fresh;
            if(windowZoomActive(fresh) || ownership.observe(fresh.getMode(),fresh.isActivated(),fresh.getScale())){
                abandonZoomControl();return;
            }
        }
        // Ignore ACTION_UP coordinates: they often jump during finger lift.
        // Do not flush a queued zoom frame on release. End first, then reset.
        hasPending=false;
        boolean tap=isTap(e);
        if(relocating && moved) {
            normalizedX=(bubbleParams.x+diameter/2f)/width; normalizedY=(bubbleParams.y+diameter/2f)/height;
            prefs.edit().putFloat("x",normalizedX).putFloat("y",normalizedY).apply();
        }
        if(tap){
            pendingTapWasZoomed=zoomedAtDown;
            int completed=taps.up(e.getEventTime(),downX,downY,ViewConfiguration.getDoubleTapTimeout());
            // A tap's adopted/logical preview never issued a zoom write. End it
            // without changing scale, ownership or persistent state.
            momentary.end();endGesture(true);
            if(completed!=0)dispatchTapAction(completed);else scheduleTapAction();
        }else if(zoomGesture && moved && momentary.ownsPreview()){
            // A deliberate scale drag commits only its latest selection, not queued intermediate frames.
            float selected=doubleTapScale.selected();boolean keepZoom=doubleTapScale.shouldLatch();
            cancelTapSequence();endGesture(true);momentary.end();
            latch.setLatched(keepZoom);
            if(keepZoom){
                rememberedScale=selected;prefs.edit().putFloat("scale",selected).apply();
                apply(selected,initialCenterX,initialCenterY,false);
            }else{
                zoomHaptics.requestRelease(isReportedZoomed());
                ownership.commandOff();magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());
            }
            if(bubble!=null)bubble.invalidate();
        }else{
            cancelTapSequence();
            if(momentary.ownsPreview() && ControllerDragMode.stopsPreview(latch.isLatched(),preserveZoomAtDown))zoomHaptics.requestRelease(isReportedZoomed());
            endGesture();
        }
    }
    private void endGesture() { endGesture(false); }
    private void endGesture(boolean preservePreview) {
        boolean restoreSize=relocating;dragMode.end();handler.removeCallbacks(relocateZoomed);
        active=false;setRefreshVote(false);Choreographer.getInstance().removeFrameCallback(cadenceFrame); pointerId=-1; relocating=false; hasPending=false;
        if(restoreSize)applyBubbleTransform();
        scaleGate.reset();handler.removeCallbacks(scaleTimeout);
        handler.removeCallbacks(activatePreview); Choreographer.getInstance().removeFrameCallback(frame); frameQueued=false;
        if(!preservePreview)stopPreview();
        if(pad!=null){pad.visible=false;pad.invalidate();} if(bubble!=null)bubble.invalidate();
        handler.removeCallbacks(fade); handler.postDelayed(fade,1800);
    }
    private void cancelGesture(){cancelGesture(true);}
    private void cancelGesture(boolean dismissMenu){
        cancelMenuScaleUpdates();
        cancelTapSequence();zoomHaptics.cancel();
        finishPhase("lifecycle cancel",SystemClock.uptimeMillis(),-1);trace("CANCEL gesture");
        if(dismissMenu)dismissControllerMenu();
        endGesture();
    }
    private void geometryChanged() {long queryStart=SystemClock.uptimeMillis();try{ if(wm==null || destroyed) return;
        WindowMetrics metrics=wm.getCurrentWindowMetrics(); Rect b=metrics.getBounds();
        float nextDensity=getResources().getDisplayMetrics().density;
        Insets nextInsets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        if(b.width()==width && b.height()==height && density==nextDensity && nextInsets.equals(lastInsets)
            && rotation==displays.getDisplay(Display.DEFAULT_DISPLAY).getRotation())return;
        cancelGesture(); density=nextDensity;
        diameter=Math.round(56*density); bubbleParams.width=diameter; bubbleParams.height=diameter; updateGeometry();
        if(bubbleHost!=null)relayoutHost(bubbleHost,diameter,diameter);
        if(padControl!=null){pad.invalidate();} }finally{probe.span("geometry callback duration",queryStart,SystemClock.uptimeMillis());}}
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); cancelGesture(); geometryChanged(); }
    @Override public void onDisplayChanged(int id) { if(id==android.view.Display.DEFAULT_DISPLAY){if(probe.recording())sampleReportedRefresh();geometryChanged();} }
    @Override public void onDisplayAdded(int id) { }
    @Override public void onDisplayRemoved(int id) { if(id==android.view.Display.DEFAULT_DISPLAY) releaseAllZoom(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { releaseAllZoom(); }
    @Override public void onDestroy() {
        destroyed=true;insetQueue.stop(); current=null; releaseAllZoom(); endDiagnostic(); handler.removeCallbacksAndMessages(null);
        if(magnifier!=null)magnifier.removeListener(magnificationChanged);
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        if(displays!=null)displays.unregisterDisplayListener(this);
        try { unregisterReceiver(screenOff); } catch(IllegalArgumentException ignored) { }
        overlayReady=false;starting=false;startupGeneration++;cleanupOverlayResources();
        super.onDestroy();
    }
    private final class Bubble extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Bubble(){super(ZoomService.this);setClickable(true);updateDescription();}
        private String actionDescription(int count){
            int action=ControllerPreferences.action(prefs,count);
            return action==ControllerSettings.MENU?"show menu":action==ControllerSettings.ZOOM?"turn zoom on or off":"none";
        }
        void updateDescription(){
            setContentDescription("Zoom controller. Tap: "+actionDescription(1)+". Double tap: "+actionDescription(2)
                +". Triple tap: "+actionDescription(3)+". When zoom is off, drag to move the controller; hold then drag to zoom and pan. When zoom is on, drag to pan; hold then drag to move the controller."
                +(ControllerPreferences.adjustZoom(prefs)?" Double-tap and keep your finger down on the second tap, then slide up or down to adjust zoom.":""));
        }
        @Override public boolean performClick(){
            super.performClick();
            if(!active && !menuIsShowing()){
                reconcileIdleLatch();cancelGesture();pendingTapWasZoomed=isReportedZoomed();dispatchTapAction(1);
            }
            setAlpha(1);handler.removeCallbacks(fade);handler.postDelayed(fade,1800);return true;
        }
        @Override protected void onDraw(Canvas c){
            // Draw nothing, but retain this view/surface and its touch stream.
            // The expanded rectangular pad is a separate surface and remains visible.
            boolean committedPreview=momentary.ownsPreview() && (!zoomGesture || scaleGestureClaimed);
            if(!bubbleAppearance.shouldDrawArtwork(active && dragMode.hidesBubble(committedPreview),relocating))return;
            float r=getWidth()/2f;
            // Original Orbit artwork: a high-contrast C-shaped ring and thumb-point dot.
            // The two-tone perimeter remains visible on both light and dark content.
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(38,38,38));
            c.drawCircle(r,r,r-2*density,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(Color.BLACK);
            c.drawCircle(r,r,r-density,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(ControllerPreferences.color(prefs));
            c.drawCircle(r,r,r-3*density,paint);
            paint.setStrokeCap(Paint.Cap.BUTT);paint.setStrokeWidth(r*OrbitArtwork.RING_STROKE);
            float orbit=r*OrbitArtwork.RING_RADIUS;
            c.drawArc(r-orbit,r-orbit,r+orbit,r+orbit,
                    OrbitArtwork.START_DEGREES,OrbitArtwork.SWEEP_DEGREES,false,paint);
            paint.setStyle(Paint.Style.FILL);c.drawCircle(r,r,r*OrbitArtwork.DOT_RADIUS,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            long arrival=(probe.capturePending() || diagnostic.mayRecord())?SystemClock.uptimeMillis():0;
            if(diagnosticEnabled())trace("input t="+e.getEventTime()+" arrival="+arrival+" lag="+(arrival-e.getEventTime())+" downTime="+e.getDownTime()+" flags="+e.getFlags()+" history="+e.getHistorySize()+" action="+e.getActionMasked()+" raw="+e.getRawX()+","+e.getRawY()+" local="+e.getX()+","+e.getY()+" second="+zoomGesture+" preview="+momentary.ownsPreview());
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float r=getWidth()/2f;if(Math.hypot(e.getX()-r,e.getY()-r)>r)return false;
                begin(e,arrival);return true;}
            captureInput(e,arrival);
            if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){cancelGesture();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){move(e,arrival);return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                probe.mark("UP/CANCEL arrival",arrival,e.getEventTime());
                if(active && isTap(e))performClick();
                finish(e);finishPhase("UP",SystemClock.uptimeMillis(),e.getEventTime());return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){probe.mark("UP/CANCEL arrival",arrival,e.getEventTime());cancelGesture();return true;}return true;
        }
    }
    private final class Pad {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final HandlerThread renderThread=new HandlerThread("TouchZoomPad");
        final Handler renderer;
        final LatestRenderQueue<PadSnapshot> renderQueue=new LatestRenderQueue<>();
        boolean visible,stopped;float fingerX,fingerY;
        final Surface ownedSurface;final SurfaceControl ownedControl;
        Pad(Surface surface,SurfaceControl control){ownedSurface=surface;ownedControl=control;renderThread.start();renderer=new Handler(renderThread.getLooper());}
        int bufferW,bufferH;float positionedX=Float.NaN,positionedY=Float.NaN;boolean shown;
        void invalidate(){
            PadSnapshot snapshot=new PadSnapshot(visible && active && dragMode.showsPad(momentary.ownsPreview()),padLeft,padTop,padWidth,padHeight,fingerX-padLeft,fingerY-padTop,density,panStarted,probe.generation());
            if(stopped)return;
            if(snapshot.visible && probe.recording())probe.mark(panStarted?"tracking marker snapshot offered":"initial pad snapshot offered",SystemClock.uptimeMillis(),-1);
            if(renderQueue.offer(snapshot))renderer.post(this::renderLatest);
            // Only the input thread changes visibility/position. An old worker draw cannot reshow a hidden pad.
            if(padControl!=null){
                int w=Math.max(1,(int)Math.ceil(snapshot.w)),h=Math.max(1,(int)Math.ceil(snapshot.h));
                if(w!=bufferW || h!=bufferH || snapshot.x!=positionedX || snapshot.y!=positionedY || shown!=snapshot.visible){
                    transact(t->t.setBufferSize(padControl,w,h).setPosition(padControl,snapshot.x,snapshot.y).setVisibility(padControl,snapshot.visible));
                    bufferW=w;bufferH=h;positionedX=snapshot.x;positionedY=snapshot.y;shown=snapshot.visible;
                }
            }
        }
        void renderLatest(){
            PadSnapshot s=renderQueue.take();Surface drawSurface=ownedSurface;
            // No UI-thread lock is held while Canvas waits for a buffer.
            if(s!=null && s.visible && drawSurface!=null && drawSurface.isValid()){
                boolean measuring=probe.recording() && s.probeGeneration==probe.generation();
                String prefix=measuring?(s.tracking?"tracking":"initial"):"";
                if(measuring)probe.markFor(s.probeGeneration,prefix+" renderer entry",SystemClock.uptimeMillis(),-1);
                Canvas c=null;long lockStart=measuring?SystemClock.uptimeMillis():0;
                try{
                    if(measuring)probe.markFor(s.probeGeneration,prefix+" canvas lock start",lockStart,-1);
                    c=drawSurface.lockCanvas(null);if(measuring){long locked=SystemClock.uptimeMillis();probe.markFor(s.probeGeneration,prefix+" canvas lock end",locked,-1);probe.spanFor(s.probeGeneration,"canvas lock duration",lockStart,locked);}c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                    paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(150,25,33,48));
                    c.drawRoundRect(0,0,s.w,s.h,12*s.density,12*s.density,paint);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*s.density);paint.setColor(Color.rgb(140,210,255));
                    c.drawRoundRect(0,0,s.w,s.h,12*s.density,12*s.density,paint);
                    paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawCircle(s.fingerX,s.fingerY,5*s.density,paint);
                }catch(Surface.OutOfResourcesException | IllegalArgumentException ignored){}
                finally{if(c!=null){long posted=measuring?SystemClock.uptimeMillis():0;if(measuring)probe.markFor(s.probeGeneration,prefix+" canvas post start",posted,-1);drawSurface.unlockCanvasAndPost(c);if(measuring){long done=SystemClock.uptimeMillis();probe.markFor(s.probeGeneration,prefix+" canvas post end",done,-1);probe.spanFor(s.probeGeneration,"canvas post duration",posted,done);synchronized(probe){if(probe.recording() && s.probeGeneration==probe.generation())cadence.tick(CadenceStats.PAD_POST,System.nanoTime());}}}}
            }
            if(renderQueue.complete())renderer.post(this::renderLatest);
        }
        void shutdown(){
            if(stopped)return;stopped=true;renderQueue.stop();renderer.removeCallbacksAndMessages(null);
            final Surface oldSurface=ownedSurface;final SurfaceControl oldControl=ownedControl;
            if(padSurface==oldSurface)padSurface=null;if(padControl==oldControl)padControl=null;
            if(oldControl!=null){try{transact(t->t.setVisibility(oldControl,false).reparent(oldControl,null));}catch(RuntimeException ignored){}}
            // Old renderer cleanup captures only old resources; retry cannot release a new overlay.
            renderer.post(()->{
                if(oldSurface!=null)oldSurface.release();
                if(oldControl!=null)oldControl.release();
                renderThread.quitSafely();
            });
        }
    }
    private static final class PadSnapshot {
        final int probeGeneration;final boolean visible,tracking;final float x,y,w,h,fingerX,fingerY,density;
        PadSnapshot(boolean v,float x,float y,float w,float h,float fx,float fy,float d,boolean tracking,int probeGeneration){this.probeGeneration=probeGeneration;this.tracking=tracking;visible=v;this.x=x;this.y=y;this.w=w;this.h=h;fingerX=fx;fingerY=fy;density=d;}
    }
}
