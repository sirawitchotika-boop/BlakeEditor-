# Blake Editor 2.0 — complete remaining work plan

**Project:** \`sirawitchotika-boop/BlakeEditor-\`  
**Planning snapshot:** November 12, 2026; refresh repository facts at every run.  
**Mode:** Six **one-time**, bounded agent phases, PR-only tests, then a separate **human full gate**. **No actual ChatGPT Tasks are created by this document.**  
**Execution instructions:** [Six-phase agent runbook](BLAKE_V2_SIX_PHASE_AGENT_RUNBOOK.md)  
**Handoff status:** [Execution checkpoint](BLAKE_V2_EXECUTION_CHECKPOINT.md)

## Verified repository status

- Protected Original remains the normal release launcher on \`main\`, observed at \`309134c44834112b4d2b731c475dddad676f4049\`.
- V2 input, code tools, settings, Markdown reader, large-file read-only routing, backup-aware Save / Save As, encoding, and recovery foundations are already implemented in earlier merged PRs. Source and current tests take priority over stale historical docs.
- **PR #24** (optional V2 Beta entry) is open, draft, and parallel to the draft-management stack. Earlier exact-head CI passed, but combination with all other features is NOT tested.
- Draft-management stack is **#25 → #26 → #27 → #29 → #30**. All are open draft PRs and unmerged as of the snapshot.
- Boss reported phone **PASS 4/4 for #25** and **PASS 5/5 for #26, #27, and #29**.
- **PR #30** adds safe Remove from Recent and a fixed Trash header. Exact head \`09f0004f27fe8209ff1223c18be47b76303af8d0\`, Actions run \`43086206591\` = **SUCCESS**. **Boss has not yet reported its phone checks.** Defer them to the final human gate; do not claim they passed.
- Closed duplicate **PR #28** is unmerged; never reapply it.
- **PR #31** carries this documentation on its own unmerged branch; do not mistake it for a code integration PR.

## What is left?

There are no required new product features beyond the open stack if review finds it safe. The remaining work is **targeted review, real bug fixes only when needed, combined PR verification, and the final human decision**. Do not create artificial work to fill scheduled time.

| Phase | What the agent does | Output | Gate |
| --- | --- | --- | --- |
| **A — Inventory & PR baseline** | Re-read this plan, master workflow skills, live PRs, branches, CI; map dependency and identify known risks | Verified status matrix + checkpoint | Read-only; no app changes |
| **B — Data safety review** | Review recovery, counters, Trash/Restore, Recent shortcut index, Save/SAF/encoding invariants; identify critical regression tests | Findings with file/line evidence, severity, and tests to add | Do not proceed past unresolved data-loss risk |
| **C — UI/maintenance review** | Review fixed Trash header, selection/scroll, Back/lifecycle, Original and external-file routing, CI security and readability | Findings + precise fix queue | Only evidenced blockers warrant code edits |
| **D — Targeted fix & PR checks** | Fix one highest-priority confirmed blocker with focused tests in a separate draft PR; otherwise report no fix needed | Fix draft PR with CI state, or clear \`NO_FIX_REQUIRED\` | No unrelated rewrites or new features |
| **E — Nonproduction integration** | If B–D safe, prepare **one staged integration draft PR** combining parallel #24 with the #25→#30 stack; do not merge individual PRs or touch release defaults | Integration PR or detailed blocked/conflict matrix | Exact-head CI must be requested; no production switch |
| **F — Automated final review / human gate packet** | Verify exact integrated PR head, run/inspect unit, regression, release, manifest, isolated APK checks, review blockers; prepare phone/full gate checklist | \`READY_FOR_HUMAN_GATE\`, \`WAITING_CI\`, or \`BLOCKED\` with evidence | **No physical phone test, merge, launcher promotion, or release** |

**Stopping early is a valid outcome.** If a phase is blocked or CI is still running, the next dependent phase records the blocker instead of guessing completion. The six appointments do **not** guarantee the whole project will be finished in one morning. Schedule additional one-off tasks only with a later explicit decision.

## PR-only verification allowed in these six phases

- Read static diffs, code and existing unit tests, GitHub Checks/Actions logs; add narrow JVM tests where authorized.
- For changed branches, use **new draft PRs** and verify \`testDebugUnitTest assembleDebug\`, existing input/caret regression, \`assembleRelease\` (not published), release launcher/reader asset checks and the isolated test package ID on the exact commit.
- Recheck checks after every new commit; never reuse a green run from an older SHA.
- If CI remains pending at the phase boundary: checkpoint \`WAITING_CI\`, do not claim success.
- Phone testing, performance profiling, physical device behavior and production signing **are not executed** by scheduled PR-only runs. They belong to the later full gate.

## Code review and maintenance priorities

**P0 data loss:** persistent recovery files and v1/v2/v3 compatibility, crash-safe atomic writes, corrupt files retained, revision races and concurrent callbacks, never-reused Untitled numbering, Trash confirmation and batch partial failure, exact cursor/selection/scroll restoration, conflict-safe detached \`Recovered — filename\`, no blind Save to linked files, and no external file deletion.

**P1 app integrity:** Recent shortcut index must hide lists only, never remove saved file or recovery data; Android SAF permissions; strict encoding/BOM; SHA/fingerprint, backups and verified writes; >2 MiB external file open routes to read-only pager; Original remains default and stable.

**P1 UI/lifecycle:** fixed Trash position above document \`ScrollView\`, no move triggered by scroll, multi-select and confirmation, navigation, keyboard/back, resume/recreation, duplicate labels with stable IDs, code editor/Markdown themes.

**P2 maintainability:** focused test gaps, CI stability, release manifest, artifacts, reasonable Kotlin code size, low-risk cleanups, stale documents and dead code. No whole-project refactor unless an actual blocker demands it.

**Optional only:** deep energy profiling, broad OEM device matrix, icon/mask refinements, major visual redesign and new editing features. Leave these out of the automatic plan unless they block the agreed final gate.

## Final human gate (NOT one of the six agent tasks)

After Phase F passes, Boss decides when to test the **exact integrated isolated APK**: Thai/English/emoji input and cursor, Original untouched, V2 Home/recovery/rename/Trash/bulk selection/Restore, fixed header and Recent shortcut behavior, safe external TXT Save/Save As, Markdown/code, large-file read-only open, theme, cold start and background restore. Test disposable copies only.

Approval to **merge PRs**, **switch production default**, and **publish a release** are **three separate explicit decisions**, never inferred from a phone PASS report. Keep old installed apps, files and app data.

## Scheduling and handoff

Proposed **November 12, 2026, Asia/Bangkok**, with 30-minute budget per phase and 15-minute gaps: A 04:00; B 04:45; C 05:30; D 06:15; E 07:00; F 07:45. **These are proposals, not created tasks.** Separate one-time Create Tasks should be created in another chat from [the runbook](BLAKE_V2_SIX_PHASE_AGENT_RUNBOOK.md).

Every run must read the plan and [checkpoint](BLAKE_V2_EXECUTION_CHECKPOINT.md) from the explicit branch \`docs/blake-v2-remaining-work-plan-2026-11-12\`, reverify live GitHub, do only its phase, and save short objective evidence. Do not rely on this chat's memory. If it cannot read or write GitHub or a task tool cannot use a coding environment, mark \`BLOCKED\` honestly and stop.
