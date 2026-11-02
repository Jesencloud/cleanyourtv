#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================================"
echo " 正在编译创维专用 Release APK (com.coocaa.tvhome)..."
echo "========================================================"
cd "$DIR/launcher"
./gradlew --no-daemon clean -PlauncherApplicationId=com.coocaa.tvhome assembleRelease

APK="$DIR/launcher/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
    echo "错误: 未找到生成的 APK 文件: $APK"
    exit 1
fi

echo ""
echo "编译成功！APK 路径: $APK"
echo ""

echo "========================================================"
echo " 检查 ADB 连接并极速更新到电视..."
echo "========================================================"
if ! adb get-state >/dev/null 2>&1; then
    echo "提示: 当前未连接 ADB 设备，已完成编译保存。"
    echo "如需安装到电视，请先执行: adb connect <电视IP>:5555"
    echo "然后执行: adb install -r -d \"$APK\""
    exit 0
fi

echo "1. 覆盖安装到电视 (保留壁纸与个性化设置)..."
adb install -r -d "$APK"

echo "2. 执行 AOT Speed 模式预编译..."
adb shell cmd package compile -m speed -f com.coocaa.tvhome

echo "3. 刷新并唤起主页桌面..."
adb shell am force-stop com.coocaa.tvhome 2>/dev/null || true
adb shell input keyevent 3

echo "========================================================"
echo " 一键编译并安装更新完成！"
echo "========================================================"
