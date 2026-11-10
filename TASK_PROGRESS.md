# Blake Editor 2.0 — crash hotfix (2026-11-11)

## Problem
User reports Blake 2.0 Preview force-closes when opening .kt, .py, .json,
and Recent Saved Files, immediately after the merged code-editor milestone #8.
This is a real-phone failure. Prior CI passed but did not exercise Android
EditText constructor callback ordering.

## Scope
Branch: fix/blake-v2-code-view-init-crash-2026-11-11
Base: main 457725c8a4bb863d6a4980e05c00a35120f0fbc5

- [x] Inspect new PreviewCodeEditText and shared showEditor() path.
- [x] Guard line-index access during the Android EditText superclass
      constructor; initialize the index only after superclass construction.
- [x] Protect editor-view creation in the preview; on a runtime exception,
      return Home and show an error while keeping the saved recovery draft.
- [x] Add JVM coverage for repeated line-index rebuilds and Thai/emoji.
- [ ] CI unit tests and APK build: not verified until the new workflow passes.
- [ ] Phone retest for .kt, .py, .json, TXT and Recent Saved Files pending.
- [ ] Confirm syntax highlighting, line numbers, themes and Save still work.

## Safety
This hotfix touches only the debug Preview view and its test. No changes to
file encoding, Save, Save As, document recovery, or regular Blake Editor.
Do not uninstall or clear app data during testing. This is a likely cause based
on source inspection; without an Android crash log the exact original stack
trace remains unverified.

## Next action
Build/debug APK with GitHub Actions; get physical phone confirmation for
opening code files and previously saved files. Keep PR in draft.
