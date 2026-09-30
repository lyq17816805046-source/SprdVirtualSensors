# SprdVirtualSensors（展锐 T760 专用）

仅适配展锐 T760（中兴远航 30S，MyOS，Android 11）的私有虚拟传感器监听工具。应用启动后由前台服务常驻，自动遍历系统传感器并匹配两个展锐私有虚拟传感器：Elevator 与 Media Flip (WAKE_UP)。一旦捕获事件，会在页面日志显示并同时弹出系统通知。

## 功能特性

- 自动遍历并匹配展锐私有虚拟传感器：
  - Elevator（电梯场景）
  - Media Flip (WAKE_UP)（翻转唤醒）
- 事件监听与通知：
  - Elevator：values[0]=1 表示进入电梯；values[0]=2 表示离开电梯
  - Media Flip：捕获翻转动作即记录日志并通知
- 前台服务运行，尽量降低 MyOS 后台清理概率
- 最小 UI：仅一个文本日志框，实时打印监听与匹配信息
- 完整 AndroidManifest 与 Gradle 配置，Kotlin 实现

## 目标设备/系统

- 芯片：Unisoc（展锐）T760
- 机型：中兴远航 30S（MyOS）
- 系统：Android 11（API 30）
- 仅此设备适配，其他设备不保证可用

## 权限说明

- 前台服务：android.permission.FOREGROUND_SERVICE（Android 11）
- 通知权限：android.permission.POST_NOTIFICATIONS（Android 13+ 运行时权限）
- 无需定位、存储、相机等敏感权限

## 事件规则

- Elevator
  - values[0] = 1：进入电梯
  - values[0] = 2：离开电梯
- Media Flip (WAKE_UP)
  - 捕获翻转动作即视为一次事件，values[] 会原样打印到日志

## 构建与安装

1) 环境
- Android Studio（推荐）或命令行 Gradle
- JDK 17
- 设备开启开发者选项与 USB 调试

2) 构建
```bash
./gradlew assembleDebug
```

3) 安装
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用步骤

- 首次启动应用后，会请求通知权限（Android 13+）
- 应用会自动启动前台服务并遍历所有传感器
- 日志区域会打印出系统传感器列表及匹配结果
- 匹配到 Elevator / Media Flip 后将注册监听
- 当事件发生：页面日志打印详细信息，同步弹出系统通知

## MyOS 后台保活建议

- 将本应用加入自启动/后台保护白名单
- 关闭或忽略电池优化（允许后台运行）
- 保留前台通知（不要手动关闭）

## 已知限制

- 仅通过传感器名称与厂商关键词（Unisoc/Spreadtrum/sprd）匹配，若 ROM 变更了名字可能匹配失败
- 不适配其他 SoC/机型
- Media Flip 的 values[] 可能因 ROM 版本不同而有差异，日志会原样打印用于排查

## 问题反馈

- 若匹配失败，请将应用首页打印的“系统传感器列表”（名称、厂商、类型、是否唤醒）贴出
- 我会根据你的日志更新匹配规则

## 许可证

MIT License
