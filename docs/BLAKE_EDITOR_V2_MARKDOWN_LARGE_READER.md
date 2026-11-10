# Blake Editor 2.0 — Markdown Reader and large-file viewer (milestone 5)

Only the debug-only **Blake 2.0 Preview** launcher gets these changes.
The regular Blake Editor app remains unchanged.

## Markdown reading
- Opening Markdown files (.md and .markdown) shows formatted reading mode
  first, not raw markup.
- **Edit Source** switches to the native text editor. **Reader** switches
  back after storing the latest recovery draft.
- Offline rendering reuses the pinned Marked and DOMPurify assets supplied by
  the existing build workflow. Scripts, forms, image loads and outside links
  are blocked. It works without an account or an internet connection.
- Titles, bold, lists, tables, block quotes and fenced code are supported.
- Markdown formatting is capped at 300,000 characters to avoid a frozen
  WebView. Larger Markdown drafts offer Edit Source only for now.
- Save and Save As are still explicit actions, as in the prior milestone.

## Large text viewer
- Files over 2 MiB open a separate **Large Text Reader**. When a document
  provider omits file size, reaching the editing limit also opens this reader.
- Reader displays at most 16,000 UTF-16 characters per page and has Previous
  and Next controls. Original bytes are never written.
- BOMs for UTF-8 and UTF-16 are detected. Otherwise UTF-8 is attempted.
  The Encoding button lets the user select legacy Thai encodings manually.
  Invalid input and likely binary content are reported, not silently replaced.
- Android document providers may be non-seekable, so later pages can require
  rescanning earlier text. The method is memory-bounded but not fast random
  access. Full indexing and search remain future work.
- There are no Save, Save As or editing controls in Large Text Reader.

## Phone tests
Use disposable files, not important originals.

1. Open a Markdown file with a heading, bold text, list and fenced code block.
   It should open in formatted reading mode.
2. Tap Edit Source, change the Markdown, and tap Reader. The formatted view
   should update. Go Home and reopen: the edits must still be there.
3. Open a TXT file bigger than 2 MiB. It must open Large Text Reader with
   a READ ONLY message and page controls.
4. Use Next and Previous. The pages should move without freezing, and the
   original TXT file must remain unchanged.
5. Open a normal small TXT file, edit, Save and Save As. It should still work.
   The original Blake Editor launcher should also remain usable.

## Verification boundaries
JVM tests cover bounded pages, Unicode/Thai/emoji, BOM, legacy encoding,
malformed text, binary input, boundaries and empty files.
Physical Android rendering and document-provider behavior still require
phone verification. CI alone cannot prove these interactions.
