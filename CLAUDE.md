# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

Android 悬浮窗计时 App。小米 13 每次解锁后，在摄像头右侧显示已用时间（M:SS），锁屏后消失并重置。横屏时显示在右上角。

- **Package name**：`com.Mitchell.phonestopwatch`
- **语言**：Kotlin，minSdk 26，targetSdk 34，Jetpack Compose
- **目标设备**：小米 13（Android 13/14，MIUI/HyperOS）

## 当前状态（2026-03-20）✅ 已完成

- 构建正常：gradle.properties 无需代理配置，Java 可直连 dl.google.com
- 悬浮窗竖屏：负 Y 值进入状态栏，公式 `-(statusBarHeight - textSizePx) / 2 - 60`
- 悬浮窗横屏：监听 `onConfigurationChanged`，横屏时显示在右上角屏幕内
- 签名 APK：使用 `phonestopwatch.jks`（已 .gitignore，本地保存）
- GitHub 同步：main 分支，v1.0.0 Release 已发布

## 构建与运行

```bash
# 命令行构建（无需 Android Studio）
C:\Users\liwentao\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat assembleDebug

# 安装到设备
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Release 构建（自动签名）
gradle.bat assembleRelease
# APK：app\build\outputs\apk\release\app-release.apk
```

## 架构

```
MainActivity        # 申请权限（悬浮窗 + 通知），启动 Service 后自动退出
StopwatchService    # ForegroundService，核心逻辑：
                    #   - ACTION_USER_PRESENT → 显示悬浮窗，从 0:00 开始每 5 秒 tick
                    #   - ACTION_SCREEN_OFF   → 隐藏悬浮窗，取消 tick，重置
                    #   - onConfigurationChanged → 横竖屏切换时更新悬浮窗位置
BootReceiver        # 开机广播，自动拉起 StopwatchService
```

## 坐标系

```
gravity = Gravity.TOP or Gravity.START
FLAG_LAYOUT_NO_LIMITS  ← 允许超出系统边界

竖屏：finalY = -(statusBarHeight - textSizePx) / 2 - 60（进入状态栏）
横屏：finalX = screenWidth - offsetPx * 3
      finalY = textSizePx / 2（右上角屏幕内）
```

## 小米 13 实测数据

| 参数 | 数值 |
|------|------|
| 屏幕宽度 | 1080 px |
| 状态栏高度 | 104 px |
| 屏幕密度 | 2.75 |

## 关键权限

```xml
SYSTEM_ALERT_WINDOW            <!-- 悬浮窗，需用户手动授权 -->
FOREGROUND_SERVICE
FOREGROUND_SERVICE_SPECIAL_USE <!-- Android 14 -->
RECEIVE_BOOT_COMPLETED
POST_NOTIFICATIONS             <!-- Android 13+ -->
```

MIUI 授权路径：设置 → 应用设置 → 授权管理 → 应用权限 → PhoneStopwatch → 显示在其他应用上层

## MIUI 注意事项

- 省电管理必须设为「无限制」，否则后台会被杀
- `ACTION_USER_PRESENT` / `ACTION_SCREEN_OFF` 必须动态注册（不能静态）
- Android 14 需声明 `foregroundServiceType="specialUse"`

## Git / GitHub

- 仓库：`git@github.com:oopi8/PhoneStopwatch.git`
- 默认分支：main
- 签名密钥：`phonestopwatch.jks`（本地，已加入 .gitignore，密码 phonestopwatch123）
- 日常推送：`git add . && git commit -m "..." && git push`
