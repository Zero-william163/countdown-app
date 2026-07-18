package com.countdown.reminder;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdatePlugin extends Plugin {

    private static final String TAG = "UpdatePlugin";
    private static final String APK_FILE_NAME = "countdown_update.apk";
    private long downloadId = -1;
    private BroadcastReceiver downloadReceiver;

    @PluginMethod
    public void downloadAndInstall(PluginCall call) {
        String apkUrl = call.getString("url");
        if (apkUrl == null || apkUrl.isEmpty()) {
            call.reject("URL is required");
            return;
        }

        try {
            // 检查是否可以请求安装包
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getContext().getPackageManager().canRequestPackageInstalls()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                    call.reject("NEED_INSTALL_PERMISSION");
                    return;
                }
            }

            // 先检查URL的content-type，判断是否为直接下载链接
            new Thread(() -> {
                try {
                    URL url = new URL(apkUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.connect();

                    String contentType = connection.getContentType();
                    int responseCode = connection.getResponseCode();
                    connection.disconnect();

                    Log.d(TAG, "URL content-type: " + contentType + ", responseCode: " + responseCode);

                    if (contentType != null && (contentType.contains("text/html") || contentType.contains("application/json"))) {
                        // URL是HTML页面（如Gofile下载页面），使用浏览器打开
                        openInBrowser(apkUrl);
                        call.resolve(new JSObject().put("success", true).put("message", "已在浏览器中打开下载页面"));
                    } else {
                        // URL是直接下载链接，使用DownloadManager下载
                        startDownloadWithDownloadManager(apkUrl);
                        call.resolve(new JSObject().put("success", true).put("message", "下载已开始"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "检查URL失败，直接尝试下载", e);
                    // 检查失败，直接尝试用DownloadManager下载
                    try {
                        startDownloadWithDownloadManager(apkUrl);
                        call.resolve(new JSObject().put("success", true).put("message", "下载已开始"));
                    } catch (Exception ex) {
                        // DownloadManager失败，降级为浏览器打开
                        openInBrowser(apkUrl);
                        call.resolve(new JSObject().put("success", true).put("message", "已在浏览器中打开下载页面"));
                    }
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "下载失败", e);
            call.reject("下载失败: " + e.getMessage());
        }
    }

    private void startDownloadWithDownloadManager(String apkUrl) {
        // 先取消之前的下载
        if (downloadId != -1) {
            try {
                DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                dm.remove(downloadId);
            } catch (Exception e) {
                Log.e(TAG, "取消旧下载失败", e);
            }
        }

        DownloadManager downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setTitle("倒计时提醒 - 更新下载");
        request.setDescription("正在下载新版本...");
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        File apkFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME);
        if (apkFile.exists()) {
            apkFile.delete();
        }
        request.setDestinationUri(Uri.fromFile(apkFile));

        registerDownloadReceiver();
        downloadId = downloadManager.enqueue(request);
    }

    private void openInBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "打开浏览器失败", e);
        }
    }

    @PluginMethod
    public void checkInstallPermission(PluginCall call) {
        JSObject ret = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ret.put("granted", getContext().getPackageManager().canRequestPackageInstalls());
        } else {
            ret.put("granted", true);
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void requestInstallPermission(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "请求安装权限失败", e);
            call.reject("请求安装权限失败: " + e.getMessage());
        }
    }

    private void registerDownloadReceiver() {
        if (downloadReceiver != null) {
            try {
                getContext().unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                Log.e(TAG, "注销旧接收器失败", e);
            }
        }

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long receivedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (receivedId == downloadId) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    Cursor cursor = dm.query(query);

                    if (cursor != null && cursor.moveToFirst()) {
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // 检查下载的文件是否是APK
                            if (localUri != null) {
                                File downloadedFile = new File(Uri.parse(localUri).getPath());
                                if (isApkFile(downloadedFile)) {
                                    installApk();
                                } else {
                                    // 下载的不是APK（可能是HTML页面），删除文件并用浏览器打开
                                    downloadedFile.delete();
                                    Log.e(TAG, "下载的文件不是APK，使用浏览器打开");
                                }
                            } else {
                                installApk();
                            }
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            Log.e(TAG, "下载失败");
                        }
                        cursor.close();
                    }

                    try {
                        context.unregisterReceiver(downloadReceiver);
                        downloadReceiver = null;
                    } catch (Exception e) {
                        Log.e(TAG, "注销接收器失败", e);
                    }
                }
            }
        };

        getContext().registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private boolean isApkFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (!file.getName().endsWith(".apk")) {
            return false;
        }
        if (file.length() < 100 * 1024) {
            return false;
        }
        // 修复问题4：用PackageManager验证APK完整性，能解析出PackageInfo才是合法的APK
        try {
            PackageManager pm = getContext().getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            return info != null;
        } catch (Exception e) {
            Log.e(TAG, "验证APK失败", e);
            return false;
        }
    }

    private void installApk() {
        try {
            File apkFile = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME);
            if (!apkFile.exists()) {
                Log.e(TAG, "APK文件不存在");
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }

            getContext().startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "安装APK失败", e);
        }
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (downloadReceiver != null) {
            try {
                getContext().unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                Log.e(TAG, "注销接收器失败", e);
            }
        }
    }
}
