#!/usr/bin/env python3
"""Explicitly import the selected public experiment; never search for other credentials."""
import argparse
import hashlib
from pathlib import Path
import zipfile


def provision(apk: Path, destination: Path, expected_sha256: str) -> None:
    if hashlib.sha256(apk.read_bytes()).hexdigest() != expected_sha256:
        raise ValueError("Selected public APK checksum does not match")
    if destination.exists():
        raise ValueError("Use a fresh external runtime directory")
    with zipfile.ZipFile(apk) as archive:
        material = {}
        for name in ("identity.pk8", "certificate.p7b"):
            entry = archive.getinfo("assets/offline-mfi/" + name)
            if not 0 < entry.file_size <= 64 * 1024:
                raise ValueError("Public experimental identity is missing or oversized")
            material[name] = archive.read(entry)
    directory = destination / "offline-mfi"
    directory.mkdir(parents=True, mode=0o700)
    for name, data in material.items():
        target = directory / name
        target.write_bytes(data)
        target.chmod(0o600)
    print("Selected public experimental identity provisioned outside source")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--allow-public-experimental-identity", action="store_true", required=True)
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[1]
    if args.destination.resolve().is_relative_to(repository):
        parser.error("Runtime identity must stay outside the repository")
    provision(args.apk, args.destination, args.sha256)


if __name__ == "__main__":
    main()
