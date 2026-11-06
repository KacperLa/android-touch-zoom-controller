package io.github.kacperla.touchzoom;

import java.util.LinkedHashMap;
import java.util.Map;

/** Platform-independent policy for controller actions and its foreground color. */
public final class ControllerSettings {
    public static final int NONE = 0, MENU = 1, ZOOM = 2, LAUNCH_APP = 3;
    public static final String SINGLE_TAP_KEY = "single_tap_action";
    public static final String DOUBLE_TAP_KEY = "double_tap_action";
    public static final String TRIPLE_TAP_KEY = "triple_tap_action";
    public static final String ADJUST_ZOOM_KEY = "adjust_zoom_level";
    public static final String COLOR_KEY = "controller_color";
    public static final String MIGRATION_KEY = "controller_settings_version";
    public static final int VERSION = 1;
    public static final int COLOR_WHITE = 0xffffffff;
    public static final int COLOR_BLUE = 0xff2196f3;
    public static final int COLOR_RED = 0xfff44336;
    public static final int COLOR_GREEN = 0xff4caf50;
    public static final int COLOR_YELLOW = 0xffffeb3b;
    public static final int COLOR_ORANGE = 0xffff9800;

    private ControllerSettings() { }

    /** Only missing keys are returned; existing choices and legacy data are untouched. */
    public static Map<String, Object> missingDefaults(Map<String, ?> existing) {
        boolean legacy = !existing.containsKey(MIGRATION_KEY) && !existing.isEmpty();
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (int count = 1; count <= 3; count++) {
            String key = actionKey(count);
            if (!existing.containsKey(key)) defaults.put(key, defaultAction(count, legacy));
        }
        if (!existing.containsKey(ADJUST_ZOOM_KEY)) defaults.put(ADJUST_ZOOM_KEY, true);
        if (!existing.containsKey(COLOR_KEY)) defaults.put(COLOR_KEY, COLOR_WHITE);
        if (!existing.containsKey(MIGRATION_KEY)) defaults.put(MIGRATION_KEY, VERSION);
        return defaults;
    }

    public static String actionKey(int tapCount) {
        switch (tapCount) {
            case 1: return SINGLE_TAP_KEY;
            case 2: return DOUBLE_TAP_KEY;
            case 3: return TRIPLE_TAP_KEY;
            default: throw new IllegalArgumentException("Tap count must be 1, 2 or 3");
        }
    }

    public static String appKey(int tapCount) {
        return actionKey(tapCount) + "_app";
    }

    /** Existing installations retain the old single-tap zoom toggle. */
    public static int defaultAction(int tapCount, boolean existingInstallation) {
        switch (tapCount) {
            case 1: return existingInstallation ? ZOOM : MENU;
            case 2: return ZOOM;
            case 3: return NONE;
            default: throw new IllegalArgumentException("Tap count must be 1, 2 or 3");
        }
    }

    public static int normalizeAction(int action) {
        return action >= NONE && action <= LAUNCH_APP ? action : NONE;
    }

    public static int normalizeColor(int color) {
        switch (color) {
            case COLOR_BLUE: case COLOR_RED: case COLOR_GREEN:
            case COLOR_YELLOW: case COLOR_ORANGE: return color;
            default: return COLOR_WHITE;
        }
    }

    public static boolean isControllerPreference(String key) {
        return SINGLE_TAP_KEY.equals(key) || DOUBLE_TAP_KEY.equals(key)
            || TRIPLE_TAP_KEY.equals(key) || ADJUST_ZOOM_KEY.equals(key)
            || COLOR_KEY.equals(key) || appKey(1).equals(key)
            || appKey(2).equals(key) || appKey(3).equals(key);
    }
}
