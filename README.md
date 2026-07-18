# 倒计时提醒 App

一个简洁的倒计时提醒应用，支持重要日期倒计时、悬浮窗显示、定时提醒等功能。

## 功能特性

- 📅 日期倒计时：设置重要日期，实时显示剩余天数
- 🪟 悬浮窗：桌面悬浮窗显示倒计时，不占用屏幕空间
- 🔔 定时提醒：设定时提醒，到点通知
- ⬆️ 自动更新：支持应用内自动检测更新并下载安装

## 技术栈

- Vue 3 + Vite
- Capacitor（移动端跨平台）
- Android 原生插件（悬浮窗、更新、闹钟）

## 开发

```bash
npm install
npm run dev
```

## 构建 Android APK

```bash
npm run build
npx cap sync android
cd android && ./gradlew assembleRelease
```

## 自动更新机制

使用 GitHub Releases API 检测新版本，发布新版本时只需在 GitHub 创建新 Release 并上传 APK 即可。
