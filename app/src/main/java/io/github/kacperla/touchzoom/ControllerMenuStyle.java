package io.github.kacperla.touchzoom;

/** Platform-independent palette shared by the quick menu and its contrast checks. */
public final class ControllerMenuStyle {
    private ControllerMenuStyle() { }

    // Match the controller's charcoal fill. Color is decoration, never label text.
    public static final int SURFACE = 0xff262626;
    public static final int TEXT = 0xffffffff;
    public static final int OUTER_EDGE = 0xff000000;
    public static final int INACTIVE_CONTROL = 0xffb8b8b8;
    public static final int SWITCH_TRACK_ON = 0xff080808;
    public static final int SWITCH_TRACK_OFF = 0xff757575;
    public static final int RIPPLE = 0x33ffffff;
    public static final float PANEL_RADIUS_DP = 28f;
    public static final float BUTTON_RADIUS_DP = 24f;
}
