"""Offline negative integrity and packaged-APK byte check tests."""
import base64
import hashlib
import io
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verified_vendor_assets import (
    VendorArchive, expected_paths, parse_manifest,
    stage_verified_archive, verify_archive, verify_packaged_apk
)


class VerifiedVendorAssetsTest(unittest.TestCase):
    def fixture(self, path: Path, content: bytes = b"console.log('ok');") -> VendorArchive:
        with tarfile.open(path, "w:gz") as archive:
            member = tarfile.TarInfo("package/dist/safe.js")
            member.size = len(content)
            archive.addfile(member, io.BytesIO(content))
        digest = base64.b64encode(hashlib.sha512(path.read_bytes()).digest()).decode()
        return VendorArchive("fixture", "https://example.invalid/fixture.tgz",
                             "sha512-" + digest,
                             (("package/dist/safe.js", "editor/vendor/safe.js"),))

    def test_matching_digest_allows_selected_file_only(self):
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            archive = folder / "valid.tgz"
            vendor = self.fixture(archive)
            stage_verified_archive(vendor, archive, folder / "staged")
            self.assertEqual(
                (folder / "staged/editor/vendor/safe.js").read_bytes(),
                b"console.log('ok');"
            )

    def test_modified_archive_fails_closed_without_writing_executable_file(self):
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            archive = folder / "tampered.tgz"
            vendor = self.fixture(archive)
            archive.write_bytes(archive.read_bytes() + b"malicious")
            with self.assertRaisesRegex(ValueError, "integrity mismatch"):
                stage_verified_archive(vendor, archive, folder / "staged")
            self.assertFalse((folder / "staged").exists())

    def test_missing_member_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            archive = folder / "valid.tgz"
            vendor = self.fixture(archive)
            missing = VendorArchive(vendor.name, vendor.url, vendor.integrity,
                                    (("package/dist/missing.js", "editor/vendor/safe.js"),))
            with self.assertRaises(KeyError):
                stage_verified_archive(missing, archive, folder / "staged")

    def test_manifest_rejects_missing_or_extra_assets(self):
        with tempfile.TemporaryDirectory() as td:
            manifest = Path(td) / "vendor.sha256"
            manifest.write_text("0" * 64 + "  app/src/main/assets/editor/vendor/extra.js\n")
            with self.assertRaisesRegex(ValueError, "Unexpected/duplicate"):
                parse_manifest(manifest, {"app/src/main/assets/editor/vendor/safe.js"})

    def test_release_apk_rejects_asset_that_differs_from_verified_source(self):
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td)
            approved = b"safe-js"
            expected_file = "app/src/main/assets/editor/vendor/safe.js"
            source = folder / expected_file
            source.parent.mkdir(parents=True)
            source.write_bytes(approved)
            manifest = folder / "vendor.sha256"
            manifest.write_text(hashlib.sha256(approved).hexdigest() + "  " +
                                str(source) + "\n")
            apk = folder / "app.apk"
            with zipfile.ZipFile(apk, "w") as zip_file:
                zip_file.writestr("assets/editor/vendor/safe.js", b"modified-js")
            # Make paths relative to tmp dir to match production manifest format.
            import os
            old_cwd = Path.cwd()
            try:
                os.chdir(folder)
                manifest.write_text(hashlib.sha256(approved).hexdigest() +
                                    "  " + expected_file + "\n")
                with self.assertRaisesRegex(ValueError, "Release APK vendor asset mismatch"):
                    verify_packaged_apk(apk, manifest, {expected_file})
            finally:
                os.chdir(old_cwd)


if __name__ == "__main__":
    unittest.main()
