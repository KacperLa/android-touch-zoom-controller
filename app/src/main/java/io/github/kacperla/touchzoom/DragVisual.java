package io.github.kacperla.touchzoom;
/** Whole-surface relocation feedback, preserving the grabbed point rather than resizing a View. */
public final class DragVisual {
 public static final float DRAG_SCALE=1.5f;
 private DragVisual(){}
 public static float scale(boolean relocating){return relocating?DRAG_SCALE:1f;}
 public static float offset(float anchor,float scale){return (scale-1f)*anchor;}
 public static float visualStart(float logical,float anchor,float scale){return logical-offset(anchor,scale);}
 public static int clampLogical(float logical,float anchor,float scale,float diameter,float low,float high){
  float offset=offset(anchor,scale);
  int min=(int)Math.ceil(low+offset),max=Math.max(min,(int)Math.floor(high-scale*diameter+offset));
  return Math.max(min,Math.min(max,Math.round(logical)));
 }
}
