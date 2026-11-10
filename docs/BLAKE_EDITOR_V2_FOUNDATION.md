# Blake Editor 2.0 — foundation plan

This repository currently contains the original Android Ace/WebView editor.
The existing app remains functional as the rebuild proceeds on separate PRs.

## Agreed product direction

- Home opens to Recent Files, not the last document.
- Open File uses Android's picker; New File opens a blank editing view.
- TXT opens editable; Markdown opens in Reader mode; code opens editable.
- Drafts are per-document, independent, and must be safely stored before leaving.
- Quick Document Switcher, clean interface, no ads, paywalls, or account requirement.
- Separate native reading and code typography; handle Thai combining marks correctly.
- Support safe UTF-8, BOM, UTF-16, Windows-874 and TIS-620 with manual selection when ambiguous.
- Large files must be readable even when editing them is unsafe.
- No real file may be rewritten merely by opening it.

## Milestone 1 — safe editor proof and file-format foundation

- A second, **debug-only** app launcher named **Blake Input Lab** runs a native
  Android EditText probe. It is not the final editor, and it saves no files.
- The lab has Thai, mixed-script, emoji, wrapped-line and source-code samples.
  Toggle System/Monospace font, line wrapping and font size and observe the caret.
- A new standalone TextEncodingCodec handles BOM, strict UTF-8 and explicit
  Windows-874/TIS-620 selection. Its APIs are not wired into the old repository.
- Pull requests trigger the existing unit-test + debug-APK workflow.
- No change is made to the existing release launcher or file-writing behavior.

## Required phone tests

On a debug APK, launch **Blake Input Lab** as a separate launcher icon:
1. Choose **Thai**, type and delete "สวัสดีครับ". The caret must align with the last visible letter.
2. Choose **Mixed** and **Emoji**, move the caret and select text. The caret and handles must align.
3. Choose **Wrapped**, toggle Wrap and font sizes. The caret must stay in the correct visual position.
4. Choose **Code**, toggle the Monospace font, then type Thai inside the code string.
5. Confirm the regular **Blake Editor** launcher still opens the old app and its original files.

Record phone model, Android version, keyboard name/version, and which tests fail.
No editor engine may be declared verified without these results.

## Known limits

- The old app still uses Ace/WebView and still handles file bytes as UTF-8.
- The old draft repository still shares one "untitled" key for unnamed documents.
- The new codec is isolated and not used to save real files.
- There is no new Home, Quick Switcher, Markdown reading UI, or large-file reader yet.
- A physical Android device is required to decide between native EditText and Sora.
