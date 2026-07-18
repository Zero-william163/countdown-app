package com.countdown.reminder;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 悬浮窗插件
 * 提供悬浮窗权限检查、引导开启、显示/隐藏悬浮窗能力
 */
@CapacitorPlugin(name = "FloatingWindowPlugin")
public class FloatingWindowPlugin extends Plugin {

    private static final String TAG = "FloatingWindowPlugin";

    /**
     * 检查是否拥有悬浮窗权限
     */
    @PluginMethod
    public void checkOverlayPermission(PluginCall call) {
        JSObject result = new JSObject();
        boolean granted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            granted = Settings.canDrawOverlays(getContext());
        } else {
            granted = true;
        }
        result.put("granted", granted);
        call.resolve(result);
    }

    /**
     * 打开悬浮窗权限设置页
     */
    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } else {
                // 低版本无需此权限，直接 resolve
            }
        } catch (Exception e) {
            Log.e(TAG, "打开悬浮窗设置失败，尝试应用详情页", e);
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e2) {
                Log.e(TAG, "备用方案也失败", e2);
            }
        }
        call.resolve();
    }

    /**
     * 显示悬浮窗
     * 参数: targetName, targetDate, targetTime
     */
    @PluginMethod
    public void showFloatingWindow(PluginCall call) {
        String targetName = call.getString("targetName", "倒计时");
        String targetDate = call.getString("targetDate", "");
        String targetTime = call.getString("targetTime", "00:00");

        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(getContext())) {
            call.reject("NO_OVERLAY_PERMISSION");
            return;
        }

        try {
            Intent intent = new Intent(getContext(), FloatingWindowService.class);
            intent.putExtra(FloatingWindowService.EXTRA_TARGET_NAME, targetName);
            intent.putExtra(FloatingWindowService.EXTRA_TARGET_DATE, targetDate);
            intent.putExtra(FloatingWindowService.EXTRA_TARGET_TIME, targetTime);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }

            Log.d(TAG, "悬浮窗已启动: " + targetName + " " + targetDate + " " + targetTime);

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "启动悬浮窗失败", e);
            call.reject("启动悬浮窗失败: " + e.getMessage());
        }
    }

    /**
     * 隐藏悬浮窗
     */
    @PluginMethod
    public void hideFloatingWindow(PluginCall call) {
        try {
            Intent intent = new Intent(getContext(), FloatingWindowService.class);
            getContext().stopService(intent);

            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "隐藏悬浮窗失败", e);
            call.reject("隐藏悬浮窗失败: " + e.getMessage());
        }
    }
}
