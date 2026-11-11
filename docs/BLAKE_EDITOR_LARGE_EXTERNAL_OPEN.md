# Blake Editor — large files through Android "Open with" (2026-11-11)

## User-reported behavior
The user confirms files over 2 MiB already open in a read-only,
Previous/Next-based viewer when opened inside Blake 2.0 Preview.
However, opening the same file directly from a file manager using
**Open with BlakeEditor** goes into the **original Blake Editor** and
looks as if the 2 MiB check does not exist.

## Root cause confirmed in code
Before this hotfix, Android's ACTION_VIEW / ACTION_EDIT intent filters targeted
\`com.blake.editor.ui.main.MainActivity\`. Its external
\`handleIncomingIntent\` directly called \`MainViewModel.openDocument(uri)\`,
which called \`AndroidDocumentRepository.read(uri)\` and \`readText()\`
without any byte ceiling.

The in-app Open button on the original editor also skipped V2's threshold.
The existing V2 \`LargeTextReaderActivity\` was debug-only and cannot be
used as a normal release-build Activity.

## Fix
- Both **original Editor > Open** and Android **Open with BlakeEditor**
  now pass through \`LargeTextOpenGate\`.
- The gate first checks known reported file size. For unknown, missing or
  potentially incorrect sizes it reads at most **2 MiB + 1 byte**, on
  \`Dispatchers.IO\`, then closes the input. No whole-file buffering.
- Files over 2 MiB show a **Large file — read only** dialog with an explicit
  **Open Reader** or Cancel choice. The original document remains untouched.
- A new production-available \`ui.large.LargeTextReaderActivity\` opens the
  same URI and offers **Previous / Next / Encoding** plus a visible
  **READ ONLY** label. It uses the existing bounded
  \`PagedTextReader\` and \`ReaderPageHistory\` components.
- This new viewer is internal (\`exported=false\`) and is available in
  release as well as debug APKs. External grants are forwarded to the
  internal reader by a read-only ClipData grant.
- The old editable repository also enforces a **hard 2 MiB input-byte
  ceiling** as a fallback for restored sessions and any other call paths.
  No large file can be silently loaded whole through \`readText()\`.
- Small files remain editable through native Android EditText. Their
  existing Save / Save As / draft recovery flows are unchanged.
- No new file-writing code, background service, polling or wake locks.


## Hotfix after failed phone test (2026-11-11)

**First build failed on the user's phone:** opening the 4+ MiB test file
through the original Blake Editor stuck at "Reading file..." and the app
closed / was force-stopped. PR #15 stays **draft**. The exact Android crash
log and installed build identifier were not available, so we cannot prove
which operation caused the hang.

**Revised approach: route BEFORE creating the editor.** The original fix
checked size from MainActivity, but MainActivity itself initializes the
full native input and WebView before doing that check. The external
ACTION_VIEW/ACTION_EDIT intent filters now belong to a small
\`ExternalFileRouterActivity\`. This router shows "Checking file size...",
probes at most 2 MiB + 1 byte on an IO thread, and opens the pager
directly for large files. Only small files are forwarded to MainActivity.
It forwards temporary URI read/write flags for small files, and read
access to the large reader. It does not edit or write the input.

**Second line of defense:** MainViewModel independently checks reported
file sizes before loading text. The bounded repository reader now throws
a typed \`OversizedEditableFileException\` when it discovers real content
exceeds the limit. MainViewModel catches it and emits a one-shot request
to show the same read-only dialog. This also covers saved-session reads
and callers that bypass the Activity check.

**Third line of defense:** The old editor refuses to create a native
EditText layout when a previously stored recovery draft contains more
than 2 MiB of UTF-16 characters, instead of risking a UI-thread freeze.

**Distinguishing a corrected build:** Android external file open should
show "Checking file size..." *before* any original editor appears.
Using the old in-app Open button will show
"Checking file size (safe open)...". If "Reading file..." appears without
that, it may indicate an earlier APK or an unexpected bypass: capture the
screen or crash log, and do not mark the fix verified.

### Focused retest
1. Launch the **same** file through Android's file manager → Open with
   Blake Editor. Expect a **Checking file size** screen, then a dialog.
   The original editor must not appear while classifying the large file.
2. Tap Open Reader: pages must show real text, Next and Previous work,
   and the screen must remain read-only.
3. Open the same file from the original editor's OPEN picker. Expect
   "Checking file size (safe open)..." followed by the read-only dialog.
4. Open a small TXT with "Open with"; it must reach the native editor,
   type Thai/emoji reliably, and Save a disposable copy.
5. Cancel a large-file prompt: the existing original editor draft and
   source file must remain unchanged.

The new external router should also be visible in the merged manifest
as the exported ACTION_VIEW/ACTION_EDIT target. MainActivity retains
only its launcher filter; LargeTextReaderActivity remains internal.

## Known limitations
- A metadata query can be wrong; a bounded input probe detects the real
  size, but small files can be opened twice (once for the probe, once for
  the normal editor). This trades a little one-time I/O for safety.
- Some content providers may deny reading or stop granting temporary
  access. In that case the app shows an error instead of editing blindly.
- The pager is memory-bounded, not indexed. Far-off pages can reread
  earlier content from non-seekable providers.
- An old saved V1 session that points at an oversized file will now show
  a size-limit error rather than loading it whole. Opening it again with
  Open or Open with will offer the paged reader.
- ACTION_SEND sharing is not currently advertised. This is about the
  ACTION_VIEW / ACTION_EDIT **Open with** entry point.

## Automated checks
- Unit tests for known-large size, missing/lying size metadata, exact
  2 MiB boundary, capped stream reads, inaccessible providers.
- Unit tests for the final editable-reader size limit, Thai/emoji and
  exact boundary.
- Existing PagedTextReader/ReaderPageHistory tests remain in the suite.
- Physical Android provider/permission behavior is not proven by JVM CI.

## Five phone checks (use disposable copies)
1. Install new debug APK over existing; do not clear data.
2. In your file manager tap **Open with BlakeEditor** on the same 4 MiB
   \`large_text_over_2MB.txt\`. Expect the read-only warning, then open
   the **Large Text Reader** with real text and Next/Previous.
3. Repeat with **Blake Editor > OPEN**, selecting the same large file.
   Expect the same warning and read-only reader. No Save button.
4. Open an ordinary small TXT or .kt file from the file manager.
   Expect native editing, reliable Thai/English input, Save and Save As.
5. Open a large file, tap Cancel on the warning, and check it did not
   replace an existing unsaved draft or modify the original. The separate
   Blake 2.0 Preview pager should still work.

No merge or production release without phone verification.
