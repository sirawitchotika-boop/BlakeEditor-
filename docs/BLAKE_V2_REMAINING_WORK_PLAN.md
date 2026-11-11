# Blake Editor 2.0 — remaining work and execution plan

**Status: planning document only. No tasks are scheduled and no code merge or release is authorized.**
**Repository:** `sirawitchotika-boop/BlakeEditor-`
**Verified checkpoint:** November 12, 2026
**Read next:** [Execution checkpoint](BLAKE_V2_EXECUTION_CHECKPOINT.md)

## What is already built?

The merged Original editor and V2 foundation cover native text/Thai/emoji input, code view, editor tools, settings, Markdown, bounded large-file reading, safe Save / Save As, encoding, recovery, and shared V2 components. Original remains the protected production launcher.

Draft-management milestones are implemented in **open, unmerged draft PRs**:

| Work | PR | CI | Physical phone |
| --- | --- | --- | --- |
| Optional V2 beta entry and theme handoff | #24 | Passed on the current PR head at earlier verification | Exact final integration still needs review |
| Durable Untitled numbering and recovery metadata | #25 | Passed | Boss: PASS 4/4 |
| Rename Draft | #26 | Passed | Boss: PASS 5/5 |
| Move to Trash and document-card spacing | #27 | Passed | Boss: PASS 5/5 |
| Multi-select, Restore, permanent delete | #29 | Passed | Boss: PASS 5/5 |
| Recent-file shortcut removal and pinned Trash button | #30 | **Passed** at `09f0004f27fe8209ff1223c18be47b76303af8d0`, run `43086206591` | **Not yet reported** |

Historical merged work is in PRs #1–#23. PR #28 was a duplicate and is closed without merge. `main` was `309134c44834112b4d2b731c475dddad676f4049` when this plan was written. **Recheck all GitHub state on every execution**, because this document is a dated snapshot.

### Can we simply test and finish?

**Possibly for the draft-management feature.** If PR #30 passes its remaining phone checks and a short data-safety review finds no blockers, the feature itself may be complete. Do **not** invent extra feature milestones to fill a schedule.

**For the whole Blake Editor 2.0 app**, there are still separate integration and release decisions: PR #24 is parallel to the stacked PRs #25 → #26 → #27 → #29 → #30. CI passes on separate PRs do not prove their **combined build** is safe. Original must not be replaced or released without explicit Boss approval.

## What remains? Small, ordered work packages

| Phase | Required? | Scope of one bounded execution | Completion / stop condition |
| --- | --- | --- | --- |
| A — Final PR #30 phone acceptance | **Required, human** | Boss tests pinned Trash, Select/Remove from Recent, restart, reopening hidden files, and unchanged real-file contents | Record PASS/FAIL; on FAIL, a separate small fix PR and new test APK |
| B — Code review: recovery and files | **Required before integration** | Review latest stacked PR changes for atomic recovery, revision races, corruption, counters, lost unsaved text, Restore conflicts, real file writes and Save As | Short findings with severity and direct file/line references; add focused tests only for confirmed gaps |
| C — Code review: UI, build and maintenance | **Required before integration** | Review fixed header/scrolling, checkbox confirmation, duplicate names, navigation, lifecycle, access to Original, CI/test coverage, and stale docs | Short findings; classify **blocking**, **nonblocking**, and **optional** |
| D — Fix blocking findings | **Only if needed** | Fix **one well-defined problem** per execution, in a separate draft PR; update focused tests and verify exact-head CI | No unresolved high-severity issues; do not expand scope just because time is available |
| E — Integration planning | **Required** | Review ancestry and conflicts between **parallel** #24 and **stacked** #25/#26/#27/#29/#30; propose safe sequence, staging branch, rollback strategy | Written dependency/conflict report; no merge or launcher change |
| F — Combined integration build | **Required if Boss authorizes staging** | Prepare a nonproduction integration **draft PR** on a separate branch containing approved changes; preserve Original; run full automated checks | Exact-head CI passes and isolated test APK is available; no automatic merge |
| G — Full phone acceptance | **Required, human** | Boss tests combined build: Original, V2 Home, draft recovery, Trash, Recent, Save, code, Markdown, Thai/emoji, external files, large files, themes and restart | Record independent PASS/FAIL on **exact combined APK** |
| H — Release or stop at beta | **Explicit Boss decision** | Review release checklist, safety/rollback, accessibility, performance, signing, promotion gate, and any unresolved issues | Boss decides **keep beta**, **approve merges**, **promote launcher**, or **release**. These are separate permissions |

