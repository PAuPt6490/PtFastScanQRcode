package com.ptfast.scanqr;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.QuantizerCelebi;
import com.google.android.material.color.utilities.Score;

import java.util.List;
import java.util.Map;

/**
 * 种子色从哪来。
 *
 * <p>Material You 的配色只取决于一个种子色,问题在于老系统没有系统调色板时去哪儿拿这个色。
 * 按用户定的优先级走:
 *
 * <ol>
 *   <li>系统 MY(Android 12+):直接读系统调色板,不经过这里,由 {@link M3#fromSystem} 负责;</li>
 *   <li>{@code WallpaperManager.getWallpaperColors}(Android 8.1+):系统从壁纸算好的主色,
 *       最省事也最接近系统设置里的观感;</li>
 *   <li>{@code WallpaperManager.getDrawable} 取壁纸位图,缩到 96×96 后用 Material 库自带的
 *       Celebi 量化器算出主色 —— 静态壁纸几乎总能拿到;</li>
 *   <li>都拿不到(例如视频动态壁纸):返回 0,界面提示"本机壁纸取不到颜色",让用户改选预设色
 *       或用「从图片取色」。</li>
 * </ol>
 */
public final class ColorSeed {

    /** 种子色来自系统调色板(Android 12+)。 */
    public static final int SOURCE_SYSTEM = 0;
    /** 种子色来自 WallpaperColors。 */
    public static final int SOURCE_WALLPAPER_COLORS = 1;
    /** 种子色来自壁纸位图量化。 */
    public static final int SOURCE_WALLPAPER_BITMAP = 2;
    /** 取不到种子色。 */
    public static final int SOURCE_NONE = 3;

    /** 量化用的采样边长:96×96 已经足够稳定,再大只是浪费一次分配。 */
    private static final int SAMPLE = 96;

    private static boolean cached;
    private static int cachedSeed;
    private static int cachedSource = SOURCE_NONE;

    private ColorSeed() {
    }

    /** 壁纸换了(或用户手动要求重新取色)时调一次。 */
    public static void invalidate() {
        cached = false;
    }

    /** 上一次 {@link #seed(Context)} 走的是哪条路。 */
    public static int source() {
        return cachedSource;
    }

    /** 取壁纸种子色,取不到返回 0。结果会缓存,壁纸变化后由 {@link #invalidate()} 清掉。 */
    public static int seed(Context context) {
        if (cached) {
            return cachedSeed;
        }
        cached = true;
        cachedSeed = 0;
        cachedSource = SOURCE_NONE;

        int fromColors = fromWallpaperColors(context);
        if (fromColors != 0) {
            cachedSeed = fromColors;
            cachedSource = SOURCE_WALLPAPER_COLORS;
            return cachedSeed;
        }
        int fromBitmap = fromWallpaperBitmap(context);
        if (fromBitmap != 0) {
            cachedSeed = fromBitmap;
            cachedSource = SOURCE_WALLPAPER_BITMAP;
        }
        return cachedSeed;
    }

    /** 系统从壁纸算好的主色里挑最鲜艳的一个。 */
    private static int fromWallpaperColors(Context context) {
        if (Build.VERSION.SDK_INT < 27) {
            return 0;
        }
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            WallpaperColors colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors == null) {
                return 0;
            }
            int best = 0;
            double bestChroma = 6.0;
            Color[] candidates = {colors.getPrimaryColor(), colors.getSecondaryColor(),
                    colors.getTertiaryColor()};
            for (Color color : candidates) {
                if (color == null) {
                    continue;
                }
                int argb = color.toArgb();
                if (Color.alpha(argb) < 0x80) {
                    continue;
                }
                double chroma = Hct.fromInt(argb).getChroma();
                if (chroma > bestChroma) {
                    bestChroma = chroma;
                    best = argb;
                }
            }
            return best;
        } catch (Throwable ignored) {
            // 有的 ROM 上取壁纸颜色会抛 SecurityException / NotSupported,退到下一条路。
            return 0;
        }
    }

    /** 把壁纸画到一张小位图上再量化。 */
    private static int fromWallpaperBitmap(Context context) {
        Bitmap bitmap = null;
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            Drawable drawable = manager.getDrawable();
            if (drawable == null) {
                return 0;
            }
            bitmap = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, SAMPLE, SAMPLE);
            drawable.draw(canvas);
            return seedFromBitmap(bitmap);
        } catch (Throwable ignored) {
            return 0;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    /**
     * 给任意一张图取种子色(设置里的「从图片取色」走这里)。
     * 量化后用 Score 排序,取最“能代表这张图”的那个颜色;取不到返回 0。
     */
    public static int seedFromBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0;
        }
        Bitmap sample = null;
        try {
            sample = Bitmap.createScaledBitmap(bitmap, SAMPLE, SAMPLE, true);
            int[] pixels = new int[SAMPLE * SAMPLE];
            sample.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE);

            // 全透明像素会把量化拉向黑色,先清成白色。
            for (int i = 0; i < pixels.length; i++) {
                if (Color.alpha(pixels[i]) < 0x40) {
                    pixels[i] = 0xFFFFFFFF;
                } else {
                    pixels[i] |= 0xFF000000;
                }
            }
            Map<Integer, Integer> counts = QuantizerCelebi.quantize(pixels, 128);
            List<Integer> ranked = Score.score(counts);
            // Score 已经排过序,但暗图/纯黑白图会把接近黑色的颜色排到前面 —— 这种种子色生成出来
            // 整套配色都是灰的。跳过太黑、太灰的候选,取第一个还能当种子用的。
            for (int candidate : ranked) {
                Hct hct = Hct.fromInt(candidate);
                if (hct.getChroma() >= 12.0 && hct.getTone() >= 20.0 && hct.getTone() <= 90.0) {
                    return candidate;
                }
            }
            return ranked.isEmpty() ? 0 : ranked.get(0);
        } catch (Throwable ignored) {
            return 0;
        } finally {
            if (sample != null && sample != bitmap) {
                sample.recycle();
            }
        }
    }
}
