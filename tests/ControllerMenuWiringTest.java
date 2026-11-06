import java.nio.file.Files;
import java.nio.file.Path;

/** Source-wiring checks only; these do not exercise Android input or accessibility. */
public final class ControllerMenuWiringTest {
    private static int assertions;
    private static void check(boolean condition,String reason){
        assertions++;if(!condition)throw new AssertionError(reason);
    }
    private static String compact(String source){
        return source.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");
    }
    public static void main(String[] args) throws Exception {
        String source=compact(Files.readString(Path.of(
                "app/src/main/java/io/github/kacperla/touchzoom/ControllerMenu.java")));
        check(source.contains("root.setBackgroundColor(android.graphics.Color.TRANSPARENT)"),"Outside area does not dim screen");
        check(!source.contains("TextView")&&!source.contains("menu_zoom_value"),"Scale value remains programmatic, not a visible label");
        check(source.contains("slider.setStateDescription(scale)")&&source.contains("slider.setContentDescription(theme.getString(R.string.menu_zoom_level))"),"Slider keeps accessible name and current value");
        check(source.contains("slider.setMinHeight(Math.round(12*density))")&&source.contains("sliderThumb.setSize(Math.round(36*density),Math.round(36*density))")&&source.contains("slider.setMinimumHeight(Math.round(64*density))"),"Larger track, thumb and touch area are explicit");
        check(!source.contains("OrbitMark")&&!source.contains("menu_release_apply")&&!source.contains("menu_dismiss_hint")&&!source.contains("heading=new"),"No visual header or instructional footer");
        check(source.contains("settings.setText(R.string.menu_settings)"),"Settings button uses concise label");
        check(!source.contains("menu_close")&&!source.contains("Buttonclose"),"No visible Close control");
        check(source.contains("root.setOnClickListener(v->dismiss())"),"Outside scrim keeps its dismissal handler");
        check(source.contains("content.setClickable(true)"),"Panel blank space consumes taps");
        check(source.contains("root.setContentDescription(theme.getString(R.string.menu_dismiss))"),"Scrim dismissal is labeled");
        check(source.contains("info.setDismissable(true)")
                &&source.contains("if(action==AccessibilityNodeInfo.ACTION_DISMISS&&showing){dismiss();returntrue;}"),
                "Panel retains a working dismiss accessibility action");
        check(source.contains("margin=Math.round(12*density)")
                &&source.contains("root.setPadding(margin,margin,margin,margin)"),"Scrim retains space around scrolling content");
        check(source.contains("slider=newMenuSlider(theme)")&&source.contains("classMenuSliderextendsSeekBar")
                &&source.contains("enabled=newSwitch(theme)")&&source.contains("Buttonsettings=newButton(theme)"),
                "Slider, zoom switch, and Settings remain native controls");
        check(source.contains("if(!cancelled&&showing)commitScale(bar.getProgress());elsesync();"),
                "Touch release retains commit/cancel behavior");
        check(source.contains("if(fromUser&&!syncing&&showing)commitScale(progress);"),
                "Touch and accessibility slider changes request live updates");
        check(source.contains("settings.setOnClickListener(v->{dismiss();actions.openSettings();})"),
                "Settings dismisses the menu before opening");
        String show=source.substring(source.indexOf("voidshow(){"),source.indexOf("privatevoidcommitScale("));
        check(show.indexOf("refreshAppearance();")>=0
                &&show.indexOf("refreshAppearance();")<show.indexOf("root.setVisibility(View.VISIBLE)"),
                "Every opening refreshes the persisted host's appearance before visibility");
        check(source.contains("context.getSharedPreferences(\"controller\",Context.MODE_PRIVATE)")
                &&source.contains("ControllerPreferences.color(preferences)"),"Accent comes from the existing controller preference");
        check(!source.contains("requestFocus(")&&!source.contains("dispatchKeyEvent("),
                "Menu does not request input focus or add a Back interception path");
        check(source.contains("root.cancelPendingInputEvents()")
                &&source.contains("root.setVisibility(View.GONE);dismissed.run()"),"Dismissal retains input cancellation and host cleanup");
        System.out.println(assertions+" menu source-wiring checks passed (not an Android runtime test)");
    }
}
