package com.william163.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 传统 RemoteViews 桌面小组件
 * 纯传统方案，兼容 Android 7.0+ (API 24+)
 * 支持每分钟自动刷新，与 App 内北京时间倒计时同步
 */
class CountdownWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "CountdownWidget"
        private const val ACTION_UPDATE_WIDGET_TIME = "com.william163.countdown.UPDATE_WIDGET_TIME"
        private const val UPDATE_INTERVAL_MILLIS = 60 * 1000L // 每分钟更新一次

        @JvmStatic
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, CountdownWidget::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    val widget = CountdownWidget()
                    for (appWidgetId in appWidgetIds) {
                        try {
                            widget.updateAppWidget(context, appWidgetManager, appWidgetId)
                        } catch (e: Exception) {
                            Log.e(TAG, "手动更新小组件 $appWidgetId 失败", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateAllWidgets 失败", e)
            }
        }

        /**
         * 启动小组件定时更新闹钟
         * 每分钟触发一次，确保桌面时间实时刷新
         */
        @JvmStatic
        fun startWidgetUpdateAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, CountdownWidget::class.java).apply {
                    action = ACTION_UPDATE_WIDGET_TIME
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

                // 取消旧的闹钟再重新设置，避免重复
                alarmManager.cancel(pendingIntent)

                val triggerAtMillis = System.currentTimeMillis() + 1000
                alarmManager.setRepeating(
                    AlarmManager.RTC,
                    triggerAtMillis,
                    UPDATE_INTERVAL_MILLIS,
                    pendingIntent
                )
                Log.d(TAG, "已启动小组件定时更新，间隔 ${UPDATE_INTERVAL_MILLIS}ms")
            } catch (e: Exception) {
                Log.e(TAG, "启动小组件定时更新失败", e)
            }
        }

        /**
         * 停止小组件定时更新闹钟
         */
        @JvmStatic
        fun stopWidgetUpdateAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, CountdownWidget::class.java).apply {
                    action = ACTION_UPDATE_WIDGET_TIME
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "已停止小组件定时更新")
            } catch (e: Exception) {
                Log.e(TAG, "停止小组件定时更新失败", e)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e(TAG, "更新小组件 $appWidgetId 失败", e)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 第一个小组件被添加时启动定时更新
        startWidgetUpdateAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 最后一个小组件被移除时停止定时更新
        stopWidgetUpdateAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)

            // 处理每分钟定时刷新广播
            if (intent.action == ACTION_UPDATE_WIDGET_TIME) {
                updateAllWidgets(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onReceive 异常", e)
        }
    }

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        // 创建最基础的 RemoteViews - 即使后续全部失败，也能显示默认布局
        val views = RemoteViews(context.packageName, R.layout.countdown_widget)

        try {
            // 读取设置
            var targetName = "倒计时提醒"
            var targetDateStr = ""
            var targetTimeStr = "00:00"

            try {
                val prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                val settingsJson = prefs.getString("countdown_settings", null)
                if (settingsJson != null) {
                    val json = org.json.JSONObject(settingsJson)
                    targetName = json.optString("targetName", "倒计时提醒")
                    targetDateStr = json.optString("targetDate", "")
                    targetTimeStr = json.optString("reminderTime", "00:00")
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取设置失败", e)
            }

            // 安全计算倒计时（使用北京时间，与 App 内同步）
            val result = calculateCountdown(targetDateStr)

            // 设置文本
            views.setTextViewText(R.id.widget_title, targetName)

            if (result.isActive) {
                views.setTextViewText(R.id.widget_days, "${result.days}")
                views.setTextViewText(R.id.widget_days_label, "天")
                views.setTextViewText(R.id.widget_time, String.format("%02d:%02d:%02d", result.hours, result.mins, result.secs))
                views.setTextViewText(R.id.widget_reminder, "每日 ${targetTimeStr} 提醒")
                views.setTextViewText(R.id.widget_button, "打开应用")
            } else {
                views.setTextViewText(R.id.widget_days, "未设置")
                views.setTextViewText(R.id.widget_days_label, "")
                views.setTextViewText(R.id.widget_time, "")
                views.setTextViewText(R.id.widget_reminder, "点击设置倒计时")
                views.setTextViewText(R.id.widget_button, "立即设置")
            }

            // 点击打开应用
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "设置小组件内容失败", e)
        }

        // 最后一定要调用 updateAppWidget，确保显示内容
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * 使用北京时间计算倒计时，与 App 内逻辑保持一致
     * App 内逻辑：目标日期按 UTC 0 点，当前时间用北京时间
     */
    private fun calculateCountdown(targetDateStr: String): CountdownResult {
        if (targetDateStr.isBlank()) {
            return CountdownResult(0, 0, 0, 0, false)
        }

        return try {
            val parts = targetDateStr.split("-")
            if (parts.size < 3) return CountdownResult(0, 0, 0, 0, false)

            val year = parts[0].trim().toIntOrNull() ?: return CountdownResult(0, 0, 0, 0, false)
            val month = parts[1].trim().toIntOrNull() ?: return CountdownResult(0, 0, 0, 0, false)
            val day = parts[2].trim().toIntOrNull() ?: return CountdownResult(0, 0, 0, 0, false)

            if (month < 1 || month > 12 || day < 1 || day > 31 || year < 1900) {
                return CountdownResult(0, 0, 0, 0, false)
            }

            // 目标日期按 UTC 0 点计算
            val targetCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            targetCal.set(year, month - 1, day, 0, 0, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
            val targetMillis = targetCal.timeInMillis

            // 当前时间转换为北京时间（与 App 内 getBeijingTimeFallback 一致）
            val now = System.currentTimeMillis()
            val beijingOffsetMillis = TimeUnit.HOURS.toMillis(8)
            val localOffsetMillis = TimeZone.getDefault().getOffset(now).toLong()
            val beijingNowMillis = now + beijingOffsetMillis - localOffsetMillis

            val diff = targetMillis - beijingNowMillis

            if (diff <= 0) {
                CountdownResult(0, 0, 0, 0, false)
            } else {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val remaining = diff % TimeUnit.DAYS.toMillis(1)
                val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                val mins = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                val secs = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                CountdownResult(days.toInt(), hours.toInt(), mins.toInt(), secs.toInt(), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "计算倒计时失败: $targetDateStr", e)
            CountdownResult(0, 0, 0, 0, false)
        }
    }

    data class CountdownResult(
        val days: Int,
        val hours: Int,
        val mins: Int,
        val secs: Int,
        val isActive: Boolean
    )
}
