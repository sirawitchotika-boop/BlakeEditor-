# Blake Editor 2.0 — UI polish and camera-cutout / status-bar repair

## Scope
This change is for the **debug Blake 2.0 Preview**, Large Text Reader and
Input Lab. The regular Blake Editor V1 launcher, file saving, draft recovery
format and actual document content are unchanged. It is **not** the
production-ready Blake 2.0 release.

## Why the white strip appeared
A phone screenshot showed a white/very light area behind the status bar
around the front camera while Blake 2.0 Preview used an AMOLED black
background. The old V1 Activity already calls EdgeToEdgeController, but the
debug V2 Activity and large-file reader did not install any equivalent
runtime bar handling or root system-window inset listener. They inherited a
transparent status bar with no consistent view drawn behind it.

This is a source-based diagnosis that must be confirmed by the phone test.

## Adapted from AuraCalc
The other project, auracalc-apk, had a similar Realme first-launch status
bar issue addressed in its PR #5. Its approach included:
- matching the **native launch and Activity themes**, not just an unrelated
  base theme;
- setting the status bar icon style and background when the active theme
  changes;
- protecting UI from device safe areas.

AuraCalc is a Capacitor/WebView app and uses the StatusBar plugin and CSS
safe-area variables. Blake V2 is native Android, so those exact APIs are
not copied. See:
https://github.com/sirawitchotika-boop/auracalc-apk/pull/5

Blake targets Android API 36. On Android 15+, status bar colors are
transparent and may ignore native window.statusBarColor. Therefore matching
theme colors alone is insufficient. This patch also renders a background
behind the system bars and applies actual insets to content. See Android's
edge-to-edge guidance:
https://developer.android.com/develop/ui/views/layout/edge-to-edge

## Changes
- A debug-only Theme.BlakePreview gives all V2/test Activities matching
  dark startup status bar, nav bar and launch-window background, avoiding
  a white cold-start flash.
- Shared PreviewSystemBars installs AndroidX edge-to-edge handling and
  updates status/nav icon contrast to match each selected theme.
- The V2 root background is drawn behind Android's transparent system bars.
  Camera/status/nav/keyboard insets adjust root padding from an **original
  captured base padding**, so repeated inset events cannot accumulate.
- The camera cutout and status bar use the maximum safe inset, not their sum.
  Landscape cutouts on the left/right are protected.
- On light/Eye Care themes, system icons switch to dark; on AMOLED/Dark they
  stay light. System navigation contrast scrims are disabled where supported
  to match the chosen background.
- Home uses clearer local-storage text and 48dp touch-sized action buttons
  with theme-matched backgrounds and borders.
- Large Text Reader now follows the selected Preview theme while keeping
  all pages read-only; Input Lab also shares safe-area handling.

## Verification
- JVM tests cover status/camera inset combination, landscape cutouts,
  keyboard vs navigation padding, repeated notifications and icon contrast.
- Compilation and unit test result: **pending CI at time of authoring**.
- Real Android tests: **not yet confirmed**.
- No emulator, full screenshot comparison or physical device run in CI.

## Phone checklist
1. Update the debug APK over the existing one; do not uninstall or clear app
   data. Open **Blake 2.0 Preview** with AMOLED or Dark. Check the top camera
   and status bar area: it should be the same color as the app background,
   with readable status-bar icons, including on a fresh/force-stopped launch.
2. Settings → Light → AMOLED → Eye Care. Each theme should keep the top area
   matched and the clock/battery icons readable. Reopen the Preview.
3. Open a .kt/.json file and a recovery draft. Check visible text, toolbar,
   Find/Replace, code colors and Native Thai/emoji input; no controls hidden
   behind the camera or bottom navigation bar.
4. Show and hide the keyboard. Check the bottom editor is still usable,
   then return Home. Optionally rotate the phone to landscape if supported.
5. Open the same >2 MiB test TXT in Large Text Reader. Next and Previous
   should still work, stay READ ONLY and respect the camera area. Also
   check the ordinary Blake Editor launcher remains unchanged.

## Release readiness (not yet a full release)
- [x] Prior user-reported Save, Markdown, code editor, Search/Replace and
      paging tests have passed through PR #10.
- [ ] This UI/cutout change passes fresh physical Android tests.
- [ ] Test across supported navigation modes, Android versions, landscape,
      and display cutout shapes where devices are available.
- [ ] Confirm production V2 launcher, settings, UX, app signing, recovery,
      large-file performance and accessibility independently before release.
- [ ] Merge requires explicit authorization; no production switch here.
