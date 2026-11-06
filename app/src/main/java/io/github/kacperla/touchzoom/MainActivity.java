package io.github.kacperla.touchzoom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** Native, scrollable settings. All gesture behavior remains in ZoomService. */
public final class MainActivity extends Activity {
    private static final String HOME = "home", ACTIONS = "actions", COLORS = "colors",
        OPACITY = "opacity", ADVANCED = "advanced";
    private static final String PAGE_STATE = "settings_page", SCROLL_STATE = "settings_scroll";
    private static final int[] COLORS_ARGB = { ControllerSettings.COLOR_WHITE,
        ControllerSettings.COLOR_BLUE, ControllerSettings.COLOR_RED,
        ControllerSettings.COLOR_GREEN, ControllerSettings.COLOR_YELLOW,
        ControllerSettings.COLOR_ORANGE };
    private static final int[] COLOR_LABELS = { R.string.color_white, R.string.color_blue,
        R.string.color_red, R.string.color_green, R.string.color_yellow, R.string.color_orange };

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private String page = HOME;
    private ScrollView scroll;
    private TextView status, sizeValue, delayValue, opacityValue;
    private SeekBar sizeSlider, delaySlider, opacitySlider;
    private SizePreview preview;
    private Button accessibilityButton;
    private boolean backRegistered;
    private final OnBackInvokedCallback back = () -> showPage(HOME);
    private final Runnable refreshStatus = new Runnable() {
        @Override public void run() {
            updateStatus();
            main.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("controller", MODE_PRIVATE);
        ControllerPreferences.ensure(preferences);
        getWindow().setDecorFitsSystemWindows(false);
        showPage(state == null ? HOME : state.getString(PAGE_STATE, HOME));
        if (state != null) {
            int scrollY = state.getInt(SCROLL_STATE, 0);
            scroll.post(() -> scroll.scrollTo(0, scrollY));
        }
    }

    private void showPage(String requestedPage) {
        page = validPage(requestedPage) ? requestedPage : HOME;
        status = null;
        accessibilityButton = null;
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(themeColor(android.R.attr.colorBackground));
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsets.CONSUMED;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(16), dp(8));
        if (!HOME.equals(page)) {
            ImageButton up = new ImageButton(this);
            up.setImageResource(R.drawable.ic_arrow_back);
            up.setImageTintList(android.content.res.ColorStateList.valueOf(themeColor(android.R.attr.textColorPrimary)));
            up.setContentDescription(getString(R.string.back_to_settings));
            up.setBackgroundResource(themeResource(android.R.attr.selectableItemBackgroundBorderless));
            up.setOnClickListener(v -> showPage(HOME));
            header.addView(up, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        TextView title = text(pageTitle(), 26);
        title.setPadding(dp(8), 0, 0, 0);
        title.setAccessibilityHeading(true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        screen.addView(header);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);
        screen.requestApplyInsets();
        setTitle(pageTitle());
        switch (page) {
            case ACTIONS: addActionSettings(content); break;
            case COLORS: addColorSettings(content); break;
            case OPACITY: addOpacitySettings(content); break;
            case ADVANCED: addAdvancedSettings(content); break;
            default: addHome(content); break;
        }
        updateBackRegistration();
        updateStatus();
    }

    private static boolean validPage(String value) {
        return HOME.equals(value) || ACTIONS.equals(value) || COLORS.equals(value)
            || OPACITY.equals(value) || ADVANCED.equals(value);
    }

    private int pageTitle() {
        switch (page) {
            case ACTIONS: return R.string.controller_actions;
            case COLORS: return R.string.controller_color;
            case OPACITY: return R.string.idle_opacity;
            case ADVANCED: return R.string.advanced;
            default: return R.string.settings_title;
        }
    }

    private void addHome(LinearLayout layout) {
        status = text(R.string.controller_off, 18);
        status.setPadding(dp(8), dp(8), dp(8), dp(8));
        layout.addView(status);
        accessibilityButton = button(layout, R.string.enable_controller, this::openAccessibilitySettings);
        row(layout, R.string.controller_actions, getString(R.string.controller_actions_summary), () -> showPage(ACTIONS));
        row(layout, R.string.controller_color, colorName(ControllerPreferences.color(preferences)), () -> showPage(COLORS));
        row(layout, R.string.idle_opacity, getString(R.string.percent_spoken,
            OpacitySettings.normalize(preferences.getInt(OpacitySettings.KEY, OpacitySettings.DEFAULT_PERCENT))), () -> showPage(OPACITY));
        row(layout, R.string.advanced, getString(R.string.advanced_summary), () -> showPage(ADVANCED));
    }

    private void addActionSettings(LinearLayout layout) {
        int[] labels = { R.string.single_tap, R.string.double_tap, R.string.triple_tap };
        for (int count = 1; count <= 3; count++) {
            final int tapCount = count;
            final int label = labels[count - 1];
            SettingsRow action = row(layout, label, actionSummary(count), null);
            action.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(R.array.controller_action_choices,
                    ControllerPreferences.action(preferences, tapCount), (dialog, selected) -> {
                        dialog.dismiss();
                        if (selected == ControllerSettings.LAUNCH_APP) {
                            chooseApp(tapCount, action);
                        } else {
                            preferences.edit().putInt(ControllerSettings.actionKey(tapCount), selected).apply();
                            action.setSummary(actionSummary(tapCount));
                        }
                    })
                .setNegativeButton(android.R.string.cancel, null).show());
        }
        divider(layout);
        Switch adjust = new Switch(this);
        adjust.setText(R.string.adjust_zoom_level);
        adjust.setTextSize(20);
        adjust.setSingleLine(false);
        adjust.setMinHeight(dp(64));
        adjust.setPadding(dp(8), dp(12), dp(8), dp(8));
        adjust.setChecked(ControllerPreferences.adjustZoom(preferences));
        adjust.setOnCheckedChangeListener((view, checked) -> preferences.edit()
            .putBoolean(ControllerSettings.ADJUST_ZOOM_KEY, checked).apply());
        layout.addView(adjust, new LinearLayout.LayoutParams(-1, -2));
        TextView help = text(R.string.adjust_zoom_help, 17);
        help.setPadding(dp(8), 0, dp(8), dp(12));
        layout.addView(help);
    }

    private void addColorSettings(LinearLayout layout) {
        TextView help = text(R.string.color_help, 17);
        help.setPadding(dp(8), dp(8), dp(8), dp(12));
        layout.addView(help);
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        int selected = ControllerPreferences.color(preferences);
        for (int index = 0; index < COLORS_ARGB.length; index++) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(COLORS_ARGB[index]);
            option.setText(COLOR_LABELS[index]);
            option.setTextSize(20);
            option.setMinHeight(dp(64));
            option.setPadding(dp(8), dp(12), dp(12), dp(12));
            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(COLORS_ARGB[index]);
            swatch.setStroke(dp(1), themeColor(android.R.attr.textColorSecondary));
            swatch.setSize(dp(28), dp(28));
            option.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, swatch, null);
            option.setCompoundDrawablePadding(dp(12));
            group.addView(option, new RadioGroup.LayoutParams(-1, -2));
            if (COLORS_ARGB[index] == selected) group.check(option.getId());
        }
        group.setOnCheckedChangeListener((view, checkedId) -> {
            View checked = view.findViewById(checkedId);
            if (checked != null) preferences.edit().putInt(ControllerSettings.COLOR_KEY, (Integer) checked.getTag()).apply();
        });
        layout.addView(group);
    }

    private void addAdvancedSettings(LinearLayout layout) {
        addSizeSettings(layout);
        divider(layout);
        addDelaySettings(layout);
        divider(layout);
        heading(layout, R.string.help_title);
        button(layout, R.string.controls_help, () -> helpDialog(R.string.controls_help, R.string.controls_help_body));
        button(layout, R.string.privacy_help, () -> helpDialog(R.string.privacy_help, R.string.privacy_help_body));
        button(layout, R.string.prototype_limits, () -> helpDialog(R.string.prototype_limits, R.string.prototype_limits_body));
        divider(layout);
        heading(layout, R.string.diagnostics_title);
        status = text(R.string.controller_off, 17);
        status.setPadding(dp(8), dp(8), dp(8), dp(8));
        layout.addView(status);
        button(layout, R.string.retry_startup, () -> {
            if (ZoomService.current != null) ZoomService.current.retryStartup();
            else Toast.makeText(this, R.string.enable_service_first, Toast.LENGTH_SHORT).show();
            updateStatus();
        });
        button(layout, R.string.record_diagnostic, () -> {
            if (ZoomService.current != null) {
                ZoomService.current.startDiagnostic();
                Toast.makeText(this, R.string.recording_diagnostic, Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, R.string.enable_service_first, Toast.LENGTH_SHORT).show();
        });
        button(layout, R.string.clear_diagnostics, () -> {
            if (ZoomService.current != null) ZoomService.current.clearDiagnostic();
        });
        button(layout, R.string.view_diagnostics, this::showDiagnostics);
        divider(layout);
        heading(layout, R.string.service_title);
        button(layout, R.string.accessibility_settings, this::openAccessibilitySettings);
        button(layout, R.string.magnification_off, () -> {
            if (ZoomService.current != null) ZoomService.current.turnOff();
        });
        button(layout, R.string.disable_service, () -> {
            if (ZoomService.current != null) ZoomService.current.disableSelf();
        });
        TextView version = text(R.string.app_name, 16);
        version.setText(getString(R.string.app_version, BuildConfig.VERSION_NAME));
        version.setPadding(dp(8), dp(16), dp(8), dp(8));
        layout.addView(version);
    }

    private void addSizeSettings(LinearLayout layout) {
        heading(layout, R.string.pad_size_title);
        sizeValue = text(R.string.pad_size_title, 20);
        layout.addView(sizeValue);
        layout.addView(text(R.string.pad_size_short_help, 17));
        sizeSlider = new SeekBar(this);
        sizeSlider.setMin(PadSettings.MIN_DP);
        sizeSlider.setMax(PadSettings.MAX_DP);
        sizeSlider.setKeyProgressIncrement(PadSettings.STEP_DP);
        sizeSlider.setContentDescription(getString(R.string.pad_size_accessibility));
        sizeSlider.setMinimumHeight(dp(48));
        layout.addView(sizeSlider, new LinearLayout.LayoutParams(-1, -2));
        preview = new SizePreview();
        layout.addView(preview, new LinearLayout.LayoutParams(-1, dp(180)));
        // Stacked native buttons remain readable at maximum font scaling.
        button(layout, R.string.smaller, () -> setSize(sizeSlider.getProgress() - 20));
        button(layout, R.string.larger, () -> setSize(sizeSlider.getProgress() + 20));
        button(layout, R.string.reset_size, () -> setSize(PadSettings.DEFAULT_DP));
        sizeSlider.setOnSeekBarChangeListener(sliderListener(this::setSize));
        setSize(preferences.getInt(PadSettings.KEY, PadSettings.DEFAULT_DP));
    }

    private void addDelaySettings(LinearLayout layout) {
        heading(layout, R.string.activation_title);
        delayValue = text(R.string.activation_title, 20);
        layout.addView(delayValue);
        layout.addView(text(R.string.activation_short_help, 17));
        delaySlider = new SeekBar(this);
        delaySlider.setMin(ActivationSettings.MIN_MS);
        delaySlider.setMax(ActivationSettings.MAX_MS);
        delaySlider.setKeyProgressIncrement(ActivationSettings.STEP_MS);
        delaySlider.setMinimumHeight(dp(48));
        delaySlider.setContentDescription(getString(R.string.delay_accessibility));
        layout.addView(delaySlider, new LinearLayout.LayoutParams(-1, -2));
        delaySlider.setOnSeekBarChangeListener(sliderListener(this::setDelay));
        button(layout, R.string.zero_delay, () -> setDelay(0));
        button(layout, R.string.reset_delay, () -> setDelay(ActivationSettings.DEFAULT_MS));
        int initial = preferences.contains(ActivationSettings.KEY)
            ? preferences.getInt(ActivationSettings.KEY, ActivationSettings.DEFAULT_MS)
            : preferences.contains(ActivationSettings.LEGACY_KEY)
                ? ActivationSettings.migratedExtra(preferences.getInt(ActivationSettings.LEGACY_KEY, 1000))
                : ActivationSettings.DEFAULT_MS;
        setDelay(initial);
    }

    private void addOpacitySettings(LinearLayout layout) {
        opacityValue = text(R.string.idle_opacity, 24);
        opacityValue.setPadding(dp(8), dp(8), dp(8), dp(8));
        layout.addView(opacityValue);
        TextView help = text(R.string.opacity_short_help, 17);
        help.setPadding(dp(8), 0, dp(8), dp(12));
        layout.addView(help);
        opacitySlider = new SeekBar(this);
        opacitySlider.setMin(OpacitySettings.MIN_PERCENT);
        opacitySlider.setMax(OpacitySettings.MAX_PERCENT);
        opacitySlider.setKeyProgressIncrement(OpacitySettings.STEP_PERCENT);
        opacitySlider.setMinimumHeight(dp(48));
        opacitySlider.setContentDescription(getString(R.string.opacity_accessibility));
        layout.addView(opacitySlider, new LinearLayout.LayoutParams(-1, -2));
        opacitySlider.setOnSeekBarChangeListener(sliderListener(this::setOpacity));
        setOpacity(preferences.getInt(OpacitySettings.KEY, OpacitySettings.DEFAULT_PERCENT));
    }

    private interface SliderChange { void accept(int value); }
    private SeekBar.OnSeekBarChangeListener sliderListener(SliderChange change) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) change.accept(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        };
    }

    private void setDelay(int requested) {
        int value = ActivationSettings.normalize(requested);
        delaySlider.setProgress(value);
        delayValue.setText(getString(R.string.extra_delay_short_value, value, ActivationSettings.totalMs(value)));
        delaySlider.setStateDescription(getString(R.string.extra_delay_value, value, ActivationSettings.totalMs(value)));
        preferences.edit().putInt(ActivationSettings.KEY, value).apply();
    }

    private void setOpacity(int requested) {
        int value = OpacitySettings.normalize(requested);
        opacitySlider.setProgress(value);
        opacityValue.setText(getString(R.string.percent_spoken, value));
        opacitySlider.setStateDescription(getString(R.string.percent_spoken, value));
        preferences.edit().putInt(OpacitySettings.KEY, value).apply();
    }

    private void setSize(int requested) {
        int value = PadSettings.normalize(requested);
        sizeSlider.setProgress(value);
        sizeValue.setText(getString(R.string.pad_size_value, value));
        sizeSlider.setStateDescription(getString(R.string.dp_value, value));
        preferences.edit().putInt(PadSettings.KEY, value).apply();
        preview.invalidate();
        preview.setContentDescription(getString(R.string.pad_preview_accessibility, value));
    }

    private void showDiagnostics() {
        TextView report = text(R.string.diagnostics_title, 16);
        report.setTextIsSelectable(true);
        report.setText(diagnosticReport());
        report.setPadding(dp(16), dp(8), dp(16), dp(16));
        ScrollView scroller = new ScrollView(this);
        scroller.addView(report);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.local_diagnostics).setView(scroller)
            .setPositiveButton(R.string.close, null)
            .setNegativeButton(R.string.refresh, null)
            .setNeutralButton(R.string.copy_diagnostics, (d, which) -> {
                String latest = diagnosticReport();
                getSystemService(ClipboardManager.class).setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.local_diagnostics), latest));
            }).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener(v -> report.setText(diagnosticReport())));
        dialog.show();
    }

    private String diagnosticReport() {
        return ZoomService.current == null ? getString(R.string.diagnostics_unavailable)
            : ZoomService.current.diagnosticReport();
    }

    private void helpDialog(int title, int body) {
        TextView help = text(body, 18);
        help.setPadding(dp(24), dp(8), dp(24), dp(16));
        ScrollView scroller = new ScrollView(this);
        scroller.addView(help);
        new AlertDialog.Builder(this).setTitle(title).setView(scroller)
            .setPositiveButton(R.string.close, null).show();
    }

    private void updateStatus() {
        if (status == null) return;
        ZoomService service = ZoomService.current;
        boolean enabled = service != null;
        int state = !enabled ? R.string.controller_off
            : service.controllerReady() ? R.string.controller_on
            : service.controllerStarting() ? R.string.controller_starting
            : R.string.controller_unavailable;
        String message = ADVANCED.equals(page) && enabled
            ? service.controllerStatus() : getString(state);
        if (!TextUtils.equals(status.getText(), message)) status.setText(message);
        if (accessibilityButton != null) accessibilityButton.setText(enabled
            ? R.string.accessibility_settings : R.string.enable_controller);
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private String actionSummary(int tapCount) {
        int selected = ControllerPreferences.action(preferences, tapCount);
        if (selected != ControllerSettings.LAUNCH_APP) return actionName(selected);
        String saved = preferences.getString(ControllerSettings.appKey(tapCount), "");
        android.content.ComponentName component = android.content.ComponentName.unflattenFromString(saved);
        if (component != null) {
            try {
                android.content.pm.ActivityInfo info = getPackageManager().getActivityInfo(component, 0);
                return getString(R.string.launch_app_summary, info.loadLabel(getPackageManager()));
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        }
        return getString(R.string.launch_app_unavailable);
    }

    /** Show only launchable activities. Cancel never changes the saved action or target. */
    private void chooseApp(int tapCount, SettingsRow row) {
        android.content.pm.PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> apps = new java.util.ArrayList<>(
            pm.queryIntentActivities(query, 0));
        apps.removeIf(app -> app.activityInfo == null || !app.activityInfo.exported
            || !app.activityInfo.enabled || !app.activityInfo.applicationInfo.enabled);
        apps.sort(new android.content.pm.ResolveInfo.DisplayNameComparator(pm));
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_launchable_apps, Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] names = new CharSequence[apps.size()];
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (android.content.pm.ResolveInfo app : apps) {
            String name = app.loadLabel(pm).toString();
            counts.put(name, counts.getOrDefault(name, 0) + 1);
        }
        for (int i = 0; i < apps.size(); i++) {
            android.content.pm.ResolveInfo app = apps.get(i);
            String name = app.loadLabel(pm).toString();
            names[i] = counts.get(name) > 1 ? name + " (" + app.activityInfo.packageName + "/" + app.activityInfo.name + ")" : name;
        }
        new AlertDialog.Builder(this).setTitle(R.string.choose_app)
            .setItems(names, (dialog, which) -> {
                android.content.pm.ActivityInfo info = apps.get(which).activityInfo;
                String component = new android.content.ComponentName(info.packageName, info.name).flattenToString();
                preferences.edit().putString(ControllerSettings.appKey(tapCount), component)
                    .putInt(ControllerSettings.actionKey(tapCount), ControllerSettings.LAUNCH_APP).apply();
                row.setSummary(actionSummary(tapCount));
            }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private String actionName(int action) {
        return getResources().getStringArray(R.array.controller_action_choices)[ControllerSettings.normalizeAction(action)];
    }

    private String colorName(int color) {
        for (int index = 0; index < COLORS_ARGB.length; index++) {
            if (COLORS_ARGB[index] == color) return getString(COLOR_LABELS[index]);
        }
        return getString(R.string.color_white);
    }

    private void heading(LinearLayout parent, int label) {
        TextView view = text(label, 23);
        view.setAccessibilityHeading(true);
        view.setPadding(0, dp(12), 0, dp(8));
        parent.addView(view);
    }

    private TextView text(int label, float size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(themeColor(android.R.attr.textColorPrimary));
        return view;
    }

    private Button button(LinearLayout layout, int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setSingleLine(false);
        button.setOnClickListener(v -> action.run());
        layout.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private SettingsRow row(LinearLayout parent, int title, String summary, Runnable action) {
        SettingsRow row = new SettingsRow(title, summary);
        if (action != null) row.setOnClickListener(v -> action.run());
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private final class SettingsRow extends LinearLayout {
        private final TextView summary;
        SettingsRow(int title, String value) {
            super(MainActivity.this);
            setOrientation(VERTICAL);
            setGravity(android.view.Gravity.CENTER_VERTICAL);
            setPadding(dp(8), dp(16), dp(8), dp(16));
            setMinimumHeight(dp(72));
            setBackgroundResource(themeResource(android.R.attr.selectableItemBackground));
            setClickable(true);
            setFocusable(true);
            setContentDescription(getString(title));
            TextView label = text(title, 20);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(label);
            summary = text(title, 17);
            summary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            summary.setTextColor(themeColor(android.R.attr.textColorSecondary));
            summary.setPadding(0, dp(4), 0, 0);
            addView(summary);
            setSummary(value);
            setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(Button.class.getName());
                }
            });
        }
        void setSummary(String value) {
            summary.setText(value);
            setStateDescription(value);
        }
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(themeColor(android.R.attr.textColorSecondary));
        line.setAlpha(0.18f);
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(16);
        params.bottomMargin = dp(8);
        parent.addView(line, params);
    }

    private int themeColor(int attribute) {
        android.content.res.TypedArray colors = obtainStyledAttributes(new int[] { attribute });
        int color = colors.getColor(0, Color.BLACK);
        colors.recycle();
        return color;
    }

    private int themeResource(int attribute) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attribute, value, true);
        return value.resourceId;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class SizePreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SizePreview() {
            super(MainActivity.this);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Rect screen = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            float ratio = Math.min((getWidth() - dp(24)) / (float) screen.width(),
                (getHeight() - dp(24)) / (float) screen.height());
            float w = screen.width() * ratio, h = screen.height() * ratio;
            float x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(230, 235, 242));
            canvas.drawRoundRect(x, y, x + w, y + h, dp(8), dp(8), paint);
            float longSide = PadSettings.longSidePixels(sizeSlider.getProgress(),
                getResources().getDisplayMetrics().density, screen.width(), screen.height());
            float[] pad = Mapping.padSize(screen.width(), screen.height(), longSide);
            float pw = pad[0] * ratio, ph = pad[1] * ratio;
            paint.setColor(Color.rgb(40, 110, 180));
            canvas.drawRoundRect((getWidth() - pw) / 2, (getHeight() - ph) / 2,
                (getWidth() + pw) / 2, (getHeight() + ph) / 2, dp(3), dp(3), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(65, 75, 90));
            canvas.drawRoundRect(x, y, x + w, y + h, dp(8), dp(8), paint);
        }
    }

    private void updateBackRegistration() {
        boolean shouldRegister = !HOME.equals(page);
        if (shouldRegister && !backRegistered) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
            backRegistered = true;
        } else if (!shouldRegister && backRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
            backRegistered = false;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(PAGE_STATE, page);
        state.putInt(SCROLL_STATE, scroll.getScrollY());
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        main.removeCallbacks(refreshStatus);
        main.post(refreshStatus);
    }

    @Override protected void onPause() {
        main.removeCallbacks(refreshStatus);
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(refreshStatus);
        if (backRegistered) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
        super.onDestroy();
    }
}
