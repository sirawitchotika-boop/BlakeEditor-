# Original Blake Editor — native keyboard reliability hotfix

## User phone report
The user confirmed on November 11, 2026 that the latest APK after PR #13
still has serious original-Ace typing bugs. Backspace across wrapped lines
jumps, deleting can get stuck, and English/Thai letters duplicate.
PR #13 passed CI but did not resolve the real-phone issue.

## Change in this PR
The original Blake Editor now uses native Android EditText for regular
typing, deletion, selection and emoji composition. The Ace WebView is kept
only for offline Markdown formatted Preview.

A documentGeneration value changes only for New, Open and Restore. This
ensures StateFlow emissions from normal typing or moving the caret never
send text back into EditText and interrupt the active keyboard input.

New, Open, Save, Save As, themes and offline Preview retain their existing
implementation. Undo/Redo use bounded native snapshots with a memory cap.
The line/column indicator follows logical text lines using UTF-16 positions.

## Intentional trade-off
To prioritize input reliability, the original launcher temporarily has no
Ace syntax colors or Ace line-number gutters. The separate Blake 2.0 Preview
still has native code colors and line numbers. Restore these to the normal
launcher only after typing and deletion pass physical phone testing.

No changes to disk file writing, recovery formats, original text encoding,
or large-file read-only behavior. No background service or wake lock.

## Phone tests
Install the new debug APK over the existing one. Do not uninstall or clear
app data. Use a disposable TXT test file, never an important original.

1. Open the original editor and verify the status says Plain Text · Native.
2. Type a long English line until it wraps. Delete backward through the
   visual wrap, then repeat. No stuck characters, jumps, or duplication.
3. Type Thai and emoji, delete combining marks and edit inside a sentence.
   Cursor and characters must remain stable, without ghost text.
4. Try Undo, Redo, Save As, reopen and formatted Preview, then return to Edit.
5. Open the separate Blake 2.0 Preview and confirm it still works.

CI success cannot prove Thai or vendor keyboard interaction on a phone.
Do not merge or release until user reports a passing real-device test.
