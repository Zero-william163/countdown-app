package com.william163.countdown;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 闹钟响铃全屏界面
 * 类似华为闹钟的响铃界面，支持锁屏显示、自动亮屏
 */
public class AlarmRingActivity extends AppCompatActivity {

    private static final String TAG = "AlarmRingActivity";
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏显示，隐藏状态栏和导航栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // 锁屏状态下也能显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        // 保持屏幕常亮
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CountdownReminder::AlarmRingWakeLock"
            );
            wakeLock.acquire(5 * 60 * 1000L);
        }

        // 解锁屏幕（如果需要）
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        }

        setContentView(R.layout.alarm_ring_activity);

        // 获取闹钟信息
        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        String content = intent.getStringExtra("content");

        // 设置界面文字
        TextView titleView = findViewById(R.id.alarm_title);
        TextView contentView = findViewById(R.id.alarm_content);
        Button dismissButton = findViewById(R.id.dismiss_button);

        if (title != null) {
            titleView.setText(title);
        }
        if (content != null) {
            contentView.setText(content);
        }

        // 关闭闹钟按钮
        dismissButton.setOnClickListener(v -> {
            stopAlarmAndFinish();
        });
    }

    private void stopAlarmAndFinish() {
        // 停止闹钟服务
        AlarmService.stopAlarm(this);

        // 释放 WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }

        // 关闭响铃界面，返回主界面
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}