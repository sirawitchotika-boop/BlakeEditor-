# Blake Editor 2.0 — Search, Replace and code quick-symbol toolbar

This feature is in the **debug-only Blake 2.0 Preview**. It does not change
the normal Blake Editor, the file-saving implementation, recovery storage,
or the read-only Large Text Reader.

## Editing actions
- Tap **Find / Replace** on the document editing screen. Markdown must be in
  Edit Source mode; the formatted Markdown reader has no search UI yet.
- Enter a phrase and tap **Next** or **Previous**. A match is selected in
  the native Android EditText. Search wraps around at document boundaries.
- **Match case** is optional. Default search ignores case. Search is literal,
  **not a regex**: punctuation and symbols are treated as ordinary text.
- Tap **Replace** while a matching phrase is selected to replace it. If the
  selection is not a match, the first tap selects the next one and asks for
  another tap before replacing.
- **Replace All** shows the number of matches and requires a second explicit
  confirmation. The tool checks that the document has not changed while
  looking for matches. It edits the recovery-backed native editor only;
  the original file still changes only when the user chooses Save.
- A replace-all limit of 100,000 matches protects the app from extreme work.
  No partial replacement is silently accepted.
- Find, Replace and Replace All use UTF-16 offsets, matching native Android
  cursor and selection positions. Thai and emoji are covered by unit tests.

## Code quick symbols
For supported code files only, a horizontal strip below the editor offers:
Tab (four spaces), braces, parentheses, brackets, semicolon, colon, equals,
quotes, slash, backslash, comparison signs and hash.

Tapping a symbol inserts it at the caret or replaces the selected text. It
keeps the native Android keyboard and selection engine. It does not write
directly to phone storage or replace the original file.

## Known limits
- This is a preview-only, literal (not regular-expression) search.
- Find/Replace is available in editable views, not the large-file read-only
  reader or the formatted Markdown reader.
- For "Replace All" the whole document is updated in the native editing
  buffer. Large editable files remain limited to the existing 2 MiB threshold.
- There is no undo/redo control in this milestone; use a disposable copy to
  test bulk replacements. Recovery storage protects the active edits before
  navigation but does not provide an unlimited undo history.
- Android selection, keyboard visibility and layout need real-device checks.
  A successful Gradle build alone does not confirm Android interactions.

## Phone test plan
Use disposable copies, not your important files.

1. Open a .kt or .py file. Tap Find / Replace, search for a word, use Next
   twice and Previous once. Confirm the highlighted match moves correctly.
2. Replace one selected match with a different word. Use Replace All on
   another word and confirm the dialog's count before accepting.
3. Search and replace Thai words, emoji and literal symbols such as "." or
   "[]". Confirm nothing unrelated changes.
4. Tap Tab, braces, quotes or other quick symbols in the code editor.
   Confirm symbols appear at the caret, including in Thai/emoji text, and
   that the keyboard and selection still work.
5. Tap Home/reopen the draft, and Save a disposable file. Confirm recovery,
   recent documents, code colors and line numbers remain functional.
