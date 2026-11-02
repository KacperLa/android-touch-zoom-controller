package io.github.kacperla.touchzoom;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.*;
import android.provider.Settings;
import android.widget.*;

public final class MainActivity extends Activity {
    private TextView status, sizeValue;
    private final Runnable refreshStatus=new Runnable(){public void run(){
        if(status==null)return;
        String message=ZoomService.current==null?"Service is off. Enable Touch Zoom Controller in accessibility settings.":ZoomService.current.controllerStatus();
        if(!android.text.TextUtils.equals(status.getText(),message))status.setText(message);
        status.postDelayed(this,500);
    }};
    private SeekBar sizeSlider, delaySlider, opacitySlider;
    private TextView delayValue, opacityValue;
    private SharedPreferences preferences;
    private SizePreview preview;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(24 * getResources().getDisplayMetrics().density); layout.setPadding(p,p,p,p);
        scroll.addView(layout); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Touch Zoom Controller\nPrototype 0.22"); title.setTextSize(28); layout.addView(title);
        status = new TextView(this); status.setTextSize(18); layout.addView(status);
        button(layout,"Retry controller startup",()->{if(ZoomService.current!=null){ZoomService.current.retryStartup();status.setText(ZoomService.current.controllerStatus());}});
        addSizeSettings(layout);
        addDelaySettings(layout);
        addOpacitySettings(layout);
        TextView help = new TextView(this); help.setTextSize(18);
        help.setText("\nControls\n• Drag immediately to move the circle; it grows to 150% while relocating and returns on release. A short 100 ms pause distinguishes holding from dragging.\n• Hold still for the short pause plus your extra delay (1 second total by default), then drag to zoom and pan. Release to zoom out unless persistent zoom is on. Light system haptics mark zoom engagement and release-to-unzoom.\n• Quick tap before zoom engages to toggle persistent zoom on/off (confirmed after the double-tap interval). At zero extra, release within 100 ms for a tap.\n• Double-tap, keep the second touch down, and slide up/down to adjust the remembered zoom level. Release keeps zoom on only when toggled on.\n• Zero extra delay still keeps the short 100 ms pause. Moving the circle does not change persistent zoom.\n\nThe controller temporarily lifts above a docked keyboard when covered, then returns when it closes. Floating keyboards may not report a usable height.\n\nThe circle hides during active zoom panning; the pad stays visible. It returns when your finger lifts so you can tap persistent zoom off. Its idle opacity is adjustable in settings above.\n\nThe pad keeps the screen’s aspect ratio and anchors to the current viewport. Near an edge it may extend off-screen: move the circle inward for full travel.\n\nPrivacy\nThis service controls magnification and displays an overlay. It does not read screen content, capture the screen, send network traffic or inject taps. Android asks you to enable the accessibility service yourself.\n\nPrototype limitations\nFull-screen zoom only, Android 14+. Folding/rotation cancels the current gesture and repositions the circle. Real-device testing of these changes is still required.\n"); layout.addView(help);
        button(layout,"Record continuous 15-second diagnostic",()->{
            if(ZoomService.current!=null){ZoomService.current.startDiagnostic();Toast.makeText(this,"Recording for 15 seconds across all gestures. View or Copy the current capture afterward. No screen content recorded.",Toast.LENGTH_LONG).show();}
        });
        button(layout,"Clear diagnostics",()->{if(ZoomService.current!=null)ZoomService.current.clearDiagnostic();});
        button(layout,"View last controller diagnostics",()->{
            TextView report=new TextView(this);report.setTextIsSelectable(true);report.setText(ZoomService.current==null ? "Service is off; in-memory diagnostics are unavailable." : ZoomService.current.diagnosticReport());
            ScrollView scroller=new ScrollView(this);scroller.addView(report);
            android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Local controller diagnostics").setView(scroller).setPositiveButton("Close",null).setNegativeButton("Refresh",null).setNeutralButton("Copy diagnostic text",(d,which)->{
                String latest=ZoomService.current==null?"Service is off; diagnostics unavailable.":ZoomService.current.diagnosticReport();
                report.setText(latest);
                android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Touch Zoom diagnostic",latest));
            }).create();
            dialog.setOnShowListener(d->dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->report.setText(ZoomService.current==null?"Service is off; diagnostics unavailable.":ZoomService.current.diagnosticReport())));
            dialog.show();
        });
        button(layout,"Open accessibility settings", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(layout,"Turn full-screen magnification off", () -> { if (ZoomService.current != null) ZoomService.current.turnOff(); });
        button(layout,"Disable controller service", () -> { if (ZoomService.current != null) ZoomService.current.disableSelf(); });
    }
    private void addSizeSettings(LinearLayout layout) {
        preferences=getSharedPreferences("controller",MODE_PRIVATE);
        TextView label=new TextView(this); label.setText(R.string.pad_size_title);label.setTextSize(23);
        label.setAccessibilityHeading(true); layout.addView(label);
        sizeValue=new TextView(this);sizeValue.setTextSize(20);layout.addView(sizeValue);
        TextView explanation=new TextView(this);explanation.setTextSize(17);
        explanation.setText(R.string.pad_size_help);layout.addView(explanation);
        sizeSlider=new SeekBar(this);sizeSlider.setMin(PadSettings.MIN_DP);sizeSlider.setMax(PadSettings.MAX_DP);
        sizeSlider.setKeyProgressIncrement(PadSettings.STEP_DP);sizeSlider.setContentDescription("Expanded touchpad long-edge size");
        sizeSlider.setMinimumHeight(dp(48));layout.addView(sizeSlider);
        preview=new SizePreview();layout.addView(preview,new LinearLayout.LayoutParams(-1,dp(180)));
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);layout.addView(buttons);
        Button smaller=new Button(this);smaller.setText(R.string.smaller);smaller.setOnClickListener(v->setSize(sizeSlider.getProgress()-20));buttons.addView(smaller,new LinearLayout.LayoutParams(0,-2,1));
        Button larger=new Button(this);larger.setText(R.string.larger);larger.setOnClickListener(v->setSize(sizeSlider.getProgress()+20));buttons.addView(larger,new LinearLayout.LayoutParams(0,-2,1));
        button(layout,"Reset size to 180 dp",()->setSize(PadSettings.DEFAULT_DP));
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){if(fromUser)setSize(progress);}
            @Override public void onStartTrackingTouch(SeekBar bar){ }
            @Override public void onStopTrackingTouch(SeekBar bar){ }
        });
        setSize(preferences.getInt(PadSettings.KEY,PadSettings.DEFAULT_DP));
    }
    private void addDelaySettings(LinearLayout layout){
        TextView heading=new TextView(this);heading.setText(R.string.activation_title);heading.setTextSize(23);heading.setAccessibilityHeading(true);layout.addView(heading);
        delayValue=new TextView(this);delayValue.setTextSize(20);layout.addView(delayValue);
        TextView help=new TextView(this);help.setTextSize(17);help.setText(R.string.activation_help);layout.addView(help);
        delaySlider=new SeekBar(this);delaySlider.setMin(ActivationSettings.MIN_MS);delaySlider.setMax(ActivationSettings.MAX_MS);delaySlider.setKeyProgressIncrement(ActivationSettings.STEP_MS);
        delaySlider.setMinimumHeight(dp(48));delaySlider.setContentDescription("Extra hold delay in milliseconds");layout.addView(delaySlider);
        delaySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser)setDelay(value);}
            @Override public void onStartTrackingTouch(SeekBar bar){ }
            @Override public void onStopTrackingTouch(SeekBar bar){ }
        });
        button(layout,"Use zero extra delay (100 ms total)",()->setDelay(0));
        button(layout,"Reset extra delay to 900 ms (1 second total)",()->setDelay(ActivationSettings.DEFAULT_MS));
        int initial=preferences.contains(ActivationSettings.KEY)
            ? preferences.getInt(ActivationSettings.KEY,ActivationSettings.DEFAULT_MS)
            : preferences.contains(ActivationSettings.LEGACY_KEY)
                ? ActivationSettings.migratedExtra(preferences.getInt(ActivationSettings.LEGACY_KEY,1000))
                : ActivationSettings.DEFAULT_MS;
        setDelay(initial);
    }
    private void setDelay(int requested){
        int value=ActivationSettings.normalize(requested);delaySlider.setProgress(value);
        String description=getString(R.string.extra_delay_value,value,ActivationSettings.totalMs(value));
        delayValue.setText(description);delaySlider.setStateDescription(description);
        preferences.edit().putInt(ActivationSettings.KEY,value).apply();
    }
    private void addOpacitySettings(LinearLayout layout){
        TextView heading=new TextView(this);heading.setText(R.string.opacity_title);heading.setTextSize(23);heading.setAccessibilityHeading(true);layout.addView(heading);
        opacityValue=new TextView(this);opacityValue.setTextSize(20);layout.addView(opacityValue);
        TextView help=new TextView(this);help.setTextSize(17);help.setText(R.string.opacity_help);layout.addView(help);
        opacitySlider=new SeekBar(this);opacitySlider.setMin(OpacitySettings.MIN_PERCENT);opacitySlider.setMax(OpacitySettings.MAX_PERCENT);
        opacitySlider.setKeyProgressIncrement(OpacitySettings.STEP_PERCENT);opacitySlider.setMinimumHeight(dp(48));
        opacitySlider.setContentDescription("Idle circle opacity in percent");layout.addView(opacitySlider);
        opacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser)setOpacity(value);}
            @Override public void onStartTrackingTouch(SeekBar bar){ }
            @Override public void onStopTrackingTouch(SeekBar bar){ }
        });
        button(layout,"Reset idle opacity to 40 percent",()->setOpacity(OpacitySettings.DEFAULT_PERCENT));
        setOpacity(preferences.getInt(OpacitySettings.KEY,OpacitySettings.DEFAULT_PERCENT));
    }
    private void setOpacity(int requested){
        int value=OpacitySettings.normalize(requested);opacitySlider.setProgress(value);
        opacityValue.setText(getString(R.string.opacity_value,value));
        opacitySlider.setStateDescription(getString(R.string.percent_spoken,value));
        preferences.edit().putInt(OpacitySettings.KEY,value).apply();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void setSize(int requested){
        int value=PadSettings.normalize(requested);sizeSlider.setProgress(value);
        sizeValue.setText(getString(R.string.pad_size_value,value));sizeSlider.setStateDescription(getString(R.string.dp_value,value));
        preferences.edit().putInt(PadSettings.KEY,value).apply();preview.invalidate();
        preview.setContentDescription("Relative size preview: touchpad long edge "+value+" dp; same screen aspect ratio");
    }
    private final class SizePreview extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        SizePreview(){super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        @Override protected void onDraw(Canvas canvas){
            Rect screen=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            float ratio=Math.min((getWidth()-dp(24))/(float)screen.width(),(getHeight()-dp(24))/(float)screen.height());
            float w=screen.width()*ratio,h=screen.height()*ratio,x=(getWidth()-w)/2,y=(getHeight()-h)/2;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(230,235,242));canvas.drawRoundRect(x,y,x+w,y+h,dp(8),dp(8),paint);
            float longSide=PadSettings.longSidePixels(sizeSlider.getProgress(),getResources().getDisplayMetrics().density,screen.width(),screen.height());
            float[] pad=Mapping.padSize(screen.width(),screen.height(),longSide);float pw=pad[0]*ratio,ph=pad[1]*ratio;
            paint.setColor(Color.rgb(40,110,180));canvas.drawRoundRect((getWidth()-pw)/2,(getHeight()-ph)/2,(getWidth()+pw)/2,(getHeight()+ph)/2,dp(3),dp(3),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Color.rgb(65,75,90));canvas.drawRoundRect(x,y,x+w,y+h,dp(8),dp(8),paint);
        }
    }
    private void button(LinearLayout layout, String text, Runnable action) { Button b = new Button(this); b.setText(text); b.setOnClickListener(v -> action.run()); layout.addView(b); }
    @Override protected void onResume(){super.onResume();status.removeCallbacks(refreshStatus);status.post(refreshStatus);}
    @Override protected void onPause(){status.removeCallbacks(refreshStatus);super.onPause();}
}
