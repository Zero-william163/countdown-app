package com.countdown.reminder

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            CountdownWidgetContent(context)
        }
    }

    @Composable
    private fun CountdownWidgetContent(context: Context) {
        val prefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
        val settingsJson = prefs.getString("countdown_settings", null)

        var targetName = "倒计时"
        var targetDateStr = ""
        var targetTimeStr = "00:00"

        if (settingsJson != null) {
            try {
                val json = org.json.JSONObject(settingsJson)
                targetName = json.optString("targetName", "倒计时")
                targetDateStr = json.optString("targetDate", "")
                targetTimeStr = json.optString("targetTime", "00:00")
            } catch (e: Exception) {
                // ignore
            }
        }

        val (days, hours, mins, secs, isActive) = calculateCountdown(targetDateStr, targetTimeStr)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF2D2D5C))
                .padding(12.dp)
                .clickable(actionStartActivity(MainActivity::class.java))
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                // 目标名称
                Text(
                    text = targetName,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                if (isActive && targetDateStr.isNotEmpty()) {
                    // 天数大字
                    Text(
                        text = "$days",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Text(
                        text = "天",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFCCCCCC)),
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // 时分秒
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else {
                    // 未设置状态
                    Text(
                        text = "点击添加倒计时",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFCCCCCC)),
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }

    private fun calculateCountdown(targetDateStr: String, targetTimeStr: String): CountdownResult {
        if (targetDateStr.isEmpty()) {
            return CountdownResult(0, 0, 0, 0, false)
        }

        return try {
            val dateParts = targetDateStr.split("-").map { it.toInt() }
            val timeParts = targetTimeStr.split(":").map { it.toInt() }

            val targetCal = Calendar.getInstance()
            targetCal.set(
                dateParts[0],
                dateParts[1] - 1,
                dateParts[2],
                timeParts.getOrElse(0) { 0 },
                timeParts.getOrElse(1) { 0 },
                0
            )
            targetCal.set(Calendar.MILLISECOND, 0)

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
