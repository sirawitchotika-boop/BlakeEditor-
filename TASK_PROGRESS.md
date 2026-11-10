# Blake Editor 2.0 — active rebuild

## Hotfix: Large Text Reader Previous button (2026-11-11)

Base: main, commit 2492983e4144e782998e190e21ac3c4cc3be6b13
Branch: fix/blake-v2-large-reader-previous-2026-11-11

- [x] User confirmed large file opens and Next works after PR #6.
- [x] User reported Previous does not work.
- [x] Root cause: Kotlin trailing lambda in next()/previous() bound to
      loadPage()'s last parameter, onFailure, not onSuccess.
      As a result, the page-history stack stayed empty on successful Next.
- [x] Use explicit named onSuccess callbacks for both navigation actions.
- [x] Extract ReaderPageHistory, record only successful navigation, and
      add JVM tests for forward/back, multi-page ordering, failed reads,
      and reset after changing encoding.
- [ ] CI unit tests + APK build: pending.
- [ ] Android phone test: Next -> Previous -> Next, verify page indicator.

## Existing app safety

The Large Text Reader is still read-only and does not write file data.
The regular Blake Editor launcher, Save, Save As, draft recovery and
Markdown rendering are unchanged.
Prior PR #1 through PR #6 are merged. All earlier Save and safety tests
were user-reported passed; large-file Previous was newly reported failing.

## Next action

Review GitHub Actions for the hotfix. Install the new debug APK and verify
that Next changes to page 2 and Previous returns to page 1.
