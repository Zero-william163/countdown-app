package com.william163.countdown;

import android.app.AlarmManager;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 闹钟恢复前台服务
 * 在设备重启后或系统时间变更后恢复每日闹钟提醒
 */
public class AlarmRestoreService extends Service {
    private static final String TAG = "AlarmRestoreService";
    private static final String ALARM_PREFS = "alarm_prefs";
    private static final String KEY_HOUR = "restore_hour";
    private static final String KEY_MINUTE = "restore_minute";
    private static final String KEY_TARGET_NAME = "restore_target_name";
    private static final String KEY_DAYS_REMAINING = "restore_days_remaining";
    private static final String KEY_TARGET_DATE = "restore_target_date";
    private static final String ALARM_IDS_KEY = "alarm_ids";
    private static final int ALARM_BASE_ID = 10000;
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
            SharedPreferences prefs = getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);

            // 检查是否有保存的调度参数
            if (!prefs.contains(KEY_HOUR) || !prefs.contains(KEY_MINUTE)) {
                Log.d(TAG, "未找到闹钟调度参数，跳过恢复");
                return;
            }

            int hour = prefs.getInt(KEY_HOUR, -1);
            int minute = prefs.getInt(KEY_MINUTE, -1);
            if (hour < 0 || minute < 0) {
                Log.d(TAG, "闹钟参数无效，跳过恢复");
                return;
            }

            String targetName = prefs.getString(KEY_TARGET_NAME, "倒计时提醒");
            int daysRemaining = prefs.getInt(KEY_DAYS_REMAINING, 0);
            String targetDate = prefs.getString(KEY_TARGET_DATE, "");

            Log.d(TAG, "恢复闹钟: " + hour + ":" + minute + ", 目标: " + targetName + ", 剩余: " + daysRemaining + "天");

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e(TAG, "无法获取AlarmManager");
                return;
            }

            Calendar now = Calendar.getInstance();
            List<Integer> alarmIds = new ArrayList<>();

            // 设置未来N天的闹钟（与AlarmPlugin逻辑一致）
            int maxDays = Math.min(Math.max(daysRemaining, 1), 30);
            for (int i = 0; i <= maxDays; i++) {
                Calendar alarmTime = Calendar.getInstance();
                alarmTime.add(Calendar.DAY_OF_YEAR, i);
                alarmTime.set(Calendar.HOUR_OF_DAY, hour);
                alarmTime.set(Calendar.MINUTE, minute);
                alarmTime.set(Calendar.SECOND, 0);
                alarmTime.set(Calendar.MILLISECOND, 0);

                // 如果时间已过，跳过
                if (alarmTime.getTimeInMillis() <= now.getTimeInMillis()) {
                    continue;
                }

                int daysLeft = daysRemaining - i;
                String content;
                if (daysLeft > 0) {
                    content = "离" + targetName + "还有" + daysLeft + "天";
                } else {
                    content = "今天就是" + targetName + "！";
                }

                int alarmId = ALARM_BASE_ID + i;
                Intent intent = new Intent(this, AlarmReceiver.class);
                intent.putExtra("title", "倒计时提醒");
                intent.putExtra("content", content);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, alarmId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AlarmManager.AlarmClockInfo alarmClockInfo =
                        new AlarmManager.AlarmClockInfo(alarmTime.getTimeInMillis(), pendingIntent);
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pendingIntent);
                }

                alarmIds.add(alarmId);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Log.d(TAG, "已恢复闹钟 ID=" + alarmId + " 时间=" + sdf.format(alarmTime.getTime()));
            }

            // 更新保存的闹钟ID列表
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (int id : alarmIds) {
                jsonArray.put(id);
            }
            prefs.edit().putString(ALARM_IDS_KEY, jsonArray.toString()).apply();

            // 恢复再响闹钟（如果存在且未过期）
            restoreSnoozeAlarm(alarmManager);

            Log.d(TAG, "闹钟恢复完成，共恢复 " + alarmIds.size() + " 个闹钟");

        } catch (Exception e) {
            Log.e(TAG, "恢复闹钟失败", e);
        }
    }

    /**
     * 恢复再响闹钟（设备重启后）
     */
    private void restoreSnoozeAlarm(AlarmManager alarmManager) {
        try {
            android.os.Bundle snoozeInfo = AlarmService.getSavedSnoozeInfo(this);
            if (snoozeInfo == null) {
                Log.d(TAG, "没有保存的再响闹钟，跳过恢复");
                return;
            }

            long triggerTime = snoozeInfo.getLong("snooze_trigger_time", 0);
            String title = snoozeInfo.getString("snooze_title", "倒计时提醒");
            String content = snoozeInfo.getString("snooze_content", "闹钟响了，点击关闭");

            if (triggerTime <= System.currentTimeMillis()) {
                Log.d(TAG, "再响闹钟已过期，清除保存的信息");
                AlarmService.cancelSnoozeAlarm(this);
                return;
            }

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("title", title);
            intent.putExtra("content", content);
            intent.putExtra("is_snooze", true);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 9999, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo alarmClockInfo =
                    new AlarmManager.AlarmClockInfo(triggerTime, pendingIntent);
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Log.d(TAG, "再响闹钟已恢复，触发时间: " + sdf.format(new Date(triggerTime)));
        } catch (Exception e) {
            Log.e(TAG, "恢复再响闹钟失败", e);
        }
    }
}