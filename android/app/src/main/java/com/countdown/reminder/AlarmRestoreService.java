package com.countdown.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 闹钟恢复前台服务
 * 在设备重启后恢复每日闹钟提醒
 */
public class AlarmRestoreService extends Service {
    private static final String TAG = "AlarmRestoreService";
    private static final String PREFS_NAME = "CapacitorStorage";
    private static final String SETTINGS_KEY = "countdown_settings";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "countdown_service_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务启动");
        
        // 创建通知渠道
        createNotificationChannel();
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification());
        
        // 恢复闹钟设置
        restoreAlarms();
        
        // 完成后停止服务
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "倒计时提醒服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持闹钟服务运行");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("倒计时提醒")
            .setContentText("正在恢复闹钟服务...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void restoreAlarms() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String settingsJson = prefs.getString(SETTINGS_KEY, null);
            
            if (settingsJson != null) {
                JSONObject settings = new JSONObject(settingsJson);
                String targetDateStr = settings.getString("targetDate");
                String targetTime = settings.getString("targetTime");
                String reminderContent = settings.getString("reminderContent");
                
                Log.d(TAG, "恢复设置: " + settingsJson);
                
                // 计算剩余天数
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date targetDate = sdf.parse(targetDateStr);
                Date today = new Date();
                
                if (targetDate != null) {
                    long diff = targetDate.getTime() - today.getTime();
                    int daysLeft = (int) Math.ceil(diff / (1000.0 * 60 * 60 * 24));
                    
                    if (daysLeft > 0) {
                        Log.d(TAG, "剩余天数: " + daysLeft);
                        // Capacitor的LocalNotifications会在应用启动时自动恢复
                        // 这里只记录日志
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "恢复闹钟失败: " + e.getMessage());
        }
    }
}