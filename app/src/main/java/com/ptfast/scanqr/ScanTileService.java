package com.ptfast.scanqr;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/** 下拉快捷开关里的「扫一扫」。点一下直接开相机页,并把已有界面提到前面。 */
public class ScanTileService extends TileService {

    @Override
    public void onClick() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_SCAN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                startActivityAndCollapse(pending);
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (Exception e) {
            startActivity(intent);
        }
    }
}
