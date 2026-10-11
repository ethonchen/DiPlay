"""Publish only the two explicitly requested, checksum-pinned Q7 test packages."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from urllib.error import HTTPError
from urllib.request import Request, urlopen

REPO = "ethonchen/DiPlay"
SPECS = [
    {
        "version": "hotspot1", "run": 38117272258,
        "sha": "f66d2abfe5e10ffbeaac09dcf9d07d03435b90b3",
        "artifact": 11694142538,
        "artifact_digest": "sha256:7f225571a64134c552d865a22b336cbca077b8f4b9c36979c9ea6ee91cf31deb",
        "apk_sha256": "d5b637a7b8796453a15694c801f1f2b714ee8b36c189f997c828082f9f31ea61",
        "notes": "适用于 HHQ Q7 / Android 6 / 仅支持 2.4 GHz Wi-Fi 的车机。\n\n- 修复 Android 6 车载热点模式的 Bonjour 服务发布，优先使用 IPv4，并处理 mDNS 端口占用。\n- 检测到车机不支持 5 GHz 后，跳过 5 GHz 信道请求，包括保存过的信道配置。\n- 包含先前的音乐播放稳定性修复，保留用户设置的音乐缓冲。\n- 用户实车反馈：车载热点可连接，音乐连续播放恢复正常。\n- 本版尚未加入导航播报自动压低音乐；需要此功能请使用 navduck1。\n\n安装：原签名同包名，可覆盖已有 Q7 测试版。versionCode 39；最低 Android 6。\n验证：完整单元测试、lint、构建、APK 原签名/兼容性检查和 Android 6 安装、启动及配对加密运行检查通过。\n构建： https://github.com/ethonchen/DiPlay/actions/runs/38117272258\n源代码： f66d2abfe5e10ffbeaac09dcf9d07d03435b90b3\nAPK SHA-256： d5b637a7b8796453a15694c801f1f2b714ee8b36c189f997c828082f9f31ea61\n\n从更高 versionCode 的 navduck1 回退到本版时，Android 默认不允许直接覆盖降级。请优先安装新包；不要为回退贸然卸载以免丢失设置。\n",
    },
    {
        "version": "navduck1", "run": 38123735905,
        "sha": "12e067407753eef46ab74c497c5707a7c0d7a9bb",
        "artifact": 11696820583,
        "artifact_digest": "sha256:4dcbec64ad798389c100ca9da4bc4aae74b6005b543b6404aedfff739cafee0d",
        "apk_sha256": "97623fec0985d67db5b9c3ada56e21688142bc2616a025ee5bfd3921ec753330",
        "notes": "适用于 HHQ Q7 / Android 6 / 仅支持 2.4 GHz Wi-Fi 的车机。\n\n- 包含 hotspot1 的全部热点连接和音乐播放修复。\n- 默认启用 CarPlay 导航播报压低音乐：收到可听导航 PCM 后，将音乐平滑降低到约 25%，播报播放完后平滑恢复。\n- 支持 iPhone 的 duckAudio / unduckAudio 指令，按手机指定的 dB 和淡入淡出时间调整音乐。\n- 导航静音流不会持续压低音乐；重叠播报、会话断开和过期指令有恢复保护。\n- 仅调整 DiPlay 音乐轨道的相对音量；保留车机音量设置，并与系统临时静音协调。\n- 自动压低音乐不依赖“音频焦点”开关，无需另开设置。\n\n安装：原签名同包名，可直接覆盖 hotspot1，versionCode 40；最低 Android 6。\n验证：完整单元测试、lint、构建、APK 原签名/兼容性检查和 Android 6 安装、启动及配对加密运行检查通过。\n导航音量逻辑已在 Android 6 和 Android 9 单元环境验证；实车听感待验证。\n请在车辆停稳时同时播放音乐并触发一次 CarPlay 导航播报，检查播报期间音乐降低、结束后恢复。若未生效，请导出本版日志。\n构建： https://github.com/ethonchen/DiPlay/actions/runs/38123735905\n源代码： 12e067407753eef46ab74c497c5707a7c0d7a9bb\nAPK SHA-256： 97623fec0985d67db5b9c3ada56e21688142bc2616a025ee5bfd3921ec753330\n",
    },
]


def api(path, optional=False):
    request = Request("https://api.github.com/repos/" + REPO + "/" + path,
                      headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                               "Accept": "application/vnd.github+json",
                               "X-GitHub-Api-Version": "2022-11-28"})
    try:
        with urlopen(request, timeout=60) as response:
            return json.load(response)
    except HTTPError as error:
        if optional and error.code == 404:
            return None
        raise


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def release_matches(spec, release):
    require(not release["draft"] and release["prerelease"], "Existing release is not the expected published test release")
    require(release["target_commitish"] == spec["sha"], "Release source SHA mismatch")
    require(release["body"].strip() == spec["notes"].strip(), "Release notes mismatch")
    ref = api("git/ref/tags/" + spec["tag"])
    require(ref["object"]["type"] == "commit" and ref["object"]["sha"] == spec["sha"], "Release tag SHA mismatch")
    assets = {asset["name"]: asset for asset in release["assets"]}
    require(set(assets) == {spec["filename"], "SHA256SUMS.txt"}, "Release asset names mismatch")
    checksums_sha = hashlib.sha256(spec["checksums"].encode("utf-8")).hexdigest()
    for name, digest in ((spec["filename"], spec["apk_sha256"]), ("SHA256SUMS.txt", checksums_sha)):
        require(assets[name]["state"] == "uploaded" and assets[name]["digest"] == "sha256:" + digest,
                "Published asset digest mismatch: " + name)


def main():
    require(os.environ.get("GITHUB_REPOSITORY") == REPO, "Wrong repository")
    require(os.environ.get("GITHUB_REF") == "refs/heads/publish/q7-tested-20261011", "Wrong publishing branch")
    # Prepare and verify both packages before making either release visible.
    for spec in SPECS:
        run = api("actions/runs/" + str(spec["run"]))
        require(run["head_sha"] == spec["sha"] and run["conclusion"] == "success" and run["status"] == "completed",
                "Source build / Android 6 smoke checks have not passed")
        require(run["path"] == ".github/workflows/q7-slim.yml", "Unexpected build workflow")
        artifact = api("actions/artifacts/" + str(spec["artifact"]))
        require(not artifact["expired"] and artifact["name"] == "DiPlay-Q7-slim-standalone"
                and artifact["digest"] == spec["artifact_digest"]
                and artifact["workflow_run"]["id"] == spec["run"]
                and artifact["workflow_run"]["head_sha"] == spec["sha"], "Artifact provenance mismatch")
        folder = Path("release-files") / spec["version"]
        subprocess.run(["gh", "run", "download", str(spec["run"]), "--repo", REPO,
                        "--name", "DiPlay-Q7-slim-standalone", "--dir", str(folder)], check=True)
        apks = list(folder.rglob("mobile-q7.apk"))
        require(len(apks) == 1, "Expected exactly one standalone APK")
        digest = hashlib.sha256(apks[0].read_bytes()).hexdigest()
        require(digest == spec["apk_sha256"], "APK checksum mismatch")
        manifests = list(folder.rglob("SHA256SUMS"))
        require(len(manifests) == 1 and manifests[0].read_text().split()[0] == digest, "CI checksum mismatch")
        spec["tag"] = "v0.2.16-android6-q7-" + spec["version"]
        spec["filename"] = "DiPlay-HHQ-Q7-Android6-0.2.16-" + spec["version"] + ".apk"
        spec["checksums"] = digest + "  " + spec["filename"] + "\n"
        spec["folder"] = folder
        shutil.copyfile(apks[0], folder / spec["filename"])
        (folder / "SHA256SUMS.txt").write_text(spec["checksums"], encoding="utf-8")
        (folder / "notes.md").write_text(spec["notes"], encoding="utf-8")
        existing = api("releases/tags/" + spec["tag"], optional=True)
        if existing:
            release_matches(spec, existing)
        else:
            require(api("git/ref/tags/" + spec["tag"], optional=True) is None, "A pre-existing tag must not be overwritten")
        spec["existing"] = existing

    for spec in SPECS:
        if spec["existing"]:
            print("Already verified: " + spec["existing"]["html_url"])
            continue
        folder = spec["folder"]
        title = "HHQ Q7 Android 6 — " + ("热点连接与音乐修复 (hotspot1)" if spec["version"] == "hotspot1"
                                         else "导航播报自动压低音乐 (navduck1)")
        subprocess.run(["gh", "release", "create", spec["tag"], str(folder / spec["filename"]),
                        str(folder / "SHA256SUMS.txt"), "--repo", REPO, "--target", spec["sha"],
                        "--title", title, "--notes-file", str(folder / "notes.md"),
                        "--prerelease", "--latest=false"], check=True)
        release = api("releases/tags/" + spec["tag"])
        release_matches(spec, release)
        print("Published and verified: " + release["html_url"])


if __name__ == "__main__":
    main()
