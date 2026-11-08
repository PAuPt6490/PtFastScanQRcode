package com.ptfast.scanqr;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Material 3 的几个容器样式。
 *
 * <p>按钮现在是 Material 库的 MaterialButton(自带 M3 的 ripple、状态层、圆角 token),
 * 这里只负责把主题色刷到 backgroundTint / textColor 上;卡片、色块仍用 GradientDrawable 画。
 * 没有 MaterialButton 的地方(极少数)回退到手绘圆角背景。
 */
public final class M3Ui {

    private M3Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    public static GradientDrawable outline(Context context, int color, int strokeColor, float radiusDp) {
        GradientDrawable shape = rounded(context, color, radiusDp);
        shape.setStroke(Math.max(1, dp(context, 1f)), strokeColor);
        return shape;
    }

    /** 把背景/文字色刷上去;MaterialButton 走 tint,保留它的 ripple 与状态层。 */
    private static void paint(TextView view, int background, int foreground) {
        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            // 单状态 ColorStateList:库里自带的 tint 会把 elevation overlay 叠进去,颜色就不准了
            button.setBackgroundTintList(flat(background));
            button.setTextColor(foreground);
            button.setRippleColor(ColorStateList.valueOf(withAlpha(foreground, 0x33)));
            button.setStrokeWidth(0);
            return;
        }
        view.setBackground(rounded(view.getContext(), background, 999f));
        view.setTextColor(foreground);
    }

    /** 恒定色:MaterialButton 自带的 ColorStateList 会把 disabled/elevation 叠色算进去,这里压成单色。 */
    public static ColorStateList flat(int color) {
        return new ColorStateList(new int[][]{new int[]{}}, new int[]{color});
    }

    /** Filled button:主色底 + onPrimary 字。 */
    public static void filled(TextView view, M3 m3) {
        paint(view, m3.primary, m3.onPrimary);
    }

    /** Tonal button:secondaryContainer 底 + onSecondaryContainer 字。 */
    public static void tonal(TextView view, M3 m3) {
        paint(view, m3.secondaryContainer, m3.onSecondaryContainer);
    }

    /** Text button:透明底 + 主色字。 */
    public static void text(TextView view, M3 m3) {
        paint(view, 0x00000000, m3.primary);
    }

    /** 顶部按钮:primaryContainer 底 + onPrimaryContainer 字。 */
    public static void chip(TextView view, M3 m3) {
        paint(view, m3.primaryContainer, m3.onPrimaryContainer);
    }

    /** 压在相机画面上的半透明胶囊(旧样式,保留给需要的地方)。 */
    public static void chip(TextView view) {
        paint(view, 0x66000000, 0xFFFFFFFF);
    }

    /** 底部卡片:28dp 圆角 + surface 底。 */
    public static GradientDrawable card(Context context, M3 m3) {
        return card(context, m3, m3.surface);
    }

    /** 同上,但底色调成调用方给的(浅色主题用纯白)。 */
    public static GradientDrawable card(Context context, M3 m3, int color) {
        GradientDrawable shape = rounded(context, color, 28f);
        shape.setStroke(Math.max(1, dp(context, 1f)), withAlpha(m3.outline, 0x40));
        return shape;
    }

    public static void tintSwitch(CompoundButton button, M3 m3) {
        ColorStateList thumb = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                new int[]{m3.primary, m3.outline});
        ColorStateList track = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                new int[]{withAlpha(m3.primary, 0x80), m3.surfaceVariant});
        if (button instanceof androidx.appcompat.widget.SwitchCompat) {
            androidx.appcompat.widget.SwitchCompat toggle = (androidx.appcompat.widget.SwitchCompat) button;
            toggle.setThumbTintList(thumb);
            toggle.setTrackTintList(track);
            return;
        }
        button.setButtonTintList(thumb);
    }

    public static void tintRadio(CompoundButton button, M3 m3) {
        ColorStateList tint = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                new int[]{m3.primary, m3.onSurfaceVariant});
        button.setButtonTintList(tint);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
