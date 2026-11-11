# Blake 2.0 — safe promotion gate (plan, not a production switch)

Date: 2026-11-11

## Current verified baseline

- **Blake Editor — Original** is the stable `MAIN/LAUNCHER` in `app/src/main/AndroidManifest.xml`. Its native typing, syntax colors, logical line numbers, Save As, Markdown Preview and safe large-file reader were accepted on a real Android phone (5/5) and merged in PR #15.
- Android external `ACTION_VIEW` / `ACTION_EDIT` file launches are handled by `ExternalFileRouterActivity`, which checks file size before forwarding small files to Original or large files to the read-only reader.
- **Blake 2.0 Preview** is currently a *debug-only*, separate launcher from `app/src/debug/AndroidManifest.xml`: `V2WorkspacePreviewActivity`. The same manifest also declares `Blake Input Lab`.
- The V2 documentation describes Home, New/Open File, per-document drafts, Quick Document Switcher, safe encodings, reader, code tools and settings. Some older milestone documents describe historical limits rather than the current state; verify each feature against source before migrating.

## Scope of the next implementation PR

**Goal:** Make the V2 workspace reusable in the regular app while preserving Original as the default and without modifying existing file-writing or external-opening behavior.

1. Inspect the real debug-only workspace dependencies, draft storage, and navigation flow. Identify code that can move safely to the main source set and code that must remain debug-only (Input Lab, test tools, preview diagnostics).
2. Extract or share the smallest reusable V2 Home/workspace component in a new branch based on the verified `main`. **Do not change the production `MAIN/LAUNCHER` declaration yet.**
3. Keep `ExternalFileRouterActivity`, read-only >2 MiB routing, Original Save/Save As, URI permission handling, recovery and the existing V2 preview intact.
4. Add focused tests for the shared navigation and draft-preservation boundary. Run `testDebugUnitTest assembleDebug` and existing input regressions.
5. Produce a reviewable draft PR and a new debug APK for targeted Android tests. Only consider switching the default launcher after the extracted path is proven on device.

## Gate checks before any launcher switch

1. **Original still works:** open code and TXT, type Thai/English/emoji, undo/redo and Save As; no caret jump or duplicated text.
2. **Blake 2.0 Preview still works:** Home, New File, Open, independent drafts, Quick Document Switcher, recovery after relaunch.
3. **File safety stays intact:** Android Files > Open with a >2 MiB TXT routes to the read-only pager; normal small text remains editable.
4. **Settings and readers:** confirm existing V2 settings, Markdown Reader and code tools retain their behavior as documented and as actually implemented.
5. **No destructive migration:** test with disposable files; never uninstall or clear app data, silently rewrite a source file or discard an unsaved draft.

## Out of scope

- Merging alternative PRs #16, #17 or #18 (closed and superseded).
- Changing the release launcher, app package, file writer, encoding defaults or external intent ownership.
- New dependencies, account services, billing, advertising, analytics or background processes.
- Publishing or releasing an APK.

**Decision required later:** Promote V2 Home to be the default launcher only after a later code PR passes CI, phone acceptance and Boss explicitly approves that launcher change. This planning document does not implement it.
