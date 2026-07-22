package com.william163.countdown;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 闹钟管理插件
 * 使用 AlarmManager 设定精确闹钟，触发后由 AlarmService 播放声音和震动
 */
@CapacitorPlugin(name = "AlarmPlugin")
public class AlarmPlugin extends Plugin {

    private static final String TAG = "AlarmPlugin";
    private static final String PREFS_NAME = "alarm_prefs";
    private static final String ALARM_IDS_KEY = "alarm_ids";
    private static final int ALARM_BASE_ID = 10000;
    private static final String KEY_HOUR = "restore_hour";
    private static final String KEY_MINUTE = "restore_minute";
    private static final String KEY_TARGET_NAME = "restore_target_name";
    private static final String KEY_DAYS_REMAINING = "restore_days_remaining";
    private static final String KEY_TARGET_DATE = "restore_target_date";

    /**
     * 设置每日闹钟
     * 参数: hour(小时), minute(分钟), targetName(目标名称), daysRemaining(剩余天数)
     */
    @PluginMethod
    public void scheduleDailyAlarms(PluginCall call) {
        Integer hour = call.getInt("hour");
        Integer minute = call.getInt("minute");
        String targetName = call.getString("targetName", "倒计时提醒");
        Integer daysRemaining = call.getInt("daysRemaining", 0);
        String targetDate = call.getString("targetDate", "");

        if (hour == null || minute == null) {
            call.reject("hour 和 minute 参数必填");
            return;
        }

        Log.d(TAG, "设置每日闹钟: " + hour + ":" + minute + ", 目标: " + targetName + ", 剩余天数: " + daysRemaining);

        try {
            // 先取消所有已有闹钟
            cancelAllAlarmsInternal();

            AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                call.reject("无法获取AlarmManager");
                return;
            }

            List<Integer> alarmIds = new ArrayList<>();
            Calendar now = Calendar.getInstance();

            // 设置未来30天的闹钟
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
                PendingIntent pendingIntent = createAlarmPendingIntent(alarmId, "倒计时提醒", content);

                // 使用 setAlarmClock 确保精确触发（最可靠的方式）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                        alarmTime.getTimeInMillis(), pendingIntent
                    );
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), pendingIntent
                    );
                }

                alarmIds.add(alarmId);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Log.d(TAG, "已设置闹钟 ID=" + alarmId + " 时间=" + sdf.format(alarmTime.getTime()) + " 内容=" + content);
            }

            // 保存闹钟ID列表
            saveAlarmIds(alarmIds);

            // 保存调度参数，用于开机/时间变更后恢复
            saveRestoreParams(hour, minute, targetName, daysRemaining, targetDate);

            JSObject result = new JSObject();
            result.put("success", true);
            result.put("count", alarmIds.size());
            call.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "设置闹钟失败", e);
            call.reject("设置闹钟失败: " + e.getMessage());
        }
    }

    /**
     * 取消所有闹钟
     */
    @PluginMethod
    public void cancelAllAlarms(PluginCall call) {
        try {
            int count = cancelAllAlarmsInternal();
            Log.d(TAG, "已取消 " + count + " 个闹钟");

            JSObject result = new JSObject();
            result.put("success", true);
            result.put("count", count);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "取消闹钟失败", e);
            call.reject("取消闹钟失败: " + e.getMessage());
        }
    }

    /**
     * 停止当前正在响铃的闹钟
     */
    @PluginMethod
    public void stopAlarm(PluginCall call) {
        try {
            AlarmService.stopAlarm(getContext());
            Log.d(TAG, "闹钟已停止");

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "停止闹钟失败", e);
            call.reject("停止闹钟失败: " + e.getMessage());
        }
    }

    /**
     * 创建闹钟 PendingIntent
     */
    private PendingIntent createAlarmPendingIntent(int alarmId, String title, String content) {
        Intent intent = new Intent(getContext(), AlarmReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("content", content);
        return PendingIntent.getBroadcast(
            getContext(), alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /**
     * 取消所有已设置的闹钟
     */
    private int cancelAllAlarmsInternal() {
        // 重置再响次数
        AlarmService.resetSnoozeCount(getContext());
        // 取消再响闹钟
        AlarmService.cancelSnoozeAlarm(getContext());

        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return 0;

        List<Integer> alarmIds = getAlarmIds();
        int count = 0;
        for (int alarmId : alarmIds) {
            PendingIntent pendingIntent = createAlarmPendingIntent(alarmId, "", "");
            alarmManager.cancel(pendingIntent);
            count++;
        }

        // 清除保存的ID
        saveAlarmIds(new ArrayList<>());
        return count;
    }

    /**
     * 保存闹钟ID列表到 SharedPreferences
     */
    private void saveAlarmIds(List<Integer> alarmIds) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();
        for (int id : alarmIds) {
            jsonArray.put(id);
        }
        prefs.edit().putString(ALARM_IDS_KEY, jsonArray.toString()).apply();
    }

    /**
     * 从 SharedPreferences 获取闹钟ID列表
     */
    private List<Integer> getAlarmIds() {
        List<Integer> alarmIds = new ArrayList<>();
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(ALARM_IDS_KEY, "[]");
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                alarmIds.add(jsonArray.getInt(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "读取闹钟ID失败", e);
        }
        return alarmIds;
    }

    /**
     * 保存调度参数，用于开机/时间变更后恢复闹钟
     */
    private void saveRestoreParams(int hour, int minute, String targetName, int daysRemaining, String targetDate) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putString(KEY_TARGET_NAME, targetName)
            .putInt(KEY_DAYS_REMAINING, daysRemaining)
            .putString(KEY_TARGET_DATE, targetDate)
            .apply();
    }
}
