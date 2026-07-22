package com.william163.countdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 闹钟触发接收器
 * 当 AlarmManager 到达设定时间时触发：
 * 1. 启动 AlarmService（前台服务）播放铃声和震动
 * 2. 直接启动 AlarmRingActivity（全屏界面），突破华为系统横幅限制
 *
 * 说明：华为鸿蒙/EMUI 系统会拦截后台 Activity，即使有 FullScreenIntent
 * 也只能弹出横幅通知。因此必须同时直接 startActivity 才能确保全屏弹出。
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

        // 启动前台服务，播放铃声和震动
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("title", title);
        serviceIntent.putExtra("content", content);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // 【关键】直接启动全屏 Activity，突破华为系统的横幅限制
        // 华为鸿蒙/EMUI 会拦截后台 Activity，必须用 FLAG_ACTIVITY_NEW_TASK
        launchAlarmActivity(context, title, content);

        Log.d(TAG, "闹钟服务已启动: " + title + " - " + content);
    }

    /**
     * 直接启动全屏闹钟界面
     * 使用关键 FLAG 组合：创建新任务栈、清空顶部、并且强行置顶
     * 这样即使华为系统拦截 FullScreenIntent，也能直接弹出全屏界面
     */
    private void launchAlarmActivity(Context context, String title, String content) {
        try {
            Intent alarmIntent = new Intent(context, AlarmRingActivity.class);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            alarmIntent.putExtra("title", title);
            alarmIntent.putExtra("content", content);
            context.startActivity(alarmIntent);
            Log.d(TAG, "直接启动全屏闹钟界面成功");
        } catch (Exception e) {
            Log.e(TAG, "直接启动全屏闹钟界面失败，依赖 FullScreenIntent 拉起", e);
        }
    }
}
