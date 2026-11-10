# Blake Editor 2.0 — Code editor and display settings (milestone 6)

This milestone is **debug-only Blake 2.0 Preview**. It does not change the
regular Blake Editor launcher or the Save/Save As/recovery file layer.

## New code editing
- Native Android EditText remains the editing engine, retaining the Thai
  input and caret behavior tested on the user's phone in the Input Lab.
- Source files (Kotlin, Java, JavaScript, TypeScript, Python, JSON, HTML,
  XML, CSS and YAML) open directly into an editable monospace code view.
- A small line-number gutter follows real lines. Wrapped screen rows are
  not counted as new file lines. Wrapping and line numbers can be toggled.
- Basic offline coloring of keywords, strings, numbers, comments and tags.
  These are display-only character spans: the underlying document text is
  untouched. Highlighting is disabled above 80,000 characters to avoid
  slowing down typing. Editing and saving still work.
- Syntax coloring is intentionally basic, not a complete compiler/parser,
  autocomplete system or integrated development environment.

## Settings
Tap **Settings** in Home or the editor to choose:
- Theme: Follow System, Light, Dark, AMOLED, Eye Care.
- Editor font size: 12–26 sp.
- Code wrapping: On or Off. Default is Off.
- Code line numbers: On or Off. Default is On.

These settings are saved in private Android preferences and survive app
reopening. Markdown reading uses the same theme colors. Large Text Reader
has its own simple dark appearance for now.

Changing settings while editing first stores the active revision as a recovery
draft, then rebuilds the editor. Existing cursor and scroll state are restored
from the revision.

## Safety and limits
- The normal Blake Editor remains untouched. V2 changes are preview-only.
- Text input remains native Android and never changes source characters for
  syntax coloring. Unicode UTF-16 indices are used to color spans.
- File writing, encoding, Save As, drafts and large-file read-only behavior
  are unchanged.
- Code syntax coloring is bounded to 80,000 characters. Larger files remain
  editable in Preview if within the existing editing limit, but display
  plain text. Large files above 2 MiB still open read-only.
- Code line numbers are drawn over a gutter; the final visual design and
  performance across Android versions require physical phone testing.
- Light/Eye Care/AMOLED Markdown support is basic. Full accessibility,
  theme consistency and optional reader size remain to be polished.

## Phone tests
Use disposable code files, not important originals.

1. Open a .kt or .py file in Blake 2.0 Preview. Expected: line numbers
   appear and keywords, strings and comments have different colors.
2. Type Thai text and an emoji inside a quoted string, move/select the cursor,
   edit, Save, reopen. Expected: text unchanged and cursor works.
3. Settings → Code wrapping: turn On then Off. A long code line should wrap
   only when On. Toggle line numbers Off and On.
4. Settings → change font size and choose Light or Eye Care. Return Home,
   close/reopen Preview; settings should still be selected.
5. Open Markdown Reader and a normal small TXT file; reader displays
   correctly and Save/Save As still work as before.

## Automated checks
Focused JVM tests cover syntax token ranges, Thai/emoji strings, comment
precedence, length limits, line numbers, CRLF/CR and wrapped-line positions.
Android physical phone tests are separate from passing CI.
