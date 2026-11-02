import io.github.kacperla.touchzoom.*;
/** Simulates the service's phase/session wiring, not Android event delivery. */
public class CaptureIntegrationTest {
 static int n; static void check(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 static void input(InputArrivalStats i,long t,int action){i.add(t-1,t,action,10,20,0,-1,0,0,-1,0,0);}
 public static void main(String[] args){
  CaptureSession s=new CaptureSession();GestureProbe p=new GestureProbe();InputArrivalStats i=new InputArrivalStats();
  s.start(1000);i.reset(false);long id=s.id();
  p.arm(0,100);p.down(1100,1099);input(i,1100,0);int first=p.generation();
  p.mark("quick tap UP",1140,1139);input(i,1140,1);p.stop("UP",1140);s.addGesture(p.report());
  check(!p.recording() && s.recording(1140));
  s.heartbeat(1150,1160);check(s.report(1160).contains("heartbeat count=1 maxLatenessMs=10"));
  p.arm(0,100);p.down(1200,1199);p.mark("second-tap scale gesture",1200,-1);input(i,1200,0);
  check(s.id()==id && p.generation()!=first && p.recording());
  p.markFor(first,"stale first renderer",1210,-1);check(!p.report().contains("stale first renderer"));
  s.heartbeat(1210,1230);input(i,1250,2);p.span("scale control",1250,1251);
  check(s.report(1251).contains("heartbeat count=2 maxLatenessMs=20"));
  p.stop("UP",1300);input(i,1300,1);s.addGesture(p.report());
  check(s.gestureCount()==2 && s.report(1300).contains("quick tap UP") && s.report(1300).contains("second-tap scale gesture"));
  check(i.total()==5 && i.report().contains("action=2"));
  check(s.recording(15999) && !s.recording(16000));check(!s.recording(17000));
  int stale=p.generation();s.clear();p.clear();i.reset(false);
  check(s.id()!=id && s.state().equals("CLEARED") && !p.recording() && i.total()==0);
  p.markFor(stale,"stale after clear",18000,-1);check(!p.report().contains("stale after clear"));
  s.start(20000);p.arm(0,100);p.down(20100,20099);
  p.markFor(stale,"stale after rearm",20101,-1);p.spanFor(stale,"stale span",20100,20120);
  check(!p.report().contains("stale after rearm") && !p.report().contains("stale span"));
  p.markFor(p.generation(),"current renderer",20101,-1);check(p.report().contains("current renderer"));
  check(s.gestureCount()==0 && s.overflowCount()==0 && !s.report(20101).contains("quick tap UP"));
  System.out.println(n+" simulated multi-gesture/session/generation integration assertions passed");
 }
}
