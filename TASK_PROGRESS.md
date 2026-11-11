# Blake Editor — original editor native-input repair

## Phone blocker
User confirms PR #13 latest APK still duplicates text and jumps the caret
while typing and backspacing across English/Thai wrapped lines.
PR #13 phone result: FAILED despite green CI. Result documented in PR #13.

## New branch
fix/blake-v1-native-input-default-2026-11-11
Base main bd1f59fd7423b40cbe69b3d0f1f831b50d23f5af

- [x] Original V1 normal editing uses Android native EditText.
- [x] WebView retained only for formatted Markdown Preview.
- [x] Document-generation sync prevents re-applying typing to input.
- [x] Native Undo/Redo history with bounded RAM.
- [x] Line-column display and pure regression tests.
- [ ] GitHub Actions unit tests and APK build pending.
- [ ] Real phone wrap/backspace, Thai/emoji typing tests pending.
- [ ] Recheck Save, Markdown Preview and separate V2 Preview on phone.

Trade-off: old Ace code colors and line-number gutter temporarily absent
on the original launcher. Native V2 Preview retains those features.
No changes to file writing or recovery. Keep draft PR unmerged until
phone evidence confirms typing reliability.
