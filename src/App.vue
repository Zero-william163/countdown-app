<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue';
import { LocalNotifications } from '@capacitor/local-notifications';
import { Preferences } from '@capacitor/preferences';
import { App } from '@capacitor/app';
import { CapacitorHttp } from '@capacitor/core';
import { PermissionChecker, UpdatePlugin, AlarmPlugin, FloatingWindowPlugin } from './permission-plugin';

const STORAGE_KEY = 'countdown_settings';
const FIRST_LAUNCH_KEY = 'has_seen_permission_guide';
const DOWNLOADED_VERSION_KEY = 'downloaded_version'; // 已下载版本号
const appVersion = ref('0.0.0'); // 初始值，启动后从原生动态获取

// 状态
const targetName = ref('');
const targetDate = ref('');
const reminderTime = ref('09:00');
const isAlarmSet = ref(false);
const hasPermission = ref(false);
const hasExactAlarmPermission = ref(false);
const hasBatteryOptimization = ref(false);
const showSettings = ref(false);
const showResetConfirm = ref(false);
const showPermissionGuide = ref(false);
const latestVersion = ref('');
const latestApkUrl = ref('');
const showUpdateNotice = ref(false);
const isUpdating = ref(false);
const updateStatus = ref('');
const showUpdateDialog = ref(false);
const hasOverlayPermission = ref(false); // 悬浮窗权限
const hasAutoStartPermission = ref(false); // 自启动权限
const isFloatingWindowShown = ref(false); // 悬浮窗是否正在显示
const autoStartSettingsOpened = ref(false); // 是否刚刚打开过自启动设置
const isCheckingUpdate = ref(false); // 是否正在检查更新

const countdown = ref({ days: 0, hours: 0, minutes: 0, seconds: 0 });
const totalRemaining = ref('');

let timer: number | null = null;

// 目标日期文本
const targetDateTimeText = computed(() => {
  return targetDate.value || '';
});

// 请求通知权限
async function requestNotificationPermission() {
  try {
    const result = await LocalNotifications.requestPermissions();
    hasPermission.value = result.display === 'granted';
  } catch (error) {
    console.error('请求权限失败:', error);
  }
  try {
    await PermissionChecker.requestNotificationPermission();
  } catch (e) {
    console.error('打开通知设置失败:', e);
  }
}

// 首次启动显示权限引导
async function checkFirstLaunch() {
  try {
    const { value } = await Preferences.get({ key: FIRST_LAUNCH_KEY });
    if (!value) {
      showPermissionGuide.value = true;
    }
  } catch (e) {
    showPermissionGuide.value = true;
  }
}

// 检查所有权限（更新状态显示用）
async function checkAllPermissions() {
  try {
    // 通知权限 - 优先使用原生检查，更准确
    try {
      const nativeNotifyResult = await PermissionChecker.checkNotificationPermission();
      hasPermission.value = nativeNotifyResult.granted;
    } catch (e) {
      try {
        const notifyResult = await LocalNotifications.checkPermissions();
        hasPermission.value = notifyResult.display === 'granted';
      } catch (ee) {
        console.log('检查通知权限失败:', ee);
      }
    }

    try {
      const alarmResult = await PermissionChecker.checkExactAlarmPermission();
      hasExactAlarmPermission.value = alarmResult.granted;
    } catch (e) {
      console.log('检查闹钟权限失败:', e);
    }

    try {
      const batteryResult = await PermissionChecker.checkBatteryOptimization();
      hasBatteryOptimization.value = batteryResult.granted;
    } catch (e) {
      console.log('检查电池优化失败:', e);
    }

    // 检查悬浮窗权限
    try {
      const overlayResult = await FloatingWindowPlugin.checkOverlayPermission();
      hasOverlayPermission.value = overlayResult.granted;
    } catch (e) {
      console.log('检查悬浮窗权限失败:', e);
      hasOverlayPermission.value = false;
    }

    // 检查自启动权限（国产手机无统一 API，优先读取本地确认标记）
    try {
      const { value: autoStartConfirmed } = await Preferences.get({ key: 'autostart_confirmed' });
      if (autoStartConfirmed === 'true') {
        hasAutoStartPermission.value = true;
      } else {
        const autoStartResult = await PermissionChecker.checkAutoStartPermission();
        hasAutoStartPermission.value = autoStartResult.granted;
      }
    } catch (e) {
      console.log('检查自启动权限失败:', e);
    }
  } catch (error) {
    console.error('检查权限失败:', error);
  }
}

// 所有权限是否都已开启
const allPermissionsGranted = computed(() => {
  return hasPermission.value && hasExactAlarmPermission.value &&
         hasBatteryOptimization.value && hasOverlayPermission.value &&
         hasAutoStartPermission.value;
});

// 打开精确闹钟设置
async function openAlarmSettings() {
  try {
    await PermissionChecker.openAlarmSettings();
  } catch (e) {
    console.error('打开闹钟设置失败:', e);
    try {
      await PermissionChecker.openAppSettings();
    } catch (ee) {
      console.error('打开应用设置失败:', ee);
    }
  }
}

// 打开电池优化设置
async function openBatterySettings() {
  try {
    await PermissionChecker.openBatterySettings();
  } catch (e) {
    console.log('打开电池设置失败:', e);
    try {
      await PermissionChecker.openAppSettings();
    } catch (ee) {
      console.log('打开应用设置失败:', ee);
    }
  }
}

// 打开自启动设置
async function openAutoStartSettings() {
  autoStartSettingsOpened.value = true;
  try {
    await PermissionChecker.openAutoStartSettings();
  } catch (e) {
    console.error('打开自启动设置失败:', e);
    try {
      await PermissionChecker.openAppSettings();
    } catch (ee) {
      console.log('打开应用设置失败:', ee);
    }
  }
}

// 打开应用设置页面
async function openAppSettingsPage() {
  try {
    await PermissionChecker.openAppSettings();
  } catch (e) {
    console.log('打开应用设置失败:', e);
  }
}

// 打开悬浮窗权限设置
async function openOverlaySettings() {
  try {
    await FloatingWindowPlugin.openOverlaySettings();
  } catch (e) {
    console.error('打开悬浮窗设置失败:', e);
    try {
      await PermissionChecker.openAppSettings();
    } catch (ee) {
      console.log('打开应用设置失败:', ee);
    }
  }
}

// 显示悬浮窗倒计时
async function showFloatingCountdown() {
  if (!isAlarmSet.value) {
    alert('请先设置倒计时');
    return;
  }
  // 先检查权限
  try {
    const perm = await FloatingWindowPlugin.checkOverlayPermission();
    if (!perm.granted) {
      alert('需要"悬浮窗"权限才能在桌面显示倒计时，即将跳转设置页面开启');
      await FloatingWindowPlugin.openOverlaySettings();
      return;
    }
  } catch (e) {
    console.log('检查悬浮窗权限失败:', e);
  }

  try {
    await FloatingWindowPlugin.showFloatingWindow({
      targetName: targetName.value,
      targetDate: targetDate.value,
      targetTime: '00:00',
    });
    isFloatingWindowShown.value = true;
    console.log('悬浮窗已显示');
  } catch (e: any) {
    console.error('显示悬浮窗失败:', e);
    if (e && e.message && e.message.includes('NO_OVERLAY_PERMISSION')) {
      alert('需要"悬浮窗"权限才能在桌面显示倒计时，即将跳转设置页面开启');
      await FloatingWindowPlugin.openOverlaySettings();
    } else {
      alert('显示悬浮窗失败: ' + (e?.message || '未知错误'));
    }
  }
}

