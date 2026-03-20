# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

Android 悬浮窗计时 App。小米 13 每次解锁后，在摄像头右侧显示已用分钟数（0、1、2…），锁屏后消失并重置。

- **实际代码在 Windows**：`C:\Users\liwentao\AndroidStudioProjects\PhoneStopwatch\`
- **Package name**：`com.Mitchell.phonestopwatch`
- **语言**：Kotlin，minSdk 24，targetSdk 34，Jetpack Compose
- **目标设备**：小米 13（Android 13/14，MIUI/HyperOS）

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
                    #   - ACTION_USER_PRESENT → 显示悬浮窗，从 0 开始每分钟 tick
                    #   - ACTION_SCREEN_OFF   → 隐藏悬浮窗，取消 tick，重置
BootReceiver        # 静态注册 RECEIVE_BOOT_COMPLETED，开机拉起 StopwatchService
```

## 悬浮窗定位

小米 13 摄像头在屏幕顶部中央，悬浮窗放在摄像头右侧：

```kotlin
WindowManager.LayoutParams(
    WRAP_CONTENT, WRAP_CONTENT,
    TYPE_APPLICATION_OVERLAY,
    FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = screenWidth / 2 + 20.dp   // 摄像头右侧
    y = statusBarHeight / 2        // 状态栏垂直居中
}
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
