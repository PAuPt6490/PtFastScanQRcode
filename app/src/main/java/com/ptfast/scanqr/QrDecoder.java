package com.ptfast.scanqr;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ZXing 的三档解码入口。
 *
 * 相机帧每帧只走「快档」:半个分辨率 + 只认二维码 + 全局直方图二值化,
 * 一次大约 2-4 ms;没命中再按间隔升级到「全分辨率」和「全格式 + TRY_HARDER」,
 * 避免每帧都付多格式 + 高开销二值化的钱(那是老版本 150 ms 才能解一次的原因)。
 */
public final class QrDecoder {

    private static final List<BarcodeFormat> QR_ONLY =
            Arrays.asList(BarcodeFormat.QR_CODE);

    private static final List<BarcodeFormat> ALL_FORMATS = Arrays.asList(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODABAR,
            BarcodeFormat.ITF,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E);

    private static final Map<DecodeHintType, Object> QUICK_HINTS = hints(QR_ONLY, false);
    private static final Map<DecodeHintType, Object> FULL_HINTS = hints(QR_ONLY, false);
    private static final Map<DecodeHintType, Object> HEAVY_HINTS = hints(ALL_FORMATS, true);

    // MultiFormatReader 不是线程安全的:相机线程和相册线程各用各的实例。
    private static final MultiFormatReader QUICK_READER = new MultiFormatReader();
    private static final MultiFormatReader FULL_READER = new MultiFormatReader();
    private static final MultiFormatReader HEAVY_READER = new MultiFormatReader();
    private static final MultiFormatReader IMAGE_READER_A = new MultiFormatReader();
    private static final MultiFormatReader IMAGE_READER_B = new MultiFormatReader();
    private static final Object CAMERA_LOCK = new Object();
    private static final Object IMAGE_LOCK = new Object();

    private QrDecoder() {
    }

    private static Map<DecodeHintType, Object> hints(List<BarcodeFormat> formats, boolean tryHarder) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, new ArrayList<>(formats));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        if (tryHarder) {
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        }
        return hints;
    }

    /** 快档:半分辨率、只认二维码、全局直方图。相机每帧走这条。 */
    public static String decodeFast(byte[] data, int width, int height) {
        if (data == null || width <= 0 || height <= 0) {
            return null;
        }
        synchronized (CAMERA_LOCK) {
            try {
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        data, width, height, 0, 0, width, height, false);
                BinaryBitmap binary = new BinaryBitmap(new GlobalHistogramBinarizer(source));
                Result result = QUICK_READER.decodeWithState(binary);
                return result == null ? null : result.getText();
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /** 中档:全分辨率、只认二维码、局部自适应二值化。 */
    public static String decodeThorough(byte[] data, int width, int height) {
        if (data == null || width <= 0 || height <= 0) {
            return null;
        }
        synchronized (CAMERA_LOCK) {
            try {
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        data, width, height, 0, 0, width, height, false);
                BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
                Result result = FULL_READER.decodeWithState(binary);
                return result == null ? null : result.getText();
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /** 重档:全分辨率、全格式、TRY_HARDER。低帧率跑,兜住一维码和难读的画面。 */
    public static String decodeHeavy(byte[] data, int width, int height) {
        if (data == null || width <= 0 || height <= 0) {
            return null;
        }
        synchronized (CAMERA_LOCK) {
            try {
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        data, width, height, 0, 0, width, height, false);
                BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
                Result result = HEAVY_READER.decodeWithState(binary);
                return result == null ? null : result.getText();
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /** 本地图片:先单码试四种朝向,再试一张图里的多个码。 */
    public static String decodeBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        for (int angle : new int[]{0, 90, 180, 270}) {
            String text = decodeOne(rotate(bitmap, angle));
            if (text != null) {
                return text;
            }
        }
        return decodeMultiple(bitmap);
    }

    private static String decodeOne(Bitmap bitmap) {
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            RGBLuminanceSource source = new RGBLuminanceSource(w, h, pixels);
            BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
            synchronized (IMAGE_LOCK) {
                Result result = IMAGE_READER_A.decodeWithState(binary);
                return result == null ? null : result.getText();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String decodeMultiple(Bitmap bitmap) {
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            RGBLuminanceSource source = new RGBLuminanceSource(w, h, pixels);
            BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
            MultipleBarcodeReader reader = new GenericMultipleBarcodeReader(IMAGE_READER_B);
            Result[] results;
            synchronized (IMAGE_LOCK) {
                results = reader.decodeMultiple(binary, HEAVY_HINTS);
            }
            if (results == null || results.length == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (Result r : results) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(r.getText());
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap rotate(Bitmap bitmap, int degrees) {
        if (degrees == 0) {
            return bitmap;
        }
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(degrees);
        try {
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        } catch (Throwable t) {
            return bitmap;
        }
    }
}
