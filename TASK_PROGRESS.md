# Blake Editor 2.0 — active rebuild

Objective: implement the approved Blake Editor 2.0 design through reviewable,
safe milestones, without changing the default branch directly.

Base: main (foundation PR #1 was merged on 2026-11-10)
Working branch: feature/blake-v2-independent-drafts-2026-11-10
Foundation merge commit: f534e4109108060c48ad87df88a25e255514b2e9

## Milestones

- [x] Debug-only native Android Input Lab and strict standalone encoding codec.
- [x] CI for foundation PR #1: unit tests and debug APK build passed.
- [x] User reported all five device checks passed on 2026-11-10,
      including Thai caret input, emoji/mixed text, wrapping and V1 save.
      Device/keyboard details were not supplied.
- [x] New V2 draft storage code with unique IDs, atomic replacement, stale-save
      checks, safe revision clearing and non-destructive legacy import.
- [ ] CI for this branch's V2 draft tests: unverified until workflow succeeds.
- [ ] Connect V2 storage to a new document manager, Home and Quick Switcher.
- [ ] Wire encoding and safe Save/Save As to real file I/O.
- [ ] Large-file reader, Markdown reader, settings, final code editing engine.
- [ ] Real Android end-to-end tests for independent drafts and recovery.

## Risk boundaries

- V1 still uses the Ace editor and its old single "untitled" recovery slot.
- The V2 draft component is isolated and cannot affect V1 data without explicit
  integration. Legacy data is never deleted during migration.
- The standalone encoding codec is not yet connected to real-document saving.
- An Android EditText baseline passed the reported phone tests. The final
  editor engine is not yet verified.
- Neither this branch nor the foundation PR is a complete Blake Editor 2.0.

## Next action

Review CI for V2 recovery tests; implement the V2 document manager with correct
"draft stored before navigation" and revision-safe Save semantics.
