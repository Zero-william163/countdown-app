package com.countdown.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 悬浮窗服务
 * 在屏幕顶层显示倒计时悬浮窗，类似 VPN 悬浮窗效果
 * 使用 WindowManager 添加 TYPE_APPLICATION_OVERLAY 类型窗口
 * 通过 START_STICKY + 前台服务保持后台存活
 */
public class FloatingWindowService extends Service {
    private static final String TAG = "FloatingWindowService";
    private static final int NOTIFICATION_ID = 3001;
    private static final String CHANNEL_ID = "floating_window_channel";
    private static final String PREFS_NAME = "CapacitorStorage";
    private static final String SETTINGS_KEY = "countdown_settings";

    public static final String EXTRA_TARGET_NAME = "target_name";
    public static final String EXTRA_TARGET_DATE = "target_date";
    public static final String EXTRA_TARGET_TIME = "target_time";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;
    private Timer updateTimer;
    private Handler mainHandler;

    // 折叠/展开相关
    private ImageView iconView;
    private View expandedView;
    private boolean isExpanded = false;

    private String targetName = "倒计时";
    private String targetDateStr = "";
    private String targetTimeStr = "00:00";
    private long targetTimestamp = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "悬浮窗服务创建");
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "悬浮窗服务启动");

        // 处理"关闭悬浮窗"动作
        if (intent != null && "ACTION_STOP_SELF".equals(intent.getAction())) {
            Log.d(TAG, "收到关闭指令");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 从 Intent 获取数据，或从 SharedPreferences 读取
        if (intent != null) {
            String name = intent.getStringExtra(EXTRA_TARGET_NAME);
            String date = intent.getStringExtra(EXTRA_TARGET_DATE);
            String time = intent.getStringExtra(EXTRA_TARGET_TIME);
            if (name != null && !name.isEmpty()) targetName = name;
            if (date != null && !date.isEmpty()) targetDateStr = date;
            if (time != null && !time.isEmpty()) targetTimeStr = time;
        }

        if (targetDateStr == null || targetDateStr.isEmpty()) {
            loadSettingsFromPrefs();
        }

        parseTargetTimestamp();

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification());

        // 检查悬浮窗权限并显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "没有悬浮窗权限，无法显示");
            stopSelf();
            return START_NOT_STICKY;
        }

        showFloatingWindow();
        startUpdating();

        // START_STICKY: 服务被杀死后系统会尝试重新创建
        return START_STICKY;
    }

    /**
     * 当用户从最近任务中划掉应用时，尝试重新启动服务
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "应用被从最近任务移除");

        // 用户设置了倒计时且已显示悬浮窗，尝试重新启动
        if (hasActiveSettings()) {
            Intent restartIntent = new Intent(this, FloatingWindowService.class);
            restartIntent.putExtra(EXTRA_TARGET_NAME, targetName);
            restartIntent.putExtra(EXTRA_TARGET_DATE, targetDateStr);
            restartIntent.putExtra(EXTRA_TARGET_TIME, targetTimeStr);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent);
            } else {
                startService(restartIntent);
            }
        }
    }

    /**
     * 判断用户是否设置了倒计时
     */
    private boolean hasActiveSettings() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.contains(SETTINGS_KEY);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 SharedPreferences 加载设置
     */
    private void loadSettingsFromPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(SETTINGS_KEY, null);
            if (json != null) {
                JSONObject settings = new JSONObject(json);
                targetName = settings.optString("targetName", "倒计时");
                targetDateStr = settings.optString("targetDate", "");
                targetTimeStr = settings.optString("targetTime", "00:00");
            }
        } catch (Exception e) {
            Log.e(TAG, "加载设置失败", e);
        }
    }

    /**
     * 解析目标时间戳
     */
    private void parseTargetTimestamp() {
        try {
            if (targetDateStr == null || targetDateStr.isEmpty()) {
                targetTimestamp = System.currentTimeMillis();
                return;
            }
            String[] dateParts = targetDateStr.split("-");
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);

            String[] timeParts = targetTimeStr.split(":");
            int hour = timeParts.length > 0 ? Integer.parseInt(timeParts[0]) : 0;
            int minute = timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0;

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month - 1, day, hour, minute, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            targetTimestamp = cal.getTimeInMillis();

            Log.d(TAG, "目标时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(targetTimestamp)));
        } catch (Exception e) {
            Log.e(TAG, "解析时间失败", e);
            targetTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * 显示悬浮窗
     */
    private void showFloatingWindow() {
        if (floatingView != null) {
            // 已经显示
            updateContent();
            return;
        }

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            Log.e(TAG, "无法获取 WindowManager");
            return;
        }

        // 加载悬浮窗布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null);

        // 获取视图引用
        iconView = floatingView.findViewById(R.id.floating_icon);
        expandedView = floatingView.findViewById(R.id.floating_expanded);

        // 设置点击事件：点击图标切换展开/折叠
        iconView.setOnClickListener(v -> toggleExpand());

        // 设置点击事件：点击展开区域（除关闭按钮外）折叠回去
        expandedView.setOnClickListener(v -> toggleExpand());

        // 设置 LayoutParams
        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // 按文档建议：FLAG_NOT_FOCUSABLE（不抢焦点）| FLAG_WATCH_OUTSIDE_TOUCH（可点击可拖动）
        // 移除 FLAG_LAYOUT_NO_LIMITS（该标志会让悬浮窗超出屏幕边界，导致"随页面滑动"的错觉）
        layoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        );

        // 初始位置：右上角，类似 VPN 小弹窗
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 30;
        layoutParams.y = 180;

        // 设置触摸监听：支持拖动 + 点击打开应用（已合并到 setupTouchListener，避免重复监听冲突）
        setupTouchListener();

        // 关闭按钮（独立按钮，不受触摸监听影响）
        ImageView closeBtn = floatingView.findViewById(R.id.floating_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> {
                Log.d(TAG, "用户关闭悬浮窗");
                stopSelf();
            });
        }

        try {
            windowManager.addView(floatingView, layoutParams);
            Log.d(TAG, "悬浮窗已显示");
            updateContent();
        } catch (Exception e) {
            Log.e(TAG, "添加悬浮窗失败", e);
            stopSelf();
        }
    }

    /**
     * 开始定时更新倒计时
     */
    private void startUpdating() {
        if (updateTimer != null) {
            updateTimer.cancel();
        }
        updateTimer = new Timer("FloatingWindowTimer", true);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (mainHandler != null) {
                    mainHandler.post(() -> updateContent());
                }
            }
        }, 0, 1000);
    }

    /**
     * 更新悬浮窗内容
     */
    private void updateContent() {
        if (floatingView == null) return;

        TextView nameView = floatingView.findViewById(R.id.floating_target_name);
        TextView daysView = floatingView.findViewById(R.id.floating_days);
        TextView timeView = floatingView.findViewById(R.id.floating_time);

        if (nameView != null) {
            nameView.setText(targetName);
        }

        long diff = targetTimestamp - System.currentTimeMillis();
        if (diff < 0) diff = 0;

        long days = diff / (1000 * 60 * 60 * 24);
        long remaining = diff % (1000 * 60 * 60 * 24);
        long hours = remaining / (1000 * 60 * 60);
        long mins = (remaining % (1000 * 60 * 60)) / (1000 * 60);
        long secs = (remaining % (1000 * 60)) / 1000;

        if (daysView != null) {
            daysView.setText(String.valueOf(days));
        }
        if (timeView != null) {
            timeView.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs));
        }
    }

    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 关闭悬浮窗的按钮
        Intent stopIntent = new Intent(this, FloatingWindowService.class);
        stopIntent.setAction("ACTION_STOP_SELF");
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("倒计时悬浮窗运行中")
            .setContentText(targetName + " - 拖动可移动，杀死后台后仍保持显示")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭悬浮窗", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持倒计时悬浮窗显示");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 切换折叠/展开状态
     */
    private void toggleExpand() {
        if (iconView == null || expandedView == null) return;
        
        isExpanded = !isExpanded;
        
        if (isExpanded) {
            // 展开：隐藏图标，显示完整卡片
            iconView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
            Log.d(TAG, "悬浮窗展开");
        } else {
            // 折叠：显示图标，隐藏完整卡片
            iconView.setVisibility(View.VISIBLE);
            expandedView.setVisibility(View.GONE);
            Log.d(TAG, "悬浮窗折叠");
        }
        
        // 调整窗口大小以适应内容变化
        if (windowManager != null && floatingView != null && layoutParams != null) {
            windowManager.updateViewLayout(floatingView, layoutParams);
        }
    }

    private void setupTouchListener() {
        if (floatingView == null) return;

        // 悬浮窗支持拖动，点击事件由 OnClickListener 处理
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;

                        if (!isDragging && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            layoutParams.x = initialX + (int) deltaX;
                            layoutParams.y = initialY + (int) deltaY;
                            windowManager.updateViewLayout(floatingView, layoutParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        // 如果是拖动，消费事件；如果是点击，不消费（让 OnClickListener 处理）
                        return isDragging;

                    case MotionEvent.ACTION_CANCEL:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "悬浮窗服务销毁");

        // 停止定时器
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer = null;
        }

        // 移除悬浮窗
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.e(TAG, "移除悬浮窗失败", e);
            }
            floatingView = null;
        }

        // 取消通知
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }
}
