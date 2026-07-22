import { registerPlugin } from '@capacitor/core';

export interface PermissionCheckerPlugin {
  checkExactAlarmPermission(): Promise<{ granted: boolean }>;
  checkBatteryOptimization(): Promise<{ granted: boolean }>;
  checkNotificationPermission(): Promise<{ granted: boolean }>;
  checkAutoStartPermission(): Promise<{ granted: boolean }>;
  checkOverlayPermission(): Promise<{ granted: boolean }>;
  openAlarmSettings(): Promise<void>;
  openBatterySettings(): Promise<void>;
  openAutoStartSettings(): Promise<void>;
  requestOverlayPermission(): Promise<void>;
  openAppSettings(): Promise<void>;
  openUrl(options: { url: string }): Promise<void>;
  requestNotificationPermission(): Promise<void>;
}

export const PermissionChecker = registerPlugin<PermissionCheckerPlugin>('PermissionChecker', {
  web: {
    checkExactAlarmPermission: () => Promise.resolve({ granted: true }),
    checkBatteryOptimization: () => Promise.resolve({ granted: true }),
    checkNotificationPermission: () => Promise.resolve({ granted: true }),
    checkAutoStartPermission: () => Promise.resolve({ granted: true }),
    checkOverlayPermission: () => Promise.resolve({ granted: true }),
    openAlarmSettings: () => Promise.resolve(),
    openBatterySettings: () => Promise.resolve(),
    openAutoStartSettings: () => Promise.resolve(),
    requestOverlayPermission: () => Promise.resolve(),
    openAppSettings: () => Promise.resolve(),
    openUrl: ({ url }: { url: string }) => { window.open(url, '_blank'); return Promise.resolve(); },
    requestNotificationPermission: () => Promise.resolve(),
  },
});

export interface UpdatePluginInterface {
  downloadAndInstall(options: { url: string }): Promise<{ success: boolean; message: string }>;
  checkInstallPermission(): Promise<{ granted: boolean }>;
  requestInstallPermission(): Promise<void>;
  getAppVersion(): Promise<{ version: string; versionCode: number; success: boolean }>;
  updateWidget(): Promise<{ success: boolean }>;
  isWidgetPinned(): Promise<{ isPinned: boolean; count: number; success: boolean }>;
  requestPinWidget(): Promise<{ success: boolean; requested: boolean; message: string }>;
}

export const UpdatePlugin = registerPlugin<UpdatePluginInterface>('UpdatePlugin', {
  web: {
    downloadAndInstall: ({ url }: { url: string }) => { window.open(url, '_blank'); return Promise.resolve({ success: true, message: 'Web platform' }); },
    checkInstallPermission: () => Promise.resolve({ granted: true }),
    requestInstallPermission: () => Promise.resolve(),
    getAppVersion: () => Promise.resolve({ version: '0.0.0', versionCode: 0, success: false }),
    updateWidget: () => Promise.resolve({ success: false }),
    isWidgetPinned: () => Promise.resolve({ isPinned: false, count: 0, success: false }),
    requestPinWidget: () => Promise.resolve({ success: false, requested: false, message: 'Web platform' }),
  },
});

export interface AlarmPluginInterface {
  scheduleDailyAlarms(options: {
    hour: number;
    minute: number;
    targetName: string;
    daysRemaining: number;
    targetDate: string;
  }): Promise<{ success: boolean; count: number }>;
  cancelAllAlarms(): Promise<{ success: boolean; count: number }>;
  stopAlarm(): Promise<{ success: boolean }>;
}

export const AlarmPlugin = registerPlugin<AlarmPluginInterface>('AlarmPlugin', {
  web: {
    scheduleDailyAlarms: () => Promise.resolve({ success: true, count: 0 }),
    cancelAllAlarms: () => Promise.resolve({ success: true, count: 0 }),
    stopAlarm: () => Promise.resolve({ success: true }),
  },
});

export interface RingtonePluginInterface {
  pickRingtone(): Promise<{ success: boolean; path: string; name: string; message: string }>;
  getCurrentRingtone(): Promise<{ success: boolean; isCustom: boolean; path?: string; name: string }>;
  resetToDefault(): Promise<{ success: boolean; message: string }>;
}

export const RingtonePlugin = registerPlugin<RingtonePluginInterface>('RingtonePlugin', {
  web: {
    pickRingtone: () => Promise.reject('Web 平台不支持'),
    getCurrentRingtone: () => Promise.resolve({ success: true, isCustom: false, name: '默认铃声' }),
    resetToDefault: () => Promise.resolve({ success: true, message: '已恢复为默认铃声' }),
  },
});
