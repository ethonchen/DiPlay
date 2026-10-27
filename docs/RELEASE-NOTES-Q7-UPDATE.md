# HHQ Q7 Android 6 — 上游合并与应用内更新

版本 `0.2.21-android6-q7-update1-slim`，versionCode **49**。

合入 shihabal3amri/DiPlay 截至 2026-10-24 的最新主干
`9d89633035d1add5b044ba3de5e57dd078bf2511`（上次同步后新增 167 个提交）。
上游目前仍标记 0.2.21，本包是 Q7 分支修订，不代表上游发布了 0.2.22。

- 合入音乐时钟和缓冲修正、无线发现时的慢速 DNS 修复、无线及 USB 重连、开机返回已运行 CarPlay、多指触控、通话音频恢复、连接故障提示及上游其他更新。
- 保留 API 23 / ARMv7、原包名、原签名、Q7 热点配置恢复、2.4 GHz 适配、导航压低音乐和实车验证的 FYT 方向盘切歌。
- 应用内更新仅检查 **ethonchen/DiPlay** 的正式 Release，跳过草稿、预发布、缺少附件的条目，并优先选择 Q7 APK。
- 下载顺序为 `https://gh-proxy.com/https://github.com/...` → `https://ghproxy.net/https://github.com/...` → GitHub 原始地址。
- 连接超时、HTTP 错误、下载截断、代理返回 HTML、文件哈希或 APK 身份校验失败均会自动切换来源；失败下载不会留下可安装的残包。
- 新发布包含 `update.json`，使用 APK 的真实 versionCode 识别同一上游版本下的升级；GitHub API 不通时，还可直接通过代理读取最新 Release 的该附件。
- 安装前检查 SHA-256、包名、签名及更高构建号，下载后点击安装，由 Android 安装器确认覆盖安装。

首次请手动覆盖安装本包。之后在 **设置 → 关于 → 检查更新** 中下载、安装。
车机须有可以访问互联网的网络；车机本地热点本身不提供互联网。

新增上游功能尚未经过 Q7 实车验证。发布流水线执行单元测试、Lint、API 23 兼容检查、优化构建、原签名校验和 Android 6 模拟器检查，检查全部通过才发布。

## 后续发布约定

1. 递增 `mobile/build.gradle.kts` 的 **versionCode**，并修改唯一的 versionName，例如 `0.2.21-android6-q7-update2` / code 50。
2. 更新本发布说明，合并到 `main`。Q7 流水线会构建、验证、发布 APK、`SHA256SUMS.txt` 和 `update.json`，不需要手工维护 JSON。
3. 使用正式 Release；实验包标记 prerelease，应用会忽略。沿用现有包名及签名缓存，禁止更换签名密钥。
4. 手动发布时，上传 `scripts/prepare_q7_release.py` 对已验证 APK 生成的三个文件；只上传 APK 时可通过 GitHub 自动生成的 digest 兼容检测更高三段版本号，同三段版本的更新应始终附上 `update.json`。

同一个 tag 的附件不可替换为不同构建；需要修改时递增 versionCode 并发布新 tag。
