# Blake 2.0 Workspace Preview — milestone 3

The debug APK includes a new launcher icon called **Blake 2.0 Preview**.

## Included
- Home: New File, Open File and Continue Writing.
- Native Android text editing with Thai input and cursor/selection.
- Multiple independent drafts, automatically saved after short pauses.
- A Quick Document Switcher showing all stored drafts.
- Before Home or switching, the latest draft must be stored successfully.
  Otherwise the editor stays open.
- A separate recovery store, with non-destructive V1 draft migration.
- Small file opening via Android picker and safe text encoding detection,
  including manual legacy Thai encoding selection.
- Large files (over 2 MiB) are not opened in this editing preview yet.

## Safety

The preview **never writes to original documents**. "Store Draft" means only
internal recovery storage. Normal Save/Save As, a large-file streaming reader,
Markdown reading mode and settings will follow in later milestones.

The existing Blake Editor and Blake Input Lab remain unchanged.

## Device checks
1. Open Blake 2.0 Preview. New File should immediately open a blank editor.
2. Type something, then Home. Continue Writing should show the saved draft.
3. Create another new file and type different text. Switch between the two:
   each must keep its own text and cursor.
4. Close and reopen the preview: your drafts should still be there.
5. Open a small TXT file from the Android picker and edit it in the preview.
   Its original stored file should remain unchanged.
6. The regular Blake Editor launcher should still work as before.

A failing draft write must not allow the preview to navigate away.
Do not merge this feature as a finished Blake Editor 2.0 release.
