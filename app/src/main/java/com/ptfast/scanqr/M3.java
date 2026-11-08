package com.ptfast.scanqr;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.MaterialDynamicColors;
import com.google.android.material.color.utilities.SchemeTonalSpot;

/**
 * Material 3 / Material You 调色板 —— 界面里每个角色色都从这里取。
 *
 * <p>两条来源:
 *
 * <ul>
 *   <li><b>Android 12+</b>:系统已经从壁纸算好一整套 MY 色调(system_accent1_* / system_neutral1_*),
 *       直接读框架资源,和系统设置里的配色完全一致;</li>
 *   <li><b>Android 11 及以下</b>:系统没有这套资源,但同一个"种子色 → 整套配色"的算法就在
 *       Material 库里。这里用 {@link SchemeTonalSpot} + {@link MaterialDynamicColors} 现算,
 *       和 Android 12 的 MY 是同一份实现,不是近似。种子色按 {@link ColorSeed} 的优先级取:
 *       壁纸主色 → 壁纸图片量化 → 预设色。</li>
 * </ul>
 */
public final class M3 {

    /** 预设种子色的个数,和设置里的色块一一对应(自定义色号另算)。 */
    private static final int PRESET_COUNT = 6;

    public final boolean dark;
    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int secondaryContainer;
    public final int onSecondaryContainer;
    public final int surface;
    public final int onSurface;
    public final int surfaceVariant;
    public final int onSurfaceVariant;
    public final int outline;

    private M3(boolean dark, int primary, int onPrimary, int primaryContainer, int onPrimaryContainer,
               int secondaryContainer, int onSecondaryContainer, int surface, int onSurface,
               int surfaceVariant, int onSurfaceVariant, int outline) {
        this.dark = dark;
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.primaryContainer = primaryContainer;
        this.onPrimaryContainer = onPrimaryContainer;
        this.secondaryContainer = secondaryContainer;
        this.onSecondaryContainer = onSecondaryContainer;
        this.surface = surface;
        this.onSurface = onSurface;
        this.surfaceVariant = surfaceVariant;
        this.onSurfaceVariant = onSurfaceVariant;
        this.outline = outline;
    }

    /** 系统 MY 调色板在这台机器上是不是真的可用(Android 12+)。 */
    public static boolean systemPaletteAvailable() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static M3 of(Context context) {
        boolean dark = isDark(context);
        int accent = Prefs.accent(context);
        if (accent == Prefs.ACCENT_SYSTEM) {
            if (systemPaletteAvailable()) {
                return fromSystem(context, dark);
            }
            // Android 12 以下没有系统调色板:壁纸主色 → 壁纸图片量化 → 预设蓝。
            int wallpaper = ColorSeed.seed(context);
            return fromSeed(wallpaper != 0 ? wallpaper : ThemeUtil.presetSeed(0), dark);
        }
        int seed;
        if (accent >= 0 && accent < PRESET_COUNT) {
            seed = ThemeUtil.presetSeed(accent);
        } else if (accent == Prefs.ACCENT_CUSTOM) {
            seed = Prefs.customColor(context);
        } else {
            seed = ThemeUtil.presetSeed(0);
        }
        return fromSeed(seed, dark);
    }

    public static boolean isDark(Context context) {
        int mode = Prefs.darkMode(context);
        if (mode == Prefs.MODE_DARK) {
            return true;
        }
        if (mode == Prefs.MODE_LIGHT) {
            return false;
        }
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 原生 Material You:直接读系统从壁纸生成的色调。 */
    private static M3 fromSystem(Context context, boolean dark) {
        Resources res = context.getResources();
        Resources.Theme theme = context.getTheme();
        int primary = res.getColor(dark ? android.R.color.system_accent1_200
                : android.R.color.system_accent1_600, theme);
        int onPrimary = res.getColor(dark ? android.R.color.system_accent1_1000
                : android.R.color.system_accent1_0, theme);
        int primaryContainer = res.getColor(dark ? android.R.color.system_accent1_700
                : android.R.color.system_accent1_100, theme);
        int onPrimaryContainer = res.getColor(dark ? android.R.color.system_accent1_100
                : android.R.color.system_accent1_900, theme);
        int secondaryContainer = res.getColor(dark ? android.R.color.system_accent2_700
                : android.R.color.system_accent2_100, theme);
        int onSecondaryContainer = res.getColor(dark ? android.R.color.system_accent2_100
                : android.R.color.system_accent2_900, theme);
        int surface = res.getColor(dark ? android.R.color.system_neutral1_900
                : android.R.color.system_neutral1_10, theme);
        int onSurface = res.getColor(dark ? android.R.color.system_neutral1_100
                : android.R.color.system_neutral1_900, theme);
        int surfaceVariant = res.getColor(dark ? android.R.color.system_neutral2_800
                : android.R.color.system_neutral2_100, theme);
        int onSurfaceVariant = res.getColor(dark ? android.R.color.system_neutral2_200
                : android.R.color.system_neutral2_700, theme);
        int outline = res.getColor(android.R.color.system_neutral2_500, theme);
        return new M3(dark, primary, onPrimary, primaryContainer, onPrimaryContainer,
                secondaryContainer, onSecondaryContainer, surface, onSurface,
                surfaceVariant, onSurfaceVariant, outline);
    }

    /**
     * 种子色 → 完整 M3 配色,用 Material 库自带的官方实现:种子色进 HCT,再按
     * SchemeTonalSpot(默认的 MY 方案)生成各档 tone,MaterialDynamicColors 把每个角色
     * 解析成实际颜色。
     */
    private static M3 fromSeed(int seed, boolean dark) {
        DynamicScheme scheme = new SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0);
        MaterialDynamicColors roles = new MaterialDynamicColors();
        return new M3(dark,
                roles.primary().getArgb(scheme),
                roles.onPrimary().getArgb(scheme),
                roles.primaryContainer().getArgb(scheme),
                roles.onPrimaryContainer().getArgb(scheme),
                roles.secondaryContainer().getArgb(scheme),
                roles.onSecondaryContainer().getArgb(scheme),
                roles.surface().getArgb(scheme),
                roles.onSurface().getArgb(scheme),
                roles.surfaceVariant().getArgb(scheme),
                roles.onSurfaceVariant().getArgb(scheme),
                roles.outline().getArgb(scheme));
    }

    /** 「系统主题色」色块显示的颜色;老系统上显示从壁纸取到的种子色。 */
    @SuppressWarnings("deprecation")
    public static int systemSeed(Context context) {
        if (!systemPaletteAvailable()) {
            int wallpaper = ColorSeed.seed(context);
            return wallpaper != 0 ? wallpaper : ThemeUtil.presetSeed(0);
        }
        return context.getResources().getColor(android.R.color.system_accent1_500,
                context.getTheme());
    }
}
