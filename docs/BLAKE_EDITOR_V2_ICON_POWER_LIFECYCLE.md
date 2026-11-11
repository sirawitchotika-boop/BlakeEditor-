# Blake Editor 2.0 — selected launcher icon, lower-power editing and recovery

**Status:** implementation in debug-preview milestone; physical Android checks pending.
This is NOT a production V2 release or a guarantee that Android will never
stop the application.

## Selected app icon
The user selected the original **open white book with teal code brackets**
artwork (user-provided image, 2026-11-11). That artwork has been resized
without changing its design for the standard launcher in WebP format.
The manifest now points to a shared adaptive icon for the normal launcher
and the Blake 2.0 Preview launcher. Android's launcher decides the mask.
A matching simple one-color open-book/code vector enables themed icons.

- app/src/main/res/drawable-nodpi/blake_launcher_art.webp
- app/src/main/res/drawable/blake_launcher_monochrome.xml
- app/src/main/res/mipmap-anydpi-v26/ic_blake_launcher.xml
- app/src/main/res/values/blake_icon_colors.xml

The color bitmap is an optimized 192px preview asset; final 432px/high-density
foreground artwork and different OEM mask previews are still release checks.

## Battery, processing and background behavior
Previously the V2 EditText code rescheduled the same 350ms disk write for
every caret/selection/scroll change. Those events also repeatedly converted
the entire editor text to a String.

Now:
- Text edits are debounced to 600ms; cursor or scroll-only changes to 1800ms.
- Cursor/scroll-only updates reuse existing draft text instead of copying it.
- Already-persisted document revisions are not written again on background.
- When the Activity stops, it cancels pending debounce/highlighting work,
  then sends one pending atomic recovery snapshot to the IO dispatcher.
- The Markdown WebView pauses while its Activity is paused. No continuously
  running timer, wake lock, foreground service, or network monitor is added.
- The old verified Save/Save As path is unchanged.

These changes reduce unnecessary activity; a real battery measurement has NOT
yet been performed. Fold/unfold can restart the Activity due to size or
configuration changes. The app keeps active document identity, caret and a
small (max 32 KiB UTF-8) pending snapshot in Android saved state. On restore,
the snapshot is accepted only if it matches the same document and has a newer
revision than the atomic disk recovery record. Larger drafts rely on the
existing atomic on-disk recovery store and asynchronous onStop flush.

**Important:** Android owns process lifetime and can kill a background app.
There is no appropriate permission or code that can guarantee a background
editor process is never killed. The goal is durable recovery and low power,
not an artificial "keep alive" service. Forced Stop, sudden power loss and
an OS kill before an async write finishes can still lose the last moments of
a large unsaved edit; use Store Draft before a risky operation.

Reference: https://developer.android.com/guide/components/activities/activity-lifecycle

## Automated tests
- Pending-state restore with Thai, emoji, different document IDs, older and
  newer revisions, corrupted cursor bounds, size cap and disk-save flags.
- Power policy tests for lower-frequency cursor persistence and skipping
  unchanged revisions.
- All existing unit tests and debug APK build run through GitHub Actions.

## Five phone tests (do NOT uninstall or clear data)
1. Install the new debug APK over the existing one. Confirm the chosen white
   book + teal code icon appears on the launcher. Check the image in the
   app drawer too; OEM adaptive-icon mask behavior may vary.
2. In Blake 2.0 Preview edit a disposable .kt or .txt file with Thai and emoji.
   Pause typing, background it, then return: the text and caret should remain.
3. Fold/unfold (if supported), rotate, or use split screen while an unsaved
   disposable file is open. Verify the editor restarts without a crash and
   recovers the same document.
4. Open another app, leave Blake in the background and return. If Android
   stopped the process, open Continue Writing; the recovery draft must be
   accessible. The app should not show a recurring background notification.
5. Verify Find/Replace, the code toolbar, Markdown Reader, large-file
   Next/Previous (read-only), and Save/Save As still work. No original file
   should change unless you tap Save.

## Release-gate checks still needed
- Real phone background/cold-start, orientation and folding tests.
- OEM icon mask and monochrome-themed launcher inspection.
- Battery profiling through Android Studio Energy Profiler / Perfetto and
  comparison against the previous debug build (not done).
- Full production V2 launcher migration, signing, accessibility and QA;
  no main merge or release is authorized as part of this draft PR.
