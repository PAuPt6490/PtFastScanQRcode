package com.ptfast.scanqr;

import android.content.ComponentName;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.google.android.material.radiobutton.MaterialRadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * 设置界面:全部用系统控件在代码里搭,不额外占 XML 和图片资源。
 * 改主题/强调色后 recreate() 立刻生效。
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int ACCENT_COUNT = Prefs.ACCENT_CUSTOM + 1;
    private static final int REQ_PICK_COLOR = 11;

    private LinearLayout content;
    private final com.google.android.material.button.MaterialButton[] dots =
            new com.google.android.material.button.MaterialButton[ACCENT_COUNT];
    private int accentValue;
    private M3 m3;
    private com.google.android.material.textfield.TextInputEditText hexField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.apply(this);
        m3 = M3.of(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(m3.surface);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(m3.surface);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        accentValue = Prefs.accent(this);
        buildUi();
    }

    private void buildUi() {
        content.removeAllViews();

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(m3.onSurface);
        content.addView(title, marginTop(0));

        addSection(R.string.sec_appearance);
        addDarkMode();
        addAccent();

        addSection(R.string.sec_ui);
        addSwitch(R.string.label_show_camera, null, Prefs.showCamera(this), "show_camera");
        addSwitch(R.string.label_show_torch, null, Prefs.showTorch(this), "show_torch");
        addSwitch(R.string.label_show_gallery, null, Prefs.showGallery(this), "show_gallery");
        addSwitch(R.string.label_show_history, null, Prefs.showHistory(this), "show_history");
        addSwitch(R.string.label_vibrate, null, Prefs.vibrate(this), "vibrate");
        addFpsRow();

        addSection(R.string.sec_launch);
        addInfo(R.string.launch_shortcut, getString(R.string.launch_shortcut_desc));
        addTileRow();
        addVolumeRow();

        TextView version = new TextView(this);
        version.setText(R.string.version_label);
        version.setTextSize(12);
        version.setGravity(Gravity.CENTER);
        version.setTextColor(m3.onSurfaceVariant);
        content.addView(version, marginTop(28));
    }

    private void addDarkMode() {
        int mode = Prefs.darkMode(this);
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        int[] ids = {R.string.mode_system, R.string.mode_light, R.string.mode_dark};
        for (int i = 0; i < ids.length; i++) {
            MaterialRadioButton button = new MaterialRadioButton(this);
            button.setText(ids[i]);
            button.setId(1000 + i);
            button.setTextColor(m3.onSurface);
            M3Ui.tintRadio(button, m3);
            group.addView(button);
            if (i == mode) {
                button.setChecked(true);
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            int value = checkedId - 1000;
            if (value == Prefs.darkMode(this)) {
                return;
            }
            Prefs.setDarkMode(this, value);
            recreate();
        });
        content.addView(group, marginTop(4));
    }

    private void addAccent() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < ACCENT_COUNT; i++) {
            final int index = i;
            com.google.android.material.button.MaterialButton dot =
                    new com.google.android.material.button.MaterialButton(this);
            dot.setGravity(Gravity.CENTER);
            dot.setTextSize(14);
            dot.setAllCaps(false);
            dot.setInsetTop(0);
            dot.setInsetBottom(0);
            dot.setMinWidth(0);
            dot.setMinimumWidth(0);
            dot.setMinHeight(0);
            dot.setMinimumHeight(0);
            dot.setPadding(0, 0, 0, 0);
            dot.setShapeAppearanceModel(new com.google.android.material.shape.ShapeAppearanceModel
                    .Builder()
                    .setAllCorners(com.google.android.material.shape.CornerFamily.ROUNDED, dp(17))
                    .build());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMarginEnd(dp(6));
            dot.setLayoutParams(lp);
            dot.setOnClickListener(v -> {
                if (index == accentValue) {
                    return;
                }
                Prefs.setAccent(this, index);
                recreate();
            });
            row.addView(dot);
            dots[i] = dot;
        }
        content.addView(row, marginTop(10));
        addCustomColor();
        TextView hint = new TextView(this);
        hint.setText(R.string.accent_system);
        hint.setTextSize(12);
        hint.setTextColor(m3.onSurfaceVariant);
        content.addView(hint, marginTop(6));
        addColorSource();
        refreshDots();
    }

    /**
     * 取色来源:说明「系统主题色」这个色块的种子色是从哪儿来的,并给一个单独的「从图片取色」入口。
     * 老系统取不到壁纸颜色时,这里会明确告诉用户,而不是悄悄显示一个假的系统色。
     */
    private void addColorSource() {
        TextView title = new TextView(this);
        title.setTextAppearance(R.style.PtText_Section);
        title.setText(R.string.color_source);
        title.setTextColor(m3.onSurface);
        content.addView(title, marginTop(16));
        addSummary(colorSourceText());

        MaterialButton pick = new MaterialButton(this);
        pick.setText(R.string.pick_from_image);
        pick.setAllCaps(false);
        pick.setMinWidth(0);
        pick.setMinimumWidth(0);
        pick.setMinHeight(M3Ui.dp(this, 44f));
        pick.setPadding(M3Ui.dp(this, 18f), 0, M3Ui.dp(this, 18f), 0);
        M3Ui.tonal(pick, m3);
        pick.setOnClickListener(v -> pickColorImage());
        content.addView(pick, marginTop(8));
    }

    private String colorSourceText() {
        if (M3.systemPaletteAvailable()) {
            return getString(R.string.color_source_system);
        }
        int seed = ColorSeed.seed(this);
        if (seed == 0) {
            return getString(R.string.color_source_none);
        }
        return getString(ColorSeed.source() == ColorSeed.SOURCE_WALLPAPER_BITMAP
                ? R.string.color_source_bitmap
                : R.string.color_source_wallpaper);
    }

    private void pickColorImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_PICK_COLOR);
        } catch (Exception e) {
            Toast.makeText(this, R.string.pick_from_image_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_COLOR || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Bitmap bitmap = null;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            bitmap = BitmapFactory.decodeStream(in, null, options);
        } catch (Exception ignored) {
            bitmap = null;
        }
        int seed = ColorSeed.seedFromBitmap(bitmap);
        if (bitmap != null) {
            bitmap.recycle();
        }
        if (seed == 0) {
            Toast.makeText(this, R.string.pick_from_image_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        // 从图片取色 = 用户的明确选择,直接写进自定义色号并切过去。
        Prefs.setCustomColor(this, seed);
        Prefs.setAccent(this, Prefs.ACCENT_CUSTOM);
        recreate();
    }

    /** 某个色块该显示的颜色:系统色取壁纸色,自定义取用户色号,其余取预设。 */
    private int dotFill(int index) {
        if (index == Prefs.ACCENT_SYSTEM) {
            return M3.systemSeed(this);
        }
        if (index == Prefs.ACCENT_CUSTOM) {
            return Prefs.customColor(this);
        }
        return ThemeUtil.presetTint(index);
    }

    private void refreshDots() {
        for (int i = 0; i < ACCENT_COUNT; i++) {
            int fill = dotFill(i);
            dots[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(fill));
            boolean selected = i == accentValue;
            if (selected) {
                dots[i].setStrokeWidth(M3Ui.dp(this, 3f));
                dots[i].setStrokeColor(android.content.res.ColorStateList.valueOf(m3.onSurface));
                dots[i].setText("✓");
            } else {
                dots[i].setStrokeWidth(0);
                dots[i].setText("");
            }
            dots[i].setTextColor(contrastOn(fill));
        }
        if (hexField != null) {
            int shown = dotFill(accentValue);
            String text = String.format(java.util.Locale.US, "#%06X", shown & 0xFFFFFF);
            if (!text.contentEquals(hexField.getText())) {
                hexField.setText(text);
            }
        }
    }

    /** 自定义色号:输入 #RRGGBB,回车或点「应用」后整套调色板从这颜色生成。 */
    private void addCustomColor() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        TextView label = new TextView(this);
        label.setText(R.string.custom_accent);
        label.setTextSize(14);
        label.setTextColor(m3.onSurfaceVariant);
        row.addView(label);

        final com.google.android.material.textfield.TextInputLayout hexBox =
                new com.google.android.material.textfield.TextInputLayout(this);
        hexBox.setBoxBackgroundMode(
                com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        hexBox.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
        hexBox.setHint(getString(R.string.custom_accent_hint));
        hexBox.setBoxStrokeColor(m3.outline);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        boxLp.setMarginStart(dp(10));
        hexBox.setLayoutParams(boxLp);
        final com.google.android.material.textfield.TextInputEditText hex =
                new com.google.android.material.textfield.TextInputEditText(this);
        hex.setSingleLine(true);
        hex.setTextSize(15);
        hex.setTypeface(android.graphics.Typeface.MONOSPACE);
        hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
        hex.setTextColor(m3.onSurface);
        hex.setText(String.format(java.util.Locale.US, "#%06X", Prefs.customColor(this) & 0xFFFFFF));
        hexField = hex;
        hexBox.addView(hex);
        hex.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                // 只等「应用」,打字过程中不落盘也不刷新色块,免得选中圈跳来跳去
            }
        });
        row.addView(hexBox);

        MaterialButton apply = new MaterialButton(this);
        apply.setText(R.string.apply);
        apply.setTextSize(13);
        apply.setMinWidth(0);
        apply.setMinimumWidth(0);
        apply.setInsetTop(0);
        apply.setInsetBottom(0);
        apply.setMinHeight(0);
        apply.setMinimumHeight(0);
        apply.setPadding(dp(14), dp(6), dp(14), dp(6));
        M3Ui.filled(apply, m3);
        apply.setOnClickListener(v -> {
            if (parseHex(hex.getText().toString()) == 0) {
                Toast.makeText(this, R.string.custom_accent_bad, Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.setCustomColor(this, parseHex(hex.getText().toString()));
            Prefs.setAccent(this, Prefs.ACCENT_CUSTOM);
            recreate();
            return;
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMarginStart(dp(8));
        apply.setLayoutParams(bp);
        row.addView(apply);
        content.addView(row);
    }

    /** 解析 #RRGGBB / RRGGBB,失败返回 0。 */
    private static int parseHex(String text) {
        if (text == null) {
            return 0;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return 0;
        }
        for (int i = 0; i < 6; i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return 0;
            }
        }
        return (int) (0xFF000000L | Long.parseLong(value, 16));
    }

    private void addSwitch(int titleRes, String summary, boolean value, String key) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(titleRes);
        toggle.setTextSize(15);
        toggle.setTextColor(m3.onSurface);
        toggle.setChecked(value);
        M3Ui.tintSwitch(toggle, m3);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> Prefs.setFlag(this, key, isChecked));
        content.addView(toggle, marginTop(6));
        if (summary != null) {
            addSummary(summary);
        }
    }

    private void addInfo(int titleRes, String summary) {
        TextView title = new TextView(this);
        title.setTextAppearance(R.style.PtText_Title);
        title.setText(titleRes);
        title.setTextColor(m3.onSurface);
        content.addView(title, marginTop(12));
        addSummary(summary);
    }

    private void addSummary(String text) {
        TextView hint = new TextView(this);
        hint.setTextAppearance(R.style.PtText_Summary);
        hint.setText(text);
        hint.setTextColor(m3.onSurfaceVariant);
        content.addView(hint, marginTop(2));
    }

    private void addTileRow() {
        MaterialButton add = new MaterialButton(this);
        add.setText(R.string.launch_tile_add);
        add.setAllCaps(false);
        add.setMinHeight(M3Ui.dp(this, 48f));
        add.setPadding(M3Ui.dp(this, 20f), 0, M3Ui.dp(this, 20f), 0);
        M3Ui.filled(add, m3);
        add.setOnClickListener(v -> requestTile());
        content.addView(add, marginTop(6));
    }

    private void requestTile() {
        if (Build.VERSION.SDK_INT < 24) {
            Toast.makeText(this, R.string.launch_tile_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.StatusBarManager manager = getSystemService(android.app.StatusBarManager.class);
            if (manager != null) {
                Icon icon = Icon.createWithResource(this, R.mipmap.ic_launcher);
                manager.requestAddTileService(new ComponentName(this, ScanTileService.class),
                        getString(R.string.shortcut_scan), icon, getMainExecutor(),
                        result -> runOnUiThread(() -> Toast.makeText(this,
                                result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                        ? R.string.launch_tile_added : R.string.launch_tile_manual,
                                Toast.LENGTH_SHORT).show()));
                return;
            }
        }
        Toast.makeText(this, R.string.launch_tile_manual, Toast.LENGTH_LONG).show();
    }

    private void addVolumeRow() {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(R.string.launch_volume);
        toggle.setTextSize(15);
        toggle.setTextColor(m3.onSurface);
        toggle.setChecked(Prefs.volumeKey(this));
        M3Ui.tintSwitch(toggle, m3);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Prefs.setVolumeKey(this, isChecked);
            if (isChecked && !accessibilityEnabled()) {
                Toast.makeText(this, R.string.launch_volume_enable, Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        content.addView(toggle, marginTop(12));
        addSummary(getString(R.string.launch_volume_desc));
    }

    public static boolean accessibilityEnabled(Activity activity) {
        ComponentName component = new ComponentName(activity, QuickLaunchService.class);
        String enabled = Settings.Secure.getString(activity.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || !enabled.contains(component.flattenToString())) {
            return false;
        }
        return Settings.Secure.getInt(activity.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
    }

    private boolean accessibilityEnabled() {
        return accessibilityEnabled(this);
    }

    /** 解码帧率:1-60,数值越大越费电;60 等于每帧都解。 */
    private void addFpsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(R.string.label_decode_fps);
        label.setTextSize(15);
        label.setTextColor(m3.onSurface);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final com.google.android.material.textfield.TextInputLayout fpsBox =
                new com.google.android.material.textfield.TextInputLayout(this);
        fpsBox.setBoxBackgroundMode(
                com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        fpsBox.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
        fpsBox.setBoxStrokeColor(m3.outline);
        final com.google.android.material.textfield.TextInputEditText input =
                new com.google.android.material.textfield.TextInputEditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setGravity(Gravity.END);
        input.setText("" + Prefs.decodeFps(this));
        input.setTextColor(m3.onSurface);
        fpsBox.addView(input);
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                int value = parse(s.toString());
                if (value >= 1 && value <= 60) {
                    Prefs.setDecodeFps(SettingsActivity.this, value);
                }
            }
        });
        row.addView(fpsBox, new LinearLayout.LayoutParams(dp(96),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView unit = new TextView(this);
        unit.setText(R.string.unit_fps);
        unit.setTextSize(12);
        unit.setTextColor(m3.onSurfaceVariant);
        row.addView(unit);
        content.addView(row, marginTop(8));
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void addSection(int titleRes) {
        View line = new View(this);
        line.setBackgroundColor(M3Ui.withAlpha(m3.outline, 0x40));
        content.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        TextView title = new TextView(this);
        title.setTextAppearance(R.style.PtText_Section);
        title.setText(titleRes);
        title.setTextColor(m3.primary);
        content.addView(title, marginTop(20));
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Picks black or white text for a swatch, M3 style: use the surface's on-colour side. */
    private static int contrastOn(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        return luma > 150 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private int colorAttr(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return value.resourceId != 0 ? getResources().getColor(value.resourceId, getTheme()) : 0xFFFFFFFF;
    }
}
