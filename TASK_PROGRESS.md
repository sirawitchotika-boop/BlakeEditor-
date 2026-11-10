# Blake Editor 2.0 — active rebuild

## Confirmed work
- [x] Foundation PR #1 merged; native EditText input and strict codec.
- [x] Draft recovery PR #2 merged; independent atomic drafts and migration.
- [x] Workspace PR #3 merged; Home and Quick Switcher preview.
- [x] User reports all five Workspace Preview phone tests passed on 2026-11-10.
- [x] Previous PRs' GitHub Actions unit tests and APK builds passed.
- [x] Implemented debug-only Save and Save As preview with strict encoding,
      source fingerprint validation, original backups and verified read-back.
- [ ] CI verification for new Save PR (pending).
- [ ] Real-phone Save/Save As tests (pending).
- [ ] Full production V2 app, Recent Files persistence, Markdown reader,
      large-file streaming, theme customization and code editing tools.

Working branch: feature/blake-v2-safe-save-2026-11-10
Base main: 196593d6029e27f225b22b83dbce15ca17e31e2c

## Safety limitations
Debug-only Blake 2.0 Preview now has explicit file-writing buttons.
Backups are retained when overwrite verification fails. Android file providers
can behave differently; use disposable test copies. No update to the main
Blake Editor launcher is included. Older drafts lacking file fingerprints
cannot overwrite originals until safely reopened or saved as new files.

## Next action
Verify this PR in GitHub Actions and test Save/Save As on an Android phone,
including cancel, encoding mismatch, and external-file conflict behavior.
