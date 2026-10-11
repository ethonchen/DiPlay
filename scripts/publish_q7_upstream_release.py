"""Publish the exact verified upstream-sync Q7 package requested by the owner."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from urllib.error import HTTPError
from urllib.request import Request, urlopen

REPO = "ethonchen/DiPlay"
BRANCH = "publish/q7-upstream-20261011"
SPEC = json.loads("{\"run\":38145388009,\"sha\":\"4a0eb758e7a5dd05a02c70dc1aa5c74dc9807912\",\"target\":\"f4205faf57ee8a33f689210ae8545e7147a35741\",\"artifact\":11704936611,\"artifact_digest\":\"sha256:926e45ca4a1c8ca7502a2a5d5bc4e9a2321017330c14e6ce242404a391408e63\",\"apk_sha256\":\"949087fdddc460d70c65d8a0cf2df4fc4b87a9866f836ebff3bbf90e962fbf1d\",\"apk_bytes\":6480816,\"filename\":\"DiPlay-Q7-0.2.18-upstream1.apk\",\"tag\":\"v0.2.18-android6-q7-upstream1\",\"title\":\"HHQ Q7 Android 6 — 上游 0.2.18 + 热点与导航音乐修复\",\"notes\":\"适用于 HHQ Q7 / Android 6 / 仅支持 2.4 GHz Wi-Fi 的车机。\\n\\n- 完整合入上游 0.2.18，包含音频解码重试、USB 与蓝牙连接改进、设置二维码备份及诊断更新。\\n- 保留 hotspot1 的车载热点发现、IPv4 Bonjour、2.4 GHz-only 和音乐连续播放修复。\\n- 保留 navduck1 的导航播报自动压低音乐及结束后恢复，支持 iPhone duckAudio / unduckAudio 指令。\\n- 接入上游最长 500 ms 的音频解码输入重试，同时保留 Q7 的重试统计。\\n- 保留 Android 6 / API 23 与 ARMv7 兼容性；Android 8+ 专用通话功能在旧系统上禁用。\\n\\n安装：原包名、原签名，versionCode 41，可直接覆盖 hotspot1 / navduck1，保留原设置。\\n版本：0.2.18-android6-q7-upstream1-slim。APK 大小：6,480,816 字节。\\n\\n验证：完整单元测试、lint、debug / 优化构建、原签名检查以及 Android 6 安装、设置启动、离线签名、配对、加密数据包、麦克风编解码和兼容运行检查全部通过。\\n合并后的组合版本实车听感仍待验证，请装车后确认热点连接、音乐连续播放以及导航播报期间音乐降低、结束后恢复。\\n\\n合入主干：[PR #6](https://github.com/ethonchen/DiPlay/pull/6)\\n主干源代码：f4205faf57ee8a33f689210ae8545e7147a35741\\n已测试构建源代码：4a0eb758e7a5dd05a02c70dc1aa5c74dc9807912（与主干源代码树完全相同）\\n构建与 Android 6 运行测试：https://github.com/ethonchen/DiPlay/actions/runs/38145388009\\nAPK SHA-256：949087fdddc460d70c65d8a0cf2df4fc4b87a9866f836ebff3bbf90e962fbf1d\\n\"}")


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def api(path, optional=False, data=None, method=None):
    encoded = None if data is None else json.dumps(data).encode("utf-8")
    request = Request(
        "https://api.github.com/repos/" + REPO + "/" + path,
        data=encoded, method=method,
        headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json",
                 "X-GitHub-Api-Version": "2022-11-28"},
    )
    try:
        with urlopen(request, timeout=60) as response:
            return json.load(response)
    except HTTPError as error:
        if optional and error.code == 404:
            return None
        raise


def verify_release(release, checksums, allow_draft=False, allow_partial=False):
    require((allow_draft or not release["draft"]) and release["prerelease"],
            "Unexpected release visibility")
    require(release["tag_name"] == SPEC["tag"] and release["name"] == SPEC["title"],
            "Release metadata mismatch")
    require(release["target_commitish"] == SPEC["target"], "Release source SHA mismatch")
    require(release["body"].strip() == SPEC["notes"].strip(), "Release notes mismatch")
    ref = api("git/ref/tags/" + SPEC["tag"], optional=allow_draft and release["draft"])
    if ref is not None:
        require(ref["object"]["type"] == "commit" and ref["object"]["sha"] == SPEC["target"],
                "Release tag SHA mismatch")
    expected = {
        SPEC["filename"]: (SPEC["apk_sha256"], SPEC["apk_bytes"]),
        "SHA256SUMS.txt": (hashlib.sha256(checksums.encode("utf-8")).hexdigest(),
                          len(checksums.encode("utf-8"))),
    }
    assets = {asset["name"]: asset for asset in release["assets"]}
    require(set(assets) <= set(expected), "Unexpected existing asset")
    if not allow_partial:
        require(set(assets) == set(expected), "Release is missing assets")
    for name, asset in assets.items():
        digest, size = expected[name]
        require(asset["state"] == "uploaded" and asset["digest"] == "sha256:" + digest
                and asset["size"] == size, "Published asset mismatch: " + name)
    return set(expected) - set(assets)


def main():
    require(os.environ.get("GITHUB_REPOSITORY") == REPO, "Wrong repository")
    require(os.environ.get("GITHUB_REF") == "refs/heads/" + BRANCH, "Wrong publishing branch")
    run = api("actions/runs/" + str(SPEC["run"]))
    require(run["head_sha"] == SPEC["sha"] and run["conclusion"] == "success"
            and run["status"] == "completed", "Source build and Android 6 checks have not passed")
    require(run["path"] == ".github/workflows/q7-slim.yml", "Unexpected source workflow")
    source = api("git/commits/" + SPEC["sha"])
    target = api("git/commits/" + SPEC["target"])
    require(source["tree"]["sha"] == target["tree"]["sha"], "Main differs from the tested source")
    artifact = api("actions/artifacts/" + str(SPEC["artifact"]))
    require(not artifact["expired"] and artifact["name"] == "DiPlay-Q7-slim-standalone"
            and artifact["digest"] == SPEC["artifact_digest"]
            and artifact["workflow_run"]["id"] == SPEC["run"]
            and artifact["workflow_run"]["head_sha"] == SPEC["sha"], "Artifact provenance mismatch")

    folder = Path("release-files")
    subprocess.run(["gh", "run", "download", str(SPEC["run"]), "--repo", REPO,
                    "--name", "DiPlay-Q7-slim-standalone", "--dir", str(folder)], check=True)
    apks = list(folder.rglob("mobile-q7.apk"))
    require(len(apks) == 1, "Expected one standalone APK")
    apk = apks[0].read_bytes()
    digest = hashlib.sha256(apk).hexdigest()
    require(digest == SPEC["apk_sha256"] and len(apk) == SPEC["apk_bytes"],
            "APK checksum or size mismatch")
    manifests = list(folder.rglob("SHA256SUMS"))
    require(len(manifests) == 1 and manifests[0].read_text().split()[0] == digest,
            "CI checksum mismatch")
    checksums = digest + "  " + SPEC["filename"] + "\n"
    shutil.copyfile(apks[0], folder / SPEC["filename"])
    (folder / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8")
    (folder / "notes.md").write_text(SPEC["notes"], encoding="utf-8")

    release = api("releases/tags/" + SPEC["tag"], optional=True)
    if release and not release["draft"]:
        verify_release(release, checksums)
        print("Already published and verified: " + release["html_url"])
        return
    if release:
        missing = verify_release(release, checksums, allow_draft=True, allow_partial=True)
        if missing:
            subprocess.run(["gh", "release", "upload", SPEC["tag"], "--repo", REPO]
                           + [str(folder / name) for name in sorted(missing)], check=True)
    else:
        require(api("git/ref/tags/" + SPEC["tag"], optional=True) is None,
                "A pre-existing tag must not be overwritten")
        subprocess.run(["gh", "release", "create", SPEC["tag"],
                        str(folder / SPEC["filename"]), str(folder / "SHA256SUMS.txt"),
                        "--repo", REPO, "--target", SPEC["target"], "--title", SPEC["title"],
                        "--notes-file", str(folder / "notes.md"),
                        "--draft", "--prerelease", "--latest=false"], check=True)
    release = api("releases/tags/" + SPEC["tag"])
    verify_release(release, checksums, allow_draft=True)
    api("releases/" + str(release["id"]), data={"draft": False}, method="PATCH")
    release = api("releases/tags/" + SPEC["tag"])
    verify_release(release, checksums)
    print("Published and verified: " + release["html_url"])


if __name__ == "__main__":
    main()
