# Blake Personal — private signing readiness

**Status:** Configuration prepared only. No permanent key or signed personal APK has been created or verified.

## Personal app identity
- Android application ID: `com.blake.editor.personal`
- Original remains the only launcher. Blake V2 stays opt-in.
- Existing `com.blake.editor` and debug/test installations remain separate. No automatic data migration.
- Version code/name currently: `1` / `0.1.0` (not a published release).

## Signing safety contract
- A personal release build must **explicitly** set Gradle property `blakePersonalSign=true`.
- Only then does Gradle read four private environment values: `BLAKE_PERSONAL_KEYSTORE_PATH`, `BLAKE_PERSONAL_STORE_PASSWORD`, `BLAKE_PERSONAL_KEY_ALIAS`, `BLAKE_PERSONAL_KEY_PASSWORD`.
- Missing values cause a clear build failure. Never print their values in logs or error messages.
- Default PR / GitHub Actions CI remains **unsigned** and must continue to pass exactly as before.
- Never commit a keystore, password, raw private key, personal documents, or a signing credential in source, GitHub PRs, chat, or public artifacts.
- The app owner must control the permanent signing key and maintain a tested, private backup in a second secure location. A signing key lost outside a supported rotation program can prevent in-place updates.
- Do not generate or import the key in a public or untrusted environment. Do not request secrets in chat.
- Do not automatically distribute a signed APK; require Boss's separate approval.

## Before a signed personal candidate
1. Agree on a secure, private location for one permanent signing key and its backup, controlled by Boss.
2. Generate or securely import that key only in an approved private environment. Record **only its public certificate SHA-256 fingerprint** in the release record.
3. Run full CI on the exact accepted source. In a separate authorized private build, supply the four environment values, explicitly enable signing, and verify the signed APK's package ID, version, certificate fingerprint and APK SHA-256.
4. Install only after approval, alongside existing Blake apps. Use disposable drafts/files for a brief acceptance check.
5. Future upgrades must retain the same signing identity and application ID, and use a higher version code. Stop on any mismatch; never uninstall or clear data to bypass an update failure.

This file records an implementation gate, **not** a signed-install or backup/restore PASS.
