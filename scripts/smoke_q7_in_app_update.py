#!/usr/bin/env python3
"""Exercise the published update1 UI on a disposable API-23 emulator.

The app itself must discover, download, verify and hand update2 to Android's
installer. No candidate APK is pushed to the device or installed through adb.
"""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.shihab.diplay.hudtest"
ROOT = Path("android6-smoke/in-app-update")


def adb(*args, binary=False):
    return subprocess.check_output(["adb", *args], text=not binary)


def snapshot(label, screenshot=False):
    # A busy UI can make uiautomator fail to acquire its idle snapshot; retry it.
    for attempt in range(3):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/update-ui.xml")
            body = adb("shell", "cat", "/sdcard/update-ui.xml")
            tree = ET.fromstring(body[body.index("<?xml"):])
            (ROOT / f"{label}.xml").write_text(body)
            if screenshot:
                (ROOT / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
            return list(tree.iter("node"))
        except (subprocess.CalledProcessError, ValueError, ET.ParseError):
            if attempt == 2:
                raise
            time.sleep(2)


def choose(nodes, predicate):
    for node in nodes:
        if predicate(node.attrib):
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if bounds:
                left, top, right, bottom = map(int, bounds.groups())
                if right > left and bottom > top:
                    return node, ((left + right) // 2, (top + bottom) // 2)
    return None


def click(found):
    node, (x, y) = found
    print("UI tap:", node.get("text") or node.get("content-desc") or node.get("resource-id"), flush=True)
    adb("shell", "input", "tap", str(x), str(y))


def text_is(*values):
    return lambda attrs: attrs.get("text", "").strip().lower() in {v.lower() for v in values}


def scroll(x):
    adb("shell", "input", "swipe", str(x), "530", str(x), "220", "450")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--target-tag", default="v0.2.21-android6-q7-update2")
    parser.add_argument("--target-code", type=int, default=50)
    parser.add_argument("--allow-legacy-file-fallback", action="store_true")
    args = parser.parse_args()
    ROOT.mkdir(parents=True, exist_ok=True)
    assert adb("shell", "getprop", "ro.build.version.sdk").strip() == "23"
    before = adb("shell", "dumpsys", "package", PACKAGE)
    assert "versionCode=49" in before and "update1" in before
    (ROOT / "before-package.txt").write_text(before)
    adb("root")
    adb("wait-for-device")
    adb("shell", "settings", "put", "secure", "install_non_market_apps", "1")
    adb("shell", "wm", "size", "1024x600")
    adb("shell", "wm", "density", "160")
    adb("shell", "mkdir", "-p", f"/data/data/{PACKAGE}/files")
    adb("shell", f"echo preserved > /data/data/{PACKAGE}/files/q7-in-app-update-check")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.shilapi.xcertplay.DiPlayActivity", "--es", "page", "settings")
    time.sleep(5)

    for attempt in range(12):
        nodes = snapshot("settings")
        about = choose(nodes, lambda a: a.get("content-desc", "").lower() == "open about" or text_is("About", "关于")(a))
        if about:
            click(about)
            break
        scroll(130)  # The category rail scrolls independently in landscape.
    else:
        raise AssertionError("About category was not found")

    for attempt in range(8):
        nodes = snapshot("about", screenshot=True)
        button = choose(nodes, text_is("Check for updates", "检查更新"))
        if button:
            click(button)
            break
        scroll(760)
    else:
        raise AssertionError("Update1's check button was not found")

    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        nodes = snapshot("discovery")
        button = choose(nodes, text_is("Download update", "下载更新"))
        if button:
            # The metadata is written by the actual, unchanged update1 app.
            metadata = adb("shell", "cat", f"/data/data/{PACKAGE}/shared_prefs/diplay_update_availability.xml")
            (ROOT / "discovered-release.xml").write_text(metadata)
            assert args.target_tag in metadata
            assert f'name="version_code" value="{args.target_code}"' in metadata
            snapshot("update2-offered", screenshot=True)
            click(button)
            break
        time.sleep(3)
    else:
        raise AssertionError("Update1 did not discover update2")

    deadline = time.monotonic() + 420
    while time.monotonic() < deadline:
        nodes = snapshot("download")
        button = choose(nodes, lambda a: a.get("text", "").startswith((f"Install {args.target_tag}", f"安装 {args.target_tag}")))
        if button:
            snapshot("download-verified", screenshot=True)
            # Pull only the APK produced by the app's own downloader.
            paths = adb("shell", "find", f"/data/data/{PACKAGE}/cache/update", "-name", "*.apk").splitlines()
            assert len(paths) == 1, paths
            downloaded = ROOT / "downloaded-update2.apk"
            subprocess.run(["adb", "pull", paths[0].strip(), str(downloaded)], check=True)
            assert hashlib.sha256(downloaded.read_bytes()).hexdigest() == args.expected_sha256
            click(button)
            break
        if choose(nodes, text_is("Update failed", "更新失败")):
            raise AssertionError("Update1 reported a download/verification failure")
        time.sleep(3)
    else:
        raise AssertionError("Update1 did not finish downloading and verifying update2")

    installer_seen = False
    manual_file_handoff = False
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        nodes = snapshot("installer")
        if choose(nodes, text_is("Update failed", "更新失败")) and not manual_file_handoff:
            if not args.allow_legacy_file_fallback:
                raise AssertionError("Update1 could not start Android's installer; see logcat and update-state.xml")
            # This is an explicit file-manager-equivalent fallback for the known
            # update1 content-URI bug. The app itself already downloaded and
            # verified these bytes; do not push or adb-install a candidate here.
            saved = f"/storage/emulated/0/Android/data/{PACKAGE}/files/update/{Path(paths[0].strip()).name}"
            external_copy = ROOT / "file-manager-copy.apk"
            subprocess.run(["adb", "pull", saved, str(external_copy)], check=True)
            assert hashlib.sha256(external_copy.read_bytes()).hexdigest() == args.expected_sha256
            manual_file_handoff = True
            print("Known update1 limitation: opening its verified saved APK as a file manager would.", flush=True)
            adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", "file://" + saved,
                "-t", "application/vnd.android.package-archive")
            continue
        if any("packageinstaller" in n.get("package", "") for n in nodes):
            installer_seen = True
            button = choose(nodes, lambda a: "packageinstaller" in a.get("package", "") and text_is("Install", "INSTALL", "Next", "NEXT", "安装", "下一步")(a))
            if button:
                snapshot("installer-confirm", screenshot=True)
                click(button)
        package = adb("shell", "dumpsys", "package", PACKAGE)
        if f"versionCode={args.target_code} " in package:
            assert installer_seen, "Android installer was not observed"
            (ROOT / "after-package.txt").write_text(package)
            break
        time.sleep(3)
    else:
        raise AssertionError("Android installer did not install update2")

    installed = adb("shell", "pm", "path", PACKAGE).strip().removeprefix("package:")
    target = ROOT / "installed-update2.apk"
    subprocess.run(["adb", "pull", installed, str(target)], check=True)
    assert hashlib.sha256(target.read_bytes()).hexdigest() == args.expected_sha256
    assert adb("shell", "cat", f"/data/data/{PACKAGE}/files/q7-in-app-update-check").strip() == "preserved"
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.shilapi.xcertplay.DiPlayActivity", "--es", "page", "settings")
    time.sleep(5)
    snapshot("update2-running", screenshot=True)
    log = adb("logcat", "-d")
    (ROOT / "logcat.txt").write_text(log)
    assert "FATAL EXCEPTION" not in log
    route = "manual file-manager-equivalent handoff required by update1 on stock Android 6" if manual_file_handoff else "update1 installer button"
    (ROOT / "result.txt").write_text(f"PASS: unchanged published update1 discovered, downloaded and verified {args.target_tag}; {route}; Android installer completed the signed upgrade, installed bytes match, versionCode={args.target_code}, private data survived without uninstall.\n")
    print((ROOT / "result.txt").read_text(), flush=True)


if __name__ == "__main__":
    try:
        main()
    finally:
        ROOT.mkdir(parents=True, exist_ok=True)
        for name, command in (
            ("logcat.txt", ("logcat", "-d")),
            ("activities.txt", ("shell", "dumpsys", "activity", "activities")),
            ("packages.txt", ("shell", "pm", "list", "packages", "-f")),
            ("update-state.xml", ("shell", "cat", f"/data/data/{PACKAGE}/shared_prefs/diplay_update_availability.xml")),
        ):
            try:
                value = adb(*command)
                (ROOT / name).write_text(value)
                if name == "logcat.txt":
                    lines = value.splitlines()
                    for index, line in enumerate(lines):
                        if "DiPlay-Update" in line or "FATAL EXCEPTION" in line:
                            print("\n".join(lines[index:index + 14]), flush=True)
            except subprocess.CalledProcessError as error:
                print(f"Could not capture {name}: {error}", flush=True)
