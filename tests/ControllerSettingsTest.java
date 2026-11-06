import io.github.kacperla.touchzoom.ControllerSettings;
import io.github.kacperla.touchzoom.OpacitySettings;
import java.util.HashMap;
import java.util.Map;

public class ControllerSettingsTest {
    private static int assertions;
    private static void check(boolean ok) {
        assertions++;
        if (!ok) throw new AssertionError("assertion " + assertions);
    }
    public static void main(String[] args) {
        check(ControllerSettings.NONE == 0);
        check(ControllerSettings.MENU == 1);
        check(ControllerSettings.ZOOM == 2);
        check(ControllerSettings.LAUNCH_APP == 3);
        check(ControllerSettings.defaultAction(1, false) == ControllerSettings.MENU);
        check(ControllerSettings.defaultAction(1, true) == ControllerSettings.ZOOM);
        for (boolean existing : new boolean[] { false, true }) {
            check(ControllerSettings.defaultAction(2, existing) == ControllerSettings.ZOOM);
            check(ControllerSettings.defaultAction(3, existing) == ControllerSettings.NONE);
        }
        for (int action = -100; action <= 100; action++) {
            check(ControllerSettings.normalizeAction(action)
                == (action >= 0 && action <= 3 ? action : ControllerSettings.NONE));
        }
        check(ControllerSettings.normalizeAction(Integer.MIN_VALUE) == ControllerSettings.NONE);
        check(ControllerSettings.normalizeAction(Integer.MAX_VALUE) == ControllerSettings.NONE);
        int[] colors = { ControllerSettings.COLOR_WHITE, ControllerSettings.COLOR_BLUE,
            ControllerSettings.COLOR_RED, ControllerSettings.COLOR_GREEN,
            ControllerSettings.COLOR_YELLOW, ControllerSettings.COLOR_ORANGE };
        for (int color : colors) {
            check(ControllerSettings.normalizeColor(color) == color);
            check((color >>> 24) == 255);
        }
        check(ControllerSettings.normalizeColor(0x00ffffff) == ControllerSettings.COLOR_WHITE);
        check(ControllerSettings.normalizeColor(0xff000000) == ControllerSettings.COLOR_WHITE);
        check(ControllerSettings.normalizeColor(0xff800080) == ControllerSettings.COLOR_WHITE);
        for (int count = 1; count <= 3; count++) {
            check(ControllerSettings.isControllerPreference(ControllerSettings.actionKey(count)));
            check(ControllerSettings.isControllerPreference(ControllerSettings.appKey(count)));
            check(!ControllerSettings.actionKey(count).equals(ControllerSettings.appKey(count)));
        }
        check(ControllerSettings.isControllerPreference(ControllerSettings.ADJUST_ZOOM_KEY));
        check(ControllerSettings.isControllerPreference(ControllerSettings.COLOR_KEY));
        check(!ControllerSettings.isControllerPreference(ControllerSettings.MIGRATION_KEY));
        check(!ControllerSettings.isControllerPreference(OpacitySettings.KEY));
        check(!ControllerSettings.isControllerPreference(null));
        for (int invalid : new int[] { Integer.MIN_VALUE, -1, 0, 4, Integer.MAX_VALUE }) {
            boolean keyRejected = false, defaultRejected = false;
            try { ControllerSettings.actionKey(invalid); } catch (IllegalArgumentException expected) { keyRejected = true; }
            try { ControllerSettings.defaultAction(invalid, false); } catch (IllegalArgumentException expected) { defaultRejected = true; }
            check(keyRejected); check(defaultRejected);
        }
        Map<String, Object> fresh = new HashMap<>();
        Map<String, Object> firstDefaults = ControllerSettings.missingDefaults(fresh);
        check(firstDefaults.size() == 6);
        check(firstDefaults.get(ControllerSettings.SINGLE_TAP_KEY).equals(ControllerSettings.MENU));
        check(firstDefaults.get(ControllerSettings.DOUBLE_TAP_KEY).equals(ControllerSettings.ZOOM));
        check(firstDefaults.get(ControllerSettings.TRIPLE_TAP_KEY).equals(ControllerSettings.NONE));
        check(firstDefaults.get(ControllerSettings.ADJUST_ZOOM_KEY).equals(true));
        check(firstDefaults.get(ControllerSettings.COLOR_KEY).equals(ControllerSettings.COLOR_WHITE));
        check(fresh.isEmpty());
        fresh.putAll(firstDefaults);
        check(ControllerSettings.missingDefaults(fresh).isEmpty());

        // Every partial combination preserves set values and fills only absent keys.
        String[] keys = { ControllerSettings.SINGLE_TAP_KEY, ControllerSettings.DOUBLE_TAP_KEY,
            ControllerSettings.TRIPLE_TAP_KEY, ControllerSettings.ADJUST_ZOOM_KEY,
            ControllerSettings.COLOR_KEY, ControllerSettings.MIGRATION_KEY };
        Object[] choices = { ControllerSettings.NONE, ControllerSettings.MENU,
            ControllerSettings.ZOOM, false, ControllerSettings.COLOR_ORANGE, ControllerSettings.VERSION };
        for (int mask = 0; mask < (1 << keys.length); mask++) {
            Map<String, Object> partial = new HashMap<>();
            partial.put("x", .82f);
            partial.put("scale", 3.5f);
            partial.put(OpacitySettings.KEY, 65);
            partial.put("hold_extra_delay_ms", 350);
            for (int index = 0; index < keys.length; index++) {
                if ((mask & (1 << index)) != 0) partial.put(keys[index], choices[index]);
            }
            Map<String, Object> before = new HashMap<>(partial);
            Map<String, Object> additions = ControllerSettings.missingDefaults(partial);
            check(partial.equals(before));
            for (String key : additions.keySet()) check(!before.containsKey(key));
            partial.putAll(additions);
            for (Map.Entry<String, Object> entry : before.entrySet()) {
                check(partial.get(entry.getKey()).equals(entry.getValue()));
            }
            if (!before.containsKey(ControllerSettings.SINGLE_TAP_KEY)) {
                check(partial.get(ControllerSettings.SINGLE_TAP_KEY).equals(
                    before.containsKey(ControllerSettings.MIGRATION_KEY)
                        ? ControllerSettings.MENU : ControllerSettings.ZOOM));
            }
            check(ControllerSettings.missingDefaults(partial).isEmpty());
        }
        System.out.println(assertions + " controller settings assertions passed");
    }
}
