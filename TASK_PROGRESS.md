# Blake Editor 2.0 — active rebuild

## Confirmed work
- [x] Foundation PR #1 merged: native Android input lab and encoding codec.
- [x] User reports all five native input phone tests passed (2026-11-10).
- [x] Draft recovery PR #2 merged: unique IDs, atomic independent drafts,
      legacy migration, and revision protection.
- [x] Foundation and recovery CI unit tests and debug APK builds passed.
- [x] Debug-only V2 Home and Quick Switcher implementation added on a new branch.
- [ ] V2 Home / Quick Switcher CI and physical device tests pending.
- [ ] Production Home and Editor, actual Save/Save As, Recent Files, large-file
      streaming, Markdown reading, themes and coding tools not completed.

Working branch: feature/blake-v2-workspace-preview-2026-11-10
Base main: 4ec1df23acd819940eb54a810ca5617961ac54b3

## Current milestone safety
The debug-only launcher "Blake 2.0 Preview" writes recovery drafts internally
and never overwrites real files. Open File preview limit is 2 MiB. Existing app
is unchanged.

## Next action
Verify CI, test New File/Home/Switch/close-and-reopen on a real Android device,
then design file Save/Save As without losing the source encoding.
