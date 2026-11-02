package io.github.kacperla.touchzoom;
/** Haptic intent belongs to explicit user actions, not every system state callback. */
public final class ZoomHaptics {
 public static final int NONE=0,ENGAGE=1,RELEASE=2;
 private boolean engage,release;
 public void requestEngage(boolean alreadyZoomed){release=false;engage=!alreadyZoomed;}
 public void requestRelease(boolean wasZoomed){engage=false;release=wasZoomed;}
 public int observe(boolean zoomed){
  if(zoomed && engage){engage=false;return ENGAGE;}
  if(!zoomed && release){release=false;return RELEASE;}
  return NONE;
 }
 public void cancel(){engage=release=false;}
}
