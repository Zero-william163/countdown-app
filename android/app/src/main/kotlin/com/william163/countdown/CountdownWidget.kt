package com.william163.countdown

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                targetTimeStr = json.optString("reminderTime", "00:00")
            } catch (e: Exception) {
            }
        }

        val (days, hours, mins, secs, isActive) = calculateCountdown(targetDateStr, targetTimeStr)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF2D2D5C))
                .padding(16.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
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
                Text(
                    text = "$days 天",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = String.format("%02d:%02d:%02d", hours, mins, secs),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFCCCCCC)),
                        fontSize = 12.sp
                    )
                )
            } else {
                Text(
                    text = "点击下方按钮设置",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFCCCCCC)),
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(16.dp))

            Button(
                text = if (isActive) "打开应用" else "立即设置",
                onClick = actionRunCallback<WidgetButtonCallback>()
            )
        }
    }

    private fun calculateCountdown(targetDateStr: String, @Suppress("UNUSED_PARAMETER") targetTimeStr: String): CountdownResult {
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

class WidgetButtonCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            val intent = android.content.Intent(context, MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)
        }
    }
}