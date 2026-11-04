package io.github.kacperla.touchzoom;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
/** Test APK only: external empty editor. Focus trials never issue the optional IME commands. */
public final class ProbeEditorActivity extends Activity {
 static final String IME_COMMAND="io.github.kacperla.touchzoom.test.IME_COMMAND";
 private EditText editor;
 private boolean initialImeRequested;
 private final BroadcastReceiver imeCommands=new BroadcastReceiver(){
  @Override public void onReceive(Context context,Intent intent){
   InputMethodManager ime=getSystemService(InputMethodManager.class);
   String command=intent.getStringExtra("command");
   // These use the real external Activity's IME, not dispatchApplyWindowInsets or service hooks.
   // Deliberately never request focus: an observer stealing it must fail the runtime assertions.
   if("show".equals(command))ime.showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);
   else if("hide".equals(command))ime.hideSoftInputFromWindow(editor.getWindowToken(),0);
   else if("numeric".equals(command)||"text".equals(command)){
    editor.setInputType("numeric".equals(command)?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);
    ime.restartInput(editor);
   }
  }
 };
 @Override public void onCreate(Bundle state){super.onCreate(state);editor=new EditText(this);
  editor.setHint("Emulator test editor");editor.setSingleLine(true);setContentView(editor);editor.requestFocus();
  registerReceiver(imeCommands,new IntentFilter(IME_COMMAND),Context.RECEIVER_EXPORTED);
 }
 @Override public void onWindowFocusChanged(boolean focused){
  super.onWindowFocusChanged(focused);
  if(focused&&!initialImeRequested){initialImeRequested=true;
   editor.post(()->getSystemService(InputMethodManager.class).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT));
  }
 }
 @Override public void onDestroy(){unregisterReceiver(imeCommands);super.onDestroy();}
}
