# Phase J — Historical Original/V2 draft recovery: safe phone acceptance

**Prepared:** 2026-11-13 (Asia/Bangkok)  
**Status:** `PHONE_NOT_TESTED` / `SIGNED_UPGRADE_BLOCKED`  
**Source reviewed:** merged `main` at `31e0d6dfee423d33f874892d9bf915b62c5d1aed`  
**Feature PR:** [#54](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/54) — **MERGED**  
**Exact tested source head:** `db89621f0c6a581cf4265c567c9c2c8cceba36cc`  
**Git tree:** `068b64ee987bc2a8a590b46f831b2daf9d232030` (same on the PR head and squashed `main`)  
**Automated checks:** [full CI #43401077651](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43401077651) — completed `success` (JVM tests, JS regressions, debug, unsigned release and isolated APK builds, package/launcher/asset checks). This is **not** a phone, signed-upgrade or backup-restore PASS.

## Disposable phone candidate — exact build

- In [Actions run #43401077651](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43401077651), under **Artifacts**, select **BlakeEditor-Isolated-Test-APK** (not the ordinary Debug APK).
- The workflow used `github.run_id=43401077651`, `github.run_attempt=1`; its verification expects the isolated application ID `com.blake.editor.test43401077651a1`.
- The displayed test launcher labels include a shortened run identifier. Both Original and Blake V2 Beta are available in this **isolated test app**.
- GitHub reports this artifact expires **2026-11-20 09:26:39 UTC**. If expired, build and verify a **new exact-head isolated candidate** and update this document's candidate/result fields. Never describe a newer run as the same APK.
- This is a separate test package/sandbox. **It does not upgrade `com.blake.editor` or transfer existing private drafts.** Do not uninstall, clear data, replace, or overwrite any existing Blake app. Do not install or test without the user's deliberate choice and an appropriate disposable phone/profile.

## Five short manual checks — all NOT_TESTED

Use only throwaway, invented text. If an expected result fails, stop and preserve the app and data. **Do not use real notes or documents.**

1. **First import:** In the isolated test app's **Original** editor, type `ORIGINAL-TEST-A`. Leave it so its draft saves, reopen Original to verify `A` remains, then open **Blake 2.0 Beta**. Expected: the existing draft enters V2 recovery on its first visit; nothing is removed from Original.
2. **Create a historical difference:** Go back to the **Original editor in the same isolated test app**, change the saved draft to `ORIGINAL-TEST-B`, leave and reopen Original to verify `B`. In V2 Beta **Home → Review Original drafts**, select the matching draft. Expected: both versions appear (`B` under Original, `A` under V2). It must not choose one automatically.
3. **Cancel stays read-only:** Tap **Cancel**, reopen both screens, and confirm Original still has `B` and the existing V2 draft still has `A`. Expected: no new recovery copy and no change to Original, V2, Trash, external files, or the marker.
4. **Explicit recovery copy:** In V2 Beta **Home → Review Original drafts**, select the difference and tap **Keep Original as separate copy**. Expected: a separate, unlinked V2 recovery draft containing full `ORIGINAL-TEST-B`. Original retains `B`, the previous V2 record retains `A`, and neither is overwritten. Repeat the confirmation on the same unchanged versions; expect no second identical recovery copy. This is a private draft, **not** an external saved document.
5. **Safety after restart:** Close and reopen the isolated test app. Expected: the new copy remains in V2 Home, previous records remain available, and no automatic review or replacement takes place. If testing with an isolated disposable linked file, verify it was not changed by viewing/creating a copy. **Do not experiment with real files or an existing production package.**

For an empty/corrupt Original source or an existing `deleted` marker, the source design must fail safely and never restore deleted text automatically. These error paths have automated JVM coverage; they are **NOT_TESTED on phone** by this checklist.

## Evidence to record (no private data)

| Item | Result |
| --- | --- |
| Device model / Android version | UNKNOWN |
| Isolated package ID | Expected `com.blake.editor.test43401077651a1`; phone NOT_VERIFIED |
| Exact candidate run / source head | `43401077651` / `db89621f0c6a581cf4265c567c9c2c8cceba36cc` |
| Checks 1–5 | All NOT_TESTED |
| Failure number and short symptom | NONE REPORTED; NOT_TESTED |
| Original installed `com.blake.editor` package/version/public certificate | UNKNOWN / NOT_VERIFIED |
| Compatible production signing identity | NOT_CONFIGURED / NOT_VERIFIED |
| Signed in-place-upgrade result | NOT_TESTED |
| Backup/restore or device-transfer result | NOT_TESTED |

Do not post private draft text, credentials, keystores, secret signing material, app-private files, or real document locations. Public package IDs, app version codes, and public signing-certificate SHA-256 are sufficient identity evidence if independently available.

## Gate and stop conditions

- **Phase J historical recovery phone gate:** `NOT_TESTED` until Boss reports the exact isolated candidate's check results. A successful APK build alone is not a device PASS.
- **Phase J signed release gate:** **BLOCKED**, separately requires authorized stable production signing identity, actual install-identity continuity, a safe disposable matching-signature in-place upgrade and distinct backup/restore test. The isolated test APK proves none of those.
- **Phase K distribution / Phase L closure:** not authorized. Original stays the regular production launcher. No app, manifest, backup-rule, version or signing changes are made by this documentation.

**Next human action:** If deliberately testing on a disposable device/profile, use the verified isolated artifact and report `1 PASS, 2 PASS, 3 PASS, 4 PASS, 5 PASS`, or the failed numbers with a short symptom. Otherwise the gate remains `NOT_TESTED`.

**Next engineering action if all phone checks pass:** Read the results and continue the separate non-secret signing/upgrade evidence gate; do not publish a release or change production signing automatically.
