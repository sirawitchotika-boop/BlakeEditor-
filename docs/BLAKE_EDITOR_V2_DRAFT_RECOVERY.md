# V2 independent draft recovery (milestone 2)

This module is in \`app/src/main/java/com/blake/editor/session/v2\`.

## Scope
- Each new untitled document receives a unique generated ID; existing documents
  have a stable ID derived from their Android document URI.
- Each draft is stored in a single versioned recovery file, updated using a
  synced temporary file and atomic rename. Writes preserve older complete
  versions when they fail before replacement.
- Each edit must increase the per-document revision. Older asynchronous saves
  cannot replace a newer draft; successful real-file saves can only clear the
  draft whose exact revision was written.
- The draft stores the document title, source URI, syntax identifier, content,
  selection, scroll position, and revision.
- Unreadable recovery files are reported separately and never silently deleted.
- V1 draft migration reads legacy data without deleting it. Import uses a
  persistent completion marker and can retry after an interrupted migration.
- Draft writing deliberately fails if an edited document exceeds the safe
  in-memory draft size. The future large-file reader is read-only at that size.

## Integration contract
- This is a **new, isolated V2 storage component**. It is not yet connected
  to the existing Ace editor, which still has the old single-untitled behavior.
- The V2 document manager must assign an ID when New File is tapped and retain
  that ID through the document's lifetime.
- Before returning Home or switching documents, finish the latest revision's
  draft write. If it fails, keep the editor open and show an error.
- After a confirmed real-file save, clear only the matching revision. If newer
  changes arrive while Save is running, do not clear them.
- Call \`importLegacy()\` **only when activating the V2 document manager**,
  not from the current V1 app.
- Do not merge this as a complete fix for the V1 recovery flow.

## Verification
Unit tests cover independent untitled documents, URI identity, stale revisions,
revision-safe clearing, state restoration, corrupt draft reporting, and
legacy migration. The native input prototype's five physical-phone tests were
reported passed by the user on 2026-11-10; this does not verify the new store
on a physical phone.
