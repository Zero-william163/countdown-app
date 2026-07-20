package com.william163.countdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机启动接收器
 * 用于在设备重启后恢复闹钟提醒
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "设备已启动，恢复闹钟服务");
            
            // 启动服务恢复闹钟
            Intent serviceIntent = new Intent(context, AlarmRestoreService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}