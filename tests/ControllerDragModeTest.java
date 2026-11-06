import io.github.kacperla.touchzoom.*;
public class ControllerDragModeTest {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 public static void main(String[]a){
  ControllerDragMode m=new ControllerDragMode();
  for(boolean zoomed:new boolean[]{false,true}){
   m.begin(zoomed,false);ok(m.movement(false)==ControllerDragMode.WAIT);
   int move=zoomed?ControllerDragMode.PAN:ControllerDragMode.RELOCATE;
   ok(m.movement(true)==move);for(int i=0;i<100;i++)ok(m.hold()==move);
   m.end();ok(m.hold()==ControllerDragMode.ENDED);ok(m.movement(true)==ControllerDragMode.ENDED);
   m.begin(zoomed,false);int hold=zoomed?ControllerDragMode.RELOCATE:ControllerDragMode.PAN;
   ok(m.hold()==hold);for(int i=0;i<100;i++)ok(m.movement(true)==hold);
   m.end();ok(m.hold()==ControllerDragMode.ENDED);
   m.begin(zoomed,true);for(int i=0;i<100;i++){ok(m.movement(true)==ControllerDragMode.SCALE);ok(m.hold()==ControllerDragMode.SCALE);}
   m.end();m.begin(!zoomed,false);ok(m.movement(true)==(!zoomed?ControllerDragMode.PAN:ControllerDragMode.RELOCATE));
  }
  for(boolean latched:new boolean[]{false,true})for(boolean external:new boolean[]{false,true}){
   ok(ControllerDragMode.stopsPreview(latched,external)==(!latched&&!external));
   for(boolean preview:new boolean[]{false,true})for(boolean pending:new boolean[]{false,true}){
    boolean owns=ControllerDragMode.ownsZoom(latched,external,preview,pending);
    if(external&&!latched)ok(!owns);else ok(owns==(latched||preview||pending));
   }
  }
  System.out.println(n+" zoom-state mode snapshot/movement/hold/precedence/cancel assertions passed");
 }
}
