package com.william163.countdown

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import java.util.concurrent.TimeUnit

/**
 * 传统 RemoteViews 桌面小组件
 * 比 Glance 兼容性更好，华为 HarmonyOS 支持更完善
 */
class CountdownWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "CountdownWidget"
        private const val PREFS_NAME = "CapacitorStorage"
        private const val SETTINGS_KEY = "countdown_settings"

        /**
         * 手动更新所有小组件
         */
        @JvmStatic
        fun updateAllWidgets(context: Context) {
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
        Log.d(TAG, "小组件已启用")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "小组件已禁用")
    }

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsJson = prefs.getString(SETTINGS_KEY, null)

        var targetName = "倒计时提醒"
        var targetDateStr = ""
        var targetTimeStr = "00:00"

        if (settingsJson != null) {
            try {
                val json = org.json.JSONObject(settingsJson)
                targetName = json.optString("targetName", "倒计时提醒")
                targetDateStr = json.optString("targetDate", "")
                targetTimeStr = json.optString("reminderTime", "00:00")
            } catch (e: Exception) {
                Log.e(TAG, "解析设置失败", e)
            }
        }

        val result = calculateCountdown(targetDateStr)
        val isActive = result.isActive

        // 创建 RemoteViews
        val views = RemoteViews(context.packageName, R.layout.countdown_widget)

        // 设置标题
        views.setTextViewText(R.id.widget_title, targetName)

        // 设置状态点颜色
        val statusColor = if (isActive) 0xFF28C76F.toInt() else 0xFF888888.toInt()
        views.setTextColor(R.id.widget_status_dot, statusColor)
        views.setTextViewText(R.id.widget_status_dot, if (isActive) "●" else "○")

        if (isActive && targetDateStr.isNotEmpty()) {
            // 显示倒计时
            views.setTextViewText(R.id.widget_days, "${result.days}")
            views.setTextViewText(R.id.widget_days_label, "天")
            views.setTextViewText(R.id.widget_time, String.format("%02d:%02d:%02d", result.hours, result.mins, result.secs))
            views.setTextViewText(R.id.widget_reminder, "⏰ 每日 ${targetTimeStr} 提醒")
            views.setTextViewText(R.id.widget_button, "打开应用")
        } else {
            // 未设置状态
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

        // 更新小组件
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "小组件 $appWidgetId 已更新")
    }

    private fun calculateCountdown(targetDateStr: String): CountdownResult {
        if (targetDateStr.isEmpty()) {
            return CountdownResult(0, 0, 0, 0, false)
        }

        return try {
            val dateParts = targetDateStr.split("-").map { it.toInt() }

            val targetCal = java.util.Calendar.getInstance()
            targetCal.set(
                dateParts[0],
                dateParts[1] - 1,
                dateParts[2],
                0,
                0,
                0
            )
            targetCal.set(java.util.Calendar.MILLISECOND, 0)

            val now = System.currentTimeMillis()
            val diff = targetCal.timeInMillis - now

            if (diff <= 0) {
                CountdownResult(0, 0, 0, 0, false)
            } else {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val remaining = diff % (1000 * 60 * 60 * 24)
                val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                val mins = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                val secs = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                CountdownResult(days.toInt(), hours.toInt(), mins.toInt(), secs.toInt(), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "计算倒计时失败", e)
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
