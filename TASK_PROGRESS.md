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
