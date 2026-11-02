import io.github.kacperla.touchzoom.*;
/** Pure coordinator scenario checks: no callback is permission to start a new gesture. */
public class EntryRampLifecycleTest {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 static final class Session {
  final EntryRamp ramp=new EntryRamp();final ScaleBackpressure gate=new ScaleBackpressure();
  boolean active;float x,y,commandX,commandY,commandScale;int commands;
  void begin(long now){cancel();active=true;x=10;y=20;ramp.start(now,1,8,true);}
  void move(float nx,float ny){x=nx;y=ny;}
  void frame(long now){if(active&&ramp.ready(now)&&!gate.waiting()){
   commandX=x;commandY=y;commandScale=ramp.value(now);ramp.sent(now);gate.sent(commandScale);commands++;
  }}
  void ack(float scale){if(gate.observe(scale))ramp.acknowledged();}
  void cancel(){active=false;ramp.cancel();gate.reset();}
 }
 public static void main(String[]a){
  for(int interrupt=0;interrupt<120;interrupt++){
   Session s=new Session();s.begin(0);s.frame(0);s.ack(s.commandScale);
   s.move(100+interrupt,200+interrupt);s.frame(17);
   if(interrupt>=17){ok(s.commandX==100+interrupt);ok(s.commandY==200+interrupt);}
   int sent=s.commands;s.cancel();s.ack(s.commandScale);
   for(int t=interrupt;t<300;t++)s.frame(t);
   ok(s.commands==sent);ok(!s.ramp.running());ok(!s.gate.waiting());
  }
  Session s=new Session();s.begin(0);s.frame(0);s.move(25,26);s.move(30,31);s.frame(60);ok(s.commands==1);
  s.ack(1);s.frame(90);ok(s.commands==2);ok(s.commandX==30&&s.commandY==31);ok(s.commandScale>7);
  s.cancel();s.begin(200);s.frame(200);s.ack(8);ok(s.gate.waiting()); // old final report cannot match new1x
  s.cancel();s.ack(1);s.frame(1000);ok(!s.active&&!s.ramp.running());
  System.out.println(n+" entry coordinator latest-center/lifecycle/stale-report assertions passed");
 }
}
