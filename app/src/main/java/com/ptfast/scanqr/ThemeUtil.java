package com.ptfast.scanqr;

import android.app.Activity;
import android.os.Build;

/** 主题只有「深浅模式 × 强调色」两维,组合在 Activity 启动时一次性套上。 */
public final class ThemeUtil {

    private static final int[] ACCENTS = {
            R.style.Accent_Blue,
            R.style.Accent_Green,
            R.style.Accent_Purple,
            R.style.Accent_Orange,
            R.style.Accent_Red,
            R.style.Accent_Cyan,
    };

    private ThemeUtil() {
    }

    public static void apply(Activity activity) {
        int mode = Prefs.darkMode(activity);
        if (mode == Prefs.MODE_DARK) {
            activity.setTheme(R.style.Theme_PtScan_Dark);
        } else if (mode == Prefs.MODE_LIGHT) {
            activity.setTheme(R.style.Theme_PtScan_Light);
        } else {
            activity.setTheme(R.style.Theme_PtScan);
        }

        int accent = Prefs.accent(activity);
        // 必须在 setTheme 之后套,否则动态配色会被 setTheme 整个换掉(只剩 M3 默认紫)
        if (M3.systemPaletteAvailable() && accent == Prefs.ACCENT_SYSTEM) {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(activity);
        }
        if (accent == Prefs.ACCENT_SYSTEM) {
            int style = Build.VERSION.SDK_INT >= 31 ? R.style.Accent_System : R.style.Accent_Blue;
            activity.getTheme().applyStyle(style, true);
        } else if (accent >= 0 && accent < ACCENTS.length) {
            activity.getTheme().applyStyle(ACCENTS[accent], true);
        }
    }

    /**
     * 自定义色号预设:用户只写 (0xRRGGBB, 色号名义) 这类配置,真正的配色按种子里生成,
     * 见 {@link #presetSeed(int)} / {@link #presetTint(int)}。
     */
    private static final int[] PRESETS = {
            0xFF4285F4, // 蓝
            0xFF34A853, // 绿
            0xFF9C27B0, // 紫
            0xFFFF6D00, // 橙
            0xFFE53935, // 红
            0xFF00ACC1, // 青
    };

    /** 预设色块本身显示的颜色(接近用户选的那个色相,不走 tone 映射)。 */
    public static int presetTint(int index) {
        if (index >= 0 && index < PRESETS.length) {
            return 0xFF000000 | PRESETS[index];
        }
        return 0xFF4285F4;
    }

    /** 预设色块对应的调色种子,整套 M3 角色色都从它生成。 */
    public static int presetSeed(int index) {
        return presetTint(index);
    }

    /** 设置界面里强调色的预览色,和上面的样式表保持一致。 */
    public static int accentColor(Activity activity, int index) {
        switch (index) {
            case 0:
                return 0xFF1A73E8;
            case 1:
                return 0xFF1E8E3E;
            case 2:
                return 0xFF7B1FA2;
            case 3:
                return 0xFFE8710A;
            case 4:
                return 0xFFD93025;
            case 5:
                return 0xFF00838F;
            case 7:
                return Prefs.customColor(activity);
            default:
                if (Build.VERSION.SDK_INT >= 31) {
                    return activity.getResources().getColor(android.R.color.system_accent1_500, activity.getTheme());
                }
                return 0xFF1A73E8;
        }
    }
}
