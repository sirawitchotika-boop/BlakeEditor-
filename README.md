# Blake

**A calm, mobile-first Android editor and reader for plain text (`.txt`) and Markdown (`.md`).**

Blake 1.0 is a clean-slate rebuild of this repository focused strictly on everyday writing and reading—with zero coding-IDE clutter, zero WebView/Ace overhead, and an **Editorial Paper & Ink** design language.

> **Legacy preservation:** The previous Blake Personal `0.1.0` codebase (Original + Blake 2.0 Beta + code editor) is permanently preserved at Git tag `legacy-v0.1.0`. Blake `1.0.0` (`versionCode = 2`) keeps package ID `com.blake.editor.personal` for clean in-place upgrades when privately signed, while CI continues generating side-by-side isolated test APKs (`Blake T<run>a<attempt>`) for safe phone testing.

## What Blake does

- **Editorial Paper & Ink UI:** Warm Paper (light), Charcoal Ink (dark), and System theme modes with clean typography and adjustable text sizes.
- **Native Android writing canvas:** Smooth Thai, English, and emoji typing with grouped Undo/Redo and a subtle, hideable Markdown formatting bar above the keyboard (`H`, `B`, `I`, lists, checklists, quotes, links).
- **Non-blocking Find & Replace:** Slim inline bar that never covers your text, live match highlighting, and instant smooth auto-scroll directly to the active match above the keyboard.
- **Instant native Markdown Reader:** One-tap toggle between writing and book-like Markdown reading (headings, lists, GFM checklists, quotes, tables, links, calm code blocks) powered by CommonMark-Java and Compose—no WebView.
- **Documents sheet:** Opens straight into your last document or a blank page, with a clean bottom sheet for New Note, Open File, local Drafts, and Recent Files (removing a Recent shortcut never deletes external files).
- **Built-in file safety:**
  - Default Android `.txt` & `.md` handler (`ACTION_VIEW` / `ACTION_EDIT`).
  - Explicit Save with SHA-256 external-modification check, private backup, and readback verification.
  - Atomic crash-safe draft recovery stored under `filesDir/blake_v1/`.
  - Read-only paged reader for large files over `2 MiB` with UTF-8, UTF-16, Windows-874, and TIS-620 encoding support.

## Architecture

```text
app/src/main/java/com/blake/editor/
├── app/AppContainer.kt              # Dependency wiring (blake_v1 private storage)
├── data/                            # Atomic DraftRecoveryStore, RecentDocumentsStore, DataStore prefs
├── editor/                          # DocumentSearch (live find/replace), NativeUndoHistory
├── file/                            # SAF repository, verified writer, strict encoding codec, 2 MiB gate & paged reader
├── intent/                          # Incoming ACTION_VIEW / ACTION_EDIT parser
├── markdown/                        # CommonMark + GFM AST parser & quick-bar formatting transforms
├── model/                           # ActiveDocument, ScratchDraft, RecentFile, Theme & FontSize models
└── ui/
    ├── theme/BlakeTheme.kt          # Editorial Warm Paper & Charcoal Ink palettes
    ├── editor/                      # BlakeEditorCanvas, FindReplaceBar, MarkdownQuickBar, MarkdownReaderView, TopBar, Sheets
    ├── main/                        # MainActivity, BlakeViewModel, BlakeUiState
    ├── reader/                      # LargeFileReaderActivity (Compose paged >2 MiB reader)
    └── router/                      # ExternalFileRouterActivity (2 MiB gate before loading)
```

## Build & Verification

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

GitHub Actions (`.github/workflows/build-apk.yml`) runs unit tests and produces:
1. `BlakeEditor-Debug-APK` — standard debug build (`com.blake.editor.personal.debug`)
2. `BlakeEditor-Isolated-Test-APK` — unique-ID side-by-side phone test APK (`Blake T<run>a<attempt>`)
3. `BlakePersonal-UNSIGNED-SIGN-FIRST` — unsigned release APK (`com.blake.editor.personal`, `versionCode = 2`) for owner-controlled offline signing
