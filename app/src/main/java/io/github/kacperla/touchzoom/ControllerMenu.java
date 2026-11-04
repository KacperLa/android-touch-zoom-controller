package io.github.kacperla.touchzoom;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

/** Native menu content, hosted in the same unmagnified display layer as the controller. */
public final class ControllerMenu {
    public interface Actions {
        float scale();
        boolean zoomed();
        boolean setScale(float scale);
        boolean setZoomed(boolean enabled);
        void cancelScaleUpdates();
        void openSettings();
    }
    private final FrameLayout root;
    private final ScrollView panel;
    private final Actions actions;
    private final Runnable dismissed;
    private final SeekBar slider;
    private final Switch enabled;
    private final SharedPreferences preferences;
    private final GradientDrawable panelFill,settingsFill,sliderThumb;
    private final int margin,preferredWidth;
    private boolean showing,syncing,tracking,cancelled;

    ControllerMenu(Context context,Actions actions,Runnable dismissed) {
        this.actions=actions;this.dismissed=dismissed;
        preferences=context.getSharedPreferences("controller",Context.MODE_PRIVATE);
        Context theme=new ContextThemeWrapper(context,android.R.style.Theme_Material);
        float density=theme.getResources().getDisplayMetrics().density;
        int padding=Math.round(20*density),touchHeight=Math.round(56*density);
        margin=Math.round(12*density);preferredWidth=Math.round(360*density);
        root=new FrameLayout(theme);
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);root.setPadding(margin,margin,margin,margin);
        root.setContentDescription(theme.getString(R.string.menu_dismiss));
        root.setOnClickListener(v->dismiss());
        root.setVisibility(View.GONE);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout content=new LinearLayout(theme);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding,padding,padding,padding);
        // Consume blank panel space without treating it as an outside dismissal.
        content.setClickable(true);content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        slider=new MenuSlider(theme);slider.setMin(110);slider.setMax(800);
        slider.setKeyProgressIncrement(10);slider.setContentDescription(theme.getString(R.string.menu_zoom_level));
        slider.setMinimumHeight(Math.round(64*density));
        slider.setMinHeight(Math.round(12*density));slider.setMaxHeight(Math.round(12*density));
        GradientDrawable track=rounded(ControllerMenuStyle.INACTIVE_CONTROL,6*density);
        GradientDrawable progress=rounded(ControllerMenuStyle.TEXT,6*density);
        ClipDrawable clip=new ClipDrawable(progress,Gravity.START,ClipDrawable.HORIZONTAL);
        LayerDrawable trackLayers=new LayerDrawable(new android.graphics.drawable.Drawable[]{track,clip});
        trackLayers.setId(0,android.R.id.background);trackLayers.setId(1,android.R.id.progress);
        slider.setProgressDrawable(trackLayers);
        sliderThumb=rounded(ControllerMenuStyle.TEXT,18*density);
        sliderThumb.setSize(Math.round(36*density),Math.round(36*density));
        sliderThumb.setStroke(Math.max(1,Math.round(2*density)),ControllerMenuStyle.SURFACE);
        slider.setThumb(sliderThumb);slider.setThumbTintList(null);slider.setThumbOffset(Math.round(18*density));
        int thumbInset=Math.round(18*density);
        slider.setPadding(Math.max(slider.getPaddingLeft(),thumbInset),slider.getPaddingTop(),
                Math.max(slider.getPaddingRight(),thumbInset),slider.getPaddingBottom());
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(ControllerMenuStyle.INACTIVE_CONTROL));
        content.addView(slider,new LinearLayout.LayoutParams(-1,-2));
        enabled=new Switch(theme);enabled.setText(R.string.menu_zoom);enabled.setTextSize(20);
        enabled.setTextColor(ControllerMenuStyle.TEXT);enabled.setMinimumHeight(touchHeight);
        enabled.setTrackTintList(new ColorStateList(new int[][]{
            new int[]{android.R.attr.state_checked},new int[]{}
        },new int[]{ControllerMenuStyle.SWITCH_TRACK_ON,ControllerMenuStyle.SWITCH_TRACK_OFF}));
        content.addView(enabled,new LinearLayout.LayoutParams(-1,-2));
        Button settings=new Button(theme);settings.setText(R.string.menu_settings);settings.setTextSize(20);
        settings.setTextColor(ControllerMenuStyle.TEXT);settings.setAllCaps(false);settings.setMinHeight(touchHeight);
        settings.setPadding(padding,Math.round(12*density),padding,Math.round(12*density));
        settingsFill=rounded(ControllerMenuStyle.SURFACE,ControllerMenuStyle.BUTTON_RADIUS_DP*density);
        settings.setBackground(new RippleDrawable(ColorStateList.valueOf(ControllerMenuStyle.RIPPLE),settingsFill,null));
        settings.setBackgroundTintList(null);
        LinearLayout.LayoutParams settingsLayout=new LinearLayout.LayoutParams(-1,-2);
        settingsLayout.topMargin=Math.round(16*density);content.addView(settings,settingsLayout);
        panel=new ScrollView(theme);panel.addView(content);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        panel.setAccessibilityPaneTitle(theme.getString(R.string.menu_title));
        panelFill=rounded(ControllerMenuStyle.SURFACE,ControllerMenuStyle.PANEL_RADIUS_DP*density);
        GradientDrawable outer=rounded(ControllerMenuStyle.OUTER_EDGE,(ControllerMenuStyle.PANEL_RADIUS_DP+2)*density);
        LayerDrawable background=new LayerDrawable(new android.graphics.drawable.Drawable[]{outer,panelFill});
        int edge=Math.round(2*density);background.setLayerInset(1,edge,edge,edge,edge);
        panel.setBackground(background);panel.setClipToOutline(true);panel.setElevation(8*density);
        panel.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS,
                        theme.getString(R.string.menu_dismiss)));
                info.setDismissable(true);
            }
            @Override public boolean performAccessibilityAction(View host,int action,Bundle arguments){
                if(action==AccessibilityNodeInfo.ACTION_DISMISS && showing){dismiss();return true;}
                return super.performAccessibilityAction(host,action,arguments);
            }
        });
        root.addView(panel,new FrameLayout.LayoutParams(preferredWidth,-2,Gravity.CENTER));
        settings.setOnClickListener(v->{dismiss();actions.openSettings();});
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                describe(progress);
                if(fromUser&&!syncing&&showing)commitScale(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar){tracking=true;cancelled=false;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                tracking=false;
                if(!cancelled&&showing)commitScale(bar.getProgress());else sync();
            }
        });
        enabled.setOnCheckedChangeListener((button,checked)->{
            if(showing&&!syncing&&!actions.setZoomed(checked)){failed();sync();}
        });
    }
    private static GradientDrawable rounded(int color,float radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);return shape;
    }
    private void refreshAppearance(){
        // The host persists while Settings is open, so read the latest selection on every show.
        int accent=ControllerPreferences.color(preferences);
        int stroke=Math.max(1,Math.round(2*root.getResources().getDisplayMetrics().density));
        panelFill.setStroke(stroke,accent);settingsFill.setStroke(stroke,accent);
        sliderThumb.setColor(accent);
        slider.setProgressTintList(ColorStateList.valueOf(accent));
        enabled.setThumbTintList(new ColorStateList(new int[][]{
            new int[]{android.R.attr.state_checked},new int[]{}
        },new int[]{accent,ControllerMenuStyle.TEXT}));
    }
    View view(){return root;}
    void setAvailableWidth(int width){
        FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)panel.getLayoutParams();
        layout.width=Math.max(1,Math.min(preferredWidth,width-2*margin));panel.setLayoutParams(layout);
    }
    void show(){
        cancelled=false;tracking=false;refreshAppearance();sync();showing=true;
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        root.setVisibility(View.VISIBLE);
        panel.scrollTo(0,0);
        // Do not request input focus or synthesize Back. The underlying editor keeps it.
    }
    private void commitScale(int progress){
        // Keep the accepted requested value while Android applies it asynchronously.
        if(!actions.setScale(progress/100f)){failed();sync();}
    }
    private void failed(){Toast.makeText(slider.getContext(),R.string.menu_zoom_failed,Toast.LENGTH_SHORT).show();}
    private void describe(int progress){
        String scale=slider.getContext().getString(R.string.menu_zoom_multiplier,progress/100f);
        slider.setStateDescription(scale);
    }
    private void sync(){
        syncing=true;
        try{
            float scale=actions.scale();if(!Float.isFinite(scale))scale=2;
            int progress=Math.round(Math.max(1.1f,Math.min(8,scale))*100);
            slider.setProgress(progress);describe(progress);enabled.setChecked(actions.zoomed());
        }finally{syncing=false;}
    }
    private final class MenuSlider extends SeekBar {
        MenuSlider(Context context){super(context);}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)cancelled=false;
            else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){cancelled=true;actions.cancelScaleUpdates();}
            return super.onTouchEvent(event); // Retain native SeekBar touch and accessibility actions.
        }
        @Override public boolean performClick(){return super.performClick();}
    }
    public boolean isShowing(){return showing;}
    public void refresh(){if(showing&&!tracking)sync();}
    public void dismiss(){
        if(!showing)return;
        cancelled=true;tracking=false;showing=false;actions.cancelScaleUpdates();
        root.cancelPendingInputEvents();
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.setVisibility(View.GONE);dismissed.run();
    }
}
