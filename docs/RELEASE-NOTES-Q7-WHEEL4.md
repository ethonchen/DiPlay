# HHQ Q7 Android 6 — 方向盘切歌实车验证版（wheel4）

- 修复 FYT6025 / Android 6 方向盘上一首、下一首不响应：补齐旧式媒体按钮接收、AudioManager / RemoteControlClient 注册和音乐命令广播入口。
- 拦截同一次按键的重复投递，过滤抬键和长按重复事件，避免按一下切两首。
- 会话结束时注销接收器，保留厂商按键通道诊断。
- 沿用此前的热点恢复、音频和导航播报压低音乐修复。

版本：`0.2.20-android6-q7-wheel4-slim`（versionCode 47）。沿用原包名与签名，可以覆盖安装。
无需开启无障碍服务或新增设置；连接 CarPlay、播放音乐后即可使用方向盘切歌。

2026-10-17 已由 HHQ Q7 实车确认可正常上下切歌。日志记录 3 次下一首、2 次上一首成功发送，5 次重复回调均被拦截。
当前会话无崩溃、音频写入错误或重缓冲；少量瞬时无线丢包暂未构成发布阻碍。

本 release 发布与车主实测完全相同的 APK，不重新打包。已安装 wheel4 的用户无需再次安装。

验证：完整单元测试、lint、源码构建、Android 6 安装启动、媒体按键路由和最终 APK 广播接收检查均通过。

实测 APK 对应源码：`e960c1111cc51eafb7f581f7f7bee28969f10a05`。
构建与检查：https://github.com/ethonchen/DiPlay/actions/runs/39018203927

APK SHA-256：`f173c80dace9c27c578247bd724a386cd81629da6e21ed641a3eabec2bae03d2`。
