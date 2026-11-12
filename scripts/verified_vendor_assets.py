#!/usr/bin/env python3
"""Install only checksum-verified release assets and check bytes inside APKs.

All version pins and SHA-512 package integrity values are reviewed constants,
not digests calculated from untrusted downloads during the same build.
"""
from __future__ import annotations

import argparse
import base64
from dataclasses import dataclass
import hashlib
import hmac
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import zipfile


ASSETS_ROOT = Path("app/src/main/assets")
MAX_ARCHIVE_BYTES = 128 * 1024 * 1024
MAX_FILE_BYTES = 4 * 1024 * 1024

ACE_FILES = (
    "ace.js", "mode-text.js", "mode-markdown.js", "mode-kotlin.js",
    "mode-javascript.js", "mode-python.js", "mode-html.js",
    "mode-css.js", "mode-json.js", "mode-xml.js",
    "theme-twilight.js", "theme-chrome.js", "ext-searchbox.js",
)


@dataclass(frozen=True)
class VendorArchive:
    name: str
    url: str
    integrity: str
    # Absolute-within-archive path -> destination within app/src/main/assets.
    entries: tuple[tuple[str, str], ...]


VENDORS = (
    VendorArchive(
        "ace-builds-1.44.0",
        "https://registry.npmjs.org/ace-builds/-/ace-builds-1.44.0.tgz",
        "sha512-PFNMSYqFdEUkul2Ntud0HvA09AgY+F1ag0UYdpMH60wNI/qOA8cB8tlTgoALMEwIdUPJK2CjrIQ7OnbiSS/ugQ==",
        tuple((f"package/src-noconflict/{name}",
               f"editor/vendor/ace/{name}") for name in ACE_FILES),
    ),
    VendorArchive(
        "marked-18.0.7",
        "https://registry.npmjs.org/marked/-/marked-18.0.7.tgz",
        "sha512-iDVQ5ldaiKXn6b2JroX5kgRfmwgqolW7NpaEzTl1k/2Zh1njIEN9yniyLV/mOvWwtsE8OGgkjsCYvijuPk1dtA==",
        (("package/lib/marked.umd.js", "editor/vendor/marked/marked.min.js"),),
    ),
    VendorArchive(
        "dompurify-3.4.15",
        "https://registry.npmjs.org/dompurify/-/dompurify-3.4.15.tgz",
        "sha512-EUBjM+B+lkDE41iE82DDSCfkoPGfXx8IxFxPMjNzm/Uk4xDet77rTN9wqlxlVg71kK7XGuUMv6wUxJUwwv+Xyw==",
        (("package/dist/purify.min.js", "editor/vendor/dompurify/purify.min.js"),),
    ),
)


def expected_paths(vendors: tuple[VendorArchive, ...] = VENDORS) -> set[str]:
    return {str(ASSETS_ROOT / target)
            for vendor in vendors for _, target in vendor.entries}


def verify_archive(archive: Path, sri: str) -> None:
    if not sri.startswith("sha512-"):
        raise ValueError("Unsupported vendor integrity format (expected sha512)")
    expected = base64.b64decode(sri[7:], validate=True)
    if len(expected) != 64:
        raise ValueError("Invalid SHA-512 pin length")
    if archive.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ValueError("Vendor archive exceeds safety size limit")
    digest = hashlib.sha512(archive.read_bytes()).digest()
    if not hmac.compare_digest(digest, expected):
        raise ValueError(f"Vendor archive integrity mismatch: {archive.name}")


def stage_verified_archive(vendor: VendorArchive, archive: Path, staging: Path) -> None:
    # Validate the entire tarball BEFORE using any file from it.
    verify_archive(archive, vendor.integrity)
    with tarfile.open(archive, "r:gz") as package:
        for entry, target in vendor.entries:
            if not entry.startswith("package/") or not target.startswith("editor/vendor/"):
                raise ValueError("Unexpected vendor path")
            if ".." in Path(entry).parts or ".." in Path(target).parts:
                raise ValueError("Unsafe vendor path")
            member = package.getmember(entry)
            if not member.isfile() or member.size < 1 or member.size > MAX_FILE_BYTES:
                raise ValueError(f"Invalid executable asset: {entry}")
            stream = package.extractfile(member)
            if stream is None:
                raise ValueError(f"Missing executable asset: {entry}")
            data = stream.read(MAX_FILE_BYTES + 1)
            if len(data) != member.size:
                raise ValueError(f"Truncated executable asset: {entry}")
            destination = staging / target
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)


def install_assets(manifest: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="blake-verified-assets-") as directory:
        tmp = Path(directory)
        staging = tmp / "staged"
        for vendor in VENDORS:
            archive = tmp / f"{vendor.name}.tgz"
            subprocess.run(
                ["curl", "--fail", "--silent", "--show-error", "--location",
                 "--retry", "3", "--proto", "=https", "--tlsv1.2",
                 vendor.url, "-o", str(archive)],
                check=True,
            )
            stage_verified_archive(vendor, archive, staging)
            print(f"Verified package SHA-512: {vendor.name}")

        expected = expected_paths()
        staged_paths = {
            str(ASSETS_ROOT / p.relative_to(staging))
            for p in staging.rglob("*") if p.is_file()
        }
        if staged_paths != expected:
            raise ValueError("Incorrect extracted vendor file inventory")

        lines = []
        for path in sorted(expected):
            relative = Path(path).relative_to(ASSETS_ROOT)
            source = staging / relative
            raw = source.read_bytes()
            destination = Path(path)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            lines.append(f"{hashlib.sha256(raw).hexdigest()}  {path}")

        manifest.parent.mkdir(parents=True, exist_ok=True)
        # Write the record only after every archive was validated and copied.
        manifest.write_text("\n".join(lines) + "\n", encoding="ascii")
    print(f"Installed and recorded {len(expected)} verified vendor JavaScript assets")


def parse_manifest(manifest: Path, expected: set[str] | None = None) -> dict[str, str]:
    expected = expected_paths() if expected is None else expected
    recorded = {}
    for line in manifest.read_text(encoding="ascii").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if match is None:
            raise ValueError("Malformed vendor manifest")
        digest, name = match.groups()
        if name not in expected or name in recorded:
            raise ValueError(f"Unexpected/duplicate vendor manifest entry: {name}")
        recorded[name] = digest
    if set(recorded) != expected:
        raise ValueError("Vendor manifest does not include the exact approved file inventory")
    return recorded


def verify_packaged_apk(apk: Path, manifest: Path, expected: set[str] | None = None) -> None:
    records = parse_manifest(manifest, expected)
    with zipfile.ZipFile(apk) as package:
        for path, expected_sha256 in records.items():
            if hashlib.sha256(Path(path).read_bytes()).hexdigest() != expected_sha256:
                raise ValueError(f"Verified source JS changed after archive validation: {path}")
            apk_member = path.removeprefix("app/src/main/")
            # Compare bytes in the release APK to the digest of the verified input.
            actual = hashlib.sha256(package.read(apk_member)).hexdigest()
            if not hmac.compare_digest(actual, expected_sha256):
                raise ValueError(f"Release APK vendor asset mismatch: {apk_member}")
    print(f"Verified {len(records)} vendored JavaScript asset hashes in release APK")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("install", "verify-apk"))
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    if args.action == "install":
        install_assets(args.manifest)
    else:
        if args.apk is None:
            parser.error("--apk is required for verify-apk")
        verify_packaged_apk(args.apk, args.manifest)


if __name__ == "__main__":
    main()
