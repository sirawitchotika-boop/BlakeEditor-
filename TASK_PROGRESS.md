# Blake Editor 2.0 — active rebuild

## Previous verified stages
- Merged PRs #1–#9 contain native text input, independent drafts, Home and
  Quick Switcher, Save/Save As, Markdown Reader, large text paging, code colors
  and settings, and the code-view constructor crash fix.
- User reported all five phone tests passed after PR #9, including .kt/.py/.json,
  Recent Saved Files, Thai/emoji typing, code colors and Save.
- Prior CI tests and debug APK builds passed.

## Milestone: Search, Replace, and quick-symbol toolbar
Branch: feature/blake-v2-search-replace-toolbar-2026-11-11
Base: main 3efca3a9f33c35307b4291b5d4493299c830f60a

- [x] Pure literal search, Next/Previous with wraparound, Replace All with
      case matching and no silent partial replacement.
- [x] Android Find/Replace panel and confirmed Replace All.
- [x] Native code toolbar for quick symbol insertion at the caret.
- [x] JVM tests for ordinary text, Thai, emoji, symbols, replacement loop
      prevention, and selection-aware insertion.
- [ ] GitHub Actions tests/build: unverified until completed.
- [ ] Real Android test for search, replace, symbol insertion and recovery.
- [ ] Production V2 launcher and remaining UI polish remain future work.

## Safety
All UI additions are debug-only Blake 2.0 Preview.
Regular Blake Editor, SAF file saving, recovery format and large-file read-only
viewer were not modified. No release/deploy/merge authorized.

## Next action
Open draft PR, verify GitHub Actions APK build and focused JVM tests, then
test search/replace, Thai and emoji, toolbar and Save on an Android phone.
