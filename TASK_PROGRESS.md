## Current milestone — opt-in V2 navigation from Original (2026-11-11)

- **Verified baseline:** PR #23 merged to `main` at `309134c44834112b4d2b731c475dddad676f4049`. User confirmed physical phone tests 1–5 PASS and GitHub Actions run #43022329708 SUCCESS on the tested isolated APK.
- **Working branch:** `feat/blake-v2-opt-in-entry-2026-11-11`, based on the exact main SHA above.
- Keep `MainActivity` as the only production launcher and `ExternalFileRouterActivity` as the external text/file receiver. Add a clearly labeled **Open Blake 2.0 (Beta)** button in Original, with a pure navigation safety gate: active file operations BLOCK; dirty Original documents require an explicit warning; switching never calls discard.
- Register V2 workspace and V2's separate read-only large-text reader as **non-exported internal Activities** in the main manifest, with no intent filters. Keep the separate debug launcher icon by changing its declaration to a debug-only `activity-alias`. Original still opens by default, including Android Files external Open with.
- CI checks both debug and release APKs, confirms Original is the sole release launcher, V2 is only internal/non-exported, no debug Input Lab or alias appears in release, and Markdown Reader assets remain packaged.
- Existing V1/V2 file writing, drafts, encoding, URI routing, size limits and shared app ID are not changed. Isolated test APK uses unique per-run package ID so no uninstall or data wipe is needed.
- **Check status at commit creation:** tests, APKs and phone results NOT YET RUN on this new branch; verify CI before asking Boss to phone-test it.
- **Next action:** draft PR, CI debug/release/isolated build, then 5 phone checks; no merge without explicit Boss approval.

---

## Latest checkpoint — V2 workspace source-set staging (2026-11-11)

- **Baseline:** PR #22 merged into `main` at `0fd2b057eaf98e8376a9dcced7afbebca2c58534`, after 5/5 user-reported phone checks on the isolated APK and passing GitHub Actions run #43018419501.
- **Current branch:** `feat/blake-v2-workspace-main-source-2026-11-11`, created from the verified baseline above.
- **Changes in this milestone:** relocate `V2WorkspacePreviewActivity` and its read-only V2 `LargeTextReaderActivity` from debug Kotlin into the shared `src/main` Kotlin source set, keeping package names and behavior unchanged. Move the Markdown Reader HTML/CSS/JS assets and the V2 preview theme into main resources so these shared Activities have their assets in a normal release build.
- **No production activation:** both V2 Activities remain registered **only in `src/debug/AndroidManifest.xml`**. `src/main/AndroidManifest.xml` is unchanged, with Original as sole MAIN/LAUNCHER and the existing external file router. No V2 navigation path is enabled in release.
- Extend GitHub CI with `assembleRelease` (not published) and an APK manifest/reader-asset assertion before the isolated side-by-side APK. Keep the existing debug build, test suite and isolated app ID behavior.
- **Safety:** do not touch the Original editor, URI routing, file writers, draft/backup formats, permission grants, or installed app data.
- **Checks:** CI and device tests are NOT RUN at the time of this commit; report actual outcomes in the PR. Never merge without Boss approval.
- **Next action:** Open draft PR, verify the new release gate and isolated test APK, then ask Boss for 5 short phone checks.

---

## Latest checkpoint — Blake 2.0 display foundation (2026-11-11)

- **Merged and phone accepted:** PR #21, main merge commit `25f196875e071f6bd091df6081ac1d7e2e8a9152`. User reported phone checks 1–5 **PASS**, CI run #43013369328 **SUCCESS**.
- **Current branch:** `feat/blake-v2-share-display-foundations-2026-11-11`, based on `main` at `25f196875e071f6bd091df6081ac1d7e2e8a9152`.
- **Next incremental step:** Move V2 Preview's native code input view, theme settings, safe-area math and system-bars helper from `src/debug` into `src/main` under the **same package and class names**. Keep V2 Preview using them. This shares UI building blocks with the regular app but **does not move the V2 Activity or enable a V2 production launcher**.
- Preserve `blake_v2_preview_editor_settings` preference name and user data; add pure JVM coverage for system-following and explicit theme selection. Existing camera/keyboard-inset regression tests still apply.
- **Forbidden scope:** changing the Original editor, external intents, file size routing, file writers, draft formats, debug-only manifests, dependencies or release behavior.
- New GitHub Actions build/unit tests and phone tests: **NOT RUN at this checkpoint**.
- **Next action:** open draft PR, verify CI and APK, then phone-test Preview display/settings, Original input, and external large-file reader. No merge without explicit Boss approval.

