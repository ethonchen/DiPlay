#!/usr/bin/env python3
"""Check install minimum, ARMv7 native libraries, API-23 signing and identity policy."""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--standalone", action="store_true")
    parser.add_argument("--optimized", action="store_true")
    parser.add_argument("--signer-sha256", help="Require the known update-signing certificate")
    args = parser.parse_args()
    badging = subprocess.check_output([str(args.build_tools / "aapt"), "dump", "badging", str(args.apk)], text=True)
    assert re.search(r"^sdkVersion:'23'$", badging, re.M), "APK installation minimum must be API 23"
    assert "package: name='com.shihab.diplay.hudtest'" in badging, "Wrong application package"
    if args.optimized:
        assert "application-debuggable" not in badging, "Optimized APK must not run debuggable"
        assert "versionCode='37'" in badging and "versionName='0.2.16-android6-q7-slim'" in badging
        assert args.apk.stat().st_size < 12 * 1024 * 1024, "Optimized Q7 APK exceeded the size budget"
    signing = subprocess.check_output([str(args.build_tools / "apksigner"), "verify", "--verbose",
        "--print-certs", "--min-sdk-version", "23", str(args.apk)], text=True)
    assert "Verified using v1 scheme (JAR signing): true" in signing, "Android 6 requires v1 signing"
    if args.signer_sha256:
        assert "Signer #1 certificate SHA-256 digest: " + args.signer_sha256.lower() in signing, "Update signing key changed"
    with zipfile.ZipFile(args.apk) as archive:
        names = set(archive.namelist())
        for library in ("libxcertplay_i2c.so", "liblocal_hotspot_radio.so", "libspeex_echo.so"):
            data = archive.read("lib/armeabi-v7a/" + library)
            assert data[:5] == b"\x7fELF\x01", "ARMv7 native library must be 32-bit ELF"
            assert int.from_bytes(data[18:20], "little") == 40, "Native library must be ARM"
        identity_names = {"assets/offline-mfi/identity.pk8", "assets/offline-mfi/certificate.p7b"}
        bundled = {name for name in names if name.startswith("assets/offline-mfi/") and not name.endswith("/")}
        assert bundled == (identity_names if args.standalone else set()), "Unexpected runtime identity assets"
        if args.standalone:
            assert all(archive.getinfo(name).file_size > 0 for name in identity_names), "Empty runtime identity"
    print("Android 6 APK checks passed: API 23, ARMv7, v1 signing, expected identity policy")


if __name__ == "__main__":
    main()
