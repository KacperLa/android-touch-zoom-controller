import io.github.kacperla.touchzoom.CaptureSession;
public class CaptureSessionTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        CaptureSession s = new CaptureSession();
        check(s.id() == 0 && !s.recording(0));
        check(s.report(100).contains("state=CLEARED"));
        s.addGesture("ignored"); check(s.gestureCount() == 0);
        s.start(100); check(s.id() == 1 && s.startedAt() == 100 && s.deadline() == 15100);
        check(s.recording(100));
        s.addGesture("first"); s.addGesture("second");
        check(s.recording(15099)); check(s.gestureCount() == 2);
        check(s.report(15099).contains("elapsedMs=14999"));
        check(!s.recording(15100)); check(s.state().equals("ENDED"));
        check(s.report(90000).contains("elapsedMs=15000"));
        s.addGesture("final"); check(s.gestureCount() == 3);
        check(s.report(90000).contains("first") && s.report(90000).contains("second"));
        s.start(20000); check(s.id() == 2 && s.gestureCount() == 0);
        check(!s.report(20001).contains("first"));
        s.recording(20200); s.recording(20100); s.stop();
        check(s.report(40000).contains("elapsedMs=200")); check(!s.recording(20201));
        s.clear(); check(s.id() == 3 && s.gestureCount() == 0 && s.overflowCount() == 0);
        check(s.startedAt() == 0 && s.deadline() == 0 && s.report(99999).contains("elapsedMs=0"));
        s.stop(); check(s.state().equals("CLEARED"));
        s.start(30000); s.addGesture(null); s.addGesture(""); check(s.gestureCount() == 0);
        String large = new String(new char[20000]).replace('\0', 'x');
        for (int i = 0; i < 100000; i++) s.addGesture(large);
        check(s.gestureCount() == 16 && s.overflowCount() == 99984);
        check(s.report(30000).length() < 16 * CaptureSession.MAX_REPORT_CHARS + 1000);
        check(s.report(30000).contains("[truncated]"));
        s.clear(); check(s.overflowCount() == 0 && s.gestureCount() == 0 && s.id() == 5);
        check(!s.report(999999).contains("[truncated]"));
        s.start(0); check(s.recording(0) && !s.recording(15000));
        boolean invalid = false; try { s.start(-1); } catch (IllegalArgumentException e) { invalid = true; }
        check(invalid); check(s.id() == 6);
        s.start(1000);
        s.heartbeat(1050, 1052); s.addGesture("a");
        s.heartbeat(1100, 1200); s.addGesture("b");
        s.heartbeat(1250, 1240);
        check(s.report(1240).contains("heartbeat count=3 maxLatenessMs=100"));
        s.heartbeat(15950, 16000);
        check(s.report(16000).contains("heartbeat count=4 maxLatenessMs=100"));
        check(s.state().equals("ENDED"));
        s.start(20000); check(s.report(20000).contains("heartbeat count=0 maxLatenessMs=0"));
        s.heartbeat(20050, 20051); s.clear();
        check(s.report(20060).contains("heartbeat count=0 maxLatenessMs=0"));
        s.heartbeat(20100, 20110); check(s.report(20110).contains("heartbeat count=0"));
        s.start(30000); s.heartbeat(44950, 45500);
        check(s.report(45500).contains("heartbeat count=1 maxLatenessMs=550"));
        check(s.state().equals("ENDED"));
        s.heartbeat(44960, 46000);
        check(s.report(46000).contains("heartbeat count=1 maxLatenessMs=550"));
        s.start(50000); s.heartbeat(65000, 65500);
        check(s.report(65500).contains("heartbeat count=0 maxLatenessMs=0"));
        check(s.state().equals("ENDED"));
        s.start(70000); s.heartbeat(69999, 70000);
        check(s.report(70000).contains("heartbeat count=0 maxLatenessMs=0"));
        System.out.println(checks + " capture lifetime/bounds/ID/multi-gesture assertions passed");
    }
}
