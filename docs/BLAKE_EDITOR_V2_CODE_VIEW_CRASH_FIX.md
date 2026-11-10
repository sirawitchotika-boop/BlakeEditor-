# Blake Editor 2.0 — code-view crash hotfix

The user reported **force-closing** when opening Kotlin, Python, JSON and
Recent Saved Files in the APK from PR #8.

## Suspected root cause
`PreviewCodeEditText` extends Android `EditText` and overrides
`onTextChanged`. Android may dispatch this callback while running the
superclass constructor, **before subclass fields have been initialized**.
The original override called `lines.rebuild(...)` where `lines` was a
non-null Kotlin property; the backing field is still null at that moment.
This can throw a NullPointerException when the editor view is created,
regardless of file language.

Without a user crash log, this is a strong source-based diagnosis, not
independently confirmed as the exact original crash stack.

## Fix
- Keep the line index nullable until the subclass is initialized.
- Safely ignore text-change callbacks before that point and rebuild from
  the current text as part of final initialization.
- Keep drawing and gutter calculations guarded until the index exists.
- If a future custom EditText constructor throws a RuntimeException, the
  Preview returns to Home and shows an error without deleting recovery data.
- Preserve code coloring, editing, file safety and persisted settings.
- Add JVM regression coverage for rapid line-index rebuilding. Actual
  Android constructor timing needs the user's phone or instrumentation.

## Phone acceptance checks
1. Install the corrected debug APK over the old one, without clearing data.
2. Open .kt, .py and .json test files; each must open, not force-close.
3. Open an existing **Recent Saved File**; it must still open with content.
4. Type Thai and emoji, then return Home and reopen. No data loss/crash.
5. Check code colors, line numbers, and Save on a disposable file.

Keep this as a draft PR pending real-phone results.