// 隐藏悬浮窗
async function hideFloatingCountdown() {
  try {
    await FloatingWindowPlugin.hideFloatingWindow();
    isFloatingWindowShown.value = false;
    console.log('悬浮窗已隐藏');
  } catch (e) {
    console.error('隐藏悬浮窗失败:', e);
    isFloatingWindowShown.value = false;
  }
}

// 关闭权限引导弹窗
async function closePermissionGuide() {
  showPermissionGuide.value = false;
  // 标记已看过引导
  await Preferences.set({ key: FIRST_LAUNCH_KEY, value: 'true' });
  // 关闭后重新检查权限状态
  await checkAllPermissions();
}

// 打开设置
function openSettings() {
  showSettings.value = true;
}

// 关闭设置
function closeSettings() {
  showSettings.value = false;
}

// 加载设置
async function loadSavedSettings() {
  try {
    const { value } = await Preferences.get({ key: STORAGE_KEY });
    if (value) {
      const settings = JSON.parse(value);
      targetName.value = settings.targetName || '';
      targetDate.value = settings.targetDate || '';
      reminderTime.value = settings.reminderTime || '09:00';
      isAlarmSet.value = true;
      updateCountdown();
      await scheduleDailyNotifications();
    } else {
      isAlarmSet.value = false;
    }
  } catch (error) {
    console.error('加载设置失败:', error);
    isAlarmSet.value = false;
  }
}

// 获取当前北京时间（从网络获取，不受设备时区和时间设置影响）
async function getBeijingTime(): Promise<Date> {
  try {
    const response = await CapacitorHttp.get({
      url: 'https://worldtimeapi.org/api/timezone/Asia/Shanghai'
    });
    const datetime = response.data.datetime;
    return new Date(datetime);
  } catch (error) {
    console.warn('获取网络时间失败，使用本地时间降级方案:', error);
    return getBeijingTimeFallback();
  }
}

// 降级方案：将本地时间强制转换为北京时间
function getBeijingTimeFallback(): Date {
  const now = new Date();
  const beijingOffset = 8 * 60;
  const localOffset = now.getTimezoneOffset();
  return new Date(now.getTime() + (localOffset + beijingOffset) * 60000);
}

// 更新倒计时
async function updateCountdown() {
  if (!isAlarmSet.value || !targetDate.value) return;

  try {
    const now = await getBeijingTime();
    console.log('[Countdown] 当前时间:', now);
    console.log('[Countdown] 目标日期:', targetDate.value);

    const dateParts = targetDate.value.split(/[-/]/).map(Number);
    console.log('[Countdown] 日期部分:', dateParts);

    if (dateParts.length !== 3 || dateParts.some(isNaN)) {
      console.error('[Countdown] 日期格式错误:', targetDate.value);
      return;
    }

    const [year, month, day] = dateParts;
    const target = new Date(Date.UTC(year, month - 1, day, 0, 0, 0));
    console.log('[Countdown] 目标时间:', target);

    const diff = target.getTime() - now.getTime();
    console.log('[Countdown] 时间差:', diff);

    if (diff <= 0) {
      countdown.value = { days: 0, hours: 0, minutes: 0, seconds: 0 };
      totalRemaining.value = '0 天 0 小时 0 分钟';
      return;
    }

    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const remaining = diff % (1000 * 60 * 60 * 24);
    const hrs = Math.floor(remaining / (1000 * 60 * 60));
    const mins = Math.floor((remaining % (1000 * 60 * 60)) / (1000 * 60));
    const secs = Math.floor((remaining % (1000 * 60)) / 1000);

    console.log('[Countdown] 计算结果:', { days, hrs, mins, secs });
    countdown.value = { days, hours: hrs, minutes: mins, seconds: secs };
    totalRemaining.value = `${days} 天 ${hrs} 小时 ${mins} 分钟`;
  } catch (e) {
    console.error('[Countdown] 计算失败:', e);
  }
}

// 调度每日闹钟 - 使用原生 AlarmManager 确保声音和震动
async function scheduleDailyNotifications() {
  try {
    const [hours, minutes] = reminderTime.value.split(':').map(Number);

    const now = await getBeijingTime();
    const dateParts = targetDate.value.split(/[-/]/).map(Number);
  const [year, month, day] = dateParts;
    const target = new Date(Date.UTC(year, month - 1, day, 0, 0, 0));
    const todayStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 0, 0, 0));
    const totalDays = Math.ceil((target.getTime() - todayStart.getTime()) / (1000 * 60 * 60 * 24));

    // 使用原生 AlarmPlugin 设置闹钟（声音+震动）
    try {
      await AlarmPlugin.scheduleDailyAlarms({
        hour: hours,
        minute: minutes,
        targetName: targetName.value,
        daysRemaining: totalDays,
        targetDate: targetDate.value
      });
      console.log('原生闹钟设置成功');
    } catch (e) {
      console.error('原生闹钟设置失败，降级到通知:', e);
      await scheduleLocalNotificationsFallback();
    }
  } catch (error) {
    console.error('设置闹钟失败:', error);
  }
}

// 降级方案：使用 LocalNotifications
async function scheduleLocalNotificationsFallback() {
  try {
    const pending = await LocalNotifications.getPending();
    if (pending.notifications.length > 0) {
      await LocalNotifications.cancel({
        notifications: pending.notifications.map(n => ({ id: n.id }))
      });
    }
  } catch (e) {
    console.log('取消旧通知:', e);
  }

  const notifications = [];
  const [hours, minutes] = reminderTime.value.split(':').map(Number);

  const now = await getBeijingTime();
  const dateParts = targetDate.value.split(/[-/]/).map(Number);
  const [year, month, day] = dateParts;
  const target = new Date(Date.UTC(year, month - 1, day, 0, 0, 0));
  const todayStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 0, 0, 0));
  const totalDays = Math.ceil((target.getTime() - todayStart.getTime()) / (1000 * 60 * 60 * 24));

  const daysToSchedule = Math.min(Math.max(totalDays, 0), 30);

  for (let i = 0; i < daysToSchedule; i++) {
    const notifyDate = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + i, hours, minutes, 0));

    if (notifyDate.getTime() < now.getTime()) {
      continue;
    }

    const daysRemaining = totalDays - i;
    const body = `离${targetName.value}还有${daysRemaining}天`;

    notifications.push({
      id: i + 1,
      title: '倒计时提醒',
      body: body,
      schedule: { at: notifyDate },
      channelId: 'alarm_channel',
      sound: 'default',
    });
  }

  if (notifications.length > 0) {
    await LocalNotifications.schedule({ notifications });
  }
}

