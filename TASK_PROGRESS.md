# Blake Editor — Original native code appearance

## Checkpoint (2026-11-11)
- Feature branch: `feat/blake-original-native-code-appearance-2026-11-11`, based on main `a33426d46949b9f854bb2bb86b99c6b257c4fbb8`.
- PR #14 keyboard fix is already merged; user previously passed native typing/caret/Save phone checks.
- PR #15 large external text routing remains **draft, open, unmerged** as confirmed before this task. Its corrected phone suite was passed by Boss; this branch does not modify those files.
- This branch does not merge or depend on PR #15.

## Feature scope
- [x] Restore code syntax coloring in **Blake Editor — Original** with foreground-only spans; never setText or change caret/selection to color code.
- [x] Show a logical-line gutter for code files, not wrapped visual rows; no gutter for plain TXT/Markdown.
- [x] Reuse production CodeSyntaxScanner/CodeLineIndex; debounce scans off main thread, limit scan size and rendered spans, and pause scanning in background.
- [x] Use Dark/AMOLED/Eye Care theme colors; keep the existing native input and undo/redo/save paths.
- [x] Add JVM regression tests for language choices, size limits, palettes, mixed scripts.
- [ ] GitHub Actions debug unit tests, WebView regression, and debug APK build: **not yet checked**.
- [ ] Android real-phone tests: **not yet run** (Thai combining text, wrapped Backspace, emoji, paste, Save As, preview, large-file Open with).

## Known limits
- Existing scanner highlights Kotlin, Java, JS, TS, Python, JSON, HTML, XML, CSS and YAML. Other code modes show line numbers without colors.
- Color scanning is disabled over 80,000 UTF-16 units, or when token counts exceed 4,000; text remains editable.
- Code gutter is enabled by file type; Original does not currently expose its own line-number toggle.
- New code visual changes are **not phone-approved** until Boss confirms the APK tests.

## Next action
Open a draft PR, verify its exact CI run and APK, then request the five quick Original phone tests. Do not merge without Boss's express approval.