---

# Blake Editor — verified checkpoint (2026-11-11)

## Status — Original editor safe baseline

- `main` merge commit: `ef1f49a90bb63764e96068d90b5a08951087e1f7`, from [PR #15](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/15).
- The merged app combines **Blake Editor — Original** native EditText, native code syntax colors and logical line numbers, and guarded large-file opening with a read-only pager over 2 MiB.
- The Original native-input recovery was merged earlier in PR #14. No Ace/WebView keyboard input has been restored; Markdown Preview remains separate.
- The code-tree at the merged PR #15 head is identical to the combined APK the user tested from [run #42998322147](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/42998322147).
- Independent CI [run #42999054525](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/42999054525) **PASSED** on the exact PR #15 head: WebView input regression, unit tests, and debug APK assembly.
- **Physical phone acceptance: PASS 5/5 (user report)** on the combined test APK:
  1. Original small code file: syntax colors and logical gutter.
  2. Original Thai/English/emoji keyboard, wrap/backspace, selection/paste.
  3. Dark / AMOLED / Eye Care appearance.
  4. Undo/Redo, Save As, reopen, Markdown Preview.
  5. Android Files external Open with >2 MiB: warning, read-only pager with Previous/Next and no Save.
- Additional earlier large-file safety checks also passed: in-app OPEN, unchanged file after reader use, normal TXT Open with/Save As, and cancel preserving unsaved drafts.

## Cleanup

- PR #15: merged to `main`.
- PR #19: previously merged **into PR #15's feature branch**, supplying the phone-tested combined code.
- PRs #16, #17 and #18: **closed, unmerged** as alternate/duplicate native-code-highlighting approaches already covered by PR #15.
- No PR #16, #17, or #18 code should be re-applied to `main`.
- Do not uninstall the Android debug app or clear its data as part of verification.

## What remains separate

- **Blake Editor — Original** is the production MAIN/LAUNCHER Activity in `app/src/main/AndroidManifest.xml`.
- **Blake 2.0 Preview** and **Blake Input Lab** are declared in `app/src/debug/AndroidManifest.xml`, not the production launcher.
- Blake 2.0 Home, draft switching and settings are not being promoted to the regular launcher in this cleanup.
- No newly built APK or phone verification is implied by this documentation-only checkpoint.

## Next action — V2 production-readiness gate

Review [the V2 promotion gate](docs/BLAKE_EDITOR_V2_PROMOTION_GATE.md) and prepare a **separate, small draft PR based on current main** to make the V2 workspace reusable outside the debug source set **without changing the default launcher, external file routing or current file writer**. Review source-set dependencies and safe navigation first; add focused tests. Verify build and phone results before any wider rollout or launcher change.

**Do not merge, publish, change the default launcher or replace stable Original without Boss's explicit approval.** Historical failure details remain in `docs/BLAKE_EDITOR_LARGE_EXTERNAL_OPEN.md` and prior PR timelines.

## V2 shared Home/recovery boundary — implementation checkpoint (2026-11-11)

- Branch: `feature/blake-v2-shared-home-policy-2026-11-11` (created from `main` at `de508a89673fa0ffb245f7601e06db5a2d9c2691`).
- Move the existing Android recreation and quiet-autosave policies unchanged from the debug source set into the main source set. The preview keeps using the same packages/classes and current tests.
- Extract read-only V2 Home draft grouping to `session.v2.V2HomeSections`; Preview Home now calls it for Continue Writing / Recent Saved Files. Add JVM tests for empty/grouped/unreadable cases.
- **Original launcher, external file router, code input, real-file writer, recovery bytes, debug manifest and V2 UI layout remain unchanged.** No new app entry points or release changes.
- GitHub Actions unit tests and debug build: **NOT RUN yet at this checkpoint**; real-phone tests **NOT RUN** for this branch.
- Next action: open a draft PR, check CI, then test the new debug APK on Android before a merge decision.