// 取消提醒
async function cancelReminder() {
  try {
    // 隐藏悬浮窗
    try {
      await FloatingWindowPlugin.hideFloatingWindow();
      isFloatingWindowShown.value = false;
    } catch (e) {
      console.log('隐藏悬浮窗:', e);
    }

    // 取消原生闹钟
    try {
      await AlarmPlugin.cancelAllAlarms();
    } catch (e) {
      console.log('取消原生闹钟:', e);
    }

    // 取消通知
    try {
      const pending = await LocalNotifications.getPending();
      if (pending.notifications.length > 0) {
        await LocalNotifications.cancel({
          notifications: pending.notifications.map(n => ({ id: n.id }))
        });
      }
    } catch (e) {
      console.log('取消通知:', e);
    }

    await Preferences.remove({ key: STORAGE_KEY });
    isAlarmSet.value = false;
    targetName.value = '';
    targetDate.value = '';
    reminderTime.value = '09:00';
    countdown.value = { days: 0, hours: 0, minutes: 0, seconds: 0 };
    totalRemaining.value = '';
    showResetConfirm.value = false;
    showSettings.value = true;
  } catch (error) {
    console.error('取消提醒失败:', error);
  }
}

// 重置确认
function confirmReset() {
  showResetConfirm.value = true;
}

function closeResetConfirm() {
  showResetConfirm.value = false;
}

// 比较版本号，返回 true 表示 version1 > version2
function compareVersions(version1: string, version2: string): boolean {
  const v1 = version1.split('.').map(Number);
  const v2 = version2.split('.').map(Number);
  const length = Math.max(v1.length, v2.length);
  for (let i = 0; i < length; i++) {
    const num1 = v1[i] || 0;
    const num2 = v2[i] || 0;
    if (num1 > num2) return true;
    if (num1 < num2) return false;
  }
  return false;
}

function parseVersionData(responseData: any): { version: string; apkUrl: string } | null {
  if (!responseData) return null;

  let data: any;
  const rawString = typeof responseData === 'string' ? responseData : JSON.stringify(responseData);

  try {
    data = JSON.parse(rawString);
    if (data.version && data.apkUrl) {
      return { version: data.version, apkUrl: data.apkUrl };
    }
    if (data.tag_name && data.assets && Array.isArray(data.assets) && data.assets.length > 0) {
      const version = data.tag_name.replace(/^v/i, '').replace(/^release[-_]/i, '');
      const apkAsset = data.assets.find((asset: any) => 
        asset.browser_download_url && asset.browser_download_url.endsWith('.apk')
      );
      if (!apkAsset) {
        console.log('[Update] GitHub API: 未找到.apk附件');
        return null;
      }
      const apkUrl = apkAsset.browser_download_url;
      if (version && apkUrl) {
        console.log('[Update] GitHub API解析成功: version=' + version + ', apkUrl=' + apkUrl);
        return { version, apkUrl };
      }
    }
  } catch (e) {
    console.log('[Update] JSON解析失败:', e);
  }

  try {
    const params = new URLSearchParams(rawString);
    const version = params.get('version');
    const apkUrl = params.get('apkUrl');
    if (version && apkUrl) {
      return { version, apkUrl };
    }
  } catch (e) {}

  try {
    const lines = rawString.trim().split('\n');
    let version = '';
    let apkUrl = '';
    for (const line of lines) {
      if (line.startsWith('version=') || line.startsWith('version:')) {
        version = line.replace(/^version[=:]\s*/, '').trim();
      } else if (line.startsWith('apkUrl=') || line.startsWith('apkUrl:') || line.startsWith('url=')) {
        apkUrl = line.replace(/^(apkUrl|url)[=:]\s*/, '').trim();
      }
    }
    if (version && apkUrl) {
      return { version, apkUrl };
    }
  } catch (e) {}

  return null;
}

// 检查更新 - 以GitHub Releases API为唯一主源
// 发布新版本时，只需在GitHub创建Release并上传APK，所有旧版本自动检测到更新
async function checkForUpdate(manualUrl?: string) {
  const GITHUB_RELEASES_URL = 'https://api.github.com/repos/Zero-william163/countdown-app/releases/latest';

  // 备用源：仅在GitHub API失败时使用（兜底）
  const fallbackUrls = [
    'https://paste.rs/6plr5',
    'https://paste.rs/YnsTY',
    'https://paste.rs/bFZdK',
    'https://paste.rs/O32nP'
  ];

  // 构建URL列表：手动URL > GitHub API（唯一主源）> 备用源
  const urls = manualUrl
    ? [manualUrl, GITHUB_RELEASES_URL, ...fallbackUrls]
    : [GITHUB_RELEASES_URL, ...fallbackUrls];

  let foundVersion = '';
  let foundApkUrl = '';

  for (const url of urls) {
    try {
      console.log('[Update] 正在请求更新源:', url);
      const response = await CapacitorHttp.get({
        url,
        headers: url.includes('api.github.com') ? { 'Accept': 'application/vnd.github+json', 'User-Agent': 'countdown-app' } : {}
      });
      console.log('[Update] 响应状态:', response.status);
      if (response.status === 200 && response.data) {
        console.log('[Update] 响应数据:', typeof response.data === 'string' ? response.data : JSON.stringify(response.data).substring(0, 500) + '...');
        const parsed = parseVersionData(response.data);
        console.log('[Update] 解析结果:', parsed);
        if (parsed) {
          if (!foundVersion || compareVersions(parsed.version, foundVersion)) {
            console.log('[Update] 发现更高版本:', parsed.version, '替换旧版本:', foundVersion || '无');
            foundVersion = parsed.version;
            foundApkUrl = parsed.apkUrl;
          } else {
            console.log('[Update] 当前版本', parsed.version, '低于已发现版本', foundVersion, '跳过');
          }
        }
      }
    } catch (e) {
      console.log('[Update] 检查更新源失败:', url, e);
    }
  }

  latestVersion.value = foundVersion;
  latestApkUrl.value = foundApkUrl;

  isCheckingUpdate.value = false;

  console.log('[Update] 检查完成: 发现版本=' + foundVersion + ', 当前版本=' + appVersion.value);
  console.log('[Update] 版本比较结果: compareVersions(' + foundVersion + ', ' + appVersion.value + ') = ' + compareVersions(foundVersion, appVersion.value));

  if (foundVersion && compareVersions(foundVersion, appVersion.value)) {
    // 检查是否已经下载过该版本（避免重复下载）
    const downloadedVersion = localStorage.getItem(DOWNLOADED_VERSION_KEY);
    if (downloadedVersion === foundVersion) {
      // 已下载但未安装，提示用户安装而不是重新下载
      console.log('[Update] 新版本已下载，等待安装:', foundVersion);
      showUpdateNotice.value = false; // 不显示顶部更新提示条
      // 显示安装提示对话框
      updateStatus.value = `新版本 v${foundVersion} 已下载完成，请点击安装`;
      showUpdateDialog.value = true;
      isUpdating.value = false;
    } else {
      showUpdateNotice.value = true;
      console.log('[Update] 显示更新提示');
    }
  } else {
    showUpdateNotice.value = false;
    console.log('[Update] 已是最新版本，不显示更新提示');
  }
}

