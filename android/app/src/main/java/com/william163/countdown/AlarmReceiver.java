package com.william163.countdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 闹钟触发接收器
 * 当 AlarmManager 到达设定时间时触发，启动 AlarmService 播放声音和震动
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

        // 启动闹钟响铃前台服务
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("title", title);
        serviceIntent.putExtra("content", content);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        Log.d(TAG, "闹钟服务已启动: " + title + " - " + content);
    }
}
