# Blake Editor — Original native code visuals

## Latest confirmed phone baseline (2026-11-11)
- PR #14: original native keyboard, Backspace, Thai/English/emoji, Undo/Redo,
  Save As and Markdown Preview passed on the user's phone.
- PR #15: user reports all 6 large-file and safety checks passed on
  revised APK, including Android Open with and Previous/Next. The PR
  remains draft/unmerged awaiting permission.
- No new device tests yet for restored syntax colors and gutter.

## Branch
feature/blake-original-native-code-highlighting-2026-11-11
Base: PR #15 head 7383d5919ea922326fb603fdf3bd31a2b43e72e2
Stacked PR base: fix/blake-external-large-text-readonly-2026-11-11
No merge into main.

## Changes
- [x] Retain the verified AppCompatEditText input implementation.
- [x] Draw accurate logical-line numbers in a small code-file gutter.
- [x] Apply non-text-changing ForegroundColorSpan syntax colors, including
      skip while IME composing is active.
- [x] Off-main-thread bounded scanner, idle debounce and cancellation.
- [x] Three native theme palettes (Dark, AMOLED, Eye Care).
- [x] Pure JVM tests for policy, colors, Thai/emoji, source offsets.
- [ ] CI testDebugUnitTest and assembleDebug.
- [ ] Physical keyboard, line wraps, themes, Undo/Redo and code Save As.
- [ ] Retest large-file external reader from stacked PR #15.

## Limits
Highlighting only for supported tokenizers and <=80k UTF-16 chars.
Other recognized code types still have a gutter. Large-code performance,
font metrics and real IME safety require phone testing. No file-write
or recovery changes. No background services/wake locks.

## Next action
Create stacked draft PR from verified PR #15 branch, verify CI,
and have user test Original code editing and 2 MiB+ routing.
