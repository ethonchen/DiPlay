# HHQ Q7 Android 6 — 上游性能与稳定性更新（update2-fix1）

版本 `0.2.21-android6-q7-update2-fix1-slim`，versionCode **51**。

合入 shihabal3amri/DiPlay 截至 2026-10-27 的主干
`a2dc2e70b3ff24e9e18e56db71cde776b30a4eec`，相对 update1 新增 87 个上游提交，
包含 22 个改动 PR 及发布说明。上游尚未发布 0.2.22。

- 日志在后台写入，减少界面和网络线程等待磁盘；触控不再生成未使用的协议十六进制日志。
- 慢连接时合并排队的同手指滑动，保留最新位置；按下、抬起和手指数变化仍按顺序发送。
- 为 CarPlay 控制请求、iAP、Bonjour、二进制 plist 和 USB 网络解析增加内存及长度限制。
- 加固设置导入、外部启动参数、USB 权限广播、诊断脱敏及更新附件校验；下载最大为 64 MiB。
- 现有 Wi-Fi / 同一局域网模式可选填外置网卡接口，默认继续自动选择；新增韩语。
- 保留 Android 6 / API 23、ARMv7、Q7 热点恢复、2.4 GHz 过滤、导航压低音乐和已验证的 FYT 方向盘路径。
- 保留 fork 更新源、现代 CA 补充和 `gh-proxy.com → ghproxy.net → GitHub` 换源。

## Android 6 安装器修正

真实 update1 已验证可以发现、下载并校验 update2；但标准 Android 6 安装器只接受 `file://`，
update1 和首版 update2 使用 `content://`，点击安装会报 `ActivityNotFoundException`。
本修正版改为在后台从已验证的私有 APK 重建外部副本，核对内容后用 Android 6 支持的文件 URI 打开安装器。
Android 7 及以后继续使用授权的内容 URI；不放开私有缓存目录的权限。

构建号递增到 51，原 update2 / code 50 的已发布附件保持不变。

## 从 update1 应用内升级

在 **设置 → 关于 → 检查更新** 中找到 update2，选择 **下载更新**，校验后选择 **安装**，
在 Android 安装器确认覆盖安装。完成后版本应为 `0.2.21-android6-q7-update2-fix1-slim`（51）。
需要能访问互联网的网络；车机热点本身不提供互联网。

旧 update1 的安装逻辑无法由服务端修改。如果点击安装仍报错，请用车机文件管理器打开：
`/storage/emulated/0/Android/data/com.shihab.diplay.hudtest/files/update/DiPlay-Q7-0.2.21-update2-fix1.apk`
然后确认覆盖安装即可；下载、哈希及签名校验已由 update1 完成。无需卸载。
修正版修复的是之后从本版本发起安装的 Android 6 兼容性。


**沿用 update1 的签名和包名，可覆盖安装并保留设置；无需卸载。**
本次不上传私有签名密钥到 GitHub；使用原私有备份离线签名，再核对所有 APK 运行内容与 CI 优化产物一致。
签名证书 SHA-256 为 `42dca8935a4e53f385388e4ed3058edc4e240107c057723a21574dde9d5a2928`。

发布前须通过完整单元测试、Lint、API-23 检查、优化构建，以及最终签名 APK 的 Android 6 安装、
从真实 update1 同签名覆盖且保留数据、实际安装字节比对和运行检查。
发布后再用真实 update1 验证发现、下载、校验及必要时通过文件管理器交给系统安装器覆盖安装。
新增上游改动尚未经过 Q7 实车确认，性能收益仍需实际使用反馈。

后续版本继续提高 versionCode 并沿用此签名。发布 APK、`SHA256SUMS.txt` 和 `update.json`；
已发布 tag 的附件不得替换成其他构建。
