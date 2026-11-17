# Blake Personal — private signing readiness

**Status (2026-11-17):** The private phone-signing and Blake Personal side-by-side installation milestone is **complete (Boss-reported PASS)**. See [the phone closeout and recovery handoff](BLAKE_PERSONAL_PHONE_CLOSEOUT_2026-11-17.md). The steps below remain the safety contract for **future** builds, not an unfulfilled first-install checklist.

## Personal app identity
- Android application ID: `com.blake.editor.personal`
- Original remains the only launcher. Blake V2 stays opt-in.
- Existing `com.blake.editor` and debug/test installations remain separate. No automatic data migration.
- Version code/name currently: `1` / `0.1.0` (installed privately, **not** published).

## Signing safety contract
- A personal release build must **explicitly** set Gradle property `blakePersonalSign=true`.
- Only then does Gradle read four private environment values: `BLAKE_PERSONAL_KEYSTORE_PATH`, `BLAKE_PERSONAL_STORE_PASSWORD`, `BLAKE_PERSONAL_KEY_ALIAS`, `BLAKE_PERSONAL_KEY_PASSWORD`.
- Missing values cause a clear build failure. Never print their values in logs or error messages.
- Default PR / GitHub Actions CI remains **unsigned** and must continue to pass exactly as before.
- Never commit a keystore, password, raw private key, personal documents, or a signing credential in source, GitHub PRs, chat, or public artifacts.
- The app owner must control the permanent signing key and maintain a tested, private backup in a second secure location. A signing key lost outside a supported rotation program can prevent in-place updates.
- Do not generate or import the key in a public or untrusted environment. Do not request secrets in chat.
- Do not automatically distribute a signed APK; require Boss's separate approval.

## For a future signed personal candidate
1. Agree on a secure, private location for one permanent signing key and its backup, controlled by Boss.
2. Generate or securely import that key only in an approved private environment. Record **only its public certificate SHA-256 fingerprint** in the release record.
3. Run full CI on the exact accepted source. In a separate authorized private build, supply the four environment values, explicitly enable signing, and verify the signed APK's package ID, version, certificate fingerprint and APK SHA-256.
4. Install only after approval, alongside existing Blake apps. Use disposable drafts/files for a brief acceptance check.
5. Future upgrades must retain the same signing identity and application ID, and use a higher version code. Stop on any mismatch; never uninstall or clear data to bypass an update failure.

**Current personal-install evidence:** Boss reported successful key generation, tested GPG + Drive encrypted backups, private Termux signing, seven signing-record matches, installation, app launch, six acceptance tests and home-screen-only cleanup. The verified original unsigned APK came from [run #44043130001](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/44043130001) for source `c5ee92d075255a716a66646e3b22289b7f1c8413`. See the [closeout record](BLAKE_PERSONAL_PHONE_CLOSEOUT_2026-11-17.md) for the detailed evidence boundary and local recovery locations. These are **user-reported phone checks**, not independently inspected phone results. The production-like **in-place upgrade path is still NOT_TESTED**; no store/GitHub Release was published.

**Signing method used for the installed candidate:** owner-controlled offline `apksigner` in Termux on the CI **unsigned** artifact; the optional Gradle signing configuration above remains a separate supported path. The signed APK and private key stay outside GitHub.
