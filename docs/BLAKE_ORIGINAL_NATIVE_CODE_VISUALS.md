# Blake Editor — native syntax highlighting and line numbers in Original

## Background
On the phone, the **Blake Editor — Original** Ace/WebView keyboard previously
caused caret jumps, unending text duplication and stuck Backspace, especially
with Thai/English input. PR #14 replaced Ace input with Android native
EditText, and the user confirmed all original-editor tests passed.
This milestone restores **code colors and real line numbers** without
reintroducing Ace keyboard handling or replacing text while typing.

The user also tested the revised PR #15 file-size routing successfully:
Android Files > Open with Blake Editor and Original > OPEN both route large
TXT to read-only Previous/Next. Four additional reader/Save/draft safety
checks passed on phone. PR #15 remains an unmerged draft pending permission.

## Design
- Build from PR #15's verified branch. The next APK must retain its
  large-file safety checks. This is a stacked review branch, not a merge.
- Keep the existing AppCompatEditText and Android IME input connection.
  **No editor.setValue, setText during ordinary typing, setSelection from
  highlighting, or WebView keyboard handling.**
- NativeInputEditText draws gutter numbers against the existing
  CodeLineIndex. A visual soft-wrap is not an extra logical line.
- Color **only** syntax spans with ForegroundColorSpan on the existing
  Editable. Never replace or rewrite the source text.
- Check Android IME composing spans first; do not recolor during active
  composition. This is important for Thai and emoji input.
- Reuse bounded CodeSyntaxScanner on Dispatchers.Default; debounce 420ms
  after edits; drop stale results when text, language, or preview changes.
  Cancel highlighting work when the editor goes into the background.
- For a small (<=80,000 UTF-16 code unit) source file, show a gutter
  for Kotlin, Java, JavaScript, TypeScript, Python, JSON, HTML, XML, CSS,
  YAML, SQL, shell, C/C++, Rust and Go.
- Syntax colors are currently supported for Kotlin, Java, JavaScript,
  TypeScript, Python, JSON, HTML, XML, CSS and YAML. Other listed code
  types get accurate line numbers but plain text until tokenizers exist.
- Plain TXT and Markdown retain native uncolored text with no gutter.
  For over 80,000 UTF-16 code units, highlighting and the gutter are
  skipped for low memory/CPU use. This does not limit normal editing.
  Files over 2 MiB are still intercepted by the read-only reader.
- Dark, AMOLED and Eye Care have separate readable token/gutter palettes.
- Color/gutter setup runs only when a file, language, theme or size
  threshold changes, NOT on every cursor or StateFlow emission.

## What stays unchanged
- Keyboard, selection, Backspace, Thai composing, wrapping and undo/redo.
- New, Open, Save/Save As, recovery and existing safety/encoding handling.
- Large Text Reader and its read-only protection from PR #15.
- Offline Markdown Preview inside Original (WebView preview only).
- The separate debug launcher **Blake 2.0 Preview** and its code viewer.
- No new background services, alarms, wake locks or continuous scans.

## Verification and remaining risks
- JVM tests cover supported modes, size bounds, theme palettes and
  UTF-16 Thai/emoji offsets. Existing scanner, logical line index and
  file-safety tests run in the full CI suite.
- Android physical keyboard IME behavior, gutter paint/rendering,
  system themes and low-power impact cannot be proven by JVM tests.
- A documented editor input regression must be treated as
  release-blocking; do not merge the draft PR until the user checks it.
- Highlighting may wait until a composing session ends to avoid
  interfering with Gboard/OEM keyboard behavior.

## Five phone checks (use a disposable TXT and code file)
1. Install the new debug APK over the existing one, without uninstalling
   or clearing app data. Open **Blake Editor — Original**, not 2.0 Preview.
2. Open a small .kt or .py file: see colored keywords/strings/comments
   and line numbers. Type Thai, English and emoji, delete through a
   wrapped line and edit the middle of a string. No caret jump, stuck
   Backspace or duplicate/ghost characters.
3. Add/delete true newline characters and make a long visual wrap.
   Numbers should increase only at **actual newlines**, not wrapped rows.
4. Switch Dark, AMOLED and Eye Care. Colors must remain readable.
   Use Undo/Redo and Save As a disposable code file; reopen and confirm
   characters stay exactly as typed.
5. Open the large >2 MiB TXT through Android Files > Open with Blake
   Editor. It must still go to read-only reader with Previous/Next.
   Open the separate Blake 2.0 Preview launcher to confirm no regressions.

Do not merge the PR or claim device pass until these checks are reported.
