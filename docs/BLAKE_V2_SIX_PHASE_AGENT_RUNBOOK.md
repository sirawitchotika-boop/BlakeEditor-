# Blake Editor 2.0 — six one-time agent tasks (PR-only runbook)

**Status: ready for review; NO TASKS have been scheduled here.**
**Anchor:** this file at \`sirawitchotika-boop/BlakeEditor-\`, branch \`docs/blake-v2-remaining-work-plan-2026-11-12\`, open draft PR #31.
**Read together:** \`docs/BLAKE_V2_REMAINING_WORK_PLAN.md\` and \`docs/BLAKE_V2_EXECUTION_CHECKPOINT.md\`.
**Scheduling timezone:** \`Asia/Bangkok\` (UTC+07:00).
**Suggested date:** November 12, 2026. All appointments are ONE-TIME events.

## Proposed appointments (exact start times, not end-time guarantees)

| One-time Create Task | Start | Nominal work budget | Gap to next start |
| --- | --- | --- | --- |
| A — Inventory and baseline | 04:00 | 04:00–04:30 | 15 minutes |
| B — Data safety and recovery review | 04:45 | 04:45–05:15 | 15 minutes |
| C — UI, maintenance and CI review | 05:30 | 05:30–06:00 | 15 minutes |
| D — One targeted fix if needed | 06:15 | 06:15–06:45 | 15 minutes |
| E — Staged integration PR | 07:00 | 07:00–07:30 | 15 minutes |
| F — Exact-head final automated gate | 07:45 | 07:45–08:15 | Final packet only |

**Honesty:** ChatGPT Create Task executes at the scheduled start, but is not a guaranteed uninterrupted 30-minute worker or a durable shell. A task may hit tool, output, permission or duration limits. The schedule may be created even while earlier results are pending; **the checkpoint gate controls whether the work can safely proceed**. If a planned time is already in the past when creating tasks, do NOT schedule it silently for another day.

## Common protocol — mandatory at start of EVERY task

1. Use the GitHub connector, or other authorized read access, to read the **latest** three documents from the named docs branch; also fetch the current \`.agents/skills/github-codex-handoff/SKILL.md\` from \`sirawitchotika-boop/boss-skills-repo\` and its relevant testing skill. Read \`README.md\`, \`AGENTS.md\` if present, and nearby project docs. Missing \`AGENTS.md\` is not a fabricated requirement.
2. Verify current \`main\` SHA, PRs #24/#25/#26/#27/#29/#30/#31, all latest heads and CI results. Do not assume the snapshot is still true.
3. Check the checkpoint. A phase may run a **read-only independent audit** if a previous audit is incomplete, but **no dependent code edits or integration** until upstream blockers and any needed CI are resolved. If the same phase already completed at the same Git refs, mark \`ALREADY_DONE\` without duplicating work.
4. Keep attention to **this phase only**. Budget source reading to the exact affected subsystem, usually at most 1–2 large diffs plus necessary tests. Do not recursively dump the entire repo. Make no more than **one small PR-quality fix** during a fix phase; prefer 1–4 production files plus associated tests. If blocked by access, a huge conflict or unfinished CI, stop, record real evidence and continue only on another explicitly scheduled future run.
5. For code changes, use **new nonproduction feature branches and draft PRs**, with tests. Never edit \`main\`, force-push another task's branch, merge PRs, change Original as default, publish APKs, clear user data, silently rewrite actual files, or change signing and external routing.
6. For PR checks, verify **the exact head SHA**. Accept only observed \`testDebugUnitTest assembleDebug\`, input/caret regression, \`assembleRelease\` (not published), release manifest/reader gate, and isolated APK package check. A CI job that is queued/in progress is \`WAITING_CI\`, not \`PASSED\`.
7. Write a short checkpoint ledger entry on the docs branch **only if authorized and writable**; use fresh refs and safe concurrency checks. In user-visible output, limit to roughly **200–300 words** with: \`Phase | Status | Verified head | Work | CI | Blocker | Next\`. Never claim the scheduled task completed real phone tests.
8. Do not over-interpret a task's nominal 30 minutes as guaranteed execution time. Stop after a useful, verifiable work unit; report what remains.

## Exact phase instructions

### Task A — 04:00: Inventory and baseline (read-only)

**Objective:** Start with a reliable map, not code changes.

- Verify the stacked PR ancestry and branch bases (particularly #24 parallel to #25→#26→#27→#29→#30); check #30 CI and read open review comments.
- Read the current code/recovery architecture **map**; list affected modules and essential test suites, summarize which behavior is already implemented and which full-gate tests are only human-verifiable.
- Identify historical merged PRs/duplicates to avoid double implementation. Record risk tiers P0 data loss / P1 compatibility / P2 polish.
- Deliver one status matrix with exact refs and **three highest-value review targets**.
- **Allowed actions:** GitHub read, documentation checkpoint update on docs branch. **Forbidden:** app code changes, PR merge, release and phone test claims.
- **Pass:** accurate live baseline and B/C entry points recorded. **Block:** cannot access GitHub or determine current heads.

### Task B — 04:45: Recovery and real-file safety review

**Objective:** Find true data-loss risks; do not rewrite everything.

- Read recovery store, workspace, identity, verified saver, and their focused tests at the **latest candidate PR head**; prioritize the #29/#30 diff and existing versioned recovery formats.
- Check revisions and persistent counters, serialization/migration, corrupted record handling, atomic Trash moves and batch partial failures, Restore of linked changed files into unlinked copies, saved revision cleanup, Recent shortcut sidecar and metadata invariants, SAF external file writes, safe Save/Save As, Unicode/encoding/BOM.
- Identify missing tests with source locations and a minimal patch if safe and authorized. Prefer reviewing only; **do not** make speculative storage rewrites.
- **Allowed:** read-only review, add tests or a single small safety fix in a **separate draft PR** if a confirmed P0/P1 defect and GitHub write access permit. Do not merge.
- **Pass:** findings classified with evidence; no unexamined P0 risk. **Block:** confirmed P0 unresolved (code review itself may be \`PASSED\` while integration gate is \`BLOCKED\`).

### Task C — 05:30: UI, maintenance, build and navigation review

**Objective:** Catch usability and integration problems without adding features.

- Check Home fixed Trash navigation OUTSIDE scrolling list; checkbox selection and final confirmations; Recent hidden shortcuts; keyboard, Android Back, Home categories, activity lifecycle, restore/focus/scroll; Original's entrypoint and external large-file reader.
- Review CI scripts/manifests for debug-only launcher leakage, isolated package uniqueness, test scope, and documentation drift. Do not silently modify Original.
- Classify bugs as **blocking**, **nonblocking**, **optional** and record a concise prioritized fix queue with file paths/lines.
- **Allowed:** GitHub read, checkpoint update; one tiny test-only change via separate draft PR if clearly needed and authorized. **Forbidden:** broad UI redesign/refactor/merge/release.
- **Pass:** review complete and D queue explicit. **Block:** cannot verify critical gate condition.

### Task D — 06:15: Fix one verified blocker or skip

**Objective:** Fix only what reviews actually demonstrated.

- Read B/C findings and latest PR heads. Prioritize a single confirmed P0, then P1. Reproduce if possible with a focused test.
- If a small correction is safe, use a **separate draft PR** on the appropriate branch; include regression test; wait for exact-head CI results when possible. Do NOT merge or combine with nonblocking feature changes.
- If there is **no** confirmed blocker, do **not** invent changes; record \`NO_FIX_REQUIRED\` and the verified test suite status.
- If defects exceed the budget, fix only one, record remaining issues, and mark integration \`BLOCKED\` until they are handled.
- **Allowed:** scoped code/test draft PR. **Forbidden:** force push, invasive migration, real user data changes, merge, default launcher promotion or release.
- **Pass:** no blocker or one verified fix with evidence. **Wait:** check queued/running CI at the next task; don't label it passing.

### Task E — 07:00: Prepare isolated integration (not a merge)

**Objective:** Combine the relevant reviewable feature work for one PR-only CI gate.

- Only proceed if Phase B/C reviews and D indicate no unresolved P0/P1 blockers; if a fix PR is still waiting for CI, stop with \`WAITING_CI\`.
- Reverify both source streams: **#24** (parallel optional beta entry) and the full stacked **#25/#26/#27/#29/#30** history (the #30 head includes the stack).
- If tools/environment support it **safely**, create a new **nonproduction integration feature branch** from current \`main\`, apply needed approved/reviewed features without changing Original's default, resolve actual conflicts carefully, and open ONE **draft integration PR targeting \`main\`**. No GitHub merge operations or release. If the combination is too large or access is insufficient, leave a precise conflict/dependency matrix and \`BLOCKED\` rather than claiming integration exists.
- Trigger PR CI if supported (normally automatic); capture exact PR head and run link.
- **Pass:** integration draft PR and traceable source commits, with CI status recorded. **Block:** any unreviewed lost-file risk, permission issue or unresolvable conflict.

### Task F — 07:45: Final PR-only gate and human checklist

**Objective:** Deliver a trustworthy handoff, not an unapproved release.

- Read exact integration draft PR head (if it exists), test statuses, regression and release-manifest checks, and any fixed-branch CI. Do not rely on earlier green builds with different SHAs.
- If prior phase is \`BLOCKED\` or integration CI still running, produce a **gap and blocker report** (\`BLOCKED\` or \`WAITING_CI\`) with exact next action. Do not call it ready.
- If all PR-only checks passed, write \`READY_FOR_HUMAN_GATE\` and a concise 5-part real-phone acceptance checklist: (1) Original typing and external file safeguards, (2) V2 native editor/code/Markdown/settings, (3) drafts/rename/Trash/Restore/Recent, (4) saved file/encoding/large-file read-only, (5) reboot/background/theme and file integrity.
- Provide exact unique isolated APK artifact link **only if** GitHub proves it exists on the exact integrated head; otherwise no fabricated download.
- **Never** run/claim human phone acceptance, merge, switch default launcher, modify signing or ship release.
- **Pass:** a complete evidence-based final gate packet; release remains Boss's decision.

## Copy-ready one-time task prompts

Create **six separate exact-time, one-shot Tasks** using the schedule above, with no RRULE. Each task prompt should use this structure (substitute only the phase letter and human-readable description):

\`\`\`text
Execute only PHASE [A/B/C/D/E/F] of Blake Editor 2.0's one-time agent runbook. First read the latest GitHub files from repo sirawitchotika-boop/BlakeEditor-, branch docs/blake-v2-remaining-work-plan-2026-11-12:
docs/BLAKE_V2_REMAINING_WORK_PLAN.md
docs/BLAKE_V2_SIX_PHASE_AGENT_RUNBOOK.md
docs/BLAKE_V2_EXECUTION_CHECKPOINT.md
Also read the master GitHub workflow skill from sirawitchotika-boop/boss-skills-repo and verify live PR/CI state. Follow the specific Phase [LETTER] scope, dependency gates, safe draft-PR-only permissions, short output and checkpoint update rules. No physical phone testing, merging, release, main branch changes, production default switch, real-file deletion, or installed app data changes. If prior dependencies are unfinished or required tools missing, checkpoint BLOCKED or WAITING_CI instead of fabricating success. Report exact SHA, PR link, CI state and next safe step.
\`\`\`

**Reminder:** Do not create tasks for past timestamps. Creating the tasks in a fresh chat may require reconnecting GitHub or enabling a supported coding environment. A one-time Task is not a persistent shell and cannot promise to run for the entire nominal 30-minute window.
