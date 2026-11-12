# Blake Editor 2.0 — Phases G–L: project closeout roadmap

**Prepared:** November 12, 2026 (Asia/Bangkok)  
**Repository:** [sirawitchotika-boop/BlakeEditor-](https://github.com/sirawitchotika-boop/BlakeEditor-)  
**Main planning baseline:** `4212f27a57c8f0223afc20dee0dc5cfd50eb51dc`

This plan starts **after Phases A–F**. It is a proposal, not permission for automatic merging, publishing, launcher changes, or user-data changes.

## Verified starting position

- [Integration PR #34](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/34) and [documentation PR #31](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/31) were merged into `main`. The open-PR query returned zero PRs at planning time.
- Integrated head `30285c7912a057000ec2dcac2242251418cc2130` passed [GitHub CI run 43124844965](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43124844965).
- Boss reported **A–F PASS** on a physical phone with the isolated APK. This report does not prove a signed in-place upgrade or production release.
- **Original remains the regular launcher**. Blake 2.0 Beta is an optional in-app workspace. The external file router and >2 MiB read-only safeguard remain part of the current design.
- `app/build.gradle.kts` currently declares `versionName="0.1.0"` and `versionCode=1`. Those are build metadata, not evidence of a published version.
- The [A–F checkpoint](BLAKE_V2_EXECUTION_CHECKPOINT.md) records the historical pre-merge status; do not read its old “open PR” rows as current facts.

## Safety and decision boundaries

**Four separate decisions:** merge review PRs; change the default launcher; sign/distribute a release; close the project. One does not authorize another.

Every phase must first refresh the current `main` SHA, PR heads, source, CI state, and any recorded physical test results. Do not uninstall Original, clear storage, delete recovery data, overwrite real files, force an Android upgrade, switch the default launcher, or publish without explicit authorization. Use disposable documents for tests.

Use accurate labels: **PASS**, **FAIL**, **NOT_TESTED**, **PARTIAL**, **BLOCKED**, and **WAITING_CI**. A green build cannot stand in for a physical-device or signed-upgrade test.

## At a glance

| Phase | Goal | Main deliverable | Exit requirement |
| --- | --- | --- | --- |
| **G — Documentation baseline** | Correct README, structure, dependencies and status | Documentation-only PR | Paths and facts reviewed; CI exact-head verified |
| **H — Stability and safety audit** | Review remaining data and build risks | Findings + narrow PRs where justified | No unresolved P0/P1 blocker |
| **I — Android acceptance** | Test latest isolated candidate on a real phone | Exact-build manual PASS/FAIL record | All required phone checks pass |
| **J — Release candidate** | Package, signing, versioning, upgrade/rollback | Identified signed candidate and safe-update evidence | Approved signing and safe upgrade |
| **K — Controlled distribution** | Decide launcher and distribution path | Approved release, or explicit deferral | Separate Boss approval and rollout evidence |
| **L — Closure** | Preserve evidence and maintenance guide | Final docs, release/tag references and issue ledger | Verified release and no unowned critical work |

No calendar dates are committed by this roadmap. The required security/device/upgrade gates may lead to additional fixes and another candidate build.

## Phase G — Documentation baseline

**State: IN_PROGRESS** while the documentation PR is prepared.

1. Check README facts against the current Git tree, `AndroidManifest.xml`, Gradle files and Actions workflow.
2. Document Original and V2 entry, external file routing, app architecture, module ownership, grouped source tree, actual direct dependencies, assets, test suites, build instructions, artifact names, and safety practices.
3. Preserve historic docs; label older status snapshots as historic rather than deleting them.
4. Create one documentation-only draft PR. Verify exact-head automated CI when available.
5. Keep all source code, build settings and saved data unchanged.

**PASS:** clear README and roadmap, accurate links, reviewed documentation diff, exact-head CI successful or explicitly marked NOT_TESTED.

## Phase H — Final stability, data safety and security review

**State: NOT_STARTED.**

Inspect the current integrated app for actual defects, not speculative redesign:

- Recovery versions, revision checks, atomic writes, corrupted records, cross-instance writes, Trash/Restore, never-reused Untitled counters and Recent shortcut metadata.
- Android SAF URI handling, Save and Save As, backup/readback, encoding/BOM, changed-linked-file conflicts, external open, and large-file read-only routing.
- Input caret/selection, navigation, keyboard/Back, lifecycle, Original compatibility, V2 Home status callbacks and theme settings.
- CI supply-chain integrity: pin or verify downloaded Ace/marked/DOMPurify assets; inspect Gradle/Actions versions, manifest, package identity and reproducibility.
- Confirm if more than one process writes recovery data; current process-wide lock does not guarantee cross-process locking.

**Output:** P0/P1/P2 findings and evidence, plus one small fix PR per real blocker with focused tests. **PASS** requires no unresolved P0/P1 defect and green CI at the accepted head. If an important fix is not ready, mark **BLOCKED** or **WAITING_CI**.

## Phase I — Fresh real-phone acceptance

**State: NOT_TESTED for a future Phase H candidate.**

Download the isolated APK from the **exact accepted Phase H head**; do not assume the earlier Phase A–F APK covers later changes. Test on disposable files without replacing or clearing Original.

1. **Original:** English, Thai, emoji, caret, selection, undo/redo, themes. Expected: text is correct and Original still works.
2. **V2 editor:** deliberate Beta entry, Home, code tools, Markdown, themes, Back and keyboard. Expected: navigation and editing work.
3. **Drafts:** New, rename, multiple drafts, Trash/Restore, bulk confirmation, Recent hide/reopen, background and cold start. Expected: no lost draft or unexpected deletion.
4. **Save:** disposable TXT, Save As, encoding/BOM, linked-file-change protection. Expected: correct file bytes and no silent overwrite.
5. **Large file:** open disposable >2 MiB TXT from Android Files or supported file provider. Expected: read-only paging and no editable whole-file load.

Record exact APK run/commit, Android version, device, tested file provider and results. Broader OS/OEM and provider coverage remains **NOT_TESTED** unless actually performed. **PASS** only with user-observed results on that candidate.

## Phase J — Signed release-candidate and safe-upgrade gate

**State: NOT_STARTED.**

1. Deliberately choose versionCode, versionName, app ID and launcher behavior. Do not silently promote V2.
2. Use an authorized, secure signing process; **never** put secrets or signing keys in GitHub source or chat.
3. Record source commit, CI build, artifact hash, signature/certificate identity, package name, manifest and release notes.
4. Test a signed **in-place upgrade** on an appropriately signed, disposable or safely backed-up app install. Verify Original data, drafts, settings, file permissions, external files and recovery records survive.
5. Prepare a rollback/stop-rollout plan. Android uninstall/clear-data is never an acceptable “rollback”; an APK downgrade may not preserve data or be supported.
6. Repeat affected phone tests if the signed candidate differs from the accepted isolated debug build.

**PASS:** exact candidate reproducible, signed with approved identity, safe upgrade observed, and data/rollback risks reviewed. Without signing authority or safe-upgrade proof: **BLOCKED**.

## Phase K — Controlled release decision

**State: NOT_STARTED. Explicit Boss approval required.**

Choose **one** release route:

- **Conservative (recommended):** Original stays default; Blake 2.0 remains opt-in.
- **Promotion later:** propose a separate small PR to make V2 the default only after CI, phone and user approval.

If authorized, publish only the verified signed candidate through the chosen distribution channel. Provide a version, SHA-256, release notes, known issues and install/upgrade guidance. Check the resulting distribution and actual installation. Keep rollout small enough to stop if data-loss issues appear.

**PASS:** Boss explicitly approves the exact release/launcher plan; the published artifact matches the approved build; real-install checks pass. If no release is wanted, mark **DEFERRED**, not "released."

## Phase L — Final handoff and project closure

**State: NOT_STARTED.**

1. Recheck main, final approved release/tag, closed/open PRs, CI evidence and actual installed candidate results.
2. Replace outdated project-status prose in README; do not falsify historic A–F records.
3. Produce final release notes, an architecture/maintenance entry point, test matrix, known limitations and safe recovery/rollback guide.
4. Record the last verified commit, artifact identity, version, signing ownership (without secrets), release channel and where future defects should be tracked.
5. Close only verified completed work; move remaining noncritical improvements to a clearly labeled backlog.
6. Tag the approved source only with explicit authorization. Preserve repository history and user data.

**PASS / PROJECT CLOSED:** approved release is verifiably delivered and tested, no unresolved P0/P1 issue, documentation matches reality, and Boss explicitly approves closure. Without a release, the development milestone may be marked complete but the entire release project must remain **NOT CLOSED**.

---

## Workload sizing (planning estimates, not execution promises)

| Phase | Expected size | Main uncertainty |
| --- | --- | --- |
| G | Small | Documentation accuracy and CI |
| H | Medium–large | Number and severity of actual bugs; dependency integrity fixes |
| I | Small–medium human testing | Device/provider availability and bugs found |
| J | Medium–large | Signing authority, existing app upgrade compatibility, data safety |
| K | Medium | Distribution route and release approval |
| L | Small | Release evidence and outstanding issues |

**Critical path:** G → H → I → J → K → L. Repeat H/I as needed when an accepted candidate changes. Avoid scheduling all later phases as automatic jobs before the prior gates are complete.

## Known follow-ups to inspect, not assume broken

- Phase C noted a possible outdated V2 Home status callback: evidence needed before changing code.
- CI downloads third-party JS assets by version, without a verified checksum pin; review supply-chain integrity.
- Shared recovery mutation locking is within the process; do not claim interprocess safety.
- The [V2 promotion gate](BLAKE_EDITOR_V2_PROMOTION_GATE.md) is historic and predates merged opt-in V2 navigation.
- The existing `TASK_PROGRESS.md` records past intermediate states. Keep it as history instead of treating it as current release status.
