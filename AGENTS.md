# Blake Editor — agent instructions

This is the **project-specific workflow entry point**. Use GitHub as the source of truth; never rely on another chat's hidden context.

## At the start of every work unit

1. Read `README.md`, [G–L closeout roadmap](docs/BLAKE_V2_CLOSEOUT_ROADMAP.md), [automation workflow](docs/BLAKE_V2_AUTOMATION_WORKFLOW.md), and [automation checkpoint](docs/BLAKE_V2_AUTOMATION_CHECKPOINT.md) at the **explicit branch or SHA** in the task. The [A–F checkpoint](docs/BLAKE_V2_EXECUTION_CHECKPOINT.md) is historical.
2. Read the master reusable skills from `sirawitchotika-boop/boss-skills-repo`:
   - `.agents/skills/autonomous-repo-workflow/SKILL.md`
   - `.agents/skills/github-codex-handoff/SKILL.md`
   - Only the specialist skill needed for this task.
3. Verify live GitHub `main`, active PR heads, exact-head CI, previous gate and approval status. `AGENTS.md` is instructions, not proof of the current project state.
4. Do **one bounded unit**. A missing dependency is `BLOCKED`; pending CI is `WAITING_CI`; already completed work is `ALREADY_DONE`.

## Scope and safety

- Original `MainActivity` stays the default launcher unless Boss separately approves promotion.
- Preserve user documents, private app data, recovery drafts, Android file-routing, backup/encoding, and the >2 MiB read-only gate.
- Do not merge, publish, sign, switch launcher, uninstall, clear app data, overwrite external files, delete branches, or assert phone PASS without explicit authority and evidence.
- For an authorized code fix: use a **new branch and draft PR**, focused tests, and exact-head CI. Never force-push another worker's branch.
- Audit tasks H.1–H.3 are read-only for app code. H.4 may make **one confirmed, approved** fix; otherwise `NO_FIX_REQUIRED`. No speculative feature additions.

## CI budget — choose checks by changed files

- Documentation only (`README.md`, `TASK_PROGRESS.md`, `docs/**`): quick GitHub check, **no APK**.
- Test files only (`app/src/test/**`): run automated tests, **no APK**.
- Any app code, resource, manifest, Gradle, action/workflow or unknown change: **full build/test gates**.
- Explicit manual workflow runs always use the full gate. Do not skip APK when an actual APK, release candidate or device test is needed.
- See [the exact scope rules](docs/BLAKE_V2_AUTOMATION_WORKFLOW.md#ci-budget-policy). A skipped check is `NOT_RUN`, not `PASS`.

## Handoff for the next chat

Use the designated checkpoint/approved tracking issue. Append work unit, status, starting SHA, exact reviewed/tested SHA, changed files, PR, CI run and outcome, blocker, and one next action. Check its current revision before writing; preserve earlier records. If persistence fails, report that clearly.

Reply to Boss in **short English**, with one status, a verified GitHub link, the actual test result, and only 3–5 simple manual checks when needed. Keep technical details in GitHub, not a wall of chat text.
