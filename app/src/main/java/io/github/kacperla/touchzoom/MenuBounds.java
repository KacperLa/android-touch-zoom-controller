package io.github.kacperla.touchzoom;

/** Unmagnified content bounds; a displayed docked IME and system bars keep their input area. */
public final class MenuBounds {
    public final int left,top,width,height;
    private MenuBounds(int left,int top,int width,int height){
        this.left=left;this.top=top;this.width=width;this.height=height;
    }
    public static MenuBounds calculate(int displayWidth,int displayHeight,int insetLeft,int insetTop,
            int insetRight,int insetBottom,boolean imeVisible,int imeBottom){
        int w=Math.max(1,displayWidth),h=Math.max(1,displayHeight);
        int left=clamp(insetLeft,0,w),top=clamp(insetTop,0,h);
        int right=Math.max(left,w-clamp(insetRight,0,w));
        int bottomInset=Math.max(clamp(insetBottom,0,h),imeVisible?clamp(imeBottom,0,h):0);
        int bottom=Math.max(top,h-bottomInset);
        return new MenuBounds(left,top,right-left,bottom-top);
    }
    public boolean usable(){return width>0 && height>0;}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
