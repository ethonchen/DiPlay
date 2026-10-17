# HHQ Q7 / Android 6 — 方向盘媒体按键修复

版本：`0.2.20-android6-q7-wheel1-slim`，versionCode：44。
APK：`DiPlay-Q7-0.2.20-wheel1.apk`。

- 修复 Android 6/7 媒体会话缺少 `FLAG_HANDLES_MEDIA_BUTTONS` 和
  `FLAG_HANDLES_TRANSPORT_CONTROLS`，导致系统不向 DiPlay 分发方向盘上一首、下一首和播放/暂停键的问题。
  这两个标志从 API 26 起不再需要，旧版 API 23 仍必须显式声明。
- 保留原有单次按下转发、忽略长按重复及松开事件的处理，并在禁用音频焦点时继续注册媒体按键。
- 导出日志中的媒体会话启动记录增加 `flags=3`，按键转发结果仍记录 `sent=true/false`。
- 保留 0.2.20 上游更新、Q7 热点恢复、2.4 GHz Wi-Fi、音乐播放和导航播报压低音乐修复。

包名仍为 `com.shihab.diplay.hudtest`，使用原 Q7 测试签名，可以直接覆盖安装并保留设置。
无需为这次标准媒体按键修复开启 ADB 或无障碍服务。

发布前检查包括完整单元测试、Lint、普通及优化 APK 构建、API 23 / ARMv7 / 原签名校验、
Android 6 模拟器安装和运行检查，以及真实 Android 6 媒体按键路由对照实验。
该实验验证 flags=0 收不到系统按键，flags=3 能分别收到一次下一首、上一首和播放/暂停。

实车方向盘经 FYT 固件送达 Android 的路径仍需装车验证。旧日志未记录到用户按下的几次下一首事件，
因此不能仅凭旧日志排除 FYT 专有按键路径；本版先修复已确认的标准 Android 路由缺陷。
安装后连接 CarPlay 并播放音乐，再测试上一首、下一首；如仍无响应，按键后立即导出本版日志。
