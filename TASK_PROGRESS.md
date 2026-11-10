# Blake Editor 2.0 — active rebuild

## Hotfix: Large Text Reader startup (2026-11-11)

Base: main, commit 537cff003d699b723845159cef0e59e3b13e9375
Working branch: fix/blake-v2-large-reader-no-bom-2026-11-11

- [x] User supplied Android screenshots: Large Text Reader for large_text_over_2MB.txt displays
      "Cannot open document: Could not open file" and Next/Previous cannot work.
- [x] Root cause confirmed: a missing byte-order mark (BOM), normal for UTF-8,
      returns null and was mistaken for a failed Android file input stream.
- [x] Treat no BOM as UTF-8; report an error only when the stream really cannot open.
- [x] Add regression tests for BOM-less Thai/English paging, empty content,
      and UTF-16 BOM detection.
- [ ] Verify Android GitHub Actions unit tests and APK build for this hotfix.
- [ ] User phone test: open the same large TXT, Next, Previous; verify original unchanged.

Prior milestones PR #1–#5 are merged. The user reported passing input,
independent drafts, Save/Save As, encoding, conflict and small-file testing.
Markdown and large-file phone checks remain incomplete.

The old Blake Editor launcher is unchanged. The large reader remains read-only.

## Next action
Run CI on the hotfix PR. If the APK builds, retest the original 4 MB TXT file,
page navigation and Markdown Reader. Do not merge or release without review.