**Do not promise a fixed number of code-fix phases.** If B and C find no blockers, skip D. If the combined build passes, do not create an unnecessary polish project. Any new feature request or larger redesign goes into a separate optional backlog.

## Code review and maintenance checklist

Use a *short* review per subsystem; do not scan the full repository without purpose.

- **Storage/data:** old recovery v1/v2/v3 compatibility, untouched corrupt files, monotonic Untitled counter, rename identity, Trash snapshots and bulk partial failures, safe detached copies on conflicts, hidden Recent shortcut index, crash/restart and stale revision tests.
- **Real file safety:** separate Save/Save As behavior, SHA/fingerprint checks, BOM/Thai encoding, SAF permissions, external Android Open With, >2 MiB read-only route, no unrequested source writes or deletion.
- **UI/device:** scrolling never triggers an action; Trash stays fixed while Recent grows; Home categories don't duplicate entries; accessible labels/contrast, touch targets, Android Back and keyboard, selection/caret/scroll on rotate/restart, Original default remains unchanged.
- **Build/security/maintenance:** exact-head unit and regression checks, release variant without V2 launcher surprise, unique isolated APK package, scoped dependencies, stale documentation, possible dead code and duplicated logic; prioritize concrete bugs over aesthetic refactors.
- **Optional after stability:** deeper battery profiling, extended device/OS matrix, icon mask refinement and broader accessibility polish unless a blocking defect appears.

## Running future one-time tasks

This plan is suitable for later **single-use Create Task instructions** in any ChatGPT conversation. **No actual times are set here.** Every separately created task must state its **phase ID**, allowlist of actions, and where this document and the checkpoint are stored in GitHub.

Each execution must:

1. Fetch this document and [the checkpoint](BLAKE_V2_EXECUTION_CHECKPOINT.md) from their **explicit GitHub branch/ref**. Do not rely on the chat transcript, previous task output, or uploaded files.
2. Inspect the live repo, PR heads, checks, and approval status. Detect if the phase is already completed; do not duplicate commits.
3. Confirm predecessor dependencies. If the previous phase is `BLOCKED`, `PARTIAL`, `WAITING_CI`, or awaiting a phone result, **do not silently continue to a dependent phase**.
4. Do **one bounded work package**: one review area, or one small fix + tests. Prefer modest diffs (roughly 1–4 core files plus tests) and a concise response. If too large, stop at a clean checkpoint rather than truncating or claiming completion.
5. Test what is possible, report the **exact commit and run link**, and make any code change on its own new branch and **draft PR** only if that action was authorized for that task.
6. Update the checkpoint if authorized and possible, using safe GitHub ref/commit checks. If it cannot be updated, report the blocker, PR link and current exact SHA. No speculative success or pretending a CI run/phone test passed.
7. Finish with no more than ~250 words: **Phase / Status / Commit / Done / Tests / Blocker / Next**; provide 3–5 phone checks only for a verified new isolated APK.

**Hard boundaries:** No automatic merge, release, production deployment, changing Original default or external file routing, force pushing unrelated branches, deleting real files, clearing installed app data, or guessing human approval. Scheduled ChatGPT runs have separate execution contexts and may lack GitHub/Codex permissions; detect this rather than pretending they ran.

## Decision log and future scheduling

- **Scheduling:** NOT STARTED. Boss will review the roadmap first and choose whether any one-time tasks are necessary.
- **Execution permission:** This planning document does not authorize code changes or merges. Individual future task prompts must grant their own limited permissions.
- **Human gates:** Phone acceptance and production/release approval remain with Boss.
- **Fastest safe path:** Finish PR #30 phone checks → two targeted reviews → fix only real blockers → plan/test combined build → Boss makes the final release decision.
