# Blake Editor 2.0 — active rebuild

## Verified previous stages
- PR #1 native input baseline and encoding; user's phone checks passed.
- PR #2 independent recovery storage; PR #3 Home and Quick Switcher.
- PR #4 Save and Save As; user reports all basic and safety tests passed.
- PR #5 Markdown Reader + large text reading.
- PR #6 and #7 large-reader bugs repaired; user reports all phone checks
  passed, including Next and Previous. All PR #1–#7 have been merged.
- GitHub Actions unit tests and debug APK builds passed for these prior PRs.

## Current stage: code view and settings
Branch: feature/blake-v2-code-tools-settings-2026-11-11
Base main: 12bb9bcf08ebc2cb09a2f3c12d0767f019b57732

- [x] Native code view with basic offline syntax spans and logical line numbers.
- [x] Preview Settings: five themes, editor font size, code wrap, line numbers.
- [x] Local preference persistence and Markdown reader theme support.
- [x] JVM tests added for token scanner and line start index.
- [ ] GitHub Actions unit tests and APK build for current stage pending.
- [ ] Physical Android code-editing and settings tests pending.
- [ ] Final production launcher, search, extra-key toolbar, polished
      appearance, large-file indexing and other V2 release checks pending.

## Safety limits
Changes are debug-only Blake 2.0 Preview and do not alter regular Blake
Editor, file encoding, Save/Save As, or recovery. Highlighting is intentionally
bounded to 80k characters. Large files above 2 MiB remain read-only.
A successful CI build does not prove native Thai/selection visuals across
devices.

## Next action
Verify Actions build. Test .kt/.py editing, Thai emoji, Code wrap,
line numbers, theme persistence and Save/Save As on the user's Android phone.
