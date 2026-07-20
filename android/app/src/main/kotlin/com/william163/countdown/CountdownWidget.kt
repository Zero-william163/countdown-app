package com.william163.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 传统 RemoteViews 桌面小组件
 * 纯传统方案，兼容 Android 7.0+ (API 24+)
 *
 * 实时更新机制：
 * 1. 天数 TextView：每天仅需刷新一次，由 AlarmManager 每分钟触发刷新足够
 * 2. 时分秒 Chronometer：使用 Android 原生 Chronometer 组件，由桌面 Launcher 进程
 *    持有系统时间自动渲染，零功耗秒级走动，无需 App 后台唤醒
 */
class CountdownWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "CountdownWidget"
        private const val ACTION_UPDATE_WIDGET_TIME = "com.william163.countdown.UPDATE_WIDGET_TIME"
        private const val UPDATE_INTERVAL_MILLIS = 60 * 1000L // 每分钟刷新一次（天数和提醒文本）

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
         * 每分钟触发一次，刷新天数和提醒文本
         * 时分秒的秒级走动由 Chronometer 自动完成，无需此闹钟参与
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

                alarmManager.cancel(pendingIntent)
                scheduleNextWidgetUpdate(alarmManager, pendingIntent)
                Log.d(TAG, "已启动小组件定时更新")
            } catch (e: Exception) {
                Log.e(TAG, "启动小组件定时更新失败", e)
            }
        }

        private fun scheduleNextWidgetUpdate(alarmManager: AlarmManager, pendingIntent: PendingIntent) {
            try {
                val nextTriggerTime = System.currentTimeMillis() + UPDATE_INTERVAL_MILLIS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC,
                        nextTriggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC,
                        nextTriggerTime,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "调度小组件更新失败", e)
            }
        }

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
        startWidgetUpdateAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopWidgetUpdateAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)

            if (intent.action == ACTION_UPDATE_WIDGET_TIME) {
                updateAllWidgets(context)
                // 重新调度下一次更新
                rescheduleWidgetUpdate(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onReceive 异常", e)
        }
    }

    private fun rescheduleWidgetUpdate(context: Context) {
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
            scheduleNextWidgetUpdate(alarmManager, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "重新调度小组件更新失败", e)
        }
    }

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.countdown_widget)

        try {
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

            views.setTextViewText(R.id.widget_title, targetName)

            // 计算目标时间戳（与 App 完全一致的基准）
            val targetMillis = calculateTargetMillis(context, targetDateStr)

            if (targetMillis > 0) {
                // 读取网络时间偏移量，计算当前网络时间
                val prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                val timeOffsetStr = prefs.getString("time_offset", "0")
                val timeOffset = try {
                    timeOffsetStr?.toLong() ?: 0L
                } catch (e: Exception) {
                    0L
                }

                // 当前网络时间 = 本地时间 + 网络时间偏移量
                val nowNetworkMillis = System.currentTimeMillis() + timeOffset
                // 转换为北京时间
                val beijingOffsetMillis = TimeUnit.HOURS.toMillis(8)
                val localOffsetMillis = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
                val beijingNowMillis = nowNetworkMillis + beijingOffsetMillis - localOffsetMillis

                val diff = targetMillis - beijingNowMillis

                if (diff > 0) {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    val remainingTodayMillis = diff - TimeUnit.DAYS.toMillis(days)

                    // 设置天数
                    views.setTextViewText(R.id.widget_days, "$days")
                    views.setTextViewText(R.id.widget_days_label, "天")

                    // 使用 Chronometer 实现秒级走动
                    // Chronometer 基于 SystemClock.elapsedRealtime()，需要将 diff 转换为 elapsedRealtime 基准
                    // base = 当前 elapsedRealtime + remainingTodayMillis
                    // 这样 Chronometer 从 base 倒数到 0，正好显示剩余的时分秒
                    val elapsedRealtime = SystemClock.elapsedRealtime()
                    val chronometerBase = elapsedRealtime + remainingTodayMillis

                    // API 24+ 支持 countDown=true 实现倒计时
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        views.setChronometerCountDown(R.id.widget_chronometer, true)
                    }
                    views.setChronometer(R.id.widget_chronometer, chronometerBase, null, true)

                    views.setTextViewText(R.id.widget_reminder, "每日 ${targetTimeStr} 提醒")
                    views.setTextViewText(R.id.widget_button, "打开应用")
                } else {
                    views.setTextViewText(R.id.widget_days, "0")
                    views.setTextViewText(R.id.widget_days_label, "天")
                    views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), "00:00:00", false)
                    views.setTextViewText(R.id.widget_reminder, "倒计时已结束")
                    views.setTextViewText(R.id.widget_button, "打开应用")
                }
            } else {
                views.setTextViewText(R.id.widget_days, "未设置")
                views.setTextViewText(R.id.widget_days_label, "")
                views.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), "00:00:00", false)
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

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * 计算目标日期的时间戳（UTC 0点），与 App 内 Date.UTC(year, month-1, day, 0, 0, 0) 完全一致
     */
    private fun calculateTargetMillis(context: Context, targetDateStr: String): Long {
        if (targetDateStr.isBlank()) {
            return -1L
        }

        return try {
            val parts = targetDateStr.split("-")
            if (parts.size < 3) return -1L

            val year = parts[0].trim().toIntOrNull() ?: return -1L
            val month = parts[1].trim().toIntOrNull() ?: return -1L
            val day = parts[2].trim().toIntOrNull() ?: return -1L

            if (month < 1 || month > 12 || day < 1 || day > 31 || year < 1900) {
                return -1L
            }

            // 与 App 内 new Date(Date.UTC(year, month - 1, day, 0, 0, 0)) 完全一致
            val targetCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            targetCal.set(year, month - 1, day, 0, 0, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
            targetCal.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "计算目标时间失败: $targetDateStr", e)
            -1L
        }
    }
}
