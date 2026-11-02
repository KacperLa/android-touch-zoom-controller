package io.github.kacperla.touchzoom;
import android.app.Activity;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
/** Test APK only: empty editor. Request IME once on initial window focus, never after controller input. */
public final class ProbeEditorActivity extends Activity {
 private EditText editor;
 private boolean initialImeRequested;
 @Override public void onCreate(Bundle state){super.onCreate(state);editor=new EditText(this);
  editor.setHint("Emulator test editor");editor.setSingleLine(true);setContentView(editor);editor.requestFocus();
 }
 @Override public void onWindowFocusChanged(boolean focused){
  super.onWindowFocusChanged(focused);
  if(focused&&!initialImeRequested){initialImeRequested=true;
   editor.post(()->getSystemService(InputMethodManager.class).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT));
  }
 }
}
