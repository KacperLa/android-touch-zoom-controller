import io.github.kacperla.touchzoom.DiagnosticTrace;
public class DiagnosticTraceTest{
 public static void main(String[] a){DiagnosticTrace d=new DiagnosticTrace();if(d.recording(0))throw new AssertionError();d.start(100);if(!d.recording(15099)||d.recording(15100))throw new AssertionError();for(int i=0;i<10000;i++)d.add("event"+i);String s=d.report();if(s.contains("event1807\n")||!s.contains("event1808\n")||!s.contains("event9999\n"))throw new AssertionError();d.start(20000);if(d.report().contains("event"+9999))throw new AssertionError();System.out.println("7 bounded opt-in trace assertions passed");}
}
