# Phase J — Release signing, backup and safe-upgrade gate

**Audit date:** 2026-11-13 (Asia/Bangkok)  
**Project:** `sirawitchotika-boop/BlakeEditor-`  
**Original audit baseline:** `f4be4149bc7183ec5472e5d4d493691f9db02a07` (before PR #49)  
**Refreshed source checkpoint (2026-11-13):** `31e0d6dfee423d33f874892d9bf915b62c5d1aed` (after merged PR #54; earlier PR #49 checkpoint remains historical)  
**Current gate:** **BLOCKED** for a signed production release. This document combines the earlier merged read-only audit with an updated source checkpoint. It does **not** approve signing, distribution, installation, merge, backup-policy changes, migration, or launcher promotion.

## Evidence and known results

- Phase H accepted-main CI [#43259241007](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43259241007) passed on `6fc1b22...`. Phase I phone acceptance on that isolated debug build was **USER-REPORTED PASS**, not a signed upgrade test.
- PRs [#45](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/45)–[#49](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/49) are merged. The PR #49 head `718e632010284a48519e9a0082ff6ef3e637bbd2` passed [full CI #43389867241](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43389867241). GitHub's commit objects show its Git tree `d11189cbf4b822c691feff188d5600505c692220` matches merged `main` `6f1e5138299ee253bf613dc91e0f8ce41cfc1b3a`. **No separate CI run on that merged commit** was observed. This is source/build evidence, not a device test or production signature.
- Production application ID is `com.blake.editor`, `versionCode=1`, `versionName=0.1.0`. Release build in `.github/workflows/build-apk.yml` is **unsigned**. Normal release has one launcher (Original), V2 Beta remains opt-in. Debug and isolated test APKs use **different application IDs**.
- Historical isolated test APKs [#43124844965](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43124844965) and [#43259241007](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43259241007) were verified to have different package IDs and different debug signing certificate fingerprints. Their signing keys **cannot establish** a stable production update path. Which exact package/signature is installed on the user's phone is **NOT_VERIFIED**.
- No production GitHub Release or approved long-lived production signing identity was found during this audit. Do not invent a signing key, owner or release channel.

- **Post-PR #54 checkpoint:** [PR #54](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/54) is merged at `31e0d6dfee423d33f874892d9bf915b62c5d1aed`. Its full [CI #43401077651](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43401077651) passed at `db89621f0c6a581cf4265c567c9c2c8cceba36cc`; both commits have tree `068b64ee987bc2a8a590b46f831b2daf9d232030`. A new isolated APK artifact exists for **disposable phone review only**, package `com.blake.editor.test43401077651a1`, expires November 20, 2026. **No phone/device, upgrade, signer or backup PASS is recorded.**

## App-private data inventory (source-derived)

| Data | Source and storage | Must verify at upgrade |
| --- | --- | --- |
| Original drafts | `FileDraftRepository.kt` in `filesDir/drafts/`: legacy `<key>.draft` + `<key>.meta`; new alternating `<key>.draft.0/.1`, `<key>.meta.0/.1`, `<key>.active` commit marker or `deleted` tombstone | Preserve active marker **and** matching slot pair; never restore only one member. Legacy files may remain after migration. |
| Original session | `SessionStore.kt`: `filesDir/session_state.properties` (atomic replacement since PR #46) | Text, cursor, file URI and dirty state survive a compatible in-place update |
| V2 recovery | `V2WorkspacePreviewActivity.kt`: `filesDir/v2_recovery/`; `RecoveryDraftStore.kt` uses `*.recovery`, `legacy-imported.marker`, `next-untitled-number.counter`, `recent-shortcuts-hidden.index` | Recovery drafts, Trash/Restore state, naming counter and Recent visibility are kept together |
| V2 save safety copies | `V2WorkspacePreviewActivity.kt`: `filesDir/v2_save_backups/` | Unresolved failed-save backups are not discarded |
| Original save safety copies | `AppContainer.kt`: `filesDir/original-safe-save-backups/` | Existing unresolved external-save backups are retained |
| Theme preferences | `SettingsRepository.kt`: DataStore named `blake_settings` (exact filename is managed by DataStore) | Theme choice survives update/restore |
| Linked external documents | Android Storage Access Framework document URIs and `UriPermissionManager.kt` / V2 picker permissions | External file bytes and provider permissions are checked separately; a stored URI string does **not** guarantee access after transfer |

**Important:** each isolated/debug test APK uses its own application ID and app-private sandbox. Merely installing a differently named test APK does **not** migrate its private drafts to `com.blake.editor`.

## PR #49 resolved the first-import compatibility gap

**Historical finding, now fixed in source:** PR #47 changed Original drafts to alternating committed text/metadata pairs. The old V2 importer could read a stale legacy pair or miss a slot-only draft. [PR #49](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/49) updated `RecoveryDraftStore.importLegacy()` to read the currently committed Original pair, respect `.active=deleted`, refuse corrupt or incomplete committed pairs, and preserve every existing V2 record. Its nine focused regression tests and full PR-head CI passed. **The first-import source defect is no longer an open task.** Real-device import and upgrade behavior remain **NOT_TESTED**.

### Remaining risk: imports completed before PR #49

At `RecoveryDraftStore.kt` lines 414–417, an existing `filesDir/v2_recovery/legacy-imported.marker` causes the importer to return without inspecting Original again. A user who opened V2 **before** PR #49 may have an old imported snapshot while Original retains a newer committed slot. This is a **possible historical mismatch**, not demonstrated loss of Original content or a confirmed affected phone.

Never delete/rewrite the marker as an automatic fix. Never overwrite a V2 recovery record, including Trash and unsaved edits. The Original slot and V2 record have separate histories; their timestamps/revision numbers cannot safely establish which text should win.

### Opt-in reconciliation contract — implemented in source, phone not tested

1. **Detect read-only:** only when an import marker already exists, inspect Original's current committed text/metadata pair using the same rules as PR #49, honoring tombstones and errors. Compare stable document identity and exact content against V2 without changing either store.
2. **Do not infer a winner:** show a difference as a reviewable **Original version** versus **V2 version**. A match requires no action. Do not silently replace a record, alter Trash, auto-import on startup, or reset the marker.
3. **Recover only on explicit user action:** offer **Keep Original text as a separate recovery copy**, using a fresh untitled identity. The copy must not retain a live `sourceUri`, persisted document hash, or saved-to-disk state. Preserve existing V2 and Original records, external documents, SAF grants, and backup files. Do not treat the copy as saved to an external file.
4. **Fail closed:** do not create a copy from deleted Original markers, incomplete/corrupt pairs, unreadable metadata or ambiguous identity. Explain the issue without deleting or rewriting anything. Prevent repeat taps from creating unintended duplicate copies.
5. **Regression coverage before any implementation:** marker already present with identical/different text; V2 edited/trashed record; Original tombstone; corrupt/incomplete committed pair; linked URI copy disconnected from Save; duplicate confirmation; and unchanged source/marker/other V2 data. Add a user-visible confirmation and test it separately.

**Implementation now on `main`:** [PR #51](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/51) added historical-marker safety tests; [PR #52](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/52) added read-only comparison; [PR #53](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/53) added a guarded, explicit, unlinked recovery-copy API; [PR #54](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/54) added **V2 Beta Home → Review Original drafts**, side-by-side text previews and an explicit **Keep Original as separate copy** action. On merged PR #54, exact-head [full CI #43401077651](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43401077651) completed `success`; the tested/merged Git trees match. **Phone acceptance remains NOT_TESTED**, and no historical mismatch on Boss's actual installed app has been established. Do not interpret source implementation as permission for an automatic migration or signed production release. Follow the [disposable-phone acceptance plan](BLAKE_V2_PHASE_J_HISTORICAL_RECOVERY_PHONE_ACCEPTANCE.md).

## Android backup decision — no policy changed

`AndroidManifest.xml` currently declares `android:allowBackup="true"` but no `android:fullBackupContent` or `android:dataExtractionRules`. With defaults, Android Auto Backup generally includes internal `filesDir`, databases and app preferences, while excluding cache and no-backup paths. A cloud backup may be limited to **25 MB per app user**, and actual backup/restore is affected by settings, quotas, Android versions and transport. Android 11-and-earlier and Android 12+ use different custom-rule formats if we decide to add them.

**Decision needed:** whether recovery drafts and unresolved save-safety copies must remain eligible for cloud backup, device-to-device transfer, or both. Excluding them to reduce quota can itself lose recovery data; including every large backup can exceed cloud quota. Do not flip `allowBackup` or introduce include/exclude lists until the intended behavior is approved and tested. An ordinary in-place update is **not** the same test as restoring from a cloud/device transfer.

Official reference: [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

## Signed in-place update gate

Android accepts an in-place update only with a compatible **application ID**, **signing certificate** (or supported key-rotation proof), and **version code**. The roadmap must choose the candidate version and an authorized signer; the current `0.1.0` / `1` is not yet a release decision.

1. **Read-only evidence:** Identify the exact currently installed app package, version code, certificate SHA-256 and installation source when safely available. Compare to the proposed production `com.blake.editor`.
2. **Authorized signing:** Establish a securely owned long-lived signing identity and distribution route. Record the *public* certificate fingerprint and the owner/custodian, not keys or passwords. If the installed app has an incompatible signer, **STOP**: do not present a differently signed APK as an upgrade.
3. **Candidate provenance:** Record exact commit, exact-head CI run, APK/AAB filename, SHA-256, package ID, version code/name, public certificate fingerprint, manifest/launcher and signed artifact verification outcome. Reproducible CI for unsigned artifacts alone is not proof of a signed candidate.
4. **Disposable upgrade:** First install a **matching-package, matching-signature** earlier build on a backed-up or disposable device/profile. Seed *disposable* Original draft, V2 recovery/Trash, theme, and external SAF file. Update **in place**; do not uninstall or clear storage. Compare pre/post state and inspect recovery, saved file bytes, URI permissions and read-only large-file path.
5. **Restore validation:** Separately test cloud restore/device transfer with explicit OS/transport details. Confirm which records restore together and whether documents require access reauthorization; do not assume settings transfer equals persisted SAF permission transfer.
6. **Stop-rollout:** On failure, stop distribution and preserve the installed app and data. Use a forward fix and recovery procedure; do **not** offer uninstall/clear data/forced APK downgrade as rollback.

Official references: [How Android app updates work](https://developer.android.com/google/play/app-updates), [App signing](https://developer.android.com/studio/publish/app-signing).

## Device check matrix (not executed)

| Area | In-place update | Backup restore / transfer |
| --- | --- | --- |
| Original session, draft text+metadata, deleted tombstones | NOT_TESTED | NOT_TESTED |
| V2 recovery, Trash/Restore, hidden Recent, Untitled counter | NOT_TESTED | NOT_TESTED |
| V2 first-time import from current Original slot format | Source fix in PR #49: full PR-head CI PASS; device NOT_TESTED | NOT_TESTED |
| Historical previously marked V2 imports (opt-in reconciliation) | PRs #52–#54 implemented a user-invoked review/copy flow; full head CI PASS; phone NOT_TESTED | NOT_TESTED |
| Theme settings and app startup | NOT_TESTED | NOT_TESTED |
| SAF persisted URI permission, Save As, encoding/BOM and conflict guard | NOT_TESTED | NOT_TESTED |
| External files unchanged; >2 MiB reader stays read-only | NOT_TESTED | NOT_TESTED |
| Unresolved Original/V2 failed-save safety copies | NOT_TESTED | NOT_TESTED |

**Phase J exit:** not PASS until production signing ownership, exact signed candidate provenance and an observed data-preserving upgrade are recorded. Phase K release and launcher promotion remain **separate explicit approvals**.

### Next safe evidence gate (release still BLOCKED)

For each previously installed Blake app that might be updated, record **non-secret** evidence: application ID, version name/code, public signing-certificate SHA-256, and install source when independently verifiable. A phone's App info screen may expose only a label and version; mark missing fields **UNKNOWN**, not guessed. Do not request private drafts, signing keys, passwords, exported app-private data, or an uninstall. An isolated `com.blake.editor.test...` APK cannot in-place upgrade production `com.blake.editor`.

Only after a compatible installed identity, authorized long-lived signer, and safe disposable baseline are established should a **separately approved signed candidate** be prepared. Capture exact candidate commit, exact-head full CI, artifact SHA-256, package/signature/version and unchanged Original launcher; verify a disposable data-preserving in-place update and a **separate** cloud/device backup-restore path. Check Original session/draft pairs and tombstones, V2 recovery/Trash/counters, both save-backup directories, themes, SAF reauthorization, external file bytes, encoding, and the >2 MiB read-only guard. Stop rollout on any mismatch; never clear data, uninstall or force downgrade as rollback.

**Next bounded gate:** run the optional, isolated, disposable-phone acceptance checks for the now-implemented review/copy UI, then record the exact build and PASS/FAIL evidence in the [phone plan](BLAKE_V2_PHASE_J_HISTORICAL_RECOVERY_PHONE_ACCEPTANCE.md). Separately establish non-secret installed-package/signature evidence. The source is implemented but **phone NOT_TESTED**, signed upgrade **BLOCKED**, and cloud/device backup restore **NOT_TESTED**. **No Phase K release, Phase L closure, production signing, or installation is authorized by this documentation.**
