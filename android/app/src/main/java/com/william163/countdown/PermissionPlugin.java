package com.william163.countdown;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PermissionChecker")
public class PermissionPlugin extends Plugin {

    private static final String TAG = "PermissionPlugin";

    @PluginMethod
    public void checkExactAlarmPermission(PluginCall call) {
        JSObject result = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            boolean canSchedule = alarmManager != null && alarmManager.canScheduleExactAlarms();
            result.put("granted", canSchedule);
        } else {
            result.put("granted", true);
        }
        call.resolve(result);
    }

    @PluginMethod
    public void checkBatteryOptimization(PluginCall call) {
        JSObject result = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            boolean isIgnoring = pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
            result.put("granted", isIgnoring);
        } else {
            result.put("granted", true);
        }
        call.resolve(result);
    }

    @PluginMethod
    public void checkNotificationPermission(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
        call.resolve(result);
    }

    @PluginMethod
    public void openAlarmSettings(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } else {
                openAppSettingsPage();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开闹钟设置失败，尝试备用方案", e);
            try {
                // 备用方案：跳转到应用详情页
                openAppSettingsPage();
            } catch (Exception e2) {
                Log.e(TAG, "备用方案也失败", e2);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void checkAutoStartPermission(PluginCall call) {
        // 国产手机厂商（华为/小米/OPPO/vivo）的自启动权限没有统一 API
        // 这里返回 false，引导用户手动开启
        JSObject result = new JSObject();
        result.put("granted", false);
        call.resolve(result);
    }

    @PluginMethod
    public void openAutoStartSettings(PluginCall call) {
        boolean opened = false;

        // 【重要】优先尝试厂商专属组件，避免直接跳转到 App Info
        // 华为/荣耀：应用启动管理（自启动管理）
        if (!opened) {
            // 华为：应用启动管理 - 方案1（推荐）
            try {
                Intent intent = new Intent();
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 华为：方案2（旧版 EMUI）
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 华为：方案3 - 手机管家
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.MainActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 华为：耗电详情页（Power usage details）
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 荣耀：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 荣耀：方案2
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 小米：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 小米：方案2 - 手机管家
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.miui.securitycenter", "com.miui.securitycenter.MainActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 小米：方案3 - 权限管理
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.PermissionManagerActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // OPPO：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // OPPO：方案2 - 安全中心
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.SafeCenterActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // OPPO：方案3 - 电池管理中的应用速冻
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.battery.BatteryActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // vivo：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // vivo：方案2 - i管家
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.iqoo.secure", "com.iqoo.secure.MainActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // vivo：方案3 - 权限管理
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 三星：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 三星：方案2 - 应用程序管理
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.appmanagement.AppManagementActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 一加：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 一加：方案2
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.oneplus.security", "com.oneplus.security.MainActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // realme：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.realme.security", "com.realme.security.permission.startup.StartupAppListActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 联想：方案1
        if (!opened) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.lenovo.security", "com.lenovo.security.ui.autostart.AutoStartActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                opened = true;
            } catch (Exception ignored) {}
        }

        // 通用：跳转到应用详情页，并显示操作指引
        if (!opened) {
            // 显示 Toast 提示操作路径
            Toast.makeText(
                getContext(),
                "请在设置中：耗电详情 → 应用启动管理 → 允许自启动",
                Toast.LENGTH_LONG
            ).show();
            openAppSettingsPage();
        }
        call.resolve();
    }

    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 先尝试直接请求忽略电池优化
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } else {
                openAppSettingsPage();
            }
        } catch (Exception e) {
            Log.e(TAG, "请求电池优化失败，尝试电池优化设置页", e);
            try {
                // 备用方案1：跳转到电池优化设置列表页
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e2) {
                Log.e(TAG, "备用方案1失败，尝试应用详情页", e2);
                openAppSettingsPage();
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        openAppSettingsPage();
        call.resolve();
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url");
        if (url != null && !url.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "打开URL失败", e);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 直接跳转到通知设置页
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } else {
                openAppSettingsPage();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开通知设置失败，尝试应用详情页", e);
            openAppSettingsPage();
        }
        call.resolve();
    }

    private void openAppSettingsPage() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "打开应用详情页失败", e);
        }
    }
}
