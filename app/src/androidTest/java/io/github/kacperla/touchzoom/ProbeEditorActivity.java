package io.github.kacperla.touchzoom;
import android.app.Activity;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
/** Test APK only: fixed empty editor for focus/IME checks. */
public final class ProbeEditorActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);EditText editor=new EditText(this);
  editor.setHint("Emulator test editor");editor.setSingleLine(true);setContentView(editor);editor.requestFocus();
  editor.postDelayed(()->getSystemService(InputMethodManager.class).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT),600);
 }
}
