# Blake Editor — external large-file open blocker (revised after phone fail)

## Phone result
Original PR #15 CI run #42959894164 passed, but **PHONE TEST FAILED**:
the original Blake Editor displayed "Reading file..." when opening
large_text_over_2MB.txt, then hung and was force-stopped / exited.
No Android crash stack or installed APK build ID was available.

## Root and approach
The first external check still entered MainActivity (which initializes
native editing and a WebView) before routing to a pager.
The direct original ViewModel document loader also had no independent
user-visible read-only fallback after metadata/provider inconsistencies.

## Branch
fix/blake-external-large-text-readonly-2026-11-11
Base main: a33426d46949b9f854bb2bb86b99c6b257c4fbb8
First PR commit: ad0ef2ee41f6c21abfeb55f2aefcdddb57aa7714

## Revised work
- [x] Android external VIEW/EDIT filters moved from MainActivity to a
      lightweight router which checks size BEFORE any editor or WebView.
- [x] Large external files launch the existing main-source, read-only paged
      reader; small files forward grants to the normal native editor.
- [x] Existing in-app OPEN still checks size before file load.
- [x] MainViewModel independently checks metadata and catches a typed
      oversized input exception; both lead to the same reader dialog.
- [x] Saved-session oversized reads also route to the reader.
- [x] Native editor refuses oversized legacy recovery text layouts.
- [x] Tests cover typed overflow + bounded stream size.
- [ ] New revised CI unit tests and APK build pending.
- [ ] Physical phone retest pending; PR #15 must remain DRAFT.
- [ ] Confirm paging/permission behavior and small-file typing + Save.

## Safety
No file writers, encodings, backup format or V2 Preview changed.
External URI access is forwarded only with existing permission flags.
No wake locks, background service, whole-file buffering or polling.
Do NOT merge until a real phone proves the corrected Open with path.

## Next action
Commit revised route to PR #15, verify CI, then test external Open with
large_text_over_2MB.txt and regular original OPEN on phone.
