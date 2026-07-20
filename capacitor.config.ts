import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'com.william163.countdown',
  appName: '倒计时提醒',
  webDir: 'dist',
  plugins: {
    LocalNotifications: {
      smallIcon: 'ic_stat_icon',
      iconColor: '#667eea',
      sound: 'default'
    },
    CapacitorHttp: {
      enabled: true
    }
  }
};

export default config;