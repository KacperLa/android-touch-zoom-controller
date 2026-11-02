package io.github.kacperla.touchzoom;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.MagnificationConfig;
import android.content.*;
import android.view.accessibility.AccessibilityEvent;

/** Test APK only: independent accessibility actor, commands restricted to shell/DUMP holders. */
public final class ExternalMagnifier extends AccessibilityService {
    public static final String ACTION="io.github.kacperla.touchzoom.PROBE_EXTERNAL_MAGNIFICATION";
    private final BroadcastReceiver commands=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(!ACTION.equals(intent.getAction()))return;
            if(intent.getBooleanExtra("reset",false)){getMagnificationController().resetCurrentMagnification(false);return;}
            getMagnificationController().setMagnificationConfig(new MagnificationConfig.Builder()
                .setMode(intent.getIntExtra("mode",MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN))
                .setActivated(true).setScale(intent.getFloatExtra("scale",2f))
                .setCenterX(intent.getFloatExtra("cx",360f)).setCenterY(intent.getFloatExtra("cy",640f)).build(),false);
        }
    };
    @Override protected void onServiceConnected(){
        registerReceiver(commands,new IntentFilter(ACTION),"android.permission.DUMP",null,Context.RECEIVER_EXPORTED);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){try{unregisterReceiver(commands);}catch(IllegalArgumentException ignored){}super.onDestroy();}
}
