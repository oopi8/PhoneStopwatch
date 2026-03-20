# PhoneStopwatch

小米 13 解锁后，在状态栏摄像头右侧显示本次解锁已用时间（格式 `M:SS`），锁屏后自动消失并重置。横屏播放视频时，计时器显示在右上角。

## 效果

- 竖屏：时间显示在状态栏，摄像头右侧
- 横屏：时间显示在右上角屏幕内
- 锁屏：计时器消失，下次解锁从 0:00 重新计时
- 开机自启：无需手动打开 App

## 环境要求

| 项目 | 要求 |
|------|------|
| 目标设备 | 小米 13（MIUI / HyperOS） |
| Android | 最低 8.0（minSdk 26） |
| 语言 | Kotlin |
| UI | Jetpack Compose |
| compileSdk | 34 |

## 项目结构

```
app/src/main/java/com/Mitchell/phonestopwatch/
├── MainActivity.kt       # 权限申请页面，启动 Service 后自动退出
├── StopwatchService.kt   # 核心逻辑：悬浮窗 + 计时 + 屏幕事件监听
└── BootReceiver.kt       # 开机广播，自动拉起 Service
```

### 核心逻辑（StopwatchService）

```
onCreate
  ├── 注册 ScreenReceiver（动态）
  └── 创建 OutlinedTextView（白字黑边）

ACTION_USER_PRESENT（解锁）
  ├── 记录解锁时间
  ├── 显示悬浮窗
  └── 每 5 秒刷新一次时间

ACTION_SCREEN_OFF（锁屏）
  ├── 隐藏悬浮窗
  └── 重置计时

onConfigurationChanged（横竖屏切换）
  └── 重新计算并更新悬浮窗位置
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示，需用户手动授权 |
| `FOREGROUND_SERVICE` | 保持后台服务运行 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14 要求 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `POST_NOTIFICATIONS` | Android 13+ 前台服务通知 |

## 安装与使用

### 方式一：直接安装 APK

从 [Releases](https://github.com/oopi8/PhoneStopwatch/releases) 下载最新 APK 安装。

### 方式二：本地构建

```bash
# 构建
gradlew assembleDebug

# 安装到已连接设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 注：无需配置代理，Gradle 可直连下载依赖。

### 首次使用授权（MIUI）

1. 打开 App，按提示授权**悬浮窗权限**
   - 路径：设置 → 应用设置 → 授权管理 → 应用权限 → PhoneStopwatch → 显示在其他应用上层 → 允许
2. 授权**通知权限**（Android 13+）
3. 前往**省电管理**，将 PhoneStopwatch 设为「无限制」，防止后台被杀

授权完成后 App 自动退出，锁屏再解锁即可看到计时器。

## 技术细节

### 悬浮窗坐标系

```
gravity = Gravity.TOP | Gravity.START
FLAG_LAYOUT_NO_LIMITS  // 允许超出系统边界

竖屏：y = 负值 → 进入状态栏区域（y=0 为状态栏底部）
横屏：y = 正值 → 显示在屏幕右上角内
```

### 小米13 实测数据

| 参数 | 数值 |
|------|------|
| 屏幕宽度 | 1080 px |
| 状态栏高度 | 104 px |
| 屏幕密度 | 2.75 |

### 文字样式

- 字号：14sp
- 样式：白色填充 + 黑色描边（Canvas 绘制两次）
- 刷新间隔：5 秒

## MIUI 注意事项

- `ACTION_USER_PRESENT` / `ACTION_SCREEN_OFF` 必须在 Service 中**动态注册**，静态注册无效
- Android 14 需声明 `foregroundServiceType="specialUse"`
- 省电管理设为「无限制」是保持后台运行的关键

## License

MIT
