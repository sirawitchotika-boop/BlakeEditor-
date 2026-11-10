# Blake Editor 2.0 — active rebuild

## Confirmed milestones
- [x] Foundation PR #1 merged; Thai native Input Lab and strict codec.
- [x] Recovery PR #2 merged; independent drafts and safe migration.
- [x] Home/Quick Switcher PR #3 merged; user reports 5/5 phone checks passed.
- [x] Save/Save As PR #4 merged; user reports 4/4 phone checks passed.
- [x] User reports additional legacy Thai encoding and external-conflict
      safety checks A/B passed (recorded in PR #4, 2026-11-11).
- [x] Debug-only Markdown Reader and read-only large-file paging code added.
- [ ] CI tests + APK build for current PR not yet verified.
- [ ] Milestone 5 real-device Markdown/large-file verification not yet tested.
- [ ] Production V2, large-file index/search, full Markdown image/link
      handling, polished settings, code tools and final device checks pending.

Working branch: feature/blake-v2-reader-large-files-2026-11-11
Base: main f527ee33a97511f758490df680f38e15dc2cc87b

## Known limitations
The old Blake Editor launcher and its data are untouched.
Markdown reading is available in the debug Blake 2.0 Preview only.
Large files over 2 MiB are read-only in bounded 16k-character pages.
Non-seekable providers may reread earlier content on later pages.
Markdown formatting has a 300k-character limit. Large Markdown is not fully
formatted/paged yet.

## Next action
Verify GitHub Actions, then test Markdown read/source switching, Unicode
recovery, 2 MiB+ page navigation, and existing Save/Save As on Android.