// 手动检查更新（设置中调用）
async function manualCheckUpdate() {
  if (isCheckingUpdate.value) return;
  
  isCheckingUpdate.value = true;
  updateStatus.value = '正在检查更新...';
  showUpdateDialog.value = true;
  
  try {
    await checkForUpdate();
    
    if (latestVersion.value && compareVersions(latestVersion.value, appVersion.value)) {
      updateStatus.value = `发现新版本 v${latestVersion.value}，点击确定开始下载`;
      isUpdating.value = false;
    } else {
      updateStatus.value = '已是最新版本';
      isUpdating.value = false;
      setTimeout(() => {
        showUpdateDialog.value = false;
      }, 2000);
    }
  } catch (e) {
    updateStatus.value = '检查更新失败，请稍后重试';
    isUpdating.value = false;
    isCheckingUpdate.value = false;
    console.error('[Update] 手动检查更新失败:', e);
  }
}

// 更新应用 - 使用DownloadManager下载并自动安装
async function updateApp() {
  if (!latestApkUrl.value) {
    alert('下载链接无效');
    return;
  }

  showUpdateDialog.value = true;
  isUpdating.value = true;
  updateStatus.value = '正在准备下载...';

  try {
    const permResult = await UpdatePlugin.checkInstallPermission();
    if (!permResult.granted) {
      updateStatus.value = '需要安装权限，正在跳转设置...';
      await UpdatePlugin.requestInstallPermission();
      setTimeout(async () => {
        const recheck = await UpdatePlugin.checkInstallPermission();
        if (recheck.granted) {
          await startDownload();
        } else {
          isUpdating.value = false;
          updateStatus.value = '未授予安装权限，无法更新';
        }
      }, 3000);
      return;
    }

    await startDownload();
  } catch (e) {
    console.error('更新失败:', e);
    isUpdating.value = false;
    updateStatus.value = '更新失败: ' + (e as Error).message;
    try {
      await PermissionChecker.openUrl({ url: latestApkUrl.value });
    } catch (ee) {
      window.open(latestApkUrl.value, '_blank');
    }
  }
}

// 开始下载APK
async function startDownload() {
  isUpdating.value = true;
  updateStatus.value = '正在下载新版本...';
  try {
    const result = await UpdatePlugin.downloadAndInstall({ url: latestApkUrl.value });
    if (result.success) {
      updateStatus.value = '下载已开始，请查看通知栏进度。下载完成后将自动弹出安装界面。';
      // 记录已下载的版本号，避免下次启动重复下载
      if (latestVersion.value) {
        localStorage.setItem(DOWNLOADED_VERSION_KEY, latestVersion.value);
        console.log('[Update] 已记录下载版本号:', latestVersion.value);
      }
      // 隐藏顶部更新提示条
      showUpdateNotice.value = false;
      // 10秒后自动关闭对话框
      setTimeout(() => {
        showUpdateDialog.value = false;
        isUpdating.value = false;
      }, 10000);
    }
  } catch (e) {
    const errMsg = (e as Error).message || '';
    if (errMsg.includes('NEED_INSTALL_PERMISSION')) {
      updateStatus.value = '需要安装权限，请在设置中允许安装未知应用';
    } else {
      updateStatus.value = '下载失败，正在使用浏览器下载...';
      try {
        await PermissionChecker.openUrl({ url: latestApkUrl.value });
      } catch (ee) {
        window.open(latestApkUrl.value, '_blank');
      }
    }
    isUpdating.value = false;
  }
}

// 关闭更新对话框
function closeUpdateDialog() {
  showUpdateDialog.value = false;
  isUpdating.value = false;
}

onMounted(async () => {
  // 0. 如果闹钟正在响铃，停止它（用户打开了应用）
  try {
    await AlarmPlugin.stopAlarm();
  } catch (e) {
    // 忽略错误，可能没有闹钟在响
  }

  // 0.1 从原生获取当前应用版本号（避免硬编码）
  try {
    const versionResult = await UpdatePlugin.getAppVersion();
    if (versionResult.success && versionResult.version) {
      appVersion.value = versionResult.version;
      console.log('[Update] 从原生获取版本号:', appVersion.value);
      // 如果当前安装版本与已下载版本不同，清除下载标记（说明已安装新版本）
      const downloadedVersion = localStorage.getItem(DOWNLOADED_VERSION_KEY);
      if (downloadedVersion && downloadedVersion !== appVersion.value) {
        console.log('[Update] 检测到已安装新版本，清除下载标记');
        localStorage.removeItem(DOWNLOADED_VERSION_KEY);
      }
    }
  } catch (e) {
    console.error('[Update] 获取版本号失败:', e);
  }

  // 1. 首次启动检查 - 始终显示权限引导
  try {
    await checkFirstLaunch();
  } catch (e) {
    console.error('首次启动检查失败:', e);
    showPermissionGuide.value = true;
  }

  // 2. 加载保存的设置
  try {
    await loadSavedSettings();
  } catch (e) {
    console.error('加载设置失败:', e);
  }

  // 3. 检查权限状态（用于界面显示）
  try {
    await checkAllPermissions();
  } catch (e) {
    console.error('检查权限失败:', e);
  }

  // 4. 检查更新
  try {
    await checkForUpdate();
  } catch (e) {
    console.error('检查更新失败:', e);
  }

  timer = window.setInterval(() => {
    updateCountdown();
  }, 1000);

  // 5. 监听应用状态变化 - 从设置返回后自动刷新权限
  try {
    App.addListener('appStateChange', async ({ isActive }) => {
      if (isActive) {
        // 从设置返回时立即刷新权限状态
        setTimeout(async () => {
          try {
            // 重新检查所有系统权限（通知、精确闹钟、电池优化、悬浮窗）
            await checkAllPermissions();
            // 单独检查悬浮窗权限
            try {
              const overlayResult = await FloatingWindowPlugin.checkOverlayPermission();
              hasOverlayPermission.value = overlayResult.granted;
            } catch (e) {
              console.log('检查悬浮窗权限失败:', e);
            }
            // 自启动权限：如果用户刚从设置返回且之前未开启，提示确认
            if (autoStartSettingsOpened.value && !hasAutoStartPermission.value) {
              autoStartSettingsOpened.value = false;
              const confirmed = confirm('已在系统设置中开启自启动权限？\n\n点击"确定"后将状态标记为已开启。');
              if (confirmed) {
                hasAutoStartPermission.value = true;
                try {
                  await Preferences.set({ key: 'autostart_confirmed', value: 'true' });
                } catch (e) {}
              }
            } else {
              autoStartSettingsOpened.value = false;
            }
            // 如果所有权限都开启，自动关闭引导弹窗
            if (showPermissionGuide.value && hasPermission.value && hasExactAlarmPermission.value &&
                hasBatteryOptimization.value && hasOverlayPermission.value && hasAutoStartPermission.value) {
              showPermissionGuide.value = false;
              await Preferences.set({ key: FIRST_LAUNCH_KEY, value: 'true' });
            }
          } catch (e) {
            console.error('应用恢复时检查失败:', e);
          }
        }, 500);
      }
    });
  } catch (e) {
    console.error('注册应用状态监听失败:', e);
  }
});

