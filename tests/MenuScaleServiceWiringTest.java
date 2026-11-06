import java.nio.file.Files;
import java.nio.file.Path;

/** Source-wiring checks, not Android runtime validation. */
public final class MenuScaleServiceWiringTest {
    private static int count;
    private static void check(boolean value,String reason){count++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        String s=Files.readString(Path.of("app/src/main/java/io/github/kacperla/touchzoom/ZoomService.java"))
                .replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");
        check(s.contains("privatefinalMenuScaleUpdatesmenuScale=newMenuScaleUpdates()"),"Menu owns a separate coordinator");
        check(s.contains("privatefinalScaleBackpressurescaleGate=newScaleBackpressure()"),"Gesture gate is retained");
        check(s.contains("if(menuScale.hasWork())returnmenuScale.selected();"),"Pending final selection wins over stale UI refresh");
        check(s.contains("cancelScaleUpdates(){cancelMenuScaleUpdates();}"),"UI cancellation reaches service");
        check(s.contains("hideMenuSurface(){cancelMenuScaleUpdates();"),"Surface dismissal cancels pending scale");
        check(s.contains("cancelGesture(booleandismissMenu){cancelMenuScaleUpdates();"),"Lifecycle and new commands cancel pending scale");
        check(s.contains("removeFrameCallback(menuScaleFrame);menuScaleFrameQueued=false;"),"Cancellation removes scheduled frame");
        check(s.contains("observeMenuScale(state);"),"Reported state acknowledges menu requests");
        check(s.contains("if(!menuScale.active()){if(!prepareMenuCommand())returnfalse;menuScale.begin();}"),"Prepare only once per slider session");
        check(s.contains("handler.postDelayed(persistMenuScale,250)"),"Preferences are debounced rather than written every MOVE");
        check(s.contains("if(!isReportedZoomed()&&!menuScale.hasWork())returntrue;"),"Off zoom only remembers chosen scale");
        String dispatch=s.substring(s.indexOf("privatevoiddispatchMenuScale(){"),s.indexOf("privatefinalScaleBackpressure"));
        check(dispatch.indexOf("refreshBubbleArtwork(fresh)")<dispatch.indexOf("menuScale.take("),"Fresh ownership state reconciles before sending");
        check(dispatch.contains("!fresh.isActivated()||fresh.getScale()<=1.01f"),"Queued updates do not reactivate externally disabled zoom");
        check(dispatch.contains("handler.postDelayed(menuScaleTimeout,1000)"),"Missing callbacks have a bounded readback watchdog");
        System.out.println(count+" menu scale service source-wiring checks passed (not an Android runtime test)");
    }
}
