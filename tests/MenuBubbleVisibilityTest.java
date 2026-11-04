import java.nio.file.Files;
import java.nio.file.Path;
/** Source-wiring regression checks, not Android compositor or accessibility runtime tests. */
public final class MenuBubbleVisibilityTest {
    private static int n;
    private static void check(boolean c,String why){n++;if(!c)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        String s=Files.readString(Path.of("app/src/main/java/io/github/kacperla/touchzoom/ZoomService.java"))
            .replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");
        String show=s.substring(s.indexOf("privatevoidshowControllerMenu()"),s.indexOf("privateControllerMenu.ActionsmenuActions()"));
        String hide=s.substring(s.indexOf("privatevoidhideMenuSurface()"),s.indexOf("privatevoiddismissControllerMenu()"));
        check(show.contains("setVisibility(bubblePackage.getSurfaceControl(),false).setPosition(menuControl,bounds.left,bounds.top).setVisibility(menuControl,true)"),"Atomic hide bubble and show menu");
        check(show.contains("bubble.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)"),"Hidden bubble removed from accessibility traversal");
        check(hide.contains("booleanrestore=overlayReady&&!destroyed"),"Teardown never restores bubble");
        check(hide.contains("setVisibility(menuControl,false)")&&hide.contains("setVisibility(bubblePackage.getSurfaceControl(),restore)"),"Dismissal restores input and artwork together");
        check(hide.contains("View.IMPORTANT_FOR_ACCESSIBILITY_AUTO"),"Normal bubble accessibility restored");
        check(!hide.contains("setAlpha")&&!show.contains("setAlpha"),"Visibility changes preserve opacity");
        check(!hide.contains("resetCurrentMagnification")&&!hide.contains("releaseAllZoom"),"Menu close does not reset zoom");
        check(!show.contains("newSurfaceControlViewHost")&&!hide.contains("newSurfaceControlViewHost"),"Host remains persistent");
        check(show.contains("try{dismissControllerMenu();}"),"Opening failure uses restoration path");
        String activity=Files.readString(Path.of("app/src/main/java/io/github/kacperla/touchzoom/MainActivity.java"));
        check(!activity.contains("R.string.reset_opacity"),"Only opacity reset removed");
        check(activity.contains("setOpacity(preferences.getInt(OpacitySettings.KEY, OpacitySettings.DEFAULT_PERCENT))"),"Saved opacity/default still restored");
        System.out.println(n+" menu bubble visibility source checks passed (not Android runtime)");
    }
}
