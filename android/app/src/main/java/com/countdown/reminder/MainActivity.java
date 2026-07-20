package com.countdown.reminder;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.content.pm.PackageInfo;

import androidx.core.content.FileProvider;

import com.getcapacitor.BridgeActivity;

import java.io.File;

public class MainActivity extends BridgeActivity {

    private static final String APK_FILE_NAME = "countdown_update.apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(PermissionPlugin.class);
        registerPlugin(UpdatePlugin.class);
        registerPlugin(AlarmPlugin.class);
        super.onCreate(savedInstanceState);
        createAlarmNotificationChannel();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 修复问题3：检查未完成的下载任务，防止广播失效导致安装不触发
        checkPendingDownload();
    }

    private void checkPendingDownload() {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
            Cursor cursor = dm.query(query);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                    if (localUri != null && title != null && title.contains("倒计时提醒")) {
                        File downloadedFile = new File(Uri.parse(localUri).getPath());
                        if (isApkFile(downloadedFile)) {
                            installApk(downloadedFile);
                            break;
                        }
                    }
                } while (cursor.moveToNext());
                cursor.close();
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    private boolean isApkFile(File file) {
        if (file == null || !file.exists()) return false;
        if (!file.getName().endsWith(".apk")) return false;
        if (file.length() < 100 * 1024) return false;
        try {
            PackageInfo info = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void installApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            }
            startActivity(intent);
        } catch (Exception e) {
            // 忽略
        }
    }

    private void createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "alarm_channel",
                "每日倒计时提醒",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("每日准时倒计时提醒，包含闹钟铃声和震动");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});

            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound != null) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                channel.setSound(alarmSound, audioAttributes);
            }

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
