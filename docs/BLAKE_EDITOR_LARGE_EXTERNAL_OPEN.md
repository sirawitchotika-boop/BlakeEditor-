# Blake Editor — large files through Android "Open with" (2026-11-11)

## User-reported behavior
The user confirms files over 2 MiB already open in a read-only,
Previous/Next-based viewer when opened inside Blake 2.0 Preview.
However, opening the same file directly from a file manager using
**Open with BlakeEditor** goes into the **original Blake Editor** and
looks as if the 2 MiB check does not exist.

## Root cause confirmed in code
Android's ACTION_VIEW / ACTION_EDIT intent filters target
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
