# Blake Editor — external large-file Open with hotfix

## User report
External **Open with BlakeEditor** on a file over 2 MiB opens through
the original editor without V2 Preview's read-only paging protection.
The same file opened inside Blake 2.0 Preview uses Previous/Next.

## Root cause
MainActivity receives ACTION_VIEW / ACTION_EDIT and directly calls
MainViewModel.openDocument() -> AndroidDocumentRepository.readText()
without a byte limit. The internal original Open picker did the same.
V2's pager is debug-only, not on this route.

## Branch
fix/blake-external-large-text-readonly-2026-11-11
Base main: a33426d46949b9f854bb2bb86b99c6b257c4fbb8

## Work
- [x] Both internal original Open and external Open with enter a common
      size-checking coroutine before editing.
- [x] Bounded probe handles unknown or lying file-provider sizes.
- [x] Explicit warning leads to read-only paged reader (Next/Previous).
- [x] New main-source (release-available) reader Activity with URI grant.
- [x] Safety cap for any remaining original document read paths.
- [x] Focused JVM tests for thresholds, stream limits and Thai/emoji.
- [ ] GitHub Actions tests + APK build pending.
- [ ] Real-phone ACTION_VIEW/EDIT URI provider tests pending.
- [ ] Native typing, Save, large reader and V2 smoke test pending.

## Safety
No changes to Save/Save As, recovery, encoding writes, user files,
the V2 debug-only reader or the normal input engine. No merging or
release without authorization.

## Next action
Open draft PR and verify CI; phone-test file manager Open with BlakeEditor
for 4 MiB, in-app Open and small-file editing.
