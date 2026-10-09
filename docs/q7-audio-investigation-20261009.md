# HHQ Q7 Android 6 音频断续验证（2026-10-09）

PR #2 已合并，合并提交为 `3cc96956fd7697a2e85d06592561aac705e2c81c`。
[Release](https://github.com/ethonchen/DiPlay/releases/tag/v0.2.14-android6-q7-slim) 发布的是车主早上安装的同一 APK，没有重新编译：5,825,334 字节，SHA-256 `b9a746bcfb218043f629508430421db5664e827fc77e5b6397e968418424a82d`。

## 实验条件与范围

车主之前的车机照片明确写有 Cortex-A7 四核、Mali-400 MP2、2G DDR、32G ROM、Android 6.0。旧诊断报告记录屏幕为 1024×600。照片没有提供 CPU 主频。

| 项目 | 对应资源配置的场景 | 受限资源对照 |
| --- | --- | --- |
| Android | 6 / API 23 | 6 / API 23 |
| 虚拟 CPU 核心数（进程实测） | 4 | 2 |
| AVD 配置 RAM | 2048 MB | 1024 MB |
| Guest MemTotal（实测） | 2,050,332 kB | 1,019,900 kB |
| 逻辑显示尺寸 | 1024×600 | 1024×600 |
| ABI | x86_64 | x86_64 |
| 图形选项 | SwiftShader | SwiftShader |

这是 Android 6 和资源限制的实验，不能等同于 Cortex-A7/Mali-400 车机的性能实测。虚拟 CPU 为 Intel Android virtual processor；没有复现 FYT/展讯 Wi-Fi、音频 HAL、硬件视频编解码器、热降频或实际行车记录仪。宿主音频输出关闭，依据播放头和重缓冲计数判断，没有进行扬声器听测。

先对哈希完全一致的发布 APK 执行安装和 Settings 启动检查。音频实验使用同一生产源码、额外增加命令行入口并经过 R8 优化的独立测试 APK；测试 APK 与发布 APK 不同，未发布或替换 Release 文件。

实际链路为：AAC-LC 48 kHz 双声道音频 → ChaCha20-Poly1305 加密 RTP/UDP 回环发送 → 生产 AudioStream 接收和解密 → AndroidMediaSink → MediaCodec → AudioTrack。每个场景 24 秒。CPU/磁盘场景包含三个 SHA-256 工作线程（20 ms 工作、20 ms 等待）及约 4 MiB/s 的循环文件写入/fsync，用于制造竞争，不代表真实记录仪。

## 实测结果

两组配置的重缓冲结果一致。表中的延迟/丢包场景各注入两次事件。

| 场景 | 音乐缓冲 | 4 核/2 GB 重缓冲 | 2 核/1 GB 重缓冲 |
| --- | --- | --- | --- |
| 正常实时发送 | 300 ms | 0 | 0 |
| CPU 与磁盘竞争 | 300 ms | 0 | 0 |
| 延迟 650 ms，随后补发全部包 | 300 ms | 1 | 1 |
| 延迟 650 ms，随后补发全部包 | 1000 ms | 0 | 0 |
| 延迟 1250 ms，随后补发全部包 | 1000 ms | 1 | 1 |
| 每次主动丢弃 30 包（约 640 ms） | 1000 ms | 1 | 1 |

两组共接收并认证 13,380 个音频包，认证错误、接收后队列丢包、解码输入丢包和 AudioTrack 写入错误均为 0；每个场景播放头均前进超过 100 万帧。丢包场景每组少发送了 60 包，接收数量与实际发送数量一致；这些缺失的音频不会被音乐缓冲恢复。

正常播放场景也出现约 250 ms 的单次阻塞写入，1000 ms 缓冲场景出现约 600 ms 的阻塞写入，同时没有写入错误。因此 `maxWriteMs` 本身不能当作 CPU 解码耗时或性能不足的证据。

这些是短时实验，不能排除真实车机的持续负载、视频与音频并行工作、无线干扰、驱动或温度问题。

## 与旧日志对照

2026-10-08 晚上的诊断报告来自旧 `0.2.14-android6-q7-hud-test` 版本，不是早上安装的瘦身版。

旧日志记录音频最大收包间隔 1244 ms，一次连续缺 40 包；48 kHz AAC 每包 1024 帧，40 包约对应 853 ms 的音频。该报告接收统计窗口累计记录 195 个缺失序号，音频写入错误和渲染队列丢包均为 0，单个流重缓冲累计到 4 次。记录的音乐起播缓冲为 300 ms，音频轨容量为 500 ms。

更直接的异常是到包不均匀和缺包。CPU 调度、内核接收队列、无线链路或发送端均可能参与，现有数据不能确定是 CPU 性能问题，也不能确定是行车记录仪导致。

## 建议的车机验证

1. 在“音频 → 音乐缓冲”选择“1000 毫秒 · 更稳定”，断开 CarPlay 后重新连接使设置生效。该设置只作用于音乐流，增加缓冲也会增加音乐起播/操作响应延迟。
2. 保持其他设置一致，对比行车记录仪运行与停止时的表现，分别导出当前瘦身版断音期间的日志。
3. 记录行车记录仪使用 USB 还是 Wi-Fi 接入，便于区分编码/磁盘竞争和无线带宽竞争。

## 可复查证据

- [PR #2](https://github.com/ethonchen/DiPlay/pull/2)
- [精确 APK 发布与哈希校验](https://github.com/ethonchen/DiPlay/actions/runs/37876377359)
- [源码回归检查、优化测试驱动构建与 2 核/1 GB 实验](https://github.com/ethonchen/DiPlay/actions/runs/37876597044)
- [4 核/2 GB 实验](https://github.com/ethonchen/DiPlay/actions/runs/37877466544)
- [测试驱动源码](https://github.com/ethonchen/DiPlay/blob/6147ee17c093638387ce63c8bc191bfdef1e2c7a/scripts/probes/Q7AudioProbe.kt)

每次模拟器任务的 evidence artifact 包含 probe.txt、AVD 配置、CPU/内存信息、logcat 与 AudioFlinger 快照。两组原始 RESULT/ENV 摘要同时保存在本目录 JSON 文件中。
