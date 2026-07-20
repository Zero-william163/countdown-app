package com.william163.countdown;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 闹钟停止接收器
 * 用户点击"关闭闹钟"按钮时触发，停止声音和震动
 */
public class AlarmStopReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmStopReceiver";
    public static final String ACTION_STOP_ALARM = "com.william163.countdown.STOP_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到停止闹钟指令");

        // 停止闹钟服务（会释放 MediaPlayer 和 Vibrator）
        AlarmService.stopAlarm(context);

        // 取消通知
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(2001);
        }

        Log.d(TAG, "闹钟已停止");
    }
}
