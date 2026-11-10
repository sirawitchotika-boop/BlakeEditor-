# Blake Editor 2.0 — active rebuild

Objective: implement the approved Blake Editor 2.0 design through reviewable,
safe milestones, without changing the default branch directly.

Working branch: feature/blake-v2-foundation-2026-11-10
Base branch: main
Base commit: f5cd8b715f8512437380b7d771f765fd1e3cc177

## Milestone checklist

- [x] Inspect current file access, native UI, WebView editor and old draft model.
- [x] Keep the original app unchanged while adding a debug-only native input lab.
- [x] Add strict standalone encoding helper and targeted unit tests.
- [x] Enable CI for pull requests; keep manual builds available.
- [ ] Check GitHub Actions build and tests. (Not run or unverified until CI confirms.)
- [ ] Real-device Thai typing and caret tests; compare a native code editor.
- [ ] Add stable document IDs and independent, atomic recovery drafts with migration.
- [ ] Integrate lossless encoding/line-ending handling and safe Save/Save As.
- [ ] Rebuild Home, Workspace, Quick Switcher and balanced Settings.
- [ ] Add large-file reader, Markdown reading, code tools and final polish.
- [ ] End-to-end and real-device release verification.

## Known risks

- The current app still uses Ace/WebView and a single "untitled" draft slot.
- Existing code uses UTF-8-only file I/O. The new codec is not integrated yet.
- GitHub Actions uses online dependency downloads during the build.
- The native input prototype is not proof of physical cursor alignment.
- Do not merge the draft PR as a finished Blake Editor 2.0 release.

## Next action

Inspect the PR CI result and install its debug APK on a phone. Record Thai caret
test results before selecting the final editor engine.
