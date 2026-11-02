import io.github.kacperla.touchzoom.DiagnosticTrace;
import java.util.ArrayDeque;
public class DiagnosticTraceBoundsTest {
 static int n;static void c(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
 public static void main(String[] a){
  DiagnosticTrace d=new DiagnosticTrace();d.start(0);ArrayDeque<String> expected=new ArrayDeque<>();int chars=0;
  for(int j=0;j<30000;j++){
   int len=j%3==0?2000:j%3==1?400:8;
   String e="event="+j+":"+"x".repeat(len);String trimmed=e.length()>512?e.substring(0,498)+" [truncated]":e;
   d.add(e);expected.add(trimmed);chars+=trimmed.length();
   while(expected.size()>8192||chars>196608)chars-=expected.removeFirst().length();
   if(j%197==0){String report=d.report();String body=report.substring(report.indexOf('\n')+1);c(body.equals(String.join("\n",expected)+"\n"));c(report.length()<205000);}
  }
  c(d.report().contains("[truncated]"));c(!d.report().contains("event=0:"));
  d.clear();c(!d.report().contains("event=29999:"));c(!d.mayRecord());
  d.start(40000);d.add("new");c(d.report().endsWith("\nnew\n"));c(!d.report().contains("[truncated]"));
  // Short entries fill the slot limit after previous character-budget trimming.
  for(int j=0;j<10000;j++)d.add("short"+j);
  c(!d.report().contains("\nshort1807\n"));c(d.report().contains("\nshort1808\n"));c(d.report().endsWith("short9999\n"));
  System.out.println(n+" trace character-budget/ring-wrap/order/reset assertions passed");
 }
}
