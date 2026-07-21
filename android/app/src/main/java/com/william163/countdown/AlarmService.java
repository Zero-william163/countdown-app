package com.william163.countdown;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
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

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;

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

        // 启动前台通知
        startForeground(NOTIFICATION_ID, createNotification(title, content));

        // 开始播放声音和震动
        startAlarmSound();
        startVibration();

        return START_STICKY;
    }

    /**
     * 播放铃声：优先播放自定义铃声，降级为系统默认闹钟
     */
    private void startAlarmSound() {
        try {
            // 读取用户自定义铃声路径
            SharedPreferences prefs = getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
            String customRingtonePath = prefs.getString("custom_ringtone_path", null);

            mediaPlayer = new MediaPlayer();

            // 设置音频属性：走 STREAM_ALARM 通道，独立于静音模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            boolean isCustomPlayed = false;

            // 优先尝试播放自定义铃声
            if (customRingtonePath != null && !customRingtonePath.trim().isEmpty()) {
                File customFile = new File(customRingtonePath);
                if (customFile.exists() && customFile.canRead()) {
                    try {
                        mediaPlayer.setDataSource(customRingtonePath);
                        mediaPlayer.setLooping(true);
                        mediaPlayer.prepare();
                        mediaPlayer.start();
                        isCustomPlayed = true;
                        Log.d(TAG, "使用自定义铃声: " + customRingtonePath);
                    } catch (Exception e) {
                        Log.e(TAG, "自定义铃声播放失败，降级到系统铃声", e);
                        // 重新创建 MediaPlayer，避免状态错误
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

            // 降级：播放系统默认闹钟铃声
            if (!isCustomPlayed) {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }

                if (alarmUri == null) {
                    Log.e(TAG, "无法获取系统铃声URI");
                    return;
                }

                mediaPlayer.setDataSource(this, alarmUri);
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
                Log.d(TAG, "使用系统默认闹钟铃声");
            }

            Log.d(TAG, "闹钟铃声开始播放");
        } catch (Exception e) {
            Log.e(TAG, "播放闹钟铃声失败", e);
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
                }
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(pattern, 0);
                }
                Log.d(TAG, "震动开始");
            } else {
                Log.d(TAG, "设备不支持震动");
            }
        } catch (Exception e) {
            Log.e(TAG, "震动失败", e);
        }
    }

    /**
     * 创建全屏闹钟通知，带有关闭按钮
     */
    private Notification createNotification(String title, String content) {
        // 点击通知打开应用
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
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
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
     * 停止所有声音和震动，释放资源
     */
    public static void stopAlarm(Context context) {
        context.stopService(new Intent(context, AlarmService.class));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "闹钟服务停止，释放资源");

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
