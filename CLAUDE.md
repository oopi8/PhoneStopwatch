# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

Android 悬浮窗计时 App。小米 13 每次解锁后，在摄像头右侧显示已用时间（MM:SS），锁屏后消失并重置。

- **实际代码在 Windows**：`C:\Users\liwentao\AndroidStudioProjects\PhoneStopwatch\`
- **Package name**：`com.Mitchell.phonestopwatch`
- **语言**：Kotlin，minSdk 26，targetSdk 34，Jetpack Compose
- **目标设备**：小米 13（Android 13/14，MIUI/HyperOS）

## 当前状态（2026-03-20）

### 最紧急：构建失败
Gradle 下载 aapt2 时 Connection reset。代理配置：

**gradle.properties 当前值（正确的）：**
```
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
systemProp.http.nonProxyHosts=localhost|127.0.0.1
```

Android Studio HTTP Proxy 设为 **No proxy**（避免双重代理）。
Clash 代理软件需要在运行中，HTTP 端口 7890。

如果构建仍然失败，尝试：
1. 确认 Clash 正在运行且已连接节点
2. 在 Windows PowerShell 测试：`curl -x http://127.0.0.1:7890 https://dl.google.com`
3. 备选方案：手动下载 aapt2 放到 Gradle 缓存

### 下一步：悬浮窗定位
构建通过后，需要把悬浮窗放到状态栏摄像头右侧。

当前代码（StopwatchService.kt）中 `finalY = 500` 是测试值，需要改为负值进入状态栏：
- 目标 y ≈ `-(statusBarHeight - textHeight) / 2`（状态栏垂直居中）
- 目标 x ≈ `screenWidth/2 + 22dp`（摄像头右侧）
- Logcat 实测：statusBarHeight=104px，screenWidth=1080，density=2.75

注意：MIUI 可能限制 TYPE_APPLICATION_OVERLAY 进入状态栏，如果负值不生效需另想办法。

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
