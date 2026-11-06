package io.github.kacperla.touchzoom;

import android.content.SharedPreferences;
import java.util.Map;

/** Android preference bridge. Initialize before writing any legacy defaults. */
public final class ControllerPreferences {
    private ControllerPreferences() { }

    public static synchronized void ensure(SharedPreferences preferences) {
        // Take this snapshot before any defaults are written. An untouched preference
        // file is a new installation; existing position/scale/settings identify an upgrade.
        Map<String, Object> missing = ControllerSettings.missingDefaults(preferences.getAll());
        if (missing.isEmpty()) return;
        SharedPreferences.Editor edit = preferences.edit();
        for (Map.Entry<String, Object> entry : missing.entrySet()) {
            if (entry.getValue() instanceof Boolean) {
                edit.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else {
                edit.putInt(entry.getKey(), (Integer) entry.getValue());
            }
        }
        edit.apply();
    }

    public static int action(SharedPreferences preferences, int tapCount) {
        return ControllerSettings.normalizeAction(preferences.getInt(
            ControllerSettings.actionKey(tapCount), ControllerSettings.defaultAction(tapCount, false)));
    }

    public static boolean adjustZoom(SharedPreferences preferences) {
        return preferences.getBoolean(ControllerSettings.ADJUST_ZOOM_KEY, true);
    }

    /** Opaque foreground ARGB; idle opacity is a separate preference. */
    public static int color(SharedPreferences preferences) {
        return ControllerSettings.normalizeColor(preferences.getInt(
            ControllerSettings.COLOR_KEY, ControllerSettings.COLOR_WHITE));
    }
}
