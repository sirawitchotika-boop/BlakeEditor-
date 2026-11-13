# Phase J — Release signing, backup and safe-upgrade gate

**Audit date:** 2026-11-13 (Asia/Bangkok)  
**Project:** `sirawitchotika-boop/BlakeEditor-`  
**Exact reviewed main:** `f4be4149bc7183ec5472e5d4d493691f9db02a07`  
**Current gate:** **BLOCKED** for a signed production release. This is a read-only **source audit** recorded in a documentation-only draft PR; it does **not** approve signing, distribution, installation, merge, backup-policy changes or launcher promotion.

## Evidence and known results

- Phase H accepted-main CI [#43259241007](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43259241007) passed on `6fc1b22...`. Phase I phone acceptance on that isolated debug build was **USER-REPORTED PASS**, not a signed upgrade test.
- PRs [#45](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/45), [#46](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/46), and [#47](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/47) are merged. #47's accepted PR-head CI [#43381841942](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43381841942) passed at `e594c16aadc4a3ad842331d7a76792bf6549f7de`. Its Git tree `05391c29525c5d8a19f5d1b20c5f31597bb6a8ee` matches merged main `f4be4149...` exactly. **No separate exact merge-commit CI run** has been observed.
- Production application ID is `com.blake.editor`, `versionCode=1`, `versionName=0.1.0`. Release build in `.github/workflows/build-apk.yml` is **unsigned**. Normal release has one launcher (Original), V2 Beta remains opt-in. Debug and isolated test APKs use **different application IDs**.
- Historical isolated test APKs [#43124844965](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43124844965) and [#43259241007](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43259241007) were verified to have different package IDs and different debug signing certificate fingerprints. Their signing keys **cannot establish** a stable production update path. Which exact package/signature is installed on the user's phone is **NOT_VERIFIED**.
- No production GitHub Release or approved long-lived production signing identity was found during this audit. Do not invent a signing key, owner or release channel.

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

## Confirmed source-level compatibility gap after PR #47

`FileDraftRepository.saveDraft()` now stores current Original content and metadata in alternating `.draft.0/.1` and `.meta.0/.1` slots and publishes `.active` only after both succeed. It intentionally leaves legacy pairs alone.

However, `RecoveryDraftStore.importLegacy()`, invoked by `V2WorkspacePreviewActivity.onCreate()`, still lists only filenames ending in `.meta` and reads only the corresponding `.draft`. It does not resolve the new `.active` marker or slot pair.

**Risk:** if V2 performs its first Original-draft import after an Original save in the new format, it can miss that draft (when no legacy pair exists) or import **older** legacy content (when an old pair remains). It can then write `legacy-imported.marker`, preventing another automatic import. This is a source-supported migration gap, **not** proof that a phone lost data. Original's current pair stays in Original.

**Required before release:** a separate focused code/test PR makes V2 import the currently committed Original pair, respects `deleted` tombstones, fails closed on corruption/incomplete pairs, preserves original source files, and never overwrites an existing V2 recovery record. Include first-import, legacy-only, committed-slot, tombstone, and corrupt-marker tests. Do not change migration behavior in this documentation PR.

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
| V2 import from current Original slot format | **KNOWN SOURCE GAP; fix first** | **KNOWN SOURCE GAP; fix first** |
| Theme settings and app startup | NOT_TESTED | NOT_TESTED |
| SAF persisted URI permission, Save As, encoding/BOM and conflict guard | NOT_TESTED | NOT_TESTED |
| External files unchanged; >2 MiB reader stays read-only | NOT_TESTED | NOT_TESTED |
| Unresolved Original/V2 failed-save safety copies | NOT_TESTED | NOT_TESTED |

**Phase J exit:** not PASS until production signing ownership, exact signed candidate provenance and an observed data-preserving upgrade are recorded. Phase K release and launcher promotion remain **separate explicit approvals**.

### One safe next work unit
A **new, small draft code PR** to update the V2 Original-draft importer for PR #47's committed slot format, with failure/compatibility regression tests. Do not sign, merge, deploy or install as part of that work.
