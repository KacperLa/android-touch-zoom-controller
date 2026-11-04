import io.github.kacperla.touchzoom.ControllerMenuStyle;
import io.github.kacperla.touchzoom.ControllerSettings;

/** Pure palette checks, not a rendered Android-control or device test. */
public final class ControllerMenuStyleTest {
    private static int assertions;
    private static void check(boolean value,String reason){
        assertions++;if(!value)throw new AssertionError(reason);
    }
    private static double linear(int channel){
        double value=channel/255.0;
        return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);
    }
    private static double luminance(int color){
        return .2126*linear((color>>16)&255)+.7152*linear((color>>8)&255)+.0722*linear(color&255);
    }
    private static double contrast(int first,int second){
        double a=luminance(first),b=luminance(second);
        return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);
    }
    public static void main(String[] args){
        check(ControllerMenuStyle.SURFACE==0xff262626,"Menu uses the controller's charcoal fill");
        check(contrast(ControllerMenuStyle.TEXT,ControllerMenuStyle.SURFACE)>=7,"White labels retain high contrast");
        check(contrast(ControllerMenuStyle.INACTIVE_CONTROL,ControllerMenuStyle.SURFACE)>=3,"Unfilled slider track remains visible");
        check(contrast(ControllerMenuStyle.TEXT,ControllerMenuStyle.SWITCH_TRACK_OFF)>=3,"Off thumb contrasts with track color");
        for(int accent:new int[]{ControllerSettings.COLOR_WHITE,ControllerSettings.COLOR_BLUE,
                ControllerSettings.COLOR_RED,ControllerSettings.COLOR_GREEN,
                ControllerSettings.COLOR_YELLOW,ControllerSettings.COLOR_ORANGE}){
            check((accent>>>24)==255,"Every controller accent is opaque");
            check(contrast(accent,ControllerMenuStyle.SURFACE)>=3,"Accent contrasts with charcoal, including white/yellow");
            check(contrast(accent,ControllerMenuStyle.SWITCH_TRACK_ON)>=3,"On thumb contrasts with track color");
        }
        check(ControllerMenuStyle.PANEL_RADIUS_DP>=24,"Panel has generous controller-like rounding");
        System.out.println(assertions+" menu palette contrast assertions passed (not a rendered/device test)");
    }
}
