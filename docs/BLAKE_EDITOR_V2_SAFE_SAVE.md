# Blake Editor 2.0 — Safe Save and Save As preview (milestone 4)

This is still a **debug-only** Blake 2.0 Preview, not a production release.

## User-visible changes
- **Save** writes the current document to its original Android file only if
  the current source checksum matches the one captured when opening it.
- Existing-file writes keep a verified backup of the original bytes until the
  new content is re-read and checked. Failed or unverifiable writes retain the
  recovery draft and original-byte backup. Some providers may not offer atomic
  writes; always use test copies while reviewing this milestone.
- **Save As** opens Android's CreateDocument picker and writes to a new document
  without changing the original source file. If canceled, recovery stays.
- Text encoding and BOM are preserved when known. Unsupported characters are
  rejected rather than silently replaced.
- Old imported/recovered documents without a known original checksum must use
  **Save As**; the editor refuses an unsafe blind overwrite.
- The recovery-file format is backward compatible with V2 version 1. New records
  include source encoding, BOM, fingerprint and saved/unsaved status.
- Home separates **Continue Writing** and **Recent Saved Files**.
- The regular Blake Editor and Input Lab launchers are unchanged.

## Deliberate limitations
- The new UI remains debug-only.
- Safe saving has a 2 MiB original-file read/backup limit. A streaming large-file
  reader and large-file editing policy remain separate work.
- Files saved via Save As use the source encoding if known, otherwise UTF-8.
  A later milestone should let the user explicitly convert to UTF-8 if a
  legacy format cannot represent a newly typed character.
- Some Android providers do not support reliable read-back or atomic writing.
  In those cases saving can fail safely; backup files are retained.
- Saved file records currently remain in the V2 recovery store for the preview.
  Production V2 will separate recent files from unsaved recovery records.
- External file content changes after a preview record was opened produce a
  conflict rather than automatically overwriting either version.

## Phone tests: use disposable files
1. In **Blake 2.0 Preview**, New File, type text, press **Save**.
   Android should ask for a name and location. After creation, the file must
   contain exactly what was typed.
2. Edit the same saved file and press **Save**. Verify the file now contains
   the updated text. Undo testing uses a COPY, never an important original.
3. Press **Save As**, choose a different file name, edit if desired, and verify
   that the first file remains unchanged.
4. Make a new draft, press Save As, then cancel. The draft must still be listed
   under Continue Writing on Home.
5. Open a Windows-874 or TIS-620 test file, choose the correct encoding,
   edit supported characters, and save. Reopen to check that Thai appears
   correctly. If an emoji cannot be encoded, saving must fail without changing
   the original.
6. Close and reopen Preview. Both saved-file entries and unfinished drafts
   should be accessible.

Do not merge this as a finished Blake Editor 2.0 release without real-device
testing of the save behavior.
