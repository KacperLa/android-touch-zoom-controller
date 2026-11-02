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
    static ZoomService current;
    private WindowManager wm;
    private SurfaceControlViewHost bubbleHost;
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
    private float lastTapX, lastTapY, moveStartX, moveStartY, pendingX, pendingY, pendingScale;
    private float grabX,grabY;
    private long downTime, lastTapTime;
    private boolean panStarted;
    private boolean moved, zoomGesture, relocating, active, frameQueued, hasPending, destroyed, failureShown;
    private final UpdatePacer pacer=new UpdatePacer();
    private final ZoomHaptics zoomHaptics=new ZoomHaptics();
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
        return "Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"; "+Build.MANUFACTURER+" "+Build.MODEL+"\nSnapshot uptime="+now+" ms. Refresh or Copy reads the current capture.\n"+captureSession.report(now)+"\n"+cadenceDisplayInfo+"\nReported during capture="+reportedMin+"–"+reportedMax+" Hz (not presented FPS).\n"+cadence.report()+"\n"+(phaseOpen?"Current gesture:\n"+probe.report():"")+"\n"+inputArrival.report()+"\nContinuous detailed events (bounded latest 8192; all gestures within the capture window):\n"+diagnostic.report();
    }
    private void trace(String event){if(diagnosticEnabled())diagnostic.add(event);}

    private final Rect region = new Rect();
    private final MomentaryZoom momentary = new MomentaryZoom();
    private final ZoomLatch latch=new ZoomLatch();
    private final Runnable singleTap=()->{if(!active && latch.hasPendingTap()){latch.cancelPendingTap();toggleLatch();}};
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
    };
    private final Runnable activatePreview = () -> {
        if(!active || relocating || destroyed)return;
        probe.mark("hold timer / activation entry",SystemClock.uptimeMillis(),-1);
        pad.visible=!zoomGesture;
        momentary.begin(initialScale);
        if(!zoomGesture)anchorPad(initialCenterX,initialCenterY);
        boolean redrawPending=hasPending;
        float targetScale=hasPending ? pendingScale : initialScale;
        momentary.select(targetScale);
        zoomHaptics.requestEngage(isReportedZoomed());
        probe.mark("activation call start",SystemClock.uptimeMillis(),-1);
        apply(targetScale,hasPending ? pendingX : initialCenterX,hasPending ? pendingY : initialCenterY,false);
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
                if(zoomGesture)apply(pendingScale,pendingX,pendingY,false);
                else applyCenter(pendingX,pendingY);
            }
        }
    };
    private void queueFrame(){
        if(!frameQueued){frameQueued=true;if(probe.recording()){frameScheduledAt=SystemClock.uptimeMillis();probe.mark("tracking frame scheduled",frameScheduledAt,-1);}Choreographer.getInstance().postFrameCallback(frame);}
    }
    private final Runnable checkKeyboard = new Runnable() {
        @Override public void run() {
            if(destroyed || wm==null || power==null || !power.isInteractive())return;
            long metricsStart=SystemClock.uptimeMillis();
            WindowInsets insets=wm.getCurrentWindowMetrics().getWindowInsets();
            probe.span("keyboard metrics query",metricsStart,SystemClock.uptimeMillis());
            boolean visible=insets.isVisible(WindowInsets.Type.ime());
            int bottom=insets.getInsets(WindowInsets.Type.ime()).bottom;
            if(visible!=imeVisible || bottom!=imeBottom){
                imeVisible=visible;imeBottom=bottom;
                Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                float desiredX=relocating ? bubbleParams.x : normalizedX*width-diameter/2f;
                float desiredY=relocating ? bubbleParams.y : normalizedY*height-diameter/2f;
                float scale=DragVisual.scale(relocating),offset=DragVisual.offset(grabY,scale);
                float targetY=KeyboardAvoidance.top(desiredY-offset,diameter*scale,height,
                    safe.top,safe.bottom,visible,bottom,8*density)+offset;
                // Moving the touch target under an active finger would invalidate raw coordinates.
                if(Math.round(targetY)!=bubbleParams.y){
                    if(active || latch.hasPendingTap())cancelGesture();
                    place(desiredX,desiredY);
                }
            }
            handler.postDelayed(this,250);
        }
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(checkKeyboard);
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))releaseAllZoom();
            else if(Intent.ACTION_SCREEN_ON.equals(intent.getAction()))handler.post(checkKeyboard);
        }
    };

    @Override public void onServiceConnected() {
        if (bubbleHost != null) return;
        current = this; destroyed = false;
        prefs = getSharedPreferences("controller", MODE_PRIVATE);
        if(!prefs.contains(ActivationSettings.KEY))prefs.edit().putInt(ActivationSettings.KEY,
            ActivationSettings.migratedExtra(prefs.getInt(ActivationSettings.LEGACY_KEY,1000))).apply();
        normalizedX = prefs.getFloat("x", .82f); normalizedY = prefs.getFloat("y", .72f);
        rememberedScale = Mapping.clamp(prefs.getFloat("scale",2),1.1f,8);
        wm = getSystemService(WindowManager.class); displays = getSystemService(DisplayManager.class);
        power=getSystemService(PowerManager.class);
        magnifier = getMagnificationController(); density = getResources().getDisplayMetrics().density;
        diameter = Math.round(56 * density);
        pad = new Pad(); bubble = new Bubble();
        bubbleParams = params(diameter,diameter);
        updateGeometry();
        Display display = displays.getDisplay(Display.DEFAULT_DISPLAY);
        padControl = new SurfaceControl.Builder().setName("Touch Zoom Pad").setBufferSize(Math.max(1,(int)Math.ceil(padWidth)),Math.max(1,(int)Math.ceil(padHeight)))
            .setFormat(PixelFormat.RGBA_8888).setHidden(false).build();
        padSurface = new Surface(padControl);
        attachAccessibilityOverlayToDisplay(Display.DEFAULT_DISPLAY,padControl);
        bubbleHost = new SurfaceControlViewHost(this, display, (IBinder)null);
        bubbleHost.setView(bubble,diameter,diameter); bubblePackage=bubbleHost.getSurfacePackage();
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
        handler.post(checkKeyboard);
        handler.postDelayed(fade,1800);
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
        int feedback=zoomHaptics.observe(isReportedZoomed());
        if(bubble!=null && feedback!=ZoomHaptics.NONE)bubble.performHapticFeedback(
            feedback==ZoomHaptics.ENGAGE?HapticFeedbackConstants.GESTURE_START:HapticFeedbackConstants.GESTURE_END);
        if(active && !zoomGesture && momentary.ownsPreview() && !panStarted && state.isActivated()
                && Float.isFinite(state.getCenterX()) && Float.isFinite(state.getCenterY())){
            anchorPad(state.getCenterX(),state.getCenterY());if(pad!=null && pad.visible)pad.invalidate();
        }
        observeScale(state);
        if(diagnosticEnabled())trace("state t="+SystemClock.uptimeMillis()+" active="+state.isActivated()+" scale="+state.getScale()+" cx="+state.getCenterX()+" cy="+state.getCenterY());
        if(bubbleAppearance.update(state.isActivated()) && bubble!=null)bubble.invalidate();
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
    private void applyCenter(float x,float y){
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
        if(diagnosticEnabled())trace("request t="+SystemClock.uptimeMillis()+" ON scale="+scale+" cx="+x+" cy="+y);
        boolean gated=active && zoomGesture;
        if(gated)scaleGate.sent(scale);
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
    public void turnOff() { releaseAllZoom();if(magnifier!=null){magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());} }
    private void releaseAllZoom(){
        boolean owned=latch.isLatched() || momentary.ownsPreview() || latch.hasPendingTap();
        latch.clear();cancelGesture();
        if(owned && magnifier!=null){magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());}
    }
    private void toggleLatch(){
        if(momentary.ownsPreview()){rememberedScale=momentary.selectedScale();momentary.end();prefs.edit().putFloat("scale",rememberedScale).apply();}
        if(latch.toggle()){
            zoomHaptics.requestEngage(isReportedZoomed());apply(rememberedScale,centerX(),centerY(),false);
        }else if(magnifier!=null){
            zoomHaptics.requestRelease(isReportedZoomed());magnifier.resetCurrentMagnification(false);refreshBubbleArtwork(config());
        }
        if(bubble!=null)bubble.invalidate();
    }
    private void stopPreview() {
        if (!momentary.end()) return;
        trace("end preview latched="+latch.isLatched());
        rememberedScale=momentary.selectedScale();
        if(prefs!=null)prefs.edit().putFloat("scale",rememberedScale).apply();
        if(magnifier!=null && !latch.isLatched()){
            magnifier.setMagnificationConfig(new MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN).setActivated(false).setScale(1).build(),false);
            refreshBubbleArtwork(config());
        }
    }
    private void reconcileIdleLatch(){
        // Read fresh state only before a new interaction, not during async enable callbacks.
        if(!active && !latch.hasPendingTap() && !momentary.ownsPreview() && latch.isLatched()){
            MagnificationConfig currentState=config();
            if(currentState!=null && !currentState.isActivated())latch.clear();
        }
    }
    private void begin(MotionEvent e,long arrival) {
        if(active)cancelGesture();
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
        active=true;setRefreshVote(true); bubble.invalidate(); failureShown=false; pointerId=e.getPointerId(0); moved=false; relocating=false; hasPending=false;
        downX=e.getRawX(); downY=e.getRawY(); downTime=e.getEventTime();
        grabX=Mapping.clamp(downX-bubbleParams.x,0,diameter);grabY=Mapping.clamp(downY-bubbleParams.y,0,diameter);
        zoomGesture=latch.hasPendingTap() && lastTapTime>0 && downTime-lastTapTime <= ViewConfiguration.getDoubleTapTimeout()
            && Math.hypot(downX-lastTapX,downY-lastTapY)<diameter;
        if(probe.recording())probe.mark(zoomGesture?"second-tap scale gesture":"ordinary first-touch gesture",SystemClock.uptimeMillis(),-1);
        handler.removeCallbacks(singleTap);
        if(latch.hasPendingTap()){
            latch.cancelPendingTap();
            if(!zoomGesture)toggleLatch(); // Commit an older/non-nearby single tap before this gesture.
        }
        if(zoomGesture) lastTapTime=0;
        MagnificationConfig initial=config();lastReportedConfig=initial;
        initialScale=initial!=null && initial.isActivated() && Float.isFinite(initial.getScale()) && initial.getScale()>1.01f ? initial.getScale() : rememberedScale;
        initialCenterX=initial!=null && Float.isFinite(initial.getCenterX()) ? initial.getCenterX() : width/2f;
        initialCenterY=initial!=null && Float.isFinite(initial.getCenterY()) ? initial.getCenterY() : height/2f;
        moveStartX=bubbleParams.x; moveStartY=bubbleParams.y;
        int delay=ActivationSettings.totalMs(prefs.getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS));
        // Include preparation in the deadline; no extra platform long-press timeout.
        // Second-tap scale preview remains a distinct immediate gesture.
        if(zoomGesture){pad.visible=false;activatePreview.run();}
        else {probe.mark("hold deadline",UpdatePacer.holdDeadline(downTime,delay),-1);handler.postAtTime(activatePreview,UpdatePacer.holdDeadline(downTime,delay));}
        region.set(magnifier.getMagnificationRegion().getBounds());
        if(region.isEmpty()) region.set(0,0,width,height);
        float nx=Mapping.normalized(initialCenterX,region.left,region.right-1);
        float ny=Mapping.normalized(initialCenterY,region.top,region.bottom-1);
        padLeft=downX-nx*padWidth; padTop=downY-ny*padHeight;
        pad.fingerX=downX; pad.fingerY=downY; pad.visible=momentary.ownsPreview() && !zoomGesture;pad.invalidate();
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
        if(relocating){place(moveStartX+x-downX,moveStartY+y-downY);return;}
        if(GestureRules.waitingForHold(zoomGesture,momentary.ownsPreview())){
            // Anchor zoom to the latest stationary finger, not stale DOWN jitter.
            pad.fingerX=x;pad.fingerY=y;
            if(moved){
                relocating=true;lastTapTime=0;handler.removeCallbacks(activatePreview);
                place(moveStartX+x-downX,moveStartY+y-downY);bubble.invalidate();
            }
            return; // No pre-hold pan. A relocation can never turn into zoom later.
        }
        // Once the hold engages zoom, every move pans; slop only classifies taps.
        if(zoomGesture && !moved)return;
        if(zoomGesture) {
            pendingScale=Mapping.zoom(initialScale,downY-y,padHeight/2); momentary.select(pendingScale); pendingX=initialCenterX; pendingY=initialCenterY;
        } else {
            panStarted=true;
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
        return GestureRules.tapBeforeHold(moved,relocating,e.getEventTime()-downTime,
            zoomGesture,momentary.ownsPreview(),ActivationSettings.totalMs(
                prefs.getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS)));
    }
    private void finish(MotionEvent e) {
        if (!active) return;
        // Ignore ACTION_UP coordinates: they often jump during finger lift.
        // Do not flush a queued zoom frame on release. End first, then reset.
        hasPending=false;
        boolean tap=isTap(e);
        if(relocating && moved) {
            normalizedX=(bubbleParams.x+diameter/2f)/width; normalizedY=(bubbleParams.y+diameter/2f)/height;
            prefs.edit().putFloat("x",normalizedX).putFloat("y",normalizedY).apply();
        }
        if(tap && zoomGesture){
            lastTapTime=0;endGesture(true);toggleLatch(); // End scale gate before independent tap-toggle write.
        }else if(tap){
            lastTapTime=e.getEventTime();lastTapX=downX;lastTapY=downY;
            latch.queueTap();endGesture(true);
            handler.postDelayed(singleTap,ViewConfiguration.getDoubleTapTimeout());
        }else{
            lastTapTime=0;
            if(momentary.ownsPreview() && !latch.isLatched())zoomHaptics.requestRelease(isReportedZoomed());
            endGesture();
        }
    }
    private void endGesture() { endGesture(false); }
    private void endGesture(boolean preservePreview) {
        boolean restoreSize=relocating;
        active=false;setRefreshVote(false);Choreographer.getInstance().removeFrameCallback(cadenceFrame); pointerId=-1; relocating=false; hasPending=false;
        if(restoreSize)applyBubbleTransform();
        scaleGate.reset();handler.removeCallbacks(scaleTimeout);
        handler.removeCallbacks(activatePreview); Choreographer.getInstance().removeFrameCallback(frame); frameQueued=false;
        if(!preservePreview)stopPreview();
        if(pad!=null){pad.visible=false;pad.invalidate();} if(bubble!=null)bubble.invalidate();
        handler.removeCallbacks(fade); handler.postDelayed(fade,1800);
    }
    private void cancelGesture() {zoomHaptics.cancel();finishPhase("lifecycle cancel",SystemClock.uptimeMillis(),-1);trace("CANCEL gesture"); handler.removeCallbacks(singleTap);latch.cancelPendingTap();lastTapTime=0;endGesture(); }
    private void geometryChanged() {long queryStart=SystemClock.uptimeMillis();try{ if(wm==null || destroyed) return;
        WindowMetrics metrics=wm.getCurrentWindowMetrics(); Rect b=metrics.getBounds();
        float nextDensity=getResources().getDisplayMetrics().density;
        Insets nextInsets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        if(b.width()==width && b.height()==height && density==nextDensity && nextInsets.equals(lastInsets)
            && rotation==displays.getDisplay(Display.DEFAULT_DISPLAY).getRotation())return;
        cancelGesture(); density=nextDensity;
        diameter=Math.round(56*density); bubbleParams.width=diameter; bubbleParams.height=diameter; updateGeometry();
        if(bubbleHost!=null)bubbleHost.relayout(diameter,diameter);
        if(padControl!=null){pad.invalidate();} }finally{probe.span("geometry callback duration",queryStart,SystemClock.uptimeMillis());}}
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); geometryChanged(); }
    @Override public void onDisplayChanged(int id) { if(id==android.view.Display.DEFAULT_DISPLAY){if(probe.recording())sampleReportedRefresh();geometryChanged();} }
    @Override public void onDisplayAdded(int id) { }
    @Override public void onDisplayRemoved(int id) { if(id==android.view.Display.DEFAULT_DISPLAY) releaseAllZoom(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { releaseAllZoom(); }
    @Override public void onDestroy() {
        destroyed=true; current=null; releaseAllZoom(); endDiagnostic(); handler.removeCallbacksAndMessages(null);
        if(magnifier!=null)magnifier.removeListener(magnificationChanged);
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        if(displays!=null)displays.unregisterDisplayListener(this);
        try { unregisterReceiver(screenOff); } catch(IllegalArgumentException ignored) { }
        if(bubblePackage!=null) { transact(t -> t.reparent(bubblePackage.getSurfaceControl(),null)); bubblePackage.release(); bubblePackage=null; }
        if(pad!=null)pad.shutdown();
        if(bubbleHost!=null){bubbleHost.release();bubbleHost=null;}
        super.onDestroy();
    }
    private final class Bubble extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Bubble(){super(ZoomService.this);setClickable(true);setContentDescription("Touch zoom: tap to toggle persistent zoom; drag immediately to reposition; hold then drag to pan; double tap and drag to adjust scale");}
        @Override public boolean performClick(){super.performClick();if(!active){reconcileIdleLatch();cancelGesture();toggleLatch();}setAlpha(1);handler.removeCallbacks(fade);handler.postDelayed(fade,1800);return true;}
        @Override protected void onDraw(Canvas c){
            // Draw nothing, but retain this view/surface and its touch stream.
            // The expanded rectangular pad is a separate surface and remains visible.
            if(!bubbleAppearance.shouldDrawArtwork(active && momentary.ownsPreview(),relocating))return;
            float r=getWidth()/2f;
            // Original Orbit artwork: a high-contrast C-shaped ring and thumb-point dot.
            // The two-tone perimeter remains visible on both light and dark content.
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(38,38,38));
            c.drawCircle(r,r,r-2*density,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(Color.BLACK);
            c.drawCircle(r,r,r-density,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(Color.WHITE);
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
                if(active && !zoomGesture && isTap(e))performClick();
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
        Pad(){renderThread.start();renderer=new Handler(renderThread.getLooper());}
        int bufferW,bufferH;float positionedX=Float.NaN,positionedY=Float.NaN;boolean shown;
        void invalidate(){
            PadSnapshot snapshot=new PadSnapshot(visible,padLeft,padTop,padWidth,padHeight,fingerX-padLeft,fingerY-padTop,density,panStarted,probe.generation());
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
            PadSnapshot s=renderQueue.take();
            // No UI-thread lock is held while Canvas waits for a buffer.
            if(s!=null && s.visible && padSurface!=null && padSurface.isValid()){
                boolean measuring=probe.recording() && s.probeGeneration==probe.generation();
                String prefix=measuring?(s.tracking?"tracking":"initial"):"";
                if(measuring)probe.markFor(s.probeGeneration,prefix+" renderer entry",SystemClock.uptimeMillis(),-1);
                Canvas c=null;long lockStart=measuring?SystemClock.uptimeMillis():0;
                try{
                    if(measuring)probe.markFor(s.probeGeneration,prefix+" canvas lock start",lockStart,-1);
                    c=padSurface.lockCanvas(null);if(measuring){long locked=SystemClock.uptimeMillis();probe.markFor(s.probeGeneration,prefix+" canvas lock end",locked,-1);probe.spanFor(s.probeGeneration,"canvas lock duration",lockStart,locked);}c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                    paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(150,25,33,48));
                    c.drawRoundRect(0,0,s.w,s.h,12*s.density,12*s.density,paint);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*s.density);paint.setColor(Color.rgb(140,210,255));
                    c.drawRoundRect(0,0,s.w,s.h,12*s.density,12*s.density,paint);
                    paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawCircle(s.fingerX,s.fingerY,5*s.density,paint);
                }catch(Surface.OutOfResourcesException | IllegalArgumentException ignored){}
                finally{if(c!=null){long posted=measuring?SystemClock.uptimeMillis():0;if(measuring)probe.markFor(s.probeGeneration,prefix+" canvas post start",posted,-1);padSurface.unlockCanvasAndPost(c);if(measuring){long done=SystemClock.uptimeMillis();probe.markFor(s.probeGeneration,prefix+" canvas post end",done,-1);probe.spanFor(s.probeGeneration,"canvas post duration",posted,done);synchronized(probe){if(probe.recording() && s.probeGeneration==probe.generation())cadence.tick(CadenceStats.PAD_POST,System.nanoTime());}}}}
            }
            if(renderQueue.complete())renderer.post(this::renderLatest);
        }
        void shutdown(){
            stopped=true;renderQueue.stop();renderer.removeCallbacksAndMessages(null);
            if(padControl!=null)transact(t->t.setVisibility(padControl,false).reparent(padControl,null));
            // Release after any in-flight worker draw, never join/wait on the input thread.
            renderer.post(()->{
                if(padSurface!=null){padSurface.release();padSurface=null;}
                if(padControl!=null){padControl.release();padControl=null;}
                renderThread.quitSafely();
            });
        }
    }
    private static final class PadSnapshot {
        final int probeGeneration;final boolean visible,tracking;final float x,y,w,h,fingerX,fingerY,density;
        PadSnapshot(boolean v,float x,float y,float w,float h,float fx,float fy,float d,boolean tracking,int probeGeneration){this.probeGeneration=probeGeneration;this.tracking=tracking;visible=v;this.x=x;this.y=y;this.w=w;this.h=h;fingerX=fx;fingerY=fy;density=d;}
    }
}
