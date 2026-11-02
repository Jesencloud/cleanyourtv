#!/bin/bash
set -e

usage() {
    cat <<'USAGE'
用法: build-and-update-coocaa.sh [--clean] [--precompile]

默认增量编译并覆盖安装。
  --clean       编译前清理构建输出
  --precompile  安装后强制执行 AOT Speed 预编译
  --help        显示此说明
USAGE
}

CLEAN_BUILD=0
PRECOMPILE=0
for arg in "$@"; do
    case "$arg" in
        --clean) CLEAN_BUILD=1 ;;
        --precompile) PRECOMPILE=1 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "错误: 未知选项 $arg" >&2; usage >&2; exit 2 ;;
    esac
done

DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================================"
echo " 正在编译创维专用 Release APK (com.coocaa.tvhome)..."
echo "========================================================"
cd "$DIR/launcher"
BUILD_TASKS=(assembleRelease)
if [ "$CLEAN_BUILD" -eq 1 ]; then
    BUILD_TASKS=(clean assembleRelease)
fi
./gradlew --no-daemon -PlauncherApplicationId=com.coocaa.tvhome "${BUILD_TASKS[@]}"

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

if [ "$PRECOMPILE" -eq 1 ]; then
    echo "2. 执行 AOT Speed 模式预编译..."
    adb shell cmd package compile -m speed -f com.coocaa.tvhome
fi

echo "刷新并唤起主页桌面..."
adb shell am force-stop com.coocaa.tvhome 2>/dev/null || true
adb shell input keyevent 3

echo "========================================================"
echo " 一键编译并安装更新完成！"
echo "========================================================"
