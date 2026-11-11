# Blake Editor — V1 typing/caret release blocker

## Report
2026-11-11, Android phone screenshot of **original V1 Ace editor**.
Backspace jumps across soft-wrap boundaries, sometimes stops, caret jumps
to the beginning and typing duplicates text. Affects English and Thai.
User states app is unusable due to editing reliability.

## Branch
fix/blake-v1-keyboard-echo-caret-2026-11-11
Base main 70fd45385cc1322553eae5329dd1a96fbf0ef5c3

## Implementation
- [x] Reviewed current V1 MainActivity, EditorBridge, EditorController,
      `editor-host.js`, and V2/native-input comparison.
- [x] Fixed off-UI-thread WebView JS bridge callback dispatch.
- [x] Added content-echo guard in old editor's Kotlin Activity.
- [x] Made Ace setContent idempotent, avoiding unnecessary cursor/undo resets.
- [x] Eliminated duplicate manual SetContent after New.
- [x] Added Kotlin and Node regression tests; integrated Node into CI.
- [ ] GitHub Actions unit tests + APK build pending.
- [ ] Physical device typing/backspace across line-wrap and Thai IME pending.
- [ ] Confirm V2 Preview and Save/Save As still work.
- [ ] Realme/Vendor Ace behavior may still need native EditText fallback.

## Safety
No modifications to data saving, recovery, encoding, or V2 Preview.
Work is on a separate review branch. Do not merge without permission.
Do not recommend editing irreplaceable files in the unverified APK.

## Next step
Check CI, fix any failures, distribute debug APK for phone tests.
