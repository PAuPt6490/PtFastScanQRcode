package com.ptfast.scanqr;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 连按两下音量下键启动扫描。只在设置里打开「音量键双击」后才响应,
 * 所有按键都原样放行(返回 false),不拦截音量调节。
 */
public class QuickLaunchService extends AccessibilityService {

    private long lastVolumeDownAt = 0L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN || !Prefs.volumeKey(this)) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastVolumeDownAt > 60 && now - lastVolumeDownAt < 700) {
            lastVolumeDownAt = 0L;
            Intent intent = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_SCAN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else {
            lastVolumeDownAt = now;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 只用来收按键,不看屏幕内容。
    }

    @Override
    public void onInterrupt() {
    }
}
