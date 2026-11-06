import java.nio.file.Files;
import java.nio.file.Path;
/** Release source guards, not proof of Play acceptance or device compatibility. */
public final class PlayPreparationTest {
    private static int n;
    private static void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        String build=Files.readString(Path.of("app/build.gradle"));
        String script=Files.readString(Path.of("scripts/build-play-bundle.sh"));
        String manifest=Files.readString(Path.of("app/src/main/AndroidManifest.xml"));
        String activity=Files.readString(Path.of("app/src/main/java/io/github/kacperla/touchzoom/MainActivity.java"));
        check(build.contains("targetSdk 36")&&build.contains("minSdk 34"),"Target36 without dropping existing minimum");
        check(build.contains("debuggable false")&&build.contains("minifyEnabled false"),"Non-debuggable release preserves class isolation");
        check(script.contains("env -u TOUCH_ZOOM_KEYSTORE -u TOUCH_ZOOM_STORE_PASSWORD -u TOUCH_ZOOM_KEY_ALIAS -u TOUCH_ZOOM_KEY_PASSWORD"),"Build receives no signing environment");
        check(script.contains("-storepass:env TOUCH_ZOOM_STORE_PASSWORD")&&script.contains("-keypass:env TOUCH_ZOOM_KEY_PASSWORD"),"Secrets not command-line values");
        check(script.contains("java scripts/VerifyBundle.java"),"Every signed payload is checked");
        check(script.contains("[[ \"$actual\" == \"$expected\" ]]"),"Original certificate pinned");
        check(!script.contains("setup-signing.sh")&&!script.contains("gh ")&&!script.contains("curl "),"No publication or key-transfer operation");
        check(!manifest.contains("screenOrientation")&&!manifest.contains("maxAspectRatio"),"No fixed orientation/aspect policy");
        check(activity.contains("setOnApplyWindowInsetsListener")&&activity.contains("WindowInsets.Type.displayCutout()"),"Existing edge-to-edge safe padding retained");
        check(activity.contains("getOnBackInvokedDispatcher().registerOnBackInvokedCallback"),"Supported Back callback retained");
        System.out.println(n+" Play preparation source guards passed (not runtime or Console acceptance)");
    }
}