onUnmounted(() => {
  if (timer) {
    clearInterval(timer);
  }
});
</script>

<template>
  <div class="app-container">
    <!-- 更新提示 -->
    <div v-if="showUpdateNotice" class="update-notice" @click="updateApp">
      <span>有新版本可用 (v{{ latestVersion }})，点击更新</span>
    </div>

    <!-- 头部 -->
    <header class="header">
      <div class="header-left">
        <div class="app-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 22C13.1 22 14 21.1 14 20H10C10 21.1 10.9 22 12 22ZM18 16V11C18 7.93 16.37 5.36 13.5 4.68V4C13.5 3.17 12.83 2.5 12 2.5C11.17 2.5 10.5 3.17 10.5 4V4.68C7.64 5.36 6 7.92 6 11V16L4 18V19H20V18L18 16ZM16 17H8V11C8 8.52 9.49 6.5 12 6.5C14.51 6.5 16 8.52 16 11V17Z" fill="white"/>
          </svg>
        </div>
        <div class="header-text">
          <h1>倒计时提醒</h1>
          <p>每一天都准时提醒你</p>
        </div>
      </div>
      <button class="settings-btn" @click="openSettings">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M19.14 12.94C19.19 12.64 19.22 12.33 19.22 12C19.22 11.68 19.19 11.36 19.14 11.06L21.54 9.21C21.76 9.04 21.82 8.73 21.69 8.48L19.42 4.52C19.29 4.27 19 4.15 18.72 4.24L15.87 5.25C15.35 4.86 14.79 4.54 14.18 4.31L13.76 1.34C13.71 1.05 13.46 0.84 13.16 0.84H10.84C10.54 0.84 10.29 1.05 10.24 1.34L9.82 4.31C9.21 4.54 8.65 4.86 8.13 5.25L5.28 4.24C5 4.15 4.71 4.27 4.58 4.52L2.31 8.48C2.18 8.73 2.24 9.04 2.46 9.21L4.86 11.06C4.81 11.36 4.78 11.68 4.78 12C4.78 12.33 4.81 12.64 4.86 12.94L2.46 14.79C2.24 14.96 2.18 15.27 2.31 15.52L4.58 19.48C4.71 19.73 5 19.85 5.28 19.76L8.13 18.75C8.65 19.14 9.21 19.46 9.82 19.69L10.24 22.66C10.29 22.95 10.54 23.16 10.84 23.16H13.16C13.46 23.16 13.71 22.95 13.76 22.66L14.18 19.69C14.79 19.46 15.35 19.14 15.87 18.75L18.72 19.76C19 19.85 19.29 19.73 19.42 19.48L21.69 15.52C21.82 15.27 21.76 14.96 21.54 14.79L19.14 12.94ZM12 15.5C10.62 15.5 9.5 14.38 9.5 13C9.5 11.62 10.62 10.5 12 10.5C13.38 10.5 14.5 11.62 14.5 13C14.5 14.38 13.38 15.5 12 15.5Z" fill="#666"/>
        </svg>
      </button>
    </header>

    <!-- 主内容 -->
    <main class="main-content">
      <!-- 状态徽章 -->
      <div class="status-badge" v-if="isAlarmSet">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M11.99 2C6.47 2 2 6.48 2 12C2 17.52 6.47 22 11.99 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 11.99 2ZM12 20C7.58 20 4 16.42 4 12C4 7.58 7.58 4 12 4C16.42 4 20 7.58 20 12C20 16.42 16.42 20 12 20ZM12.5 7H11V13L16.25 16.15L17 14.92L12.5 12.25V7Z" fill="#2B7FFF"/>
        </svg>
        <span>倒计时进行中</span>
      </div>

      <!-- 目标名称 -->
      <h2 class="target-name" v-if="isAlarmSet">{{ targetName }}</h2>

      <!-- 目标日期 -->
      <p class="target-datetime" v-if="isAlarmSet">目标日期：{{ targetDateTimeText }}</p>

      <!-- 倒计时卡片 -->
      <div class="countdown-grid" v-if="isAlarmSet">
        <div class="countdown-box">
          <span class="countdown-number">{{ String(countdown.days).padStart(2, '0') }}</span>
          <span class="countdown-label">天</span>
        </div>
        <div class="countdown-box">
          <span class="countdown-number">{{ String(countdown.hours).padStart(2, '0') }}</span>
          <span class="countdown-label">时</span>
        </div>
        <div class="countdown-box">
          <span class="countdown-number">{{ String(countdown.minutes).padStart(2, '0') }}</span>
          <span class="countdown-label">分</span>
        </div>
        <div class="countdown-box">
          <span class="countdown-number">{{ String(countdown.seconds).padStart(2, '0') }}</span>
          <span class="countdown-label">秒</span>
        </div>
      </div>

      <!-- 剩余时间总计 -->
      <div class="total-remaining" v-if="isAlarmSet">
        <span class="total-label">剩余时间总计：</span>
        <span class="total-value">{{ totalRemaining }}</span>
      </div>

      <!-- 每日提醒卡片 -->
      <div class="reminder-card" v-if="isAlarmSet">
        <div class="reminder-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 22C13.1 22 14 21.1 14 20H10C10 21.1 10.9 22 12 22ZM18 16V11C18 7.93 16.37 5.36 13.5 4.68V4C13.5 3.17 12.83 2.5 12 2.5C11.17 2.5 10.5 3.17 10.5 4V4.68C7.64 5.36 6 7.92 6 11V16L4 18V19H20V18L18 16ZM16 17H8V11C8 8.52 9.49 6.5 12 6.5C14.51 6.5 16 8.52 16 11V17Z" fill="#2B7FFF"/>
          </svg>
        </div>
        <div class="reminder-info">
          <h3>每日提醒</h3>
          <p>每天 {{ reminderTime }} 准时提醒</p>
        </div>
        <div v-if="hasPermission && hasExactAlarmPermission && hasBatteryOptimization" class="permission-status active">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M9 16.17L4.83 12L3.41 13.41L9 19L21 7L19.59 5.59L9 16.17Z" fill="#28C76F"/>
          </svg>
          <span>所有权限已开启</span>
        </div>
        <div v-else class="permission-status warning" @click="showPermissionGuide = true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M1 21H23L12 2L1 21ZM13 18H11V16H13V18ZM13 14H11V10H13V14Z" fill="#FF9500"/>
          </svg>
          <span>权限不完整，点击检查</span>
        </div>
      </div>

      <!-- 重新设置按钮 -->
      <button class="reset-btn" v-if="isAlarmSet" @click="confirmReset">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 5V1L7 6L12 11V7C15.31 7 18 9.69 18 13C18 16.31 15.31 19 12 19C8.69 19 6 16.31 6 13H4C4 17.42 7.58 21 12 21C16.42 21 20 17.42 20 13C20 8.58 16.42 5 12 5Z" fill="#666"/>
        </svg>
        <span>重新设置</span>
      </button>

      <!-- 悬浮窗控制按钮 -->
      <button class="float-control-btn" v-if="isAlarmSet" @click="isFloatingWindowShown ? hideFloatingCountdown() : showFloatingCountdown()">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path v-if="!isFloatingWindowShown" d="M3 5H21V17H3V5ZM3 3C1.9 3 1 3.9 1 5V17C1 18.1 1.9 19 3 19H21C22.1 19 23 18.1 23 17V5C23 3.9 22.1 3 21 3H3ZM9 21H15V23H9V21Z" fill="white"/>
          <path v-else d="M3 5H21V17H3V5ZM9 21H15V23H9V21ZM7 12L11 8L13 10L17 6V9L13 13L11 11L7 15V12Z" fill="white"/>
        </svg>
        <span>{{ isFloatingWindowShown ? '隐藏悬浮窗' : '显示悬浮窗' }}</span>
      </button>

      <!-- 暂无闹钟状态 -->
      <div class="empty-state" v-if="!isAlarmSet">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 22C13.1 22 14 21.1 14 20H10C10 21.1 10.9 22 12 22ZM18 16V11C18 7.93 16.37 5.36 13.5 4.68V4C13.5 3.17 12.83 2.5 12 2.5C11.17 2.5 10.5 3.17 10.5 4V4.68C7.64 5.36 6 7.92 6 11V16L4 18V19H20V18L18 16ZM16 17H8V11C8 8.52 9.49 6.5 12 6.5C14.51 6.5 16 8.52 16 11V17Z" fill="#D1D5DB"/>
          </svg>
        </div>
        <p class="empty-text">暂无闹钟</p>
        <p class="empty-hint">点击下方按钮添加倒计时提醒</p>
      </div>
    </main>

    <!-- 添加按钮 -->
    <button class="fab-button" v-if="!isAlarmSet" @click="openSettings">
      <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 5V19M5 12H19" stroke="white" stroke-width="3" stroke-linecap="round"/>
      </svg>
    </button>

    <!-- 底部 -->
    <footer class="footer">
      <p>倒计时提醒 APP</p>
    </footer>

    <!-- 设置弹窗 -->
    <div v-if="showSettings" class="modal-overlay" @click.self="closeSettings">
      <div class="modal-card">
        <div class="modal-header">
          <h3>修改设置</h3>
          <button class="modal-close" @click="closeSettings">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 6.41L17.59 5L12 10.59L6.41 5L5 6.41L10.59 12L5 17.59L6.41 19L12 13.41L17.59 19L19 17.59L13.41 12L19 6.41Z" fill="#999"/>
            </svg>
          </button>
        </div>

        <div class="modal-body">
          <div class="form-group">
            <label>当前版本</label>
            <div class="version-info">
              <span class="version-current">v{{ appVersion }}</span>
              <button 
                class="btn-check-update" 
                @click="manualCheckUpdate"
                :disabled="isCheckingUpdate"
              >
                <svg v-if="!isCheckingUpdate" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M17.65 6.35C16.2 4.9 14.21 4 12 4C9.79 4 7.8 4.9 6.35 6.35C4.9 7.8 4 9.79 4 12C4 14.21 4.9 16.2 6.35 17.65C7.8 19.1 9.79 20 12 20C14.21 20 16.2 19.1 17.65 17.65C19.1 16.2 20 14.21 20 12C20 9.79 19.1 7.8 17.65 6.35ZM12 18C9.79 18 8 16.21 8 14C8 11.79 9.79 10 12 10C14.21 10 16 11.79 16 14C16 16.21 14.21 18 12 18ZM13 7H11V13L16.25 16.15L17 14.92L13 12.25V7Z" fill="#2B7FFF"/>
                </svg>
                <span>{{ isCheckingUpdate ? '检查中...' : '检查更新' }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 重置确认弹窗 -->
    <div v-if="showResetConfirm" class="modal-overlay" @click.self="closeResetConfirm">
      <div class="confirm-card">
        <p class="confirm-text">确定要重置倒计时吗？</p>
        <div class="confirm-actions">
          <button class="btn-text" @click="closeResetConfirm">CANCEL</button>
          <button class="btn-text btn-primary-text" @click="cancelReminder">OK</button>
        </div>
      </div>
    </div>

    <!-- 权限引导弹窗 - 首次启动始终显示 -->
    <div v-if="showPermissionGuide" class="modal-overlay">
      <div class="permission-guide-card">
        <!-- 标题部分（固定不滚动） -->
        <div class="permission-guide-header">
          <div class="permission-guide-icon">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 22C13.1 22 14 21.1 14 20H10C10 21.1 10.9 22 12 22ZM18 16V11C18 7.93 16.37 5.36 13.5 4.68V4C13.5 3.17 12.83 2.5 12 2.5C11.17 2.5 10.5 3.17 10.5 4V4.68C7.64 5.36 6 7.92 6 11V16L4 18V19H20V18L18 16ZM16 17H8V11C8 8.52 9.49 6.5 12 6.5C14.51 6.5 16 8.52 16 11V17Z" fill="#FF9500"/>
            </svg>
          </div>
          <h3 class="permission-guide-title">权限设置</h3>
          <p class="permission-guide-text">为确保闹钟准时响起，请逐一开启以下权限。点击每项可跳转到对应设置页面：</p>
        </div>

        <!-- 滚动区域（占据剩余空间） -->
        <div class="permission-guide-scroll">
          <div class="permission-guide-actions">
          <!-- 通知权限 -->
          <div class="permission-item" @click="requestNotificationPermission">
            <div class="permission-item-info">
              <div class="permission-item-name">
                <span>通知权限</span>
                <span v-if="hasPermission" class="perm-badge perm-badge-ok">已开启</span>
                <span v-else class="perm-badge perm-badge-warn">未开启</span>
              </div>
              <p class="permission-item-desc">允许发送提醒通知，否则闹钟不会弹出</p>
            </div>
            <svg v-if="hasPermission" class="perm-check-icon" viewBox="0 0 24 24" fill="none"><path d="M9 16.17L4.83 12L3.41 13.41L9 19L21 7L19.59 5.59L9 16.17Z" fill="#28C76F"/></svg>
            <svg v-else class="perm-arrow-icon" viewBox="0 0 24 24" fill="none"><path d="M8.59 16.59L13.17 12L8.59 7.41L10 6L16 12L10 18L8.59 16.59Z" fill="#999"/></svg>
          </div>

          <!-- 精确闹钟权限 -->
          <div class="permission-item" @click="openAlarmSettings">
            <div class="permission-item-info">
              <div class="permission-item-name">
                <span>精确闹钟</span>
                <span v-if="hasExactAlarmPermission" class="perm-badge perm-badge-ok">已开启</span>
                <span v-else class="perm-badge perm-badge-warn">未开启</span>
              </div>
              <p class="permission-item-desc">确保闹钟在精确时间点触发，不延迟</p>
            </div>
            <svg v-if="hasExactAlarmPermission" class="perm-check-icon" viewBox="0 0 24 24" fill="none"><path d="M9 16.17L4.83 12L3.41 13.41L9 19L21 7L19.59 5.59L9 16.17Z" fill="#28C76F"/></svg>
            <svg v-else class="perm-arrow-icon" viewBox="0 0 24 24" fill="none"><path d="M8.59 16.59L13.17 12L8.59 7.41L10 6L16 12L10 18L8.59 16.59Z" fill="#999"/></svg>
          </div>

          <!-- 电池优化 -->
          <div class="permission-item" @click="openBatterySettings">
            <div class="permission-item-info">
              <div class="permission-item-name">
                <span>关闭电池优化</span>
                <span v-if="hasBatteryOptimization" class="perm-badge perm-badge-ok">已开启</span>
                <span v-else class="perm-badge perm-badge-warn">未开启</span>
              </div>
              <p class="permission-item-desc">防止系统休眠时杀掉闹钟，确保后台准时提醒</p>
            </div>
            <svg v-if="hasBatteryOptimization" class="perm-check-icon" viewBox="0 0 24 24" fill="none"><path d="M9 16.17L4.83 12L3.41 13.41L9 19L21 7L19.59 5.59L9 16.17Z" fill="#28C76F"/></svg>
            <svg v-else class="perm-arrow-icon" viewBox="0 0 24 24" fill="none"><path d="M8.59 16.59L13.17 12L8.59 7.41L10 6L16 12L10 18L8.59 16.59Z" fill="#999"/></svg>
          </div>

          <!-- 悬浮窗权限 -->
          <div class="permission-item" @click="openOverlaySettings">
            <div class="permission-item-info">
              <div class="permission-item-name">
                <span>悬浮窗权限</span>
                <span v-if="hasOverlayPermission" class="perm-badge perm-badge-ok">已开启</span>
                <span v-else class="perm-badge perm-badge-warn">未开启</span>
              </div>
              <p class="permission-item-desc">在桌面显示倒计时悬浮窗，类似华为闹钟</p>
            </div>
            <svg v-if="hasOverlayPermission" class="perm-check-icon" viewBox="0 0 24 24" fill="none"><path d="M9 16.17L4.83 12L3.41 13.41L9 19L21 7L19.59 5.59L9 16.17Z" fill="#28C76F"/></svg>
            <svg v-else class="perm-arrow-icon" viewBox="0 0 24 24" fill="none"><path d="M8.59 16.59L13.17 12L8.59 7.41L10 6L16 12L10 18L8.59 16.59Z" fill="#999"/></svg>
          </div>

          <!-- 自启动权限 -->
          <div class="permission-item" @click="openAutoStartSettings">
            <div class="permission-item-info">
              <div class="permission-item-name">
                <span>允许自启动</span>
                <span v-if="hasAutoStartPermission" class="perm-badge perm-badge-ok">已开启</span>
                <span v-else class="perm-badge perm-badge-warn">未开启</span>
              </div>
              <p class="permission-item-desc">杀死后台后仍能显示悬浮窗和提醒闹钟（跳转后请手动确认）</p>
            </div>
            <svg v-if="hasAutoStartPermission" class="perm-check-icon" viewBox="0 0 24 24" fill="none"><path d="M9 16.17L4.83 12L3.41 13.41L9 19L21 7L19.59 5.59L9 16.17Z" fill="#28C76F"/></svg>
            <svg v-else class="perm-arrow-icon" viewBox="0 0 24 24" fill="none"><path d="M8.59 16.59L13.17 12L8.59 7.41L10 6L16 12L10 18L8.59 16.59Z" fill="#999"/></svg>
          </div>

          <!-- 打开应用设置 -->
          <div class="permission-item" @click="openAppSettingsPage">
            <div class="permission-item-info">
              <div class="permission-item-name">
                <span>打开应用设置</span>
              </div>
              <p class="permission-item-desc">无法找到以上选项？点击进入应用详情页</p>
            </div>
            <svg class="perm-arrow-icon" viewBox="0 0 24 24" fill="none"><path d="M8.59 16.59L13.17 12L8.59 7.41L10 6L16 12L10 18L8.59 16.59Z" fill="#999"/></svg>
          </div>
          </div>
        </div>

        <!-- 底部按钮（固定在卡片底部，不滚动） -->
        <div class="permission-guide-footer">
          <div v-if="allPermissionsGranted" style="width:100%;color:#28C76F;font-size:13px;margin-bottom:8px;font-weight:600;">✓ 所有权限已开启</div>
          <button class="btn btn-permission-secondary" @click="checkAllPermissions">刷新状态</button>
          <button class="btn btn-permission-primary" @click="closePermissionGuide">已完成设置</button>
        </div>
      </div>
    </div>

    <!-- 更新对话框 -->
    <div v-if="showUpdateDialog" class="modal-overlay">
      <div class="confirm-card">
        <h3 style="margin-bottom: 12px; color: #1a1a1a;">应用更新</h3>
        <p style="margin-bottom: 16px; color: #666; text-align: center; line-height: 1.6;">{{ updateStatus }}</p>
        <div v-if="isUpdating" style="display: flex; justify-content: center; margin: 16px 0;">
          <div style="width: 32px; height: 32px; border: 3px solid #e0e0e0; border-top: 3px solid #2B7FFF; border-radius: 50%; animation: spin 1s linear infinite;"></div>
        </div>
        <div class="confirm-actions" v-if="!isUpdating">
          <button class="btn-text" @click="closeUpdateDialog">{{ latestVersion && compareVersions(latestVersion, appVersion) ? '稍后' : '关闭' }}</button>
          <button v-if="latestVersion && compareVersions(latestVersion, appVersion)" class="btn-text btn-primary-text" @click="startDownload">立即更新</button>
        </div>
        <div class="confirm-actions" v-else>
          <button class="btn-text" @click="closeUpdateDialog">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #F5F7FA;
  min-height: 100vh;
}

