package com.william163.countdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 闹钟触发接收器
 * 当 AlarmManager 到达设定时间时触发，启动 AlarmService（前台服务）
 * 由 AlarmService 通过 FullScreenIntent 通知机制拉起全屏闹钟界面
 * 不再直接调用 startActivity，完全依赖系统认可的全屏通知通道
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

        // 启动前台服务，由服务通过 FullScreenIntent 通知拉起全屏界面
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
