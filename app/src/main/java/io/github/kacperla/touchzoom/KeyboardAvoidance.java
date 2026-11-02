package io.github.kacperla.touchzoom;

/** Temporary display placement only: this never changes the user's saved position. */
public final class KeyboardAvoidance {
    private KeyboardAvoidance() { }
    public static float top(float preferredTop,float diameter,float displayHeight,
            float safeTop,float safeBottom,boolean imeVisible,float imeBottom,float gap) {
        float maximum=Math.max(safeTop,displayHeight-safeBottom-diameter);
        float position=Mapping.clamp(preferredTop,safeTop,maximum);
        if(!imeVisible || imeBottom<=0)return position;
        float keyboardTop=displayHeight-Mapping.clamp(imeBottom,0,displayHeight);
        // Don't move an already unobscured controller merely to add a margin.
        if(position+diameter<=keyboardTop)return position;
        return Math.max(safeTop,Math.min(position,keyboardTop-Math.max(0,gap)-diameter));
    }
}