.app-container {
  max-width: 480px;
  margin: 0 auto;
  padding: 16px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.update-notice {
  background: #ff6b6b;
  color: white;
  padding: 10px 16px;
  border-radius: 8px;
  text-align: center;
  margin-bottom: 12px;
  cursor: pointer;
  font-weight: 500;
  font-size: 13px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.app-icon svg {
  width: 28px;
  height: 28px;
}

.header-text h1 {
  font-size: 22px;
  font-weight: 700;
  color: #1A1A2E;
  margin-bottom: 2px;
}

.header-text p {
  font-size: 13px;
  color: #8A8A9A;
}

.settings-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.settings-btn svg {
  width: 24px;
  height: 24px;
}

.main-content {
  background: white;
  border-radius: 24px;
  padding: 28px 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  flex: 1;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #E8F3FF;
  color: #2B7FFF;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 24px;
}

.status-badge svg {
  width: 18px;
  height: 18px;
}

.target-name {
  font-size: 32px;
  font-weight: 800;
  color: #1A1A2E;
  text-align: center;
  margin-bottom: 12px;
}

.target-datetime {
  font-size: 15px;
  color: #6B7280;
  text-align: center;
  margin-bottom: 28px;
}

.countdown-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 24px;
}

.countdown-box {
  background: linear-gradient(135deg, #2B7FFF 0%, #1E6FE8 100%);
  border-radius: 16px;
  padding: 16px 8px;
  text-align: center;
  color: white;
  box-shadow: 0 6px 16px rgba(43, 127, 255, 0.25);
}

.countdown-number {
  display: block;
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
  margin-bottom: 6px;
}

.countdown-label {
  font-size: 13px;
  opacity: 0.9;
}

.total-remaining {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 28px;
  font-size: 15px;
}

.total-label {
  color: #6B7280;
}

.total-value {
  color: #1A1A2E;
  font-weight: 700;
}

.reminder-card {
  background: #F8FAFF;
  border-radius: 16px;
  padding: 18px;
  margin-bottom: 20px;
}

.reminder-icon {
  width: 44px;
  height: 44px;
  background: #E8F3FF;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.reminder-icon svg {
  width: 24px;
  height: 24px;
}

.reminder-info h3 {
  font-size: 17px;
  font-weight: 700;
  color: #1A1A2E;
  margin-bottom: 4px;
}

.reminder-info p {
  font-size: 14px;
  color: #6B7280;
  margin-bottom: 12px;
}

.permission-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #28C76F;
  font-weight: 600;
}

.permission-status svg {
  width: 18px;
  height: 18px;
}

.permission-status:not(.active) {
  color: #2B7FFF;
  cursor: pointer;
}

.permission-status.warning {
  color: #FF9500;
}

.reset-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  border: none;
  border-radius: 16px;
  background: #F3F4F6;
  color: #4B5563;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.reset-btn:active {
  background: #E5E7EB;
}

