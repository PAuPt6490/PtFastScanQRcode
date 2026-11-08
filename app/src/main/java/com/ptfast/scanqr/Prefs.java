package com.ptfast.scanqr;

import android.content.Context;
import android.content.SharedPreferences;

/** 所有用户设置集中在这里,SharedPreferences 一个文件,免得散落。 */
public final class Prefs {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    /** 颜色预设数量;等于这个值表示用系统主题色。 */
    public static final int ACCENT_SYSTEM = 6;
    /** 自定义色号:色值存在 custom_color 里。 */
    public static final int ACCENT_CUSTOM = 7;

    private static final String NAME = "settings";

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int darkMode(Context c) {
        return get(c).getInt("dark_mode", MODE_SYSTEM);
    }

    public static void setDarkMode(Context c, int value) {
        get(c).edit().putInt("dark_mode", value).apply();
    }

    public static int accent(Context c) {
        return get(c).getInt("accent", 0);
    }

    public static void setAccent(Context c, int value) {
        get(c).edit().putInt("accent", value).apply();
    }

    /** 自定义种子色,默认靛蓝。 */
    public static int customColor(Context c) {
        return get(c).getInt("custom_color", 0xFF3F51B5);
    }

    public static void setCustomColor(Context c, int color) {
        get(c).edit().putInt("custom_color", color | 0xFF000000).apply();
    }

    public static boolean showCamera(Context c) {
        return get(c).getBoolean("show_camera", true);
    }

    public static boolean showTorch(Context c) {
        return get(c).getBoolean("show_torch", true);
    }

    public static boolean showGallery(Context c) {
        return get(c).getBoolean("show_gallery", true);
    }

    public static boolean showHistory(Context c) {
        return get(c).getBoolean("show_history", true);
    }

    public static boolean vibrate(Context c) {
        return get(c).getBoolean("vibrate", true);
    }

    public static void setFlag(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    /** 取景框中心相对视图高度的偏移百分比,正数向下。 */
    public static float frameOffset(Context c) {
        return get(c).getFloat("frame_offset", 0f);
    }

    public static void setFrameOffset(Context c, float value) {
        get(c).edit().putFloat("frame_offset", value).apply();
    }

    /** 解码帧率上限(帧/秒),60 表示每帧都解。 */
    public static int decodeFps(Context c) {
        return get(c).getInt("decode_fps", 30);
    }

    public static void setDecodeFps(Context c, int value) {
        get(c).edit().putInt("decode_fps", value).apply();
    }

    /** 音量键双击启动:需要无障碍服务,默认关。 */
    public static boolean volumeKey(Context c) {
        return get(c).getBoolean("volume_key", false);
    }

    public static void setVolumeKey(Context c, boolean value) {
        get(c).edit().putBoolean("volume_key", value).apply();
    }
}
