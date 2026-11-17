# Blake Personal — phone-installed milestone and recovery handoff

**Recorded:** 2026-11-17 (Asia/Bangkok)  
**Status:** Personal install and short acceptance **PASS (Boss-reported)**; source and CI evidence independently checked. **Not a GitHub Release or a Play Store release.**  
**Scope:** Blake Personal phone-only track. This is **not** a blanket PASS for every phase in the separate [Blake 2.0 G–L roadmap](BLAKE_V2_CLOSEOUT_ROADMAP.md).

## What is installed

- Application label: **Blake Personal**; application ID: `com.blake.editor.personal`.
- Version name/code: **`0.1.0` / `1`** (do not label this 1.0 in release metadata).
- Exactly one normal launcher: Original `MainActivity`. Blake V2 is accessed from within Original.
- Source `main` commit at build/closeout: [`c5ee92d075255a716a66646e3b22289b7f1c8413`](https://github.com/sirawitchotika-boop/BlakeEditor-/commit/c5ee92d075255a716a66646e3b22289b7f1c8413).
- Implementation: [PR #56](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/56) (identity/launcher), [PR #57](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/57) (optional private signing support), [PR #58](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/58) (unambiguous unsigned APK artifact); all merged before this checkpoint.
- [GitHub Actions run #44043130001](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/44043130001) completed successfully on the PR #58 tested source tree. Its artifact **`BlakePersonal-UNSIGNED-SIGN-FIRST`** contains **`BlakePersonal-UNSIGNED.apk`**.
- **Unsigned input APK SHA-256:** `9822e61751335521b9e832d39dbd54a3542babb8cce7d5ee70d64c1f1d8a848b`. This is **not** the hash of the signed APK.
- Boss signed the candidate privately on Android in Termux with `apksigner`; a signed APK and a signing record are kept on the phone, **not uploaded to GitHub**. This is distinct from the optional Gradle private-signing path supported in source.

## Device verification — Boss-reported, not remotely inspected

The following is a record of Boss's reports; it must **not** be represented as an independently run on-device test by a GitHub agent.

1. Permanent PKCS12 RSA-4096 signing key, alias `blakepersonal`, generated privately in Termux. GPG AES-256 encrypted backup decrypt/byte comparison **PASS**.
2. Encrypted backup round-tripped through private Google Drive storage and compared byte-for-byte: **PASS**.
3. Unsigned APK download/hash, phone signing, APK signature and application/launcher identity checks: **PASS**.
4. Nonempty signing record exists. Boss reported **all seven** field checks **PASS**: app name, package, version, exact source SHA, APK filename, signed APK SHA-256 match, public signing certificate SHA-256 match.
5. Boss explicitly approved installation. **`BlakePersonal-SIGNED.apk` installed successfully** as a separate personal app; first launch showed Original: **PASS**.
6. Short acceptance (six checks): create a disposable note; save/reopen; cold restart/persistence; open V2 from Original; return to Original; confirm one older test app and its notes still work. Boss reported **6/6 PASS**.
7. Home-screen cleanup **PASS**: older icons were *removed as shortcuts*, **not uninstalled**. Existing test apps and local app data remain.

**Evidence limits:** No public signing certificate fingerprint or signed APK hash is copied here, because the verified values remain in Boss's local record. Exact device/Android version and wider OEM/file-provider coverage were not collected here. This was a *fresh side-by-side install*, **not a signed in-place upgrade test**. No GitHub Release, tag, Store publication, automatic migration, app deletion or update rollout is claimed.

## Private locations and preservation rules

These are **paths/names only**, not contents. Never request, print or upload passwords, private keys, decrypted backups, personal notes, or signed artifacts.

| Item | Private owner-controlled location |
| --- | --- |
| Permanent signing key | Termux: `$HOME/blake-personal-signing/blake-personal-release.p12` |
| Local encrypted backup | Termux: `$HOME/blake-personal-signing/blake-personal-release.p12.gpg` |
| Additional encrypted backup | Private Google Drive copy, `BlakePersonal-Key-Backup.p12.gpg` |
| Installed candidate file | Downloads: `BlakePersonal-SIGNED.apk` |
| Public-identity signing record | Downloads: `BlakePersonal-Signing-Record.txt` |

Keep both separate passwords stored privately. Retain the signed APK, local record and verified encrypted backup. **Never overwrite the record automatically.** A home-screen shortcut removal is not an uninstall or a deletion of an app sandbox.

## Safe return-to-project instructions

1. First inspect live `main`, open PRs, `AGENTS.md`, `README.md`, the current closeout notes, and the master skills repo; this historical SHA is an anchor, not a permanent claim about current `main`.
2. For a routine update, keep **the same `com.blake.editor.personal` app ID and permanent signing identity**, and increment **versionCode above `1`**. Check the signed candidate's public certificate fingerprint and APK SHA-256 against the release record without revealing secrets.
3. Use an authorized, exact-head build and small disposable-data tests first. A future **in-place update preserving existing personal data has NOT been tested** and requires its own safe gate and Boss approval. Do not use uninstall, clear storage or downgrade as a workaround.
4. Keep Original as launcher and V2 opt-in unless separately approved. Do not publish, create a release/tag, distribute, merge, sign another build or delete an old test app without separate approval.
5. If a problem is reported: preserve the existing installed app/data; capture the symptom, version, package and last known-good source/CI refs; stop before any destructive step. Re-read the private signing record locally, not by pasting secrets into chat.
6. The Blake Personal *phone-install milestone* is complete. The G–L program has its own independent security, provider, upgrade, distribution and closure gates; do not silently mark them complete.

**Next safe action:** None required for normal use. Resume only for a real issue or an explicitly requested update.