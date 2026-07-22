package com.william163.countdown;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AlarmRingActivity extends AppCompatActivity {

    private static final String TAG = "AlarmRingActivity";
    private PowerManager.WakeLock wakeLock;
    private Handler timeHandler;
    private TextView timeView;
    private TextView dateView;
    private View sliderCenter;
    private float startX;
    private float currentX;
    private float sliderWidth;
    private boolean isDragging = false;
    private ProgressBar countdownProgress;
    private CountDownTimer autoStopCountdown;
    private int snoozeCount;
    private String alarmTitle;
    private String alarmContent;
    private static final long AUTO_STOP_DURATION = 120000; // 2分钟
    private static final int MAX_SNOOZE = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 【关键】必须在 setContentView 之前设置以下窗口标志
        // 这些标志确保 Activity 能穿透锁屏、点亮屏幕并显示在最上层

        // 1. 全屏显示，隐藏状态栏和导航栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // 2. 穿透锁屏、点亮屏幕（Android 8.0+ 使用新 API）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            // 请求解除键盘锁
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            // Android 8.0 以下使用旧版 Flags
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            );
        }

        // 3. 保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 4. 获取 WakeLock 确保设备完全唤醒
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CountdownReminder::AlarmRingWakeLock"
            );
            wakeLock.acquire(5 * 60 * 1000L);
        }

        setContentView(R.layout.alarm_ring_activity);

        timeView = findViewById(R.id.alarm_time);
        dateView = findViewById(R.id.alarm_date);
        sliderCenter = findViewById(R.id.slider_center);
        countdownProgress = findViewById(R.id.countdown_progress);

        updateTime();

        timeHandler = new Handler(Looper.getMainLooper());
        timeHandler.postDelayed(timeUpdateRunnable, 1000);

        Intent intent = getIntent();
        alarmTitle = intent.getStringExtra("title");
        alarmContent = intent.getStringExtra("content");
        if (alarmTitle != null && !alarmTitle.isEmpty()) {
            TextView titleView = findViewById(R.id.alarm_title);
            titleView.setText(alarmTitle);
        }

        // 读取再响次数
        snoozeCount = AlarmService.getSnoozeCount(this);
        updateSnoozeButtonState();

        Button snoozeButton = findViewById(R.id.snooze_button);
        snoozeButton.setOnClickListener(v -> {
            if (snoozeCount < MAX_SNOOZE) {
                snoozeAlarm();
            }
        });

        sliderCenter.setOnTouchListener(sliderTouchListener);

        // 启动超时自动停止倒计时
        startAutoStopCountdown();
    }

    private Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateTime();
            timeHandler.postDelayed(this, 1000);
        }
    };

    private void updateTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日 EEEE", Locale.getDefault());
        timeView.setText(timeFormat.format(calendar.getTime()));
        dateView.setText(dateFormat.format(calendar.getTime()));
    }

    /**
     * 更新稍后提醒按钮状态（达到上限后禁用）
     */
    private void updateSnoozeButtonState() {
        Button snoozeButton = findViewById(R.id.snooze_button);
        int remaining = MAX_SNOOZE - snoozeCount;
        if (remaining <= 0) {
            snoozeButton.setEnabled(false);
            snoozeButton.setText("已达到最大提醒次数");
            snoozeButton.setAlpha(0.5f);
        } else {
            snoozeButton.setEnabled(true);
            snoozeButton.setText("10 分钟后提醒");
            snoozeButton.setAlpha(1f);
        }
    }

    /**
     * 启动超时自动停止倒计时
     * 2分钟后自动稍后提醒（如果未达上限）或停止
     */
    private void startAutoStopCountdown() {
        if (countdownProgress != null) {
            countdownProgress.setMax(120);
            countdownProgress.setProgress(120);
        }
        autoStopCountdown = new CountDownTimer(AUTO_STOP_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                if (countdownProgress != null) {
                    countdownProgress.setProgress(seconds);
                }
            }

            @Override
            public void onFinish() {
                if (snoozeCount < MAX_SNOOZE) {
                    snoozeAlarm();
                } else {
                    stopAlarmAndFinish();
                }
            }
        };
        autoStopCountdown.start();
    }

    /**
     * 音量键触发稍后提醒
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (snoozeCount < MAX_SNOOZE) {
                snoozeAlarm();
            }
            return true; // 消费事件，阻止音量变化
        }
        return super.onKeyDown(keyCode, event);
    }

    private OnTouchListener sliderTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    isDragging = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!isDragging) return false;
                    currentX = event.getRawX();
                    float deltaX = currentX - startX;

                    float maxOffset = 120f;
                    float offset = Math.max(-maxOffset, Math.min(maxOffset, deltaX));

                    sliderCenter.setTranslationX(offset);

                    float progress = Math.abs(offset) / maxOffset;
                    sliderCenter.setScaleX(1 + progress * 0.2f);
                    sliderCenter.setScaleY(1 + progress * 0.2f);

                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!isDragging) return false;
                    isDragging = false;

                    float finalOffset = sliderCenter.getTranslationX();
                    float maxOffsetFinal = 120f;

                    if (Math.abs(finalOffset) > maxOffsetFinal * 0.7f) {
                        stopAlarmAndFinish();
                    } else {
                        sliderCenter.animate()
                            .translationX(0)
                            .scaleX(1)
                            .scaleY(1)
                            .setDuration(300)
                            .start();
                    }
                    return true;
            }
            return false;
        }
    };

    private void snoozeAlarm() {
        AlarmService.incrementSnoozeCount(this);
        AlarmService.snoozeAlarm(this, 10, alarmTitle, alarmContent);
        Toast.makeText(this, "已设置 10 分钟后再次提醒", Toast.LENGTH_SHORT).show();
        finishAlarmActivity();
    }

    private void stopAlarmAndFinish() {
        AlarmService.resetSnoozeCount(this);
        AlarmService.cancelSnoozeAlarm(this);
        AlarmService.stopAlarm(this);
        finishAlarmActivity();
    }

    private void finishAlarmActivity() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }

        if (timeHandler != null) {
            timeHandler.removeCallbacks(timeUpdateRunnable);
            timeHandler = null;
        }

        if (autoStopCountdown != null) {
            autoStopCountdown.cancel();
            autoStopCountdown = null;
        }

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
        if (timeHandler != null) {
            timeHandler.removeCallbacks(timeUpdateRunnable);
            timeHandler = null;
        }
        if (autoStopCountdown != null) {
            autoStopCountdown.cancel();
            autoStopCountdown = null;
        }
    }
}