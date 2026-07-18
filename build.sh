#!/bin/bash

# Android构建脚本
# 用于在本地或CI环境中构建APK

set -e

echo "========================================"
echo "倒计时提醒 Android APK 构建脚本"
echo "========================================"

# 设置环境变量
export ANDROID_HOME=${ANDROID_HOME:-/root/android-sdk}
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$ANDROID_HOME}
export JAVA_HOME=${JAVA_HOME:-/root/.local/share/mise/installs/java/17.0.2}
export PATH=$JAVA_HOME/bin:$PATH
export JAVA_OPTS="-Xmx2048m"

echo "ANDROID_HOME: $ANDROID_HOME"
echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"

# 检查Android SDK
if [ ! -d "$ANDROID_HOME/platforms" ]; then
    echo "错误: Android SDK未正确安装"
    echo "请先安装Android SDK"
    exit 1
fi

# 进入Android项目目录
cd android

# 清理旧的构建文件
echo "清理旧的构建文件..."
rm -rf .gradle build app/build

# 回到项目根目录编译前端
echo "编译前端代码..."
cd ..
npm run build

# 同步Capacitor
echo "同步Capacitor项目..."
npx cap sync android

# 强制使用 Java 17 编译选项（兼容当前环境）
echo "检查编译选项..."
sed -i 's/JavaVersion.VERSION_21/JavaVersion.VERSION_17/g' android/app/capacitor.build.gradle

# 构建APK
echo "开始构建APK..."
cd android
gradle assembleDebug --no-daemon --no-build-cache

# 检查构建结果
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "========================================"
    echo "构建成功!"
    echo "APK位置: app/build/outputs/apk/debug/app-debug.apk"
    echo "========================================"
    
    # 复制到项目根目录
    cp app/build/outputs/apk/debug/app-debug.apk ../countdown-reminder.apk
    echo "APK已复制到: countdown-reminder.apk"
else
    echo "错误: APK构建失败"
    exit 1
fi