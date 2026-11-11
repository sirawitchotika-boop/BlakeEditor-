# Blake Editor 2.0 — six-phase execution checkpoint

**Plan:** [Complete remaining work plan](BLAKE_V2_REMAINING_WORK_PLAN.md)  
**Runbook:** [One-time six-phase agent runbook](BLAKE_V2_SIX_PHASE_AGENT_RUNBOOK.md)  
**Repository:** `sirawitchotika-boop/BlakeEditor-`  
**Checkpoint branch:** `docs/blake-v2-remaining-work-plan-2026-11-12` (draft PR #31; NOT `main`).

## Phase C — UI and maintenance review (2026-11-12, Asia/Bangkok)

- **Phase:** C; **Status:** `PASSED` for the independent read-only UI/maintenance audit; **integration remains BLOCKED** by Phase B's unresolved P0 cross-instance recovery mutation race. No new confirmed P0/P1 UI defect in the inspected source. No app code changed, no new PR.
- **Live refs checked:** `main` = `309134c44834112b4d2b731c475dddad676f4049`; draft PR #24 `4f6fccd0a5675a67aa2df38ad0b566ae31162a8d`, #25 `bb74f2e86f4ecd01e21f9b2722f3bb83a6faca7d`, #26 `81e8769e660d274688bed49cbb2fb5ad24d8409a`, #27 `10588024d9879c6bd347a9261fb8bd30cc39ba3d`, #29 `72ee83d240e440ded0b3126025da83f50581d544`, #30 `09f0004f27fe8209ff1223c18be47b76303af8d0`, docs #31 `cfd9c0d9b71a203fa555d2da4133384c196d69a6`, safety #32 `6cc82d837dc19203cf35c92654aa1cbd2472d5b5`; all remain open, draft and unmerged. #24 remains parallel to the #25→#30 stack; #32 is based on #30.
- **UI behavior verified by static source:** `V2WorkspacePreviewActivity.kt:279-294` places the Trash navigation and draft selector above/outside the Home document `ScrollView`; `:354-423,482-675` uses Android multi-choice dialogs, review and final confirmation, partial-failure reporting for Recent hide, Move, and permanent Delete. `V2HomeSections.kt:16-23` hides Recent shortcuts without filtering unsaved drafts or Trash. `V2WorkspacePreviewActivity.kt:142-155,206-254,721-813` covers Back/IME, state snapshots, pause/stop recovery, cursor and scroll restoration; physical behavior remains unverified. `ExternalFileRouterActivity.kt:28-99` routes incoming text by size before Original initialization; `app/src/main/AndroidManifest.xml:9-100` retains Original MAIN/LAUNCHER and external file router on #30. PR #24's release manifest adds V2 as non-exported, internal-only and a debug-only launcher alias; its exact-head CI confirms the release gate, but combined behavior has NOT been tested.
- **Nonblocking P2 UI race to consider later:** `V2WorkspacePreviewActivity.kt:294-349` asynchronously loads Home without rechecking whether Home is still displayed before updating the shared `status` field. If a user opens a draft before that query finishes, a stale Home result can overwrite the editor's status text and fill a detached list. The code interleaving is possible; no phone reproduction and no evidence of file loss. Do not displace the P0 fix for this polish.
- **Optional P2 CI hardening:** `.github/workflows/build-apk.yml:20-47` downloads Ace/Markdown vendor JavaScript from third-party CDNs at build time without checksum verification; action references use major tags rather than immutable SHAs. This is a reproducibility/supply-chain review item, not a demonstrated compromise or blocker. `README.md` describes the early V2 foundation and Input Lab, not the full current draft-management stack; docs update is optional after integration.
- **Exact-head CI rechecked:** #24 [43053995070](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43053995070), #30 [43086206591](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43086206591), and #32 [43105595182](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43105595182) all completed **SUCCESS** on their current heads; #25/#26/#27/#29/#31 exact-head PR runs also completed SUCCESS. Inspected job steps on #24/#30/#32: JVM unit/debug, input/caret, unsigned release build, release manifest/reader assets, isolated test package ID and artifact uploads passed; #24 additionally passed V2-entry theme and button tests. **#32 was WAITING_CI in Phase B but is now SUCCESS.** No new CI triggered, no local or Android device tests run, and no integrated #24+#30 run exists.
- **D fix queue:** (1) **P0 BLOCKING** fix Phase B's cross-instance recovery mutation race in `RecoveryDraftStore.kt:102-114,257-405` with deterministic concurrency tests and new draft PR; (2) confirm exact-head CI for the P0 fix and incorporate already-green #32 as appropriate; (3) **P2 NONBLOCKING** stale Home status callback, build asset pinning, docs drift only if they become evidenced gate failures. **No speculative UI patch in C.**
- **Human gate remains:** Original/2.0 keyboard and Back, fixed Trash during long scroll, multi-select Cancel/Restore, Recent hide/reopen, rotation/background, real SAF/large-file routing, theme consistency, isolated integrated APK. Do not claim phone PASS.
- **Next safe action:** Phase D addresses one confirmed P0 recovery race in a separate draft PR and verifies its exact-head CI. Phase E cannot integrate while that P0 remains unresolved.

## Phase B — data safety review (2026-11-12, Asia/Bangkok)

- **Phase:** B; **Status:** `BLOCKED` for integration due to unresolved **P0 cross-instance recovery mutation race**. The review itself is complete at the inspected refs; Phase C may run its independent read-only audit, and Phase D must address the blocker. No phone acceptance.
- **Live refs:** `main` = `309134c44834112b4d2b731c475dddad676f4049`. Draft heads #24 `4f6fccd0a5675a67aa2df38ad0b566ae31162a8d`, #25 `bb74f2e86f4ecd01e21f9b2722f3bb83a6faca7d`, #26 `81e8769e660d274688bed49cbb2fb5ad24d8409a`, #27 `10588024d9879c6bd347a9261fb8bd30cc39ba3d`, #29 `72ee83d240e440ded0b3126025da83f50581d544`, #30 `09f0004f27fe8209ff1223c18be47b76303af8d0`, #31 `727577a1711f34dc94c858406bf4d392acbc1e30`. All seven earlier exact-head Actions runs were SUCCESS; none verify integration.
- **P0 unresolved, code-level concurrency finding:** `app/src/main/java/com/blake/editor/session/v2/RecoveryDraftStore.kt:102-114,257-405` protects save/rename/Trash/Restore/delete with **instance-local** `@Synchronized` only; companion object shares a lock for number reservation at `:99`, not other mutations. Two store instances pointing at the same private directory can both pass revision checks against the same old snapshot, then an older write or deletion can overwrite/remove a newer record. This is a demonstrable unsafe interleaving from the code, **not** a device-reproduced incident. `RecoveryDraftStoreTest.kt:393-416` tests sequential stale revisions, not simultaneous store instances. **Integration gate BLOCKED** pending a shared mutation lock or equivalent, with deterministic concurrent regression coverage and exact-head CI. No direct code rewrite of this P0 was attempted in B.
- **Confirmed P1 availability bug fixed in separate draft [PR #32](https://github.com/sirawitchotika-boop/BlakeEditor-/pull/32):** `RecoveryDraftStore.kt:213-240` initially refused the first Untitled counter reservation when only the **optional** `recent-shortcuts-hidden.index` was corrupt, despite all recovery files being readable. Minimal fix excludes that sidecar alone from the unsafe-counter check, while still rejecting corrupt `.recovery` records; adds two tests in `RecoveryDraftStoreTest.kt`. Branch `fix/v2-optional-recent-index-counter-2026-11-12` based on #30; exact head `6cc82d837dc19203cf35c92654aa1cbd2472d5b5`; only 2 files, 27 additions/1 deletion. **CI [43105595182](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43105595182): in progress when checked; WAITING_CI, NOT PASSED.** No local tests run.
- **Reviewed safeguards (static, not phone-tested):** v1/v2/v3 recovery deserialization `RecoveryDraftStore.kt:505-566`; atomic fsynced temp writes `:585-605`; durable Untitled counter `:213-249`; revision guards, Trash and detached linked-file Restore `:257-405`; Recent visibility-only sidecar `:120-204`; SAF read/write and verified backup/readback `VerifiedDocumentSaver.kt:33-84`, `V2WorkspacePreviewActivity.kt:1270-1420`; strict UTF/Thai encoding/BOM `TextEncodingCodec.kt:40-129`. Existing focused tests cover these normal and error paths, but not cross-instance mutation races or provider/device behavior.
- **Tests:** Existing #30 exact-head CI [43086206591](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43086206591) SUCCESS; #32 CI pending as above. Physical Android, real SAF provider, and integrated #24+#30 tests **NOT RUN**. No merges, release, launcher switch, or user-file changes.
- **Next:** Phase C read-only UI audit; Phase D fix P0 cross-instance recovery mutation race in its own scoped draft PR, check #32 CI and exact-head checks. Phase E must remain BLOCKED until P0 fixed and all required CI green.

## Phase A — live inventory and PR baseline (2026-11-12 04:04 Asia/Bangkok)

- **Phase:** A; **Status:** `PASSED` (read-only baseline; NOT an integration or phone acceptance).
- **Repository / main:** `sirawitchotika-boop/BlakeEditor-`, `main` = `309134c44834112b4d2b731c475dddad676f4049`; release manifest still declares Original `com.blake.editor.ui.main.MainActivity` as MAIN/LAUNCHER and `ExternalFileRouterActivity` for external file intents.
- **Verified open draft PR heads:** #24 `4f6fccd0a5675a67aa2df38ad0b566ae31162a8d` (parallel from main); #25 `bb74f2e86f4ecd01e21f9b2722f3bb83a6faca7d`; #26 `81e8769e660d274688bed49cbb2fb5ad24d8409a`; #27 `10588024d9879c6bd347a9261fb8bd30cc39ba3d`; #29 `72ee83d240e440ded0b3126025da83f50581d544`; #30 `09f0004f27fe8209ff1223c18be47b76303af8d0`; documentation #31 `19d9f43e1b4a00f4e25203bcb9198c3d65482b04` before this checkpoint edit.
- **Verified ancestry:** compare API confirms #25 is ahead of main, each subsequent stack head is ahead of its predecessor with no behind commits; #24 and #30 diverge at main. The #30 head contains the #25→#26→#27→#29 history. No combined #24+#30 head or CI exists. #28 is closed and unmerged; do not replay it. Earlier #1–#15 and #20–#23 include merged foundations and safeguards; superseded #16–#18 remain closed.
- **Exact-head CI observed:** #24 [43053995070](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43053995070), #25 [43063989310](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43063989310), #26 [43072595650](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43072595650), #27 [43076157104](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43076157104), #29 [43082417668](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43082417668), #30 [43086206591](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43086206591), #31 [43092923013](https://github.com/sirawitchotika-boop/BlakeEditor-/actions/runs/43092923013) all completed SUCCESS on the stated heads. Inspected #24/#30 job steps: input/caret regression, JVM unit/debug build, release build/launcher/reader checks, isolated package verification and artifact upload succeeded. #24 also passed V2-entry theme/button checks. **No new CI triggered; no local tests run.** No submitted reviews or inline review comments on open #24/#25/#26/#27/#29/#30/#31.
- **Code architecture map:** V2 recovery/identity/workspace/verified save under `app/src/main/java/com/blake/editor/session/v2/{DocumentIdentity,DraftWorkspace,RecoveryDraftStore,VerifiedDocumentSaver,V2HomeSections}.kt`; Home/navigation UI `ui/preview/V2WorkspacePreviewActivity.kt`; Original `ui/main/MainActivity.kt`, `editor/NativeInputEditText.kt`; file safety `file/{AndroidDocumentRepository,TextEncodingCodec,LargeTextOpenGate,PagedTextReader,UriPermissionManager}.kt`, `ui/large/ExternalFileRouterActivity.kt`. Essential JVM suites `session/v2/{DraftWorkspace,RecoveryDraftFormatV2,RecoveryDraftStore,V2HomeSections,VerifiedDocumentSaver}Test.kt`, `file/{TextEncodingCodec,LargeTextOpenGate,PagedTextReader}Test.kt`, `editor/{NativeDocumentSyncGuard,NativeUndoHistory}Test.kt`, plus `app/src/test/js/editor-host-input-regression.test.cjs`.
- **Three highest-value next reviews:** (1) **P0** recovery v1/v2/v3 compatibility, atomic writes, revision races, Trash/Restore partial failures, persistent Untitled counters and corrupt-record retention (Phase B); (2) **P1** external SAF Save/Save As, encoding/BOM, linked-file conflict handling and >2 MiB read-only route (Phase B); (3) **P1/P2** fixed Trash header, Recent shortcut visibility-only behavior, selection/back/lifecycle, Original default/manifest and CI integration (Phase C). These are risk targets, **not confirmed defects**.
- **Dependency status:** Baseline complete. B and C can begin read-only audits; D/E must wait for evidence of no unresolved P0/P1 defects. #30 phone checks remain unreported; earlier user-reported phone passes for #25/#26/#27/#29 are historical and do not verify combined work.
- **Human gate remains:** real Android input, recovery, Trash, file integrity, external open and isolated integrated APK acceptance; separate approval required for merges, launcher change and release.
- **Next safe action:** Phase B review latest #30 recovery/workspace/storage and safety tests, then Phase C UI and manifest checks. No app code changed, no PR opened or merged.

## Initial verified state (November 12, 2026)

| Item | Status |
| --- | --- |
| `main` | `309134c44834112b4d2b731c475dddad676f4049` |
| Parallel V2 Beta entry PR | #24 open/draft/unmerged |
| Stacked draft PRs | #25 → #26 → #27 → #29 → #30, all unmerged |
| PR #30 head | `09f0004f27fe8209ff1223c18be47b76303af8d0` |
| PR #30 CI | Run #43086206591 SUCCESS on that exact head |
| Previous user phone acceptance | #25 PASS 4/4; #26/#27/#29 PASS 5/5 |
| PR #30 phone test | **PENDING**, deferred to final human gate |
| Documentation PR #31 | Open/draft/unmerged |
| Scheduled tasks | **NOT CREATED IN THIS CHAT** |
| Actual code changes/merges/releases | **NONE authorized by the documentation PR** |

All values are a snapshot. Revalidate before acting; do not mark external phone tests PASS without Boss's report.

## Live phase ledger

| Phase | Proposed start (Asia/Bangkok) | Status | Main artifact / link |
| --- | --- | --- | --- |
| A — Baseline | 2026-11-12 04:00 | `PASSED` | [Phase A baseline in this checkpoint](#phase-a--live-inventory-and-pr-baseline-2026-11-12-0404-asiabangkok) |
| B — Data safety review | 2026-11-12 04:45 | `BLOCKED` (P0 race; #32 CI pending) | [P0 finding and P1 draft PR #32](#phase-b--data-safety-review-2026-11-12-asiabangkok) |
| C — UI / maintenance review | 2026-11-12 05:30 | `PASSED` (independent audit; P0 integration blocker remains) | [Phase C findings and D queue](#phase-c--ui-and-maintenance-review-2026-11-12-asiabangkok) |
| D — Minimal fixes | 2026-11-12 06:15 | `NOT_STARTED` | — |
| E — Integration draft PR | 2026-11-12 07:00 | `NOT_STARTED` | — |
| F — Final PR-only gate | 2026-11-12 07:45 | `NOT_STARTED` | — |

`NOT_STARTED` = not run; `PASSED` = evidenced complete; `NO_FIX_REQUIRED` = no defects found for D; `PARTIAL` = work done but unfinished; `WAITING_CI` = exact-head checks incomplete; `BLOCKED` = unsafe to proceed; `ALREADY_DONE` = verified idempotent skip; `READY_FOR_HUMAN_GATE` = CI/review ready, but human phone/release approval still required.

## Append an honest short record at the top after each run

```text
Phase:
Start/reference time:
Status: PASSED | NO_FIX_REQUIRED | PARTIAL | WAITING_CI | BLOCKED | ALREADY_DONE | READY_FOR_HUMAN_GATE
Repository / actual current main SHA:
Verified PRs / exact tested head:
Dependency status:
Findings / code changes (include file:line where relevant):
Tests and checks actually run with URLs / PASS / FAIL / NOT RUN:
Checkpoint updated at commit/branch (or failed to update):
Human gate remains:
Next safe action:
```

**Checkpoint mutation rules:** Update only this file on its own documentation branch, via a fresh read and compare-and-swap/expected head. Do not overwrite another run's results; on a conflict re-read, combine, and retry once. If no write access, put the same report in the task result and mark checkpoint write `BLOCKED`. Never write a result for a different phase or invent timestamps/CI outcomes.

**Keep the schedule itself separate** from this document. A status row is not proof a ChatGPT Create Task was created.
