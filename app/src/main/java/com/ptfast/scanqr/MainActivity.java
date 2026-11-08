package com.ptfast.scanqr;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 相机预览 + 解码 + 结果面板。整块界面自绘样式,不依赖 AppCompat。 */
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {

    /** 磁贴 / 桌面快捷方式 / 音量键进来时带的 action。 */
    public static final String ACTION_SCAN = "com.ptfast.scanqr.SCAN";


    private static final int REQ_PERMISSION = 1;
    private static final int REQ_PICK_IMAGE = 2;
    private static final int REQ_CAMERA_PHOTO = 3;
    private static final long DECODE_INTERVAL_MS = 0L;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://|www\\.)[^\\s]+$", Pattern.CASE_INSENSITIVE);

    private TextureView textureView;
    private EditText resultView;
    private TextView statusView;
    private TextView btnTorch;
    private TextView btnCamera;
    private Uri pendingCaptureUri;
    private TextView btnGallery;
    private TextView btnHistory;
    private TextView btnSettings;
    private int appliedMode = -1;
    /** Camera picture top edge inside the view, in pixels; the top bar centres on it. */
    private int previewTopPx;
    private M3 m3;
    private int appliedAccent = -1;
    private TextView btnCopy;
    private TextView btnOpen;
    private TextView btnShare;
    private TextView btnClear;
    /**
     * 画面在屏幕上的竖直位置:画面上边框到屏幕上边框的距离,按视图高度的百分比算,
     * 所以和分辨率无关(1080x2400 和 1440x3200 上构图一致)。
     * 11% 是标定出来的值:4:3 画面占 62.7% 高,上下合计留 37.3%,
     * 上面留 11%、下面留 26.3%。
     */
    private static final float PREVIEW_TOP_PERCENT = 10.5f;
    private float previewTopPercent = PREVIEW_TOP_PERCENT;
    private int lastViewWidth;
    private int lastViewHeight;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder requestBuilder;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Size previewSize;
    private Size analyzeSize;
    private int sensorOrientation = 90;

    /** 叠加在「按传感器方向旋转」之上的手动微调,负数=逆时针。 */
    private static final int PREVIEW_EXTRA_ROTATION = -90;

    private ScanOverlay overlay;
    private Rect activeArray;
    private boolean afSupported;
    private boolean macroSupported;
    private Rect focusRegion;
    private Rect manualRegion;
    private long manualFocusAt = 0L;
    private volatile int afState = -1;
    private long lastAfTriggerAt = 0L;
    private long lastRingAt = 0L;

    private volatile boolean scanning = false;
    private volatile boolean decoding = false;
    /** 近距/弱光下连续对焦容易"假对焦":画面还虚,状态却报对上了。
     *  这里用一个便宜的清晰度指标盯着,连续几次都虚就切微距模式重新走一遍行程。 */
    private boolean macroFocus = false;
    private int blurryStreak = 0;
    private float sharpMax = 1f;
    private volatile float sharpness = 0f;
    private int frameCounter = 0;
    private int decodeCount = 0;
    private long decodeFastUsum = 0L;
    private long decodeSlowUsum = 0L;
    private long decodeWindowStart = SystemClock.elapsedRealtime();
    private byte[] halfBuffer;
    private byte[] fullBuffer;
    private byte[] rowBuffer;
    private volatile boolean pickerOpen = false;
    private volatile boolean torchOn = false;
    private long lastDecodeAt = 0L;
    /** 两次解码之间的最小间隔,由设置里的「解码帧率」算出来。 */
    private long decodeIntervalMs = 0L;
    private String lastShown;
    private long lastShownAt = 0L;
    private boolean introShown = false;

    private final Handler uiHandler = new Handler();
    private final Runnable resumeScan = () -> scanning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.apply(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.preview);
        overlay = findViewById(R.id.overlay);
        resultView = findViewById(R.id.result);
        statusView = findViewById(R.id.status);
        btnTorch = findViewById(R.id.btnTorch);
        btnCamera = findViewById(R.id.btnCamera);
        btnGallery = findViewById(R.id.btnGallery);
        btnHistory = findViewById(R.id.btnHistory);
        btnCopy = findViewById(R.id.btnCopy);
        btnOpen = findViewById(R.id.btnOpen);
        btnShare = findViewById(R.id.btnShare);
        btnClear = findViewById(R.id.btnClear);

        textureView.setSurfaceTextureListener(this);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        btnTorch.setOnClickListener(v -> toggleTorch());
        btnCamera.setOnClickListener(v -> shootWithCamera());
        btnGallery.setOnClickListener(v -> pickImage());
        btnHistory.setOnClickListener(v -> showHistory());
        btnCopy.setOnClickListener(v -> copyResult());
        btnOpen.setOnClickListener(v -> openResult());
        btnShare.setOnClickListener(v -> shareResult());
        btnClear.setOnClickListener(v -> clearResult());
        btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> openSettings());
        // 点预览任意位置就对焦到那儿,顺手在点上画个圈
        textureView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
            }
            return true;
        });

        if (!hasCameraPermission()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMISSION);
        }
        maybeShowIntro();
        applyPrefs();
        registerShortcut();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeChangedSinceApply()) {
            recreate();
            return;
        }
        applyPrefs();
        scanning = true;
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        scanning = false;
        uiHandler.removeCallbacks(autoFocusTick);
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ACTION_SCAN.equals(intent.getAction())) {
            clearResult();
        }
        scanning = true;
        statusView.setText(R.string.status_ready);
    }


    /** 长按桌面图标出现的「扫一扫」。动态快捷方式不需要 manifest 和 xml,系统自己收好。 */
    private void registerShortcut() {
        try {
            android.content.pm.ShortcutManager manager = getSystemService(android.content.pm.ShortcutManager.class);
            if (manager == null) {
                return;
            }
            android.content.pm.ShortcutInfo shortcut = new android.content.pm.ShortcutInfo.Builder(this, "scan")
                    .setShortLabel(getString(R.string.shortcut_scan))
                    .setLongLabel(getString(R.string.shortcut_scan_short))
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(new Intent(this, MainActivity.class).setAction(ACTION_SCAN))
                    .setRank(0)
                    .build();
            manager.setDynamicShortcuts(java.util.Collections.singletonList(shortcut));
        } catch (Throwable ignored) {
        }
    }

    /** 把 M3 角色色贴到相机页的控件上:底部卡片、胶囊按钮、顶部半透明 chip。 */
    private void applyPalette() {
        int backgroundColor = m3.dark ? m3.surface : 0xFFFFFFFF;
        View root = findViewById(R.id.root);
        if (root != null) {
            root.setBackgroundColor(backgroundColor);
        }
        View panel = findViewById(R.id.panel);
        if (panel instanceof com.google.android.material.card.MaterialCardView) {
            com.google.android.material.card.MaterialCardView card =
                    (com.google.android.material.card.MaterialCardView) panel;
            card.setCardBackgroundColor(backgroundColor);
            card.setStrokeWidth(0);
        }
        statusView.setTextColor(m3.onSurfaceVariant);
        resultView.setTextColor(m3.onSurface);
        resultView.setHintTextColor(m3.onSurfaceVariant);
        M3Ui.filled(btnCopy, m3);
        M3Ui.tonal(btnOpen, m3);
        M3Ui.tonal(btnShare, m3);
        M3Ui.text(btnClear, m3);
        M3Ui.chip(btnCamera, m3);
        M3Ui.chip(btnTorch, m3);
        M3Ui.chip(btnGallery, m3);
        M3Ui.chip(btnHistory, m3);
        M3Ui.chip(btnSettings, m3);
        for (TextView chip : new TextView[]{btnCamera, btnTorch, btnGallery, btnHistory, btnSettings}) {
            if (chip instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton button =
                        (com.google.android.material.button.MaterialButton) chip;
                button.setInsetTop(0);
                button.setInsetBottom(0);
                button.setMinHeight(0);
                button.setMinimumHeight(0);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setPadding(M3Ui.dp(this, 6f), M3Ui.dp(this, 8f),
                        M3Ui.dp(this, 6f), M3Ui.dp(this, 8f));
                button.setTextSize(13f);
                button.setCornerRadius(M3Ui.dp(this, 18f));
            }
        }
        if (overlay != null) {
            overlay.setAccent(m3.primary);
        }
        applyFrameOffset();
        centerTopBar();
        applySystemBarIcons();
        // 状态栏 / 导航栏跟着底色走,不然白底界面顶上是条黑边
        android.view.Window window = getWindow();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(backgroundColor));
        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(backgroundColor);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void applyFrameOffset() {
        float value = Prefs.frameOffset(this);
        if (overlay != null) {
            overlay.setFrameOffsetPercent(value);
        }
    }

    /** 白底要配深色状态栏图标,深色底反之。 */
    /** Keeps the five buttons on the vertical centre line of the white band above the camera picture. */
    private void centerTopBar() {
        final View bar = findViewById(R.id.topBar);
        if (bar == null) {
            return;
        }
        bar.post(new Runnable() {
            @Override
            public void run() {
                int barHeight = bar.getHeight();
                if (barHeight <= 0) {
                    return;
                }
                int band = previewTopPx > 0 ? previewTopPx : barHeight + M3Ui.dp(MainActivity.this, 32f);
                int margin = (band - barHeight) / 2;
                if (margin < M3Ui.dp(MainActivity.this, 8f)) {
                    margin = M3Ui.dp(MainActivity.this, 8f);
                }
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) bar.getLayoutParams();
                if (params.topMargin != margin) {
                    params.topMargin = margin;
                    bar.setLayoutParams(params);
                }
            }
        });
    }

    private void applySystemBarIcons() {
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (m3.dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    /** 按设置里勾选的项显示/隐藏顶部按钮。 */
    private void applyPrefs() {
        btnCamera.setVisibility(Prefs.showCamera(this) ? View.VISIBLE : View.GONE);
        btnTorch.setVisibility(Prefs.showTorch(this) ? View.VISIBLE : View.GONE);
        btnGallery.setVisibility(Prefs.showGallery(this) ? View.VISIBLE : View.GONE);
        btnHistory.setVisibility(Prefs.showHistory(this) ? View.VISIBLE : View.GONE);
        int fps = Prefs.decodeFps(this);
        decodeIntervalMs = fps >= 60 ? 0L : 1000L / Math.max(1, fps);
        m3 = M3.of(this);
        applyPalette();
        appliedMode = Prefs.darkMode(this);
        appliedAccent = Prefs.accent(this);
    }

    private boolean themeChangedSinceApply() {
        return appliedMode >= 0
                && (appliedMode != Prefs.darkMode(this) || appliedAccent != Prefs.accent(this));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ---------------------------------------------------------------- 权限

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (textureView.isAvailable()) {
                    openCamera();
                }
            } else {
                statusView.setText("没有相机权限，只能用「相册」识别本地图片");
                Toast.makeText(this, "可以在系统设置里重新授予相机权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------- 相机

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void startBackgroundThread() {
        if (backgroundThread != null) {
            return;
        }
        backgroundThread = new HandlerThread("ptq-camera");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread == null) {
            return;
        }
        backgroundThread.quitSafely();
        try {
            backgroundThread.join(500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        backgroundThread = null;
        backgroundHandler = null;
    }

    private String chooseCameraId() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
            String[] ids = cameraManager.getCameraIdList();
            return ids.length > 0 ? ids[0] : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void openCamera() {
        if (cameraDevice != null || !hasCameraPermission() || !textureView.isAvailable()) {
            return;
        }
        String id = chooseCameraId();
        if (id == null) {
            statusView.setText("这台设备上找不到相机，只能用「相册」识别本地图片");
            return;
        }
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation == null ? 90 : orientation;
            activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            afSupported = afModes != null && afModes.length > 0;
            macroSupported = false;
            if (afModes != null) {
                for (int mode : afModes) {
                    if (mode == CaptureRequest.CONTROL_AF_MODE_MACRO) {
                        macroSupported = true;
                        break;
                    }
                }
            }
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return;
            }
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class),
                    activeArray, textureView.getWidth(), textureView.getHeight());
            analyzeSize = chooseAnalyzeSize(map.getOutputSizes(ImageFormat.YUV_420_888));
            if (previewSize == null || analyzeSize == null) {
                return;
            }
            imageReader = ImageReader.newInstance(analyzeSize.getWidth(), analyzeSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(this::onFrame, backgroundHandler);
            cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    runOnUiThread(() -> statusView.setText("相机启动失败（" + error + "）"));
                }
            }, backgroundHandler);
        } catch (Throwable t) {
            runOnUiThread(() -> statusView.setText("相机启动失败：" + t.getClass().getSimpleName()));
        }
    }

    /**
     * 预览尺寸直接按传感器的有效像素区挑:同一长宽比里取最大的那一档,
     * 预览流的口径和原厂相机一致:1440x1080(4:3),即整块传感器的视野,
     * 一个像素都不裁。原厂相机的预览流实测就是这一档(dumpsys media.camera)——
     * 12MP 的 4000x3000 虽然也能推起来,但占带宽、费电,对取景没有额外好处。
     * 显示时按等比缩放居中(上下留黑),不再为了铺满屏幕去放大裁掉两边。
     */
    private Size choosePreviewSize(Size[] sizes, Rect active, int viewWidth, int viewHeight) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        // 首选原厂相机的预览档位
        for (Size size : sizes) {
            if (size.getWidth() == 1440 && size.getHeight() == 1080) {
                return size;
            }
        }
        int aw = active != null && active.width() > 0 ? active.width() : 4000;
        int ah = active != null && active.height() > 0 ? active.height() : 3000;
        double target = (double) Math.max(aw, ah) / Math.min(aw, ah);
        Size best = null;
        long bestPixels = -1;
        for (Size size : sizes) {
            int w = Math.max(1, size.getWidth());
            int h = Math.max(1, size.getHeight());
            double aspect = (double) Math.max(w, h) / Math.min(w, h);
            if (Math.abs(aspect - target) > 0.02) {
                continue;
            }
            long pixels = (long) w * h;
            // 只取 2MP 以内的 4:3 档,再大的档位对取景没用,反而拖低帧率
            if (pixels > 2100000L) {
                continue;
            }
            if (pixels > bestPixels) {
                bestPixels = pixels;
                best = size;
            }
        }
        if (best != null) {
            return best;
        }
        // 兜底:万一没有 4:3 的档,退到最接近屏幕比例的那一档
        double screen = (double) Math.max(1, viewHeight) / Math.max(1, viewWidth);
        double bestDiff = Double.MAX_VALUE;
        for (Size size : sizes) {
            int w = Math.max(1, size.getWidth());
            int h = Math.max(1, size.getHeight());
            double aspect = (double) Math.max(w, h) / Math.min(w, h);
            double diff = Math.abs(aspect - screen);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = size;
            }
        }
        return best != null ? best : sizes[0];
    }

    private Size chooseSize(Size[] sizes, int maxPixels) {
        if (sizes == null) {
            return null;
        }
        Size best = null;
        long bestScore = Long.MAX_VALUE;
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * size.getHeight();
            if (pixels > maxPixels) {
                continue;
            }
            long score = Math.abs(pixels - maxPixels / 2L);
            if (bestScore > score) {
                bestScore = score;
                best = size;
            }
        }
        return best != null ? best : sizes[0];
    }

    private Size chooseAnalyzeSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        for (Size size : sizes) {
            if (size.getWidth() == 1280 && size.getHeight() == 720) {
                return size;
            }
        }
        for (Size size : sizes) {
            if (size.getWidth() == 960 && size.getHeight() == 540) {
                return size;
            }
        }
        return chooseSize(sizes, 1280 * 720);
    }

    private void startPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) {
                return;
            }
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(surface);
            requestBuilder.addTarget(imageReader.getSurface());
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            requestBuilder.set(CaptureRequest.FLASH_MODE,
                    torchOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(surface);
            surfaces.add(imageReader.getSurface());
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    captureSession = session;
                    runOnUiThread(() -> {
                        configureTransform(textureView.getWidth(), textureView.getHeight());
                        scanning = true;
                        applyFrameRegionToRepeat();
                        requestFocus(false);
                        uiHandler.removeCallbacks(autoFocusTick);
                        uiHandler.postDelayed(autoFocusTick, 600L);
                    });
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    runOnUiThread(() -> statusView.setText("相机配置失败"));
                }
            }, backgroundHandler);
        } catch (Throwable t) {
            runOnUiThread(() -> statusView.setText("预览启动失败：" + t.getClass().getSimpleName()));
        }
    }

    private int displayRotationDegrees() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    /**
     * 预览画面:等比缩放 + 居中,再叠加手动微调偏移(标定用)。
     * 整幅 4:3 画面都看得见,不再为了铺满 20:9 屏幕去裁边放大。
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (previewSize == null || viewWidth == 0 || viewHeight == 0) {
            return;
        }
        Matrix matrix = new Matrix();
        float centerX = viewWidth / 2f;
        float centerY = viewHeight / 2f;
        // 预览缓冲是横的,TextureView 拿到的变换矩阵已经把它转正了,
        // 所以这里只做"等比缩放 + 居中":整幅画面都看得见,不再裁边放大。
        // 之前按屏幕比例 FILL 的做法会把 4:3 的画面裁掉两边,看着像 1.6 倍变焦。
        float bufferWidth = previewSize.getHeight();
        float bufferHeight = previewSize.getWidth();
        if (bufferWidth <= 0f || bufferHeight <= 0f) {
            return;
        }
        float fit = Math.min(viewWidth / bufferWidth, viewHeight / bufferHeight);
        lastViewWidth = viewWidth;
        lastViewHeight = viewHeight;
        matrix.setScale(fit * bufferWidth / viewWidth, fit * bufferHeight / viewHeight,
                centerX, centerY);
        // postTranslate 作用在视图坐标系里:直接就是屏幕像素。
        // 目标:画面上边框落在 previewTopPercent 处(0 = 紧贴屏幕上边框)。
        float contentHeight = fit * bufferHeight;
        float defaultTop = (viewHeight - contentHeight) / 2f;
        float offsetY = previewTopPercent / 100f * viewHeight - defaultTop;
        matrix.postTranslate(0f, offsetY);
        textureView.setTransform(matrix);
        previewTopPx = Math.round(defaultTop + offsetY);
        centerTopBar();
    }

    /**
     * 取景框内自动对焦:把取景框中心换算成传感器坐标,圈一块对焦/测光区域,
     * 对不上时(还没 FOCUSED)每 0.9 秒重新触发一次单次对焦,对上之后交给连续对焦微调。
     * 这样不用点屏幕,把码放进取景框里就会自动拉焦。
     */
    private void updateFrameFocusRegion() {
        if (overlay == null || previewSize == null || textureView == null) {
            return;
        }
        RectF frame = overlay.frame();
        float vw = Math.max(1f, textureView.getWidth());
        float vh = Math.max(1f, textureView.getHeight());
        Rect area = activeArray;
        if (area == null) {
            area = new Rect(0, 0, previewSize.getWidth(), previewSize.getHeight());
        }
        int side = (int) (frame.width() / vw * area.width());
        Rect region = sensorRegionFor(frame.centerX() / vw, frame.centerY() / vh, side);
        if (region != null) {
            focusRegion = region;
        }
    }

    /**
     * 视图上的归一化坐标(0..1)换算成传感器坐标里的一块方形区域。
     * side 是想要的边长(传感器像素),会被夹在短边的 1/8 到 1/3 之间。
     */
    private Rect sensorRegionFor(float u, float v, int side) {
        Rect area = activeArray;
        if (area == null) {
            if (previewSize == null) {
                return null;
            }
            area = new Rect(0, 0, previewSize.getWidth(), previewSize.getHeight());
        }
        int total = ((90 * (sensorOrientation / 90) + PREVIEW_EXTRA_ROTATION) % 360 + 360) % 360;
        u = Math.max(0f, Math.min(1f, u));
        v = Math.max(0f, Math.min(1f, v));
        float sx;
        float sy;
        if (total == 90) {
            sx = v;
            sy = 1f - u;
        } else if (total == 180) {
            sx = 1f - u;
            sy = 1f - v;
        } else if (total == 270) {
            sx = 1f - v;
            sy = u;
        } else {
            sx = u;
            sy = v;
        }
        int shortSide = Math.min(area.width(), area.height());
        side = Math.max(shortSide / 8, Math.min(side, shortSide / 3));
        int cx = area.left + (int) (sx * area.width());
        int cy = area.top + (int) (sy * area.height());
        Rect region = new Rect(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
        if (!region.intersect(area)) {
            return null;
        }
        return region;
    }

    /**
     * 手指点屏幕对焦:按点的位置圈一小块区域单次对焦,并在点上画一圈提示。
     * 之后 6 秒内对焦由这里接管,超时再交回取景框中心的自动对焦。
     */
    private void focusAt(float x, float y) {
        CameraCaptureSession session = captureSession;
        CaptureRequest.Builder builder = requestBuilder;
        if (session == null || builder == null || !afSupported || textureView == null) {
            return;
        }
        Rect area = activeArray;
        if (area == null) {
            if (previewSize == null) {
                return;
            }
            area = new Rect(0, 0, previewSize.getWidth(), previewSize.getHeight());
        }
        int side = Math.min(area.width(), area.height()) / 6;
        Rect region = sensorRegionFor(x / Math.max(1, textureView.getWidth()),
                y / Math.max(1, textureView.getHeight()), side);
        if (region == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        manualRegion = region;
        manualFocusAt = now;
        lastAfTriggerAt = now;
        lastRingAt = now;
        MeteringRectangle meter = new MeteringRectangle(region, MeteringRectangle.METERING_WEIGHT_MAX);
        try {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{meter});
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{meter});
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            session.capture(builder.build(), afCallback, backgroundHandler);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (overlay != null) {
                overlay.showFocus(x, y);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 触发一次单次对焦。已经对上(或刚触发过 0.9 秒内)就跳过,免得画面一直在拉风箱。
     * showRing 为真时在取景框中心画一圈对焦提示。
     */
    private void requestFocus(boolean showRing) {
        CameraCaptureSession session = captureSession;
        CaptureRequest.Builder builder = requestBuilder;
        if (session == null || builder == null || !afSupported) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        // 正在拉焦(ACTIVE_SCAN)时再推一发只会打架;其余的都不跳过。
        // 近距离时相机常在画面还虚的时候就报 FOCUSED,所以"已对上"不再是跳过理由。
        boolean hunting = afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN;
        if (hunting || now - lastAfTriggerAt < 1000L) {
            return;
        }
        // 刚点过屏幕的 6 秒内,对焦点跟着手指走;之后回到取景框中心
        if (now - manualFocusAt < 6000L && manualRegion != null) {
            focusRegion = manualRegion;
        } else {
            updateFrameFocusRegion();
        }
        if (focusRegion == null) {
            return;
        }
        MeteringRectangle meter = new MeteringRectangle(focusRegion, MeteringRectangle.METERING_WEIGHT_MAX);
        try {
            // 重复请求保持「连续对焦 + 框内区域」不动,只在需要时插一发单次触发,
            // 避免每次都重设重复请求导致预览抖一下
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{meter});
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{meter});
            builder.set(CaptureRequest.CONTROL_AF_MODE, macroFocus && macroSupported
                    ? CaptureRequest.CONTROL_AF_MODE_MACRO
                    : CaptureRequest.CONTROL_AF_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            session.capture(builder.build(), null, backgroundHandler);
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            builder.set(CaptureRequest.CONTROL_AF_MODE, macroFocus && macroSupported
                    ? CaptureRequest.CONTROL_AF_MODE_MACRO
                    : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            lastAfTriggerAt = now;
            if (showRing && now - lastRingAt > 2500L && overlay != null) {
                lastRingAt = now;
                overlay.showFocus(overlay.frame().centerX(), overlay.frame().centerY());
            }
        } catch (Throwable ignored) {
        }
    }

    /** 把取景框区域写进重复请求,让连续对焦也盯着框里的东西。 */
    private void applyFrameRegionToRepeat() {
        CameraCaptureSession session = captureSession;
        CaptureRequest.Builder builder = requestBuilder;
        if (session == null || builder == null) {
            return;
        }
        if (SystemClock.uptimeMillis() - manualFocusAt < 6000L && manualRegion != null) {
            focusRegion = manualRegion;
        } else {
            updateFrameFocusRegion();
        }
        if (focusRegion == null) {
            return;
        }
        MeteringRectangle meter = new MeteringRectangle(focusRegion, MeteringRectangle.METERING_WEIGHT_MAX);
        try {
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{meter});
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{meter});
            builder.set(CaptureRequest.CONTROL_AF_MODE, macroFocus && macroSupported
                    ? CaptureRequest.CONTROL_AF_MODE_MACRO
                    : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            session.setRepeatingRequest(builder.build(), afCallback, backgroundHandler);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 便宜的清晰度指标:中心区域水平相邻像素差的平均值。
     * 只要相对值(和自己比),所以不需要精确的 Laplacian。
     */
    private static float measureSharpness(byte[] luma, int width, int height) {
        if (luma == null || width < 8 || height < 8) {
            return 0f;
        }
        int x0 = width / 4;
        int x1 = width - x0;
        int y0 = height / 4;
        int y1 = height - y0;
        long sum = 0L;
        int count = 0;
        for (int y = y0; y < y1; y += 4) {
            int row = y * width;
            for (int x = x0 + 1; x < x1; x += 4) {
                int d = luma[row + x] - luma[row + x - 1];
                sum += d < 0 ? -d : d;
                count++;
            }
        }
        return count == 0 ? 0f : (float) sum / count;
    }

    /**
     * 相机报"对上了"但画面明显比最清楚的时候糊 → 认为假对焦,
     * 连中三次就切微距模式,让镜头重新走一遍全程找近距离焦点。
     */
    private void updateFocusQuality() {
        float value = sharpness;
        if (value > sharpMax) {
            sharpMax = value;
        } else {
            sharpMax *= 0.995f;
            if (sharpMax < value) {
                sharpMax = value;
            }
        }
        boolean settled = afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                || afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;
        if (settled && sharpMax > 1f) {
            if (value < sharpMax * 0.45f) {
                blurryStreak++;
            } else {
                blurryStreak = 0;
            }
            if (blurryStreak >= 3 && !macroFocus) {
                macroFocus = true;
                blurryStreak = 0;
                lastAfTriggerAt = 0L;
            }
        }
        if (macroFocus && sharpMax > 1f && value >= sharpMax * 0.85f) {
            macroFocus = false;
            blurryStreak = 0;
        }
    }

    /** 取景框自动对焦的节拍:1 秒一次,只在扫描状态下工作;自动对焦不画圈,免得老是闪。 */
    private final Runnable autoFocusTick = new Runnable() {
        @Override
        public void run() {
            if (scanning) {
                requestFocus(false);
            }
            uiHandler.postDelayed(this, 1000L);
        }
    };

    /** 跟着重复请求记下 AF 状态,用来判断"还要不要再拉一次"。 */
    private final CameraCaptureSession.CaptureCallback afCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    Integer state = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (state != null) {
                        afState = state;
                    }
                }
            };

    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private void toggleTorch() {
        torchOn = !torchOn;
        btnTorch.setText(torchOn ? "关闭手电筒" : "手电筒");
        if (requestBuilder == null || captureSession == null) {
            return;
        }
        try {
            requestBuilder.set(CaptureRequest.FLASH_MODE,
                    torchOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            captureSession.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- 解码

    /**
     * 相机帧解码。分三档,按帧计数升级:
     *   每帧  —— 半分辨率 + 只认二维码 + 全局直方图(最快,正常扫码都在这一档命中)
     *   每 2 帧—— 全分辨率 + 只认二维码 + 局部自适应二值化
     *   每 8 帧—— 全分辨率 + 全格式 + TRY_HARDER(兜住一维码、反光、小码)
     * 不做旋转:二维码的定位符号本身就带方向,转 90 度对识别没有帮助,
     * 白白多一次全帧内存拷贝(1280x720 每帧 1.8 MB)。
     */
    private void onFrame(ImageReader reader) {
        Image image;
        try {
            image = reader.acquireLatestImage();
        } catch (Throwable ignored) {
            return;
        }
        if (image == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!scanning || decoding || pickerOpen || now - lastDecodeAt < decodeIntervalMs) {
            image.close();
            return;
        }
        lastDecodeAt = now;
        decoding = true;

        int frameIndex = ++frameCounter;
        boolean wantFull = (frameIndex & 1) == 0;
        boolean wantHeavy = frameIndex % 8 == 0;

        int halfWidth = 0;
        int halfHeight = 0;
        int fullWidth = 0;
        int fullHeight = 0;
        byte[] half = null;
        byte[] full = null;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            fullWidth = image.getWidth();
            fullHeight = image.getHeight();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            byte[] row = rowBuffer;
            if (row == null || row.length < rowStride) {
                row = new byte[rowStride];
                rowBuffer = row;
            }
            halfWidth = (fullWidth + 1) / 2;
            halfHeight = (fullHeight + 1) / 2;
            if (halfBuffer == null || halfBuffer.length < halfWidth * halfHeight) {
                halfBuffer = new byte[halfWidth * halfHeight];
            }
            half = halfBuffer;
            if (wantFull) {
                if (fullBuffer == null || fullBuffer.length < fullWidth * fullHeight) {
                    fullBuffer = new byte[fullWidth * fullHeight];
                }
                full = fullBuffer;
            }
            for (int y = 0; y < fullHeight; y++) {
                buffer.position(y * rowStride);
                int count = Math.min(rowStride, buffer.remaining());
                if (count <= 0) {
                    break;
                }
                buffer.get(row, 0, count);
                if (full != null) {
                    int fullRow = y * fullWidth;
                    int limit = Math.min(fullWidth, count / pixelStride);
                    for (int x = 0; x < limit; x++) {
                        full[fullRow + x] = row[x * pixelStride];
                    }
                }
                if ((y & 1) == 0) {
                    int halfRow = (y >> 1) * halfWidth;
                    int limit = Math.min(halfWidth, count / (pixelStride * 2));
                    for (int x = 0; x < limit; x++) {
                        half[halfRow + x] = row[x * pixelStride * 2];
                    }
                }
            }
        } catch (Throwable ignored) {
            half = null;
            full = null;
        } finally {
            image.close();
        }

        if (half != null) {
            sharpness = measureSharpness(half, halfWidth, halfHeight);
        }
        updateFocusQuality();

        String text = null;
        long t0 = System.nanoTime();
        long tFast = 0L;
        long tSlow = 0L;
        if (half != null) {
            text = QrDecoder.decodeFast(half, halfWidth, halfHeight);
            tFast = (System.nanoTime() - t0) / 1000L;
        }
        if (text == null && full != null) {
            long t1 = System.nanoTime();
            text = wantHeavy
                    ? QrDecoder.decodeHeavy(full, fullWidth, fullHeight)
                    : QrDecoder.decodeThorough(full, fullWidth, fullHeight);
            tSlow = (System.nanoTime() - t1) / 1000L;
        }
        if (text == null && wantHeavy && half != null) {
            // 全分辨率也没读到,再给半分辨率一次局部自适应二值化的机会
            text = QrDecoder.decodeThorough(half, halfWidth, halfHeight);
        }
        decodeCount++;
        decodeFastUsum += tFast;
        decodeSlowUsum += tSlow;
        decoding = false;
        if (text != null && !text.isEmpty()) {
            final String found = text;
            runOnUiThread(() -> handleResult(found, false));
        }
    }

    private void handleResult(String text, boolean fromImage) {
        lastDecodeAt = SystemClock.elapsedRealtime();
        resultView.setText(text);
        statusView.setText(fromImage ? "已从图片识别" : "已识别");
        vibrate(fromImage ? 20 : 35);
        HistoryStore.add(this, text);

        long now = SystemClock.elapsedRealtime();
        if (text.equals(lastShown) && now - lastShownAt < 2500L) {
            return;
        }
        lastShown = text;
        lastShownAt = now;

        scanning = false;
        uiHandler.postDelayed(resumeScan, 1500L);

        String url = normalizeUrl(text);
        if (url != null) {
            promptOpenUrl(url);
        }
    }

    private void vibrate(int ms) {
        if (!Prefs.vibrate(this)) {
            return;
        }
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String normalizeUrl(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = text.trim();
        if (oneLine.contains("\n") || oneLine.length() < 5) {
            return null;
        }
        if (!URL_PATTERN.matcher(oneLine).matches()) {
            return null;
        }
        String lower = oneLine.toLowerCase(Locale.ROOT);
        if (lower.startsWith("www.")) {
            return "http://" + oneLine;
        }
        return oneLine;
    }

    private void promptOpenUrl(final String url) {
        if (isFinishing()) {
            return;
        }
        String pretty = url.length() > 120 ? url.substring(0, 117) + "…" : url;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("检测到网址")
                .setMessage(pretty)
                .setPositiveButton("用浏览器打开", (d, w) -> browse(url))
                .setNeutralButton("复制", (d, w) -> copyToClipboard(url))
                .setNegativeButton("取消", (d, w) -> scanning = true)
                .setOnDismissListener(d -> scanning = true);
        tintDialog(builder.show());
    }

    // ---------------------------------------------------------------- 图片

    /**
     * 借系统相机拍一张再识别:跳出去用系统相机(变焦/夜景/对焦都是原厂的),拍完拿回原图解码。
     * 照片落在应用私有缓存里,通过自带的 CaptureProvider 暴露成 content:// 给相机写入,
     * 不需要存储权限,也不用引 AndroidX 的 FileProvider。
     */
    private void shootWithCamera() {
        pickerOpen = true;
        scanning = false;
        try {
            File dir = CaptureProvider.dir(this);
            // 顺手清掉上一次留下的照片,别让缓存一直涨
            File[] old = dir.listFiles();
            if (old != null) {
                for (File file : old) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
            File target = new File(dir, "shot-" + System.currentTimeMillis() + ".jpg");
            pendingCaptureUri = CaptureProvider.uriFor(target.getName());
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_CAMERA_PHOTO);
        } catch (Throwable t) {
            pendingCaptureUri = null;
            pickerOpen = false;
            scanning = true;
            Toast.makeText(this, "没有可用的相机应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickImage() {
        pickerOpen = true;
        scanning = false;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_PICK_IMAGE);
        } catch (Throwable t) {
            pickerOpen = false;
            scanning = true;
            Toast.makeText(this, "没有可用的文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        pickerOpen = false;
        scanning = true;
        if (requestCode == REQ_CAMERA_PHOTO) {
            Uri shot = pendingCaptureUri;
            pendingCaptureUri = null;
            if (resultCode != RESULT_OK || shot == null) {
                return;
            }
            decodeImageAsync(shot, true);
            return;
        }
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        decodeImageAsync(uri, false);
    }

    /** 后台线程解码一张图,结果回主线程展示。 */
    private void decodeImageAsync(Uri uri, boolean fromCamera) {
        statusView.setText("正在识别图片…");
        final Uri target = uri;
        new Thread(() -> {
            Bitmap bitmap = loadBitmap(target);
            String text = bitmap == null ? null : QrDecoder.decodeBitmap(bitmap);
            if (bitmap != null) {
                bitmap.recycle();
            }
            final String result = text;
            runOnUiThread(() -> {
                if (result != null && !result.isEmpty()) {
                    handleResult(result, true);
                } else {
                    statusView.setText(fromCamera ? "照片里没找到二维码或条码"
                            : "这张图里没找到二维码或条码");
                    Toast.makeText(this, "没识别到内容，再拍一张或换张更清晰的图",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }, "ptq-decode").start();
    }

    private Bitmap loadBitmap(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / sample > 2200) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 结果操作

    private void copyResult() {
        String text = resultView.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "还没有内容", Toast.LENGTH_SHORT).show();
            return;
        }
        copyToClipboard(text);
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("PtFastScanQRcode", text));
        }
    }

    private void openResult() {
        String text = resultView.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "还没有内容", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = normalizeUrl(text);
        if (url != null) {
            browse(url);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(text));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "没有能打开这个内容的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void browse(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareResult() {
        String text = resultView.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "还没有内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, "分享到"));
        } catch (Throwable ignored) {
        }
    }

    private void clearResult() {
        resultView.setText("");
        statusView.setText(R.string.status_ready);
        lastShown = null;
        scanning = true;
    }

    // ---------------------------------------------------------------- 历史

    private void showHistory() {
        final List<HistoryStore.Item> items = HistoryStore.load(this);
        if (items.isEmpty()) {
            Toast.makeText(this, "还没有扫描记录", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] entries = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            HistoryStore.Item item = items.get(i);
            String text = item.text.replace('\n', ' ');
            if (text.length() > 60) {
                text = text.substring(0, 57) + "…";
            }
            entries[i] = item.timeLabel() + "  " + text;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("历史记录")
                .setItems(entries, (dialog, which) -> {
                    HistoryStore.Item item = items.get(which);
                    resultView.setText(item.text);
                    statusView.setText("来自历史记录");
                    String url = normalizeUrl(item.text);
                    if (url != null) {
                        promptOpenUrl(url);
                    }
                })
                .setNeutralButton("清空全部", (dialog, which) -> HistoryStore.clear(this))
                .setNegativeButton("关闭", null);
        tintDialog(builder.show());
    }

    // ---------------------------------------------------------------- 引导

    private void maybeShowIntro() {
        if (introShown) {
            return;
        }
        boolean seen = getPreferences(MODE_PRIVATE).getBoolean("intro_seen", false);
        if (seen) {
            return;
        }
        introShown = true;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("怎么用")
                .setMessage("1. 对准二维码/条码，自动识别；识别到网址会问你要不要用浏览器打开。\n\n"
                        + "2. 也可以点右上角「相册」读一张本地图片，或点「相机」用系统相机拍一张。\n\n"
                        + "3. 取景框里的东西会自动对焦，近距离对不上时把手机拿远到 10 厘米左右。")
                .setPositiveButton("知道了", (d, w) ->
                        getPreferences(MODE_PRIVATE).edit().putBoolean("intro_seen", true).apply());
        tintDialog(builder.show());
    }

    private void tintDialog(android.app.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        M3 palette = m3 != null ? m3 : M3.of(this);
        tintDialogButton(dialog, AlertDialog.BUTTON_POSITIVE, palette.primary);
        tintDialogButton(dialog, AlertDialog.BUTTON_NEGATIVE, palette.onSurfaceVariant);
        tintDialogButton(dialog, AlertDialog.BUTTON_NEUTRAL, palette.primary);
    }

    private void tintDialogButton(android.app.Dialog dialog, int which, int color) {
        if (!(dialog instanceof AlertDialog)) {
            return;
        }
        android.widget.Button button = ((AlertDialog) dialog).getButton(which);
        if (button != null) {
            button.setTextColor(color);
        }
    }

}
