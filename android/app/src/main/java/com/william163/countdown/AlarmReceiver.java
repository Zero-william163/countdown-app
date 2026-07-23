package com.william163.countdown;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 闹钟触发接收器
 * 当 AlarmManager 到达设定时间时触发，只负责启动 AlarmService
 * 全屏界面由 AlarmService 通过 FullScreenIntent 机制拉起（系统认可的正规通道）
 * 
 * 注意：Android 10+ 严禁在后台广播中直接调用 startActivity()
 * 必须通过前台通知的 setFullScreenIntent 才能合法拉起全屏 Activity
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

        // 只启动前台服务，由服务通过 FullScreenIntent 拉起全屏界面
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
