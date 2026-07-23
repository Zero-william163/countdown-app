package com.william163.countdown;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;

/**
 * 闹钟响铃前台服务
 * 使用 MediaPlayer 播放系统闹钟铃声，使用 Vibrator 震动
 * 类似华为闹钟的实现方式
 */
public class AlarmService extends Service {
    private static final String TAG = "AlarmService";
    private static final int NOTIFICATION_ID = 2001;
    private static final String CHANNEL_ID = "alarm_ringing_channel";
    private static final long AUTO_STOP_DELAY = 2 * 60 * 1000L; // 2分钟自动停止
    private static final int MAX_SNOOZE = 3; // 最大再响次数
    private static final String SNOOZE_PREFS = "alarm_prefs";
    private static final String SNOOZE_COUNT_KEY = "snooze_count";

    private static final String ACTION_ALARM_UI_READY = "ACTION_ALARM_UI_READY";
    private static final long UI_READY_TIMEOUT = 1500; // 1.5秒保底

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private ValueAnimator volumeAnimator;
    private Handler autoStopHandler;
    private Runnable autoStopRunnable;
    private Handler syncHandler;
    private BroadcastReceiver uiReadyReceiver;
    private boolean isPlaying = false;
    private String alarmTitle = "倒计时提醒";
    private String alarmContent = "闹钟响了，点击关闭";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "闹钟服务启动");

        // 获取 WakeLock 确保设备保持唤醒
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CountdownReminder::AlarmWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 最多保持10分钟
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = "倒计时提醒";
        String content = "闹钟响了，点击关闭";

        if (intent != null) {
            title = intent.getStringExtra("title");
            if (title == null) title = "倒计时提醒";
            content = intent.getStringExtra("content");
            if (content == null) content = "闹钟响了，点击关闭";
        }

        this.alarmTitle = title;
        this.alarmContent = content;

        // 启动前台通知（通过 FullScreenIntent 拉起全屏界面）
        startForeground(NOTIFICATION_ID, createNotification(title, content));

        // 预加载铃声资源，但不播放（等待UI就绪信号）
        prepareAlarmSound();

        // 注册广播监听器，等待 AlarmRingActivity 的 UI 就绪信号
        syncHandler = new Handler(Looper.getMainLooper());
        uiReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "收到 UI 就绪信号，开始响铃和震动");
                startPlayingAndVibrating();
            }
        };
        // 【关键】Android 14+（targetSdk >= 34）注册非系统广播接收器必须指定
        // RECEIVER_NOT_EXPORTED / RECEIVER_EXPORTED，否则抛 SecurityException，
        // 会导致后面的兜底定时器无法执行，震动和声音都不会启动。
        // 用 try-catch 包裹，即使注册失败也保证兜底定时器能正常调度。
        try {
            IntentFilter filter = new IntentFilter(ACTION_ALARM_UI_READY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(uiReadyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(uiReadyReceiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "注册 UI 就绪广播接收器失败", e);
        }

        // 【保险机制】1.5秒后如果还没收到 UI 信号，强制兜底响铃
        syncHandler.postDelayed(() -> {
            Log.d(TAG, "UI 就绪超时，强制兜底响铃");
            startPlayingAndVibrating();
        }, UI_READY_TIMEOUT);

        // 超时自动停止（2分钟后）
        startAutoStopTimer();

        return START_STICKY;
    }

    /**
     * 预加载铃声资源（子弹上膛），不调用 start()
     * 等待 UI 就绪信号后再播放
     */
    private void prepareAlarmSound() {
        try {
            SharedPreferences prefs = getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            String customRingtonePath = prefs.getString("custom_ringtone_path", null);

            mediaPlayer = new MediaPlayer();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            boolean isCustomPrepared = false;

            if (customRingtonePath != null && !customRingtonePath.trim().isEmpty()) {
                File customFile = new File(customRingtonePath);
                if (customFile.exists() && customFile.canRead()) {
                    try {
                        mediaPlayer.setDataSource(customRingtonePath);
                        mediaPlayer.setLooping(true);
                        mediaPlayer.prepare();
                        isCustomPrepared = true;
                        Log.d(TAG, "预加载自定义铃声完成: " + customRingtonePath);
                    } catch (Exception e) {
                        Log.e(TAG, "自定义铃声预加载失败，降级到系统铃声", e);
                        try {
                            mediaPlayer.release();
                        } catch (Exception ex) { /* ignore */ }
                        mediaPlayer = new MediaPlayer();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build();
                            mediaPlayer.setAudioAttributes(audioAttributes);
                        } else {
                            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                        }
                    }
                }
            }

            if (!isCustomPrepared) {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

                if (alarmUri == null) {
                    Log.e(TAG, "无法获取系统铃声URI");
                    return;
                }

                mediaPlayer.setDataSource(this, alarmUri);
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                Log.d(TAG, "预加载系统默认闹钟铃声完成");
            }

            Log.d(TAG, "铃声资源预加载完成，等待 UI 就绪信号");
        } catch (Exception e) {
            Log.e(TAG, "预加载铃声失败", e);
        }
    }

    /**
     * 【核心】收到 UI 就绪信号后，开始播放声音和震动
     * 声音和震动严格同步启动，防止出现"只有震动没有声音"的错觉
     */
    private void startPlayingAndVibrating() {
        if (isPlaying) return;
        isPlaying = true;

        // 注销广播监听器（已完成使命）
        if (uiReadyReceiver != null) {
            try {
                unregisterReceiver(uiReadyReceiver);
            } catch (Exception e) { /* ignore */ }
            uiReadyReceiver = null;
        }

        // 取消兜底定时器
        if (syncHandler != null) {
            syncHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "startPlayingAndVibrating: 开始播放声音和震动");

        // 【关键】先启动震动（独立于声音，确保即使声音加载失败也能震动）
        startVibration();

        // 再启动声音
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(0.3f, 0.3f); // 初始音量30%，立即可听见
                mediaPlayer.start();
                startVolumeFadeIn();
                Log.d(TAG, "闹钟铃声开始播放（初始音量30%）");
            } catch (Exception e) {
                Log.e(TAG, "启动播放失败，但震动仍在继续", e);
            }
        } else {
            Log.w(TAG, "mediaPlayer 为 null，声音无法播放，但震动已启动");
        }
    }

    /**
     * 使用 Vibrator 实现震动
     * 模式：震动500ms，停止500ms，循环
     */
    private void startVibration() {
        try {
            // 震动模式：等待0ms，震动500ms，停止500ms，震动500ms，停止500ms...
            long[] pattern = {0, 500, 500, 500, 500};

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                    Log.d(TAG, "震动: 使用 VibratorManager 获取 Vibrator");
                } else {
                    Log.e(TAG, "震动: VibratorManager 为 null");
                }
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                Log.d(TAG, "震动: 使用旧版 VibratorService");
            }

            if (vibrator == null) {
                Log.e(TAG, "震动: vibrator 为 null，无法震动");
                return;
            }

            if (!vibrator.hasVibrator()) {
                Log.w(TAG, "震动: 设备不支持震动");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                vibrator.vibrate(effect);
                Log.d(TAG, "震动: VibrationEffect.createWaveform 已启动");
            } else {
                vibrator.vibrate(pattern, 0);
                Log.d(TAG, "震动: 旧版 vibrate(pattern) 已启动");
            }
        } catch (SecurityException se) {
            Log.e(TAG, "震动: 权限被拒绝", se);
        } catch (Exception e) {
            Log.e(TAG, "震动失败", e);
        }
    }

    /**
     * 创建全屏闹钟通知
     * 使用系统认可的 FullScreenIntent 机制拉起全屏闹钟界面
     * 不设置 setOngoing(true)，允许通知在用户关闭闹钟后自动消失
     */
    private Notification createNotification(String title, String content) {
        // 点击通知也打开全屏闹钟界面
        Intent openIntent = new Intent(this, AlarmRingActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra("title", title);
        openIntent.putExtra("content", content);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 关闭闹钟的按钮
        Intent stopIntent = new Intent(this, AlarmStopReceiver.class);
        stopIntent.setAction("com.william163.countdown.STOP_ALARM");
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)
            .setVibrate(null)
            .addAction(android.R.drawable.ic_media_pause, "关闭闹钟", stopPendingIntent);

        // 全屏意图 - 使用专门的响铃界面
        Intent fullScreenIntent = new Intent(this, AlarmRingActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        fullScreenIntent.putExtra("title", title);
        fullScreenIntent.putExtra("content", content);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setFullScreenIntent(fullScreenPendingIntent, true);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "闹钟响铃",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("闹钟响铃时的通知");
            channel.setBypassDnd(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 音量渐进增大（Fade-in）
     * 在10秒内将音量从30%逐渐过渡到100%，避免突然大声吓到用户
     * 初始音量设为30%确保与震动同时出现时即可被听见
     */
    private void startVolumeFadeIn() {
        if (mediaPlayer != null) {
            try {
                // 从30%开始渐增到100%，确保一开始就能听到声音
                volumeAnimator = ValueAnimator.ofFloat(0.3f, 1f);
                volumeAnimator.setDuration(10000); // 10秒渐变
                volumeAnimator.addUpdateListener(animation -> {
                    float volume = (float) animation.getAnimatedValue();
                    if (mediaPlayer != null) {
                        try {
                            mediaPlayer.setVolume(volume, volume);
                        } catch (Exception e) {
                            Log.e(TAG, "设置音量失败", e);
                        }
                    }
                });
                volumeAnimator.start();
                Log.d(TAG, "音量渐进增大已启动（30% -> 100%）");
            } catch (Exception e) {
                Log.e(TAG, "启动音量渐进失败", e);
            }
        }
    }

    /**
     * 超时自动停止
     * 响铃2分钟后自动停止，如果再响次数未达上限则自动稍后提醒
     */
    private void startAutoStopTimer() {
        autoStopHandler = new Handler(Looper.getMainLooper());
        autoStopRunnable = () -> {
            Log.d(TAG, "闹钟超时自动停止");
            int count = getSnoozeCount(this);
            if (count < MAX_SNOOZE) {
                snoozeAlarm(this, 10, alarmTitle, alarmContent);
            } else {
                stopAlarm(this);
            }
        };
        autoStopHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY);
    }

    /**
     * 获取当前再响次数
     */
    public static int getSnoozeCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(SNOOZE_COUNT_KEY, 0);
    }

    /**
     * 增加再响次数
     */
    public static void incrementSnoozeCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(SNOOZE_COUNT_KEY, 0);
        prefs.edit().putInt(SNOOZE_COUNT_KEY, count + 1).apply();
    }

    /**
     * 重置再响次数
     */
    public static void resetSnoozeCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(SNOOZE_COUNT_KEY, 0).apply();
    }

    /**
     * 获取最大再响次数
     */
    public static int getMaxSnoozeCount() {
        return MAX_SNOOZE;
    }

    /**
     * 停止所有声音和震动，释放资源
     */
    public static void stopAlarm(Context context) {
        context.stopService(new Intent(context, AlarmService.class));
    }

    private static final String SNOOZE_PREFS_NAME = "snooze_alarm_prefs";
    private static final String KEY_SNOOZE_TIME = "snooze_trigger_time";
    private static final String KEY_SNOOZE_TITLE = "snooze_title";
    private static final String KEY_SNOOZE_CONTENT = "snooze_content";
    private static final int SNOOZE_REQUEST_CODE = 9999;

    /**
     * 稍后提醒：停止当前闹钟，在指定分钟数后重新触发
     * 使用独立的 requestCode (SNOOZE_REQUEST_CODE = 9999)，避免与正常闹钟冲突
     */
    public static void snoozeAlarm(Context context, int minutes, String title, String content) {
        stopAlarm(context);

        long triggerTime = System.currentTimeMillis() + minutes * 60 * 1000L;
        // 使用独立 requestCode，避免覆盖用户设置的每日提醒闹钟
        final int requestCode = SNOOZE_REQUEST_CODE;

        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("title", title != null ? title : "倒计时提醒");
            intent.putExtra("content", content != null ? content : "闹钟响了，点击关闭");
            intent.putExtra("is_snooze", true);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 先取消可能已存在的旧再响闹钟，避免重复
            alarmManager.cancel(pendingIntent);

            // 保存再响闹钟信息，用于设备重启后恢复
            SharedPreferences prefs = context.getSharedPreferences(SNOOZE_PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putLong(KEY_SNOOZE_TIME, triggerTime)
                .putString(KEY_SNOOZE_TITLE, title)
                .putString(KEY_SNOOZE_CONTENT, content)
                .apply();

            // 使用 setAlarmClock 确保在 Doze 模式下也能准时唤醒
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.app.AlarmManager.AlarmClockInfo alarmClockInfo =
                    new android.app.AlarmManager.AlarmClockInfo(triggerTime, pendingIntent);
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                );
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                );
            }
            Log.d(TAG, "已设置 " + minutes + " 分钟后再次提醒，触发时间: " + triggerTime);
        } catch (Exception e) {
            Log.e(TAG, "设置稍后提醒失败", e);
        }
    }

    /**
     * 取消再响闹钟
     */
    public static void cancelSnoozeAlarm(Context context) {
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, SNOOZE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(pendingIntent);

            // 清除保存的再响信息
            SharedPreferences prefs = context.getSharedPreferences(SNOOZE_PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();

            Log.d(TAG, "已取消再响闹钟");
        } catch (Exception e) {
            Log.e(TAG, "取消再响闹钟失败", e);
        }
    }

    /**
     * 获取保存的再响闹钟信息
     */
    public static android.os.Bundle getSavedSnoozeInfo(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SNOOZE_PREFS_NAME, Context.MODE_PRIVATE);
        long triggerTime = prefs.getLong(KEY_SNOOZE_TIME, 0);
        if (triggerTime <= 0) return null;

        android.os.Bundle bundle = new android.os.Bundle();
        bundle.putLong(KEY_SNOOZE_TIME, triggerTime);
        bundle.putString(KEY_SNOOZE_TITLE, prefs.getString(KEY_SNOOZE_TITLE, "倒计时提醒"));
        bundle.putString(KEY_SNOOZE_CONTENT, prefs.getString(KEY_SNOOZE_CONTENT, "闹钟响了，点击关闭"));
        return bundle;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "闹钟服务停止，释放资源");

        // 注销广播监听器
        if (uiReadyReceiver != null) {
            try {
                unregisterReceiver(uiReadyReceiver);
            } catch (Exception e) { /* ignore */ }
            uiReadyReceiver = null;
        }

        // 取消同步 Handler
        if (syncHandler != null) {
            syncHandler.removeCallbacksAndMessages(null);
            syncHandler = null;
        }

        // 取消音量渐进动画
        if (volumeAnimator != null) {
            volumeAnimator.cancel();
            volumeAnimator = null;
        }

        // 取消超时自动停止
        if (autoStopHandler != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
            autoStopHandler = null;
        }

        // 停止并释放 MediaPlayer
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "释放MediaPlayer失败", e);
            }
        }

        // 停止震动
        if (vibrator != null) {
            try {
                vibrator.cancel();
                vibrator = null;
            } catch (Exception e) {
                Log.e(TAG, "停止震动失败", e);
            }
        }

        // 释放 WakeLock
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                wakeLock = null;
            } catch (Exception e) {
                Log.e(TAG, "释放WakeLock失败", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
