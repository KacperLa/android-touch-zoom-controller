import java.nio.file.Files;
import java.nio.file.Path;
/** Resource/copy regressions, not a launcher, TalkBack or compositor runtime test. */
public final class LauncherAndCopyTest {
 private static int n;
 private static String read(String path)throws Exception{return Files.readString(Path.of(path));}
 private static void check(boolean c,String message){n++;if(!c)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  String manifest=read("app/src/main/AndroidManifest.xml");
  check(manifest.contains("android:icon=\"@mipmap/ic_launcher\"")&&manifest.contains("android:roundIcon=\"@mipmap/ic_launcher\""),"Launcher and round masks use adaptive icon");
  check(manifest.contains("android:label=\"@string/app_name\""),"Current app name");
  String foreground=read("app/src/main/res/drawable/ic_launcher_foreground.xml");
  check(foreground.contains("16.8,16.8")&&foreground.contains("strokeWidth=\"5.04\"")&&foreground.contains("5.6,5.6"),"Existing Orbit mark proportions");
  check((16.8+5.04/2)*1.2857142857<33,"Glyph fits adaptive safe circle");
  check(read("app/src/main/res/mipmap-anydpi/ic_launcher.xml").contains("<monochrome android:drawable=\"@drawable/ic_launcher_foreground\""),"Themed icon keeps same mark");
  String copy=read("app/src/main/res/values/controller_settings_strings.xml");
  check(copy.contains("When zoom is off:")&&copy.contains("When zoom is on:"),"Help distinguishes starting zoom state");
  check(copy.contains("Your current selections are shown there."),"No hardcoded new-install tap assumption");
  check(copy.contains("When zoom is off, it saves the level for next time."),"Menu off-state accurately described");
  check(copy.contains("controller and menu accent color"),"Color scope matches controls");
  check(copy.contains("until cleared, replaced or the service stops"),"Diagnostic duration not mistaken for retention");
  check(!copy.contains("Prototype 0.27"),"No stale hardcoded version");
  check(read("app/src/main/java/io/github/kacperla/touchzoom/MainActivity.java").contains("BuildConfig.VERSION_NAME")&&read("app/src/main/java/io/github/kacperla/touchzoom/ZoomService.java").contains("BuildConfig.VERSION_NAME"),"Settings and reports use built version");
  check(!read("app/src/main/java/io/github/kacperla/touchzoom/DiagnosticTrace.java").contains("Zoom0."),"Detailed trace has no contradictory version");
  System.out.println(n+" launcher/copy source checks passed (not Android runtime)");
 }
}
