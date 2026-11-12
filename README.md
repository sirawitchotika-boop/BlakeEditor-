# Blake Editor

**A mobile-first Android text and code editor with an optional Blake 2.0 Beta workspace.**

Blake Editor keeps the existing **Original** editor as the regular app launcher. Blake 2.0 Beta is available by an intentional in-app action; it has **not** replaced the Original launcher. The project supports native Android typing, syntax-aware code editing, Markdown preview, draft recovery, safe document saving and read-only opening of large external files.

> **Project status — November 12, 2026:** The reviewed Blake 2.0 feature integration ([PR #34](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/34)) and six-phase documentation ([PR #31](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/31)) are merged into `main`. The user reported passing the A–F isolated-phone checklist. This is **not** evidence of a signed production update or a published release. The next steps are documented in [the G–L closeout roadmap](docs/BLAKE_V2_CLOSEOUT_ROADMAP.md).

## Start here

| Need | Where |
| --- | --- |
| Source code | [`app/src/main/`](app/src/main/) |
| Original Android editor | [`ui/main/MainActivity.kt`](app/src/main/java/com/blake/editor/ui/main/MainActivity.kt) |
| Blake 2.0 Beta | [`ui/preview/V2WorkspacePreviewActivity.kt`](app/src/main/java/com/blake/editor/ui/preview/V2WorkspacePreviewActivity.kt) |
| Recovery and draft persistence | [`session/v2/`](app/src/main/java/com/blake/editor/session/v2/) |
| File reading, encoding and SAF | [`file/`](app/src/main/java/com/blake/editor/file/) |
| CI / test APKs | [Build Debug APK workflow](.github/workflows/build-apk.yml) |
| Release and closeout plan | [G–L roadmap](docs/BLAKE_V2_CLOSEOUT_ROADMAP.md) |
| Cross-chat automation and CI budget | [Project automation workflow](docs/BLAKE_V2_AUTOMATION_WORKFLOW.md) · [Live checkpoint](docs/BLAKE_V2_AUTOMATION_CHECKPOINT.md) |

## What the app does

**Original (default launcher)**

- Android-native text input for English, Thai, emoji, selection, undo/redo and cursor handling.
- Text and code editing with syntax presentation and line-number support.
- Open documents from inside the app or through Android file-opening intents.
- Markdown preview, themes, and safe Save / Save As workflows.
- Guarded large-file handling: external text files **over 2 MiB** use a read-only paged reader rather than loading the full file into the editable view.

**Blake 2.0 Beta (opt-in workspace)**

- Home, New/Open, multiple independent drafts, Recent, and quick document switching.
- Persisted Untitled numbering, rename, Trash, Restore, confirmed bulk actions and Recent shortcut hiding.
- Recovery records with revision checks, atomic writes and process-wide synchronization for store mutations.
- Code tools, search/replace, Markdown reading, settings and themes.
- Linked-document safeguards and verified save paths designed to preserve existing user files.

**Debug tools:** Blake Input Lab is a diagnostic input screen in the debug source set. It is not the production launcher or a document saver.

These are implemented capabilities, not a claim that every Android manufacturer, file provider or release-install path has been certified.

## App layout and data flow

```text
Android launcher
  └── Original: MainActivity
       ├── NativeInputEditText / editor controller / theme
       ├── DocumentRepository + encoding + guarded save/open
       ├── Optional "Blake 2.0 Beta" button
       │    └── V2WorkspacePreviewActivity
       │         ├── DraftWorkspace + RecoveryDraftStore
       │         ├── Rename / Trash / Restore / Recent / selection
       │         ├── VerifiedDocumentSaver
       │         ├── Code tools / Markdown reader / themes
       │         └── V2 LargeTextReaderActivity (internal)
       └── Original Markdown / reader features

Android ACTION_VIEW / ACTION_EDIT for external documents
  └── ExternalFileRouterActivity
       ├── Small supported text: editable Original flow
       └── File > 2 MiB: read-only LargeTextReaderActivity
```

The routing, recovery, and file-saving paths are intentionally distinct. **Removing a Recent shortcut does not delete the file or its recovery record.** A restored draft linked to a changed external file must not silently overwrite the real file. Use disposable documents for acceptance testing.

## Repository tree

This is a **grouped map of the actual `main` tree**, not an imaginary full recursive listing. Names are shown where they are useful for navigation; follow the linked directories for every individual file.

```text
BlakeEditor-/
├── .github/
│   └── workflows/build-apk.yml        # PR/manual unit, build, APK and manifest gates
├── README.md
├── TASK_PROGRESS.md                    # Historical milestones; not a live release status
├── build.gradle.kts                    # Android Gradle plugin version
├── settings.gradle.kts                 # Repositories and :app inclusion
├── gradle.properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts                # SDK, package identities and dependencies
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # Original launcher; external routing; internal V2
│       │   ├── assets/
│       │   │   ├── editor/
│       │   │   │   ├── index.html, editor.css, editor-host.js, preview.js
│       │   │   │   └── vendor/{ace,marked,dompurify}/
│       │   │   └── reader/{index.html,reader.css,reader.js}
│       │   ├── java/com/blake/editor/
│       │   │   ├── BlakeEditorApp.kt
│       │   │   ├── app/AppContainer.kt
│       │   │   ├── core/{model,util}/
│       │   │   ├── editor/
│       │   │   │   ├── NativeInputEditText.kt
│       │   │   │   ├── EditorController.kt, EditorBridge.kt
│       │   │   │   ├── NativeUndoHistory.kt, NativeCodePresentation.kt
│       │   │   │   └── v2/{CodeLineIndex,CodeSyntaxScanner,EditorFindReplace}.kt
│       │   │   ├── file/
│       │   │   │   ├── AndroidDocumentRepository.kt, DocumentRepository.kt
│       │   │   │   ├── TextEncodingCodec.kt, UriPermissionManager.kt
│       │   │   │   ├── LargeTextOpenGate.kt, PagedTextReader.kt
│       │   │   │   └── BoundedEditableTextReader.kt, DocumentMetadataReader.kt
│       │   │   ├── intent/{IncomingIntentParser,OpenDocumentRequest}.kt
│       │   │   ├── session/
│       │   │   │   ├── SessionStore.kt, FileDraftRepository.kt
│       │   │   │   └── v2/
│       │   │   │       ├── DocumentIdentity.kt, DraftWorkspace.kt
│       │   │   │       ├── RecoveryDraftStore.kt, VerifiedDocumentSaver.kt
│       │   │   │       └── V2HomeSections.kt
│       │   │   ├── settings/{SettingsRepository,ThemeController,SharedEditorTheme}.kt
│       │   │   └── ui/
│       │   │       ├── main/{MainActivity,MainViewModel,MainUiState,V2EntryPolicy}.kt
│       │   │       ├── large/{ExternalFileRouterActivity,LargeTextReaderActivity}.kt
│       │   │       ├── preview/
│       │   │       │   ├── V2WorkspacePreviewActivity.kt
│       │   │       │   ├── PreviewCodeEditText.kt, PreviewEditorSettings.kt
│       │   │       │   ├── PreviewPowerPolicy.kt, PreviewResumePolicy.kt
│       │   │       │   ├── PreviewSafeArea.kt, PreviewSystemBars.kt
│       │   │       │   └── LargeTextReaderActivity.kt
│       │   │       └── system/EdgeToEdgeController.kt
│       │   └── res/{layout,drawable,drawable-nodpi,mipmap-anydpi-v26,values,values-night}/
│       ├── debug/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/blake/editor/ui/lab/
│       │       ├── InputLabActivity.kt
│       │       └── InputProbeSamples.kt
│       └── test/
│           ├── java/com/blake/editor/
│           │   ├── core/util/
│           │   ├── editor/ and editor/v2/
│           │   ├── file/
│           │   ├── intent/
│           │   ├── session/ and session/v2/
│           │   ├── settings/
│           │   └── ui/{lab,main,preview}/
│           └── js/editor-host-input-regression.test.cjs
└── docs/
    ├── BLAKE_V2_CLOSEOUT_ROADMAP.md     # G–L plan and closure checklist
    ├── BLAKE_V2_REMAINING_WORK_PLAN.md   # Historic A–F plan
    ├── BLAKE_V2_SIX_PHASE_AGENT_RUNBOOK.md
    ├── BLAKE_V2_EXECUTION_CHECKPOINT.md
    └── BLAKE_EDITOR_*.md                 # Feature-specific milestone notes
```

### Module responsibility guide

| Path | Responsibility |
| --- | --- |
| `ui/main/` | Original entry point, user actions, editor UI and opt-in V2 button |
| `ui/preview/` | V2 workspace, its input/settings and internal reader |
| `ui/large/` | External Android file intent classification and safe large-file reader |
| `editor/`, `editor/v2/` | Input sync, undo, syntax, code line indexing and find/replace |
| `file/` | Android content URIs, metadata, decoding, bounds and paging |
| `session/`, `session/v2/` | Draft identity, persistence, revision and recovery workflows |
| `settings/` | Shared theme and settings behavior |
| `assets/editor/` | HTML/JS/CSS for editor and Markdown surfaces |
| `assets/reader/` | Read-only HTML/JS/CSS reader assets |
| `src/debug/` | Input Lab and debug-only manifest entries |
| `src/test/` | JVM tests and Node regression check |
| `docs/` | Historical design decisions, gate evidence and roadmap |

## Build settings and dependencies

Values below were read from the Gradle files on `main` on **November 12, 2026**. This is the declared dependency list, not a software bill of materials with resolved transitive packages.

| Item | Declared value |
| --- | --- |
| Android Gradle Plugin | `com.android.application:9.4.0` |
| Gradle distribution | `9.6.0` |
| Java source / target | `17` |
| Android compile / target / minimum SDK | `37` / `36` / `28` |
| Application namespace and release ID | `com.blake.editor` |
| Current Gradle versionName / versionCode | `0.1.0` / `1` — **not** a published release claim |
| Default debug application ID | `com.blake.editor.debug` |
| Per-run isolated debug ID | `com.blake.editor.test<run_id>a<attempt>` |

**Android dependencies** (from `app/build.gradle.kts`):

| Dependency | Version |
| --- | --- |
| `androidx.core:core-ktx` | 1.19.0 |
| `androidx.appcompat:appcompat` | 1.7.1 |
| `androidx.activity:activity-ktx` | 1.13.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.11.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0 |
| `androidx.webkit:webkit` | 1.17.0 |
| `androidx.documentfile:documentfile` | 1.1.0 |
| `androidx.datastore:datastore-preferences` | 1.2.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 |
| `junit:junit` (tests) | 4.13.2 |

**JavaScript assets downloaded by CI:**

- Ace Editor `1.44.0`, from cdnjs.
- marked `18.0.7`, from jsDelivr.
- DOMPurify `3.4.15`, from cdnjs.

Some checked-in `assets/editor/vendor/` files are small placeholders. **CI downloads the full vendor assets before building.** A local build must provision the same assets to match CI. Checksums are not yet pinned; dependency integrity review remains in Phase H.

## Building and testing

### Recommended: GitHub Actions

1. Open [Actions](https://github.com/sirawitchotika-boop/BlakeEditor-/actions).
2. Select **Build Debug APK**, then use **Run workflow** on the intended branch. Pull requests trigger the same workflow.
3. Check the run's **conclusion** and its **exact head commit**.
4. Download the correct artifact under the completed run:
   - `BlakeEditor-Debug-APK`: normal debug package, **not** the preferred side-by-side phone test.
   - `BlakeEditor-Isolated-Test-APK`: uniquely named debug install that can coexist with Original.

Artifacts have a **7-day** configured retention period. They are test outputs, not signed production releases. The workflow is triggered by `workflow_dispatch` and `pull_request`; it does **not** automatically run for a push to `main`.

### Local developer build

Install JDK 17, Android SDK/platform for API 37, and Gradle 9.6.0. The GitHub tree contains Gradle wrapper **properties**, but the executable wrapper files are not checked in; CI creates the wrapper when needed. With the matching full JavaScript vendor assets prepared:

```bash
gradle wrapper --gradle-version 9.6.0
./gradlew testDebugUnitTest assembleDebug assembleRelease
node app/src/test/js/editor-host-input-regression.test.cjs
```

The release assemble step is **not** signing, publication or a safe upgrade test. For an exact reproducible build use the current GitHub Actions workflow and inspect its asset-download steps.

### CI checks

The workflow verifies the web editor input/caret regression, V2 theme and button checks, JVM tests, debug build, nonpublished release build, release launcher and reader assets, isolated package ID and uploaded APK artifacts. Source-only documentation PRs can still trigger the build.

Historical integrated evidence: [PR #34 CI run 43124844965](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43124844965) passed on integrated head `30285c7912a057000ec2dcac2242251418cc2130`. The A–F phone acceptance was later reported by the user. **Do not treat that historical PR run as a new run on the current `main` SHA.**

## File and user-data safety rules

- Never uninstall the installed Original app or clear its storage as a shortcut to testing.
- Use test-only documents for Trash, Restore, Save, Save As and encoding checks.
- Keep Android SAF URI permission handling and the external file router intact.
- Recovery metadata, draft text and Recent shortcut visibility are different concerns; never equate “hide Recent” with delete.
- Keep the read-only large-file protection and warning behavior.
- The shared recovery lock protects in-process store mutations; a broader cross-process lock is **not** claimed.
- A new APK package ID or signing key is **not** automatically a safe upgrade for existing users.
- Default launcher promotion, merging code and publishing a release are three separate decisions.

## Development phases and release decision

- **A–F:** integration audits, fixes, exact-head CI and isolated-phone acceptance — completed in the earlier project run (historical evidence in [checkpoint](docs/BLAKE_V2_EXECUTION_CHECKPOINT.md)).
- **G:** README, architecture and closeout documentation.
- **H:** final stability, security and data-safety review.
- **I:** Android device and provider acceptance on a release candidate.
- **J:** version/signing/upgrade/rollback and reproducible release-candidate gate.
- **K:** controlled, separately approved release; optional V2 or default-V2 launcher decided explicitly.
- **L:** final release verification, documentation, tag, maintenance handoff and closure.

See [G–L closeout roadmap](docs/BLAKE_V2_CLOSEOUT_ROADMAP.md) for scoped deliverables, exact pass criteria, and stop conditions. **No phase automatically authorizes the next phase's merge, rollout or destructive operations.**

## Documentation index

- [Current G–L closeout roadmap](docs/BLAKE_V2_CLOSEOUT_ROADMAP.md)
- [A–F remaining-work plan](docs/BLAKE_V2_REMAINING_WORK_PLAN.md)
- [A–F agent runbook](docs/BLAKE_V2_SIX_PHASE_AGENT_RUNBOOK.md)
- [A–F GitHub execution checkpoint](docs/BLAKE_V2_EXECUTION_CHECKPOINT.md)
- [V2 recovery design](docs/BLAKE_EDITOR_V2_DRAFT_RECOVERY.md)
- [V2 safe save](docs/BLAKE_EDITOR_V2_SAFE_SAVE.md)
- [External large-file safeguards](docs/BLAKE_EDITOR_LARGE_EXTERNAL_OPEN.md)
- [Historical promotion gate](docs/BLAKE_EDITOR_V2_PROMOTION_GATE.md)

Historical milestone files (including `TASK_PROGRESS.md`) are useful for context but may describe older branch states. **Use current code and exact-head CI as the source of truth.**

## Contributing and maintenance

Keep work in small PRs based on the current `main`. Document affected files and data-safety boundaries; add focused regression coverage for every bug fix. Verify exact-head CI and use an isolated, disposable-data APK for device acceptance. Preserve the Original launcher and external file data unless an explicit later gate authorizes a change. Never commit private keys, real user documents, local Android SDK paths, or recovery files.