.reset-btn svg {
  width: 20px;
  height: 20px;
}

.float-control-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 12px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
  transition: transform 0.15s;
}

.float-control-btn:active {
  transform: scale(0.98);
}

.float-control-btn svg {
  width: 18px;
  height: 18px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.empty-icon {
  width: 80px;
  height: 80px;
  background: #F3F4F6;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-icon svg {
  width: 40px;
  height: 40px;
}

.empty-text {
  font-size: 20px;
  font-weight: 700;
  color: #1A1A2E;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 15px;
  color: #9CA3AF;
}

.fab-button {
  position: fixed;
  bottom: 32px;
  right: 32px;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2B7FFF 0%, #1E6FE8 100%);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 20px rgba(43, 127, 255, 0.4);
  cursor: pointer;
  z-index: 100;
  transition: transform 0.2s, box-shadow 0.2s;
}

.fab-button:active {
  transform: scale(0.95);
  box-shadow: 0 4px 12px rgba(43, 127, 255, 0.3);
}

.fab-button svg {
  width: 30px;
  height: 30px;
}

.footer {
  text-align: center;
  padding: 20px 0;
}

.footer p {
  font-size: 13px;
  color: #9CA3AF;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.modal-card {
  background: white;
  border-radius: 24px;
  width: 100%;
  max-width: 400px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 20px 0;
}

.modal-header h3 {
  font-size: 20px;
  font-weight: 800;
  color: #1A1A2E;
}

.modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-close svg {
  width: 24px;
  height: 24px;
}

.modal-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 18px;
}

