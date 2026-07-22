package com.william163.countdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 闹钟触发接收器
 * 当 AlarmManager 到达设定时间时触发，启动 AlarmService 播放声音和震动
 * 同时直接尝试启动全屏闹钟界面（华为等厂商系统可能拦截后台启动Activity，
 * 由 AlarmService 的 FullScreenIntent 通知作为兜底）
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "闹钟触发！");

        String title = intent.getStringExtra("title");
        String content = intent.getStringExtra("content");
        if (title == null) title = "倒计时提醒";
        if (content == null) content = "闹钟响了，点击关闭";

        // 1. 启动闹钟响铃前台服务（播放声音和震动 + 发送 FullScreenIntent 通知兜底）
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("title", title);
        serviceIntent.putExtra("content", content);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // 2. 直接尝试启动全屏闹钟界面
        // 在华为等厂商系统上可能被拦截，但如果用户已开启"后台弹出界面"权限就能成功
        try {
            Intent alarmActivityIntent = new Intent(context, AlarmRingActivity.class);
            alarmActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            alarmActivityIntent.putExtra("title", title);
            alarmActivityIntent.putExtra("content", content);
            context.startActivity(alarmActivityIntent);
            Log.d(TAG, "直接启动全屏闹钟界面成功");
        } catch (Exception e) {
            Log.w(TAG, "直接启动全屏界面被拦截，依赖通知兜底: " + e.getMessage());
        }

        Log.d(TAG, "闹钟服务已启动: " + title + " - " + content);
    }
}
