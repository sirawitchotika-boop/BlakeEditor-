# Blake Editor — critical cursor/typing regression (V1 Ace WebView)

**Reported 2026-11-11**. A physical Android screenshot shows the original
Blake Editor toolbar (**NEW / OPEN / SAVE / SAVE AS / UNDO / REDO / THEME /
PREVIEW**) rather than the separate debug **Blake 2.0 Preview**.

User reported in both Thai and English:
- Backspace at a wrapped line boundary jumps the caret to the line start.
- Backspace stops deleting at certain characters.
- Previously entered text duplicates while typing.
- Deleting can move the caret before/after a line and appear to append text
  continuously, making the document unusable.

## Diagnosis from source

The original editor uses **Ace inside Android WebView**, and sends every
content, caret and scroll event to Kotlin through an Android JavaScript
interface. The old bridge passed events directly from the WebView private
background thread into StateFlow/ViewModel and activity cache state.

The activity then observed the Flow and sometimes sent the same content
**back to Ace** via `SetContent`. Ace's `editor.setValue(text, -1)` resets
the selection/undo state. That can create a feedback loop with Android IME
composition, particularly during rapid typing, backspace and soft wrapping.
The screenshot and behavior support this diagnosis, but only device testing
can confirm which part caused each symptom; Ace's IME implementation may
have additional issues.

## Fix in this PR
- Dispatch all WebView bridge events back to Android's main UI thread,
  serializing them with UI StateFlow collection.
- Track the last value that Ace already contains. Do not echo native
  StateFlow updates that originated from Ace.
- Do not send a second manual `SetContent` when pressing New; only the
  new document state drives the reset.
- Make JS `EditorHost.setContent` **idempotent**: when value is already the
  same, keep current caret, IME composition, selection and undo history.
  Genuine opens of a different file still replace the editor content and
  reset the undo history.
- Add pure Kotlin regression tests for typing, Thai combining marks,
  emoji, deletion, cursor-only updates, and changing documents.
- Add a Node.js test harness for the real `editor-host.js`, checking
  identical content/soft-wrapped text does not invoke Ace `setValue`.
  The existing GitHub Actions build now runs this test before Gradle.

## Safety
- Only old V1 WebView synchronization, its JS host and tests change.
- Does NOT change file writes, backups, Save/Save As, V2 Preview, the
  native EditText, or recovery records.
- No document gets edited or reformatted automatically; do not test
  with an irreplaceable file.
- This is a *first focused fix*. More Ace/Android keyboard issues may
  remain until verified on the user's Realme Android phone.

## Five phone acceptance tests
1. Install the **new** debug APK on top of the old one. Open the original
   Blake Editor (NEW / OPEN / SAVE toolbar), not Blake 2.0 Preview.
   Use a disposable TXT, not an important original.
2. Type `abcdef`, then backspace one character at a time until the line
   is empty. No unexpected duplicates, trapped character or caret jump.
3. Type a long English sentence so it wraps visually, and backspace
   across that wrapped boundary. The caret must move naturally; no
   teleport to the start of a line.
4. Type and erase Thai text, including combining marks, and emoji.
   Repeat across wrapped lines. No duplicated characters or stuck
   backspace. Test a few paragraphs.
5. Save a disposable copy and reopen it. Exact characters should be
   retained. Confirm the independent **Blake 2.0 Preview** still works.

## Limitations
JVM and Node tests cannot simulate Android's physical keyboard/IME and
vendor WebView behavior. The phone acceptance test is required before
calling the issue fixed. If symptoms remain, next step is a native
EditText-backed editing fallback (already used in the V2 Preview),
rather than masking the bug by rewriting live text.

No merge/release without user approval.