.form-group label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #4B5563;
  margin-bottom: 8px;
}

.form-group label svg {
  width: 18px;
  height: 18px;
}

.input-field {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid #E5E7EB;
  border-radius: 12px;
  font-size: 16px;
  color: #1A1A2E;
  background: #FAFBFC;
  transition: border-color 0.2s, background 0.2s;
}

.input-field:focus {
  outline: none;
  border-color: #2B7FFF;
  background: white;
}

.version-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #FAFBFC;
  border-radius: 12px;
  border: 1px solid #E5E7EB;
}

.version-current {
  font-size: 16px;
  font-weight: 600;
  color: #1A1A2E;
}

.btn-check-update {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #2B7FFF;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-check-update:hover {
  background: #1E6FE8;
}

.btn-check-update:disabled {
  background: #9CA3AF;
  cursor: not-allowed;
}

.btn-check-update svg {
  width: 16px;
  height: 16px;
}

.modal-footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 0 20px 20px;
}

.btn {
  padding: 14px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s;
}

.btn:active {
  transform: scale(0.98);
}

.btn-cancel {
  background: #F3F4F6;
  color: #4B5563;
}

.btn-save {
  background: linear-gradient(135deg, #2B7FFF 0%, #1E6FE8 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(43, 127, 255, 0.3);
}

.confirm-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  width: 100%;
  max-width: 320px;
}

.confirm-text {
  font-size: 17px;
  color: #1A1A2E;
  margin-bottom: 24px;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 24px;
}

.btn-text {
  border: none;
  background: transparent;
  font-size: 15px;
  font-weight: 600;
  color: #6B7280;
  cursor: pointer;
  padding: 8px;
}

.btn-primary-text {
  color: #2B7FFF;
}

.permission-guide-card {
  background: white;
  border-radius: 24px;
  padding: 24px 20px 16px;
  width: 100%;
  max-width: 360px;
  max-height: 90vh;
  text-align: center;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.permission-guide-header {
  flex-shrink: 0;
}

.permission-guide-scroll {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  min-height: 0;
  padding: 0 4px;
}

.permission-guide-footer {
  flex-shrink: 0;
  display: flex;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #F0F0F0;
}

.permission-guide-icon {
  width: 64px;
  height: 64px;
  background: #FFF3E0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
}

.permission-guide-icon svg {
  width: 32px;
  height: 32px;
}

.permission-guide-title {
  font-size: 20px;
  font-weight: 800;
  color: #1A1A2E;
  margin-bottom: 12px;
}

.permission-guide-text {
  font-size: 15px;
  color: #6B7280;
  line-height: 1.6;
  margin-bottom: 24px;
  white-space: pre-line;
}

.permission-guide-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.permission-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #F8F9FA;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
  border: 1px solid #E5E7EB;
}

.permission-item:active {
  background: #EEF0F2;
}

.permission-item-info {
  flex: 1;
  text-align: left;
}

.permission-item-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1A1A2E;
  margin-bottom: 4px;
}

.permission-item-desc {
  font-size: 13px;
  color: #8E8E93;
  line-height: 1.4;
}

.perm-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  font-weight: 500;
}

.perm-badge-ok {
  background: #E8F8EE;
  color: #28C76F;
}

.perm-badge-warn {
  background: #FFF4E6;
  color: #FF9500;
}

.perm-check-icon {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.perm-arrow-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.btn-permission {
  background: linear-gradient(135deg, #FF9500 0%, #FF7700 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(255, 149, 0, 0.3);
}

.btn-permission-outline {
  background: white;
  color: #2B7FFF;
  border: 2px solid #2B7FFF;
}

.btn-permission-secondary {
  background: #F3F4F6;
  color: #6B7280;
  flex: 1;
}

.permission-guide-footer .btn {
  flex: 1;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
