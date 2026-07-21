package com.william163.countdown;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 铃声选择插件
 * - 选择本地音频文件
 * - 复制到 App 私有目录，防止源文件被删除
 * - 读取当前铃声设置
 */
@CapacitorPlugin(
    name = "RingtonePlugin",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_MEDIA_AUDIO }, alias = "audio"),
        @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "storage")
    }
)
public class RingtonePlugin extends Plugin {

    private static final String TAG = "RingtonePlugin";
    private static final String PREFS_NAME = "CapacitorStorage";
    private static final String KEY_RINGTONE_PATH = "custom_ringtone_path";
    private static final String KEY_RINGTONE_NAME = "custom_ringtone_name";
    private static final String RINGTONE_DIR = "custom_ringtones";

    /**
     * 选择音频文件
     */
    @PluginMethod
    public void pickRingtone(PluginCall call) {
        // 检查权限
        if (!hasReadAudioPermission()) {
            requestReadAudioPermission(call);
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            // 支持的格式：mp3, wav, m4a, aac, ogg, flac
            String[] mimeTypes = {"audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
                    "audio/m4a", "audio/mp4", "audio/aac", "audio/x-aac",
                    "audio/ogg", "audio/flac", "audio/x-flac"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            startActivityForResult(call, intent, "onPickResult");
        } catch (Exception e) {
            Log.e(TAG, "打开文件选择器失败", e);
            call.reject("打开文件选择器失败: " + e.getMessage());
        }
    }

    @ActivityCallback
    private void onPickResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }

        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("用户取消选择");
            return;
        }

        Uri uri = result.getData().getData();
        if (uri == null) {
            call.reject("未选择文件");
            return;
        }

        try {
            // 获取文件名
            String fileName = getFileName(uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "ringtone_" + System.currentTimeMillis() + ".mp3";
            }

            // 复制到私有目录
            String localPath = copyToPrivateDir(uri, fileName);
            if (localPath == null) {
                call.reject("文件复制失败");
                return;
            }

            // 保存到 SharedPreferences
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_RINGTONE_PATH, localPath)
                    .putString(KEY_RINGTONE_NAME, fileName)
                    .apply();

            Log.d(TAG, "铃声设置成功: " + fileName + " -> " + localPath);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("path", localPath);
            ret.put("name", fileName);
            ret.put("message", "铃声设置成功");
            call.resolve(ret);

        } catch (Exception e) {
            Log.e(TAG, "处理选中文件失败", e);
            call.reject("处理失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前铃声设置
     */
    @PluginMethod
    public void getCurrentRingtone(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String path = prefs.getString(KEY_RINGTONE_PATH, null);
            String name = prefs.getString(KEY_RINGTONE_NAME, null);

            if (path != null && !path.isEmpty()) {
                File file = new File(path);
                if (file.exists()) {
                    ret.put("isCustom", true);
                    ret.put("path", path);
                    ret.put("name", name != null ? name : "自定义铃声");
                } else {
                    // 文件不存在，清理记录
                    prefs.edit().remove(KEY_RINGTONE_PATH).remove(KEY_RINGTONE_NAME).apply();
                    ret.put("isCustom", false);
                    ret.put("name", "默认铃声");
                }
            } else {
                ret.put("isCustom", false);
                ret.put("name", "默认铃声");
            }
            ret.put("success", true);
        } catch (Exception e) {
            Log.e(TAG, "获取铃声设置失败", e);
            ret.put("success", false);
            ret.put("isCustom", false);
        }
        call.resolve(ret);
    }

    /**
     * 重置为默认铃声
     */
    @PluginMethod
    public void resetToDefault(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String path = prefs.getString(KEY_RINGTONE_PATH, null);

            // 删除私有目录中的文件
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    file.delete();
                }
            }

            // 清理记录
            prefs.edit().remove(KEY_RINGTONE_PATH).remove(KEY_RINGTONE_NAME).apply();

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("message", "已恢复为默认铃声");
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "重置铃声失败", e);
            call.reject("重置失败: " + e.getMessage());
        }
    }

    /**
     * 检查读取音频权限
     */
    private boolean hasReadAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestReadAudioPermission(PluginCall call) {
        String alias;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            alias = "audio";
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            alias = "storage";
        } else {
            // 不需要权限，直接打开文件选择器
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(call, intent, "onPickResult");
                return;
            } catch (Exception e) {
                call.reject("无法打开文件选择器");
                return;
            }
        }
        requestPermissionForAlias(alias, call, "onPermissionResult");
    }

    @PermissionCallback
    private void onPermissionResult(PluginCall call) {
        if (hasReadAudioPermission()) {
            // 权限已授予，再次尝试打开选择器
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("audio/*");
                    String[] mimeTypes = {"audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
                            "audio/m4a", "audio/mp4", "audio/aac", "audio/x-aac",
                            "audio/ogg", "audio/flac", "audio/x-flac"};
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(call, intent, "onPickResult");
                } catch (Exception e) {
                    call.reject("打开文件选择器失败");
                }
            });
        } else {
            call.reject("需要音频读取权限才能选择铃声");
        }
    }

    /**
     * 从 URI 获取文件名
     */
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取文件名失败", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    /**
     * 将选中的音频文件复制到 App 私有目录
     */
    private String copyToPrivateDir(Uri uri, String fileName) {
        try {
            // 创建私有目录
            File dir = new File(getContext().getFilesDir(), RINGTONE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 清理旧文件
            if (dir.listFiles() != null) {
                for (File oldFile : dir.listFiles()) {
                    oldFile.delete();
                }
            }

            // 目标文件
            File destFile = new File(dir, fileName);

            // 复制文件
            ContentResolver resolver = getContext().getContentResolver();
            InputStream input = resolver.openInputStream(uri);
            FileOutputStream output = new FileOutputStream(destFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            output.flush();
            output.close();
            input.close();

            Log.d(TAG, "文件已复制到: " + destFile.getAbsolutePath() + ", 大小: " + destFile.length());
            return destFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "复制文件失败", e);
            return null;
        }
    }
}