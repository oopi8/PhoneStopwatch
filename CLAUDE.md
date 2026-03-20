# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

Android 悬浮窗计时 App。小米 13 每次解锁后，在摄像头右侧显示已用时间（MM:SS），锁屏后消失并重置。

- **实际代码在 Windows**：`C:\Users\liwentao\AndroidStudioProjects\PhoneStopwatch\`
- **Package name**：`com.Mitchell.phonestopwatch`
- **语言**：Kotlin，minSdk 26，targetSdk 34，Jetpack Compose
- **目标设备**：小米 13（Android 13/14，MIUI/HyperOS）

## 当前状态（2026-03-20）

### 已完成 ✅
- 构建正常：**gradle.properties 不需要任何代理配置**，Java 可直接访问 dl.google.com
- 悬浮窗竖屏：负 Y 值进入状态栏，公式 `-(statusBarHeight - textSizePx) / 2 - 60`
- 悬浮窗横屏：监听 `onConfigurationChanged`，横屏时显示在右上角屏幕内

### 构建方式
除 Android Studio 外，也可直接命令行构建：
```
cd C:\Users\liwentao\AndroidStudioProjects\PhoneStopwatch
C:\Users\liwentao\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 构建与运行

在 Windows Android Studio 中操作：

```
# 运行到设备
Run → Run 'app'（Shift+F10）

# 查看日志
Logcat → 过滤 tag: PhoneStopwatch

# ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 架构

```
MainActivity        # 启动时申请 SYSTEM_ALERT_WINDOW 权限，启动 Service
StopwatchService    # ForegroundService，核心逻辑：
                    #   - onCreate: 动态注册 ScreenReceiver，创建 WindowManager 悬浮窗
                    #   - ACTION_USER_PRESENT → 显示悬浮窗，从 0:00 开始每 5 秒 tick
                    #   - ACTION_SCREEN_OFF   → 隐藏悬浮窗，取消 tick，重置
BootReceiver        # 静态注册 RECEIVE_BOOT_COMPLETED，开机拉起 StopwatchService
```

## 悬浮窗显示

- 自定义 `OutlinedTextView`：白色填充 + 黑色描边（Canvas drawText 两次）
- 文字大小：14sp
- 刷新：每 5 秒（TICK_MS=5000）
- 格式：`M:SS`（例如 0:00, 1:23, 10:05）

## 坐标系（重要）

```
gravity = Gravity.TOP or Gravity.START
FLAG_LAYOUT_NO_LIMITS  ← 允许超出系统边界
y = 0 是状态栏底部，负值才能进入状态栏区域
```

## 关键权限

```xml
SYSTEM_ALERT_WINDOW      <!-- 悬浮窗，需用户手动授权 -->
FOREGROUND_SERVICE
FOREGROUND_SERVICE_SPECIAL_USE
RECEIVE_BOOT_COMPLETED
```

`SYSTEM_ALERT_WINDOW` 在 MIUI 路径：设置 → 应用设置 → 授权管理 → 显示在其他应用上层

## MIUI 注意事项

- 安装后必须在省电管理中将 App 设为"无限制"，否则后台会被杀
- `ACTION_USER_PRESENT` / `ACTION_SCREEN_OFF` 不能静态注册，必须在 Service 中动态注册
- Android 14 需声明 `foregroundServiceType="specialUse"`
