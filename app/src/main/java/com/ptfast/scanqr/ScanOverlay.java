package com.ptfast.scanqr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** 取景框:四周压暗,中间留一个方形窗口,画四个角标和一句提示。 */
public class ScanOverlay extends View {

    private final Paint dimPaint = new Paint();
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final RectF tmp = new RectF();

    private float frameOffsetPercent = 0f;
    private int lastWidth = 0;
    private int lastHeight = 0;
    private float focusX = 0f;
    private float focusY = 0f;
    private long focusAt = 0L;

    public ScanOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        dimPaint.setColor(0x00000000);
        framePaint.setColor(0x33FFFFFF);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(1));
        cornerPaint.setColor(0xFF4CC2FF);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dp(3.5f));
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        hintPaint.setColor(0xCCFFFFFF);
        hintPaint.setTextSize(dp(13));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintBgPaint.setColor(0x99000000);
        focusPaint.setColor(0xFFFFD24C);
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(dp(2));
        setWillNotDraw(false);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        lastWidth = w;
        lastHeight = h;
        recomputeFrame();
    }

    /** 按当前尺寸和偏移量重算取景框,偏移量是相对视图高度的百分比。 */
    private void recomputeFrame() {
        int w = lastWidth;
        int h = lastHeight;
        if (w <= 0 || h <= 0) {
            return;
        }
        float side = Math.min(w * 0.76f, h * 0.46f);
        float left = (w - side) / 2f;
        float top = Math.max(dp(84), h * 0.26f - side / 2f);
        top += h * frameOffsetPercent / 100f;
        float maxTop = h - dp(16) - side;
        top = Math.max(dp(84), Math.min(top, maxTop));
        frame.set(left, top, left + side, top + side);
        invalidate();
    }

    public void setFrameOffsetPercent(float percent) {
        frameOffsetPercent = percent;
        recomputeFrame();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if ((dimPaint.getColor() >>> 24) != 0) {
            canvas.drawRect(0, 0, w, frame.top, dimPaint);
            canvas.drawRect(0, frame.bottom, w, h, dimPaint);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom, dimPaint);
            canvas.drawRect(frame.right, frame.top, w, frame.bottom, dimPaint);
        }

        canvas.drawRoundRect(frame, dp(18), dp(18), framePaint);

        float len = dp(26);
        float r = dp(18);
        // 四个角:两段线 + 圆角过渡
        corner(canvas, frame.left + r, frame.top, frame.left + r + len, frame.top, frame.left, frame.top + r, frame.left, frame.top + r + len);
        corner(canvas, frame.right - r - len, frame.top, frame.right - r, frame.top, frame.right, frame.top + r, frame.right, frame.top + r + len);
        corner(canvas, frame.left, frame.bottom - r - len, frame.left, frame.bottom - r, frame.left + r, frame.bottom, frame.left + r + len, frame.bottom);
        corner(canvas, frame.right, frame.bottom - r - len, frame.right, frame.bottom - r, frame.right - r, frame.bottom, frame.right - r - len, frame.bottom);

        String hint = getResources().getString(R.string.hint_scan);
        float baseline = frame.bottom + dp(30);
        float halfWidth = hintPaint.measureText(hint) / 2f + dp(14);
        float top = baseline - hintPaint.getTextSize() - dp(8);
        tmp.set(frame.centerX() - halfWidth, top, frame.centerX() + halfWidth, baseline + dp(8));
        canvas.drawRoundRect(tmp, dp(14), dp(14), hintBgPaint);
        canvas.drawText(hint, frame.centerX(), baseline, hintPaint);
        drawFocus(canvas);
    }

    /** 点击对焦时的方框动画:由大缩到小、边淡出,700ms 结束。 */
    private void drawFocus(Canvas canvas) {
        if (focusAt == 0L) {
            return;
        }
        long elapsed = SystemClock.uptimeMillis() - focusAt;
        if (elapsed >= 700L) {
            focusAt = 0L;
            return;
        }
        float t = elapsed / 700f;
        float size = dp(72) - dp(20) * Math.min(1f, t * 3f);
        int alpha = (int) (255 * (1f - t * t));
        focusPaint.setAlpha(Math.max(0, alpha));
        canvas.drawRect(focusX - size / 2f, focusY - size / 2f,
                focusX + size / 2f, focusY + size / 2f, focusPaint);
        postInvalidateOnAnimation();
    }

    /** 在点击位置显示对焦圈。 */
    public void showFocus(float x, float y) {
        focusX = x;
        focusY = y;
        focusAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private void corner(Canvas canvas, float x1, float y1, float x2, float y2,
                        float x3, float y3, float x4, float y4) {
        canvas.drawLine(x1, y1, x2, y2, cornerPaint);
        canvas.drawLine(x3, y3, x4, y4, cornerPaint);
    }

    /** 用 M3 主色画角标,和界面其余部分同一套配色。 */
    public void setAccent(int color) {
        cornerPaint.setColor(color);
        focusPaint.setColor(color);
        framePaint.setColor((color & 0x00FFFFFF) | 0x33000000);
        invalidate();
    }

    public RectF frame() {
        return frame;
    }
}
