# Native highlighting and line numbers — Original Blake Editor

## Scope
This is a stacked draft PR on top of PR #15 (verified on phone but not
merged). It does not change the native EditText input engine, file open,
Save/Save As, recovery or the separate Blake 2.0 Preview.

## Implementation
- Original editor retains Android EditText as the only keyboard owner.
- A left gutter paints logical line numbers; soft-wrapped visual rows do
  not receive new numbers. No Ace editor is restored.
- Lightweight syntax color spans apply directly to the current Editable.
  Never setText, replace characters, or move the selection for coloring.
- Debounced 450 ms after typing, with 64k character and 1500 match caps.
  Plain text is left uncolored. Theme changes refresh colors.
- This is an intentionally small syntax-color pass, not a complete
  language parser. Kotlin, Java, JS, Python, JSON and Markdown get basic
  strings/keywords/numbers/headings; more languages can follow.
- The gutter uses native TextView layout; wrapped line numbers need
  verification with Thai and emoji on a physical Android phone.

## Acceptance tests
1. Original editor: open .kt and .py; see syntax colors and line numbers.
2. Type Thai/English and backspace across wrapped lines. No caret jump,
   duplicated characters or stuck deletion.
3. Open a long line; visual wrapping should not increment the logical
   gutter line number. Newline should increment it.
4. Switch themes and Preview -> Edit; input remains stable.
5. Save As and reopen disposable code/text; exact content unchanged.
6. Retest PR #15 external large-file Open with and in-app OPEN routes
   before merging this stacked change.

CI is necessary but cannot prove Android IME behavior. No merges without
explicit approval.