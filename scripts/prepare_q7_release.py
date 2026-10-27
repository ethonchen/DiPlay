#!/usr/bin/env python3
"""Prepare APK, checksum and the app's update manifest from the verified Q7 APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    badging = subprocess.check_output([str(args.build_tools / "aapt"), "dump", "badging", str(args.apk)], text=True)
    pkg = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    minimum = int(re.search(r"^sdkVersion:'(\d+)'$", badging, re.M)[1])
    assert pkg and pkg[1] == "com.shihab.diplay.hudtest" and minimum == 23
    code, version = int(pkg[2]), pkg[3]
    assert version.endswith("-slim") and "-android6-q7-" in version
    base = version.removesuffix("-slim")
    tag = "v" + base
    name = "DiPlay-Q7-" + base.replace("-android6-q7", "") + ".apk"
    args.output.mkdir(parents=True, exist_ok=True)
    output_apk = args.output / name
    shutil.copyfile(args.apk, output_apk)
    digest = hashlib.sha256(output_apk.read_bytes()).hexdigest()
    manifest = dict(schemaVersion=1, packageName=pkg[1], versionCode=code, versionName=version,
                    minSdk=minimum, tagName=tag, apkName=name,
                    apkUrl=f"https://github.com/ethonchen/DiPlay/releases/download/{tag}/{name}",
                    size=output_apk.stat().st_size, sha256=digest)
    (args.output / "update.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (args.output / "SHA256SUMS.txt").write_text(f"{digest}  {name}\n")
    print(f"Prepared {tag}, versionCode {code}, {manifest['size']} bytes")


if __name__ == "__main__":
    main()
