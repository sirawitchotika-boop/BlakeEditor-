# Blake Editor 2.0 — automation workflow (project rules)

**Master reusable skills:** [boss-skills-repo](https://github.com/sirawitchotika-boop/boss-skills-repo)  
**Project closeout plan:** [Phases G–L](BLAKE_V2_CLOSEOUT_ROADMAP.md)  
**Cross-chat status:** [Current automation checkpoint](BLAKE_V2_AUTOMATION_CHECKPOINT.md)  
**Historical evidence:** [A–F checkpoint](BLAKE_V2_EXECUTION_CHECKPOINT.md)

This is the *Blake-specific execution protocol*, not a copy of the shared skill. It is suitable for one-time ChatGPT Tasks or a fresh manual chat. **It does not schedule tasks or grant future approval.**

## Fast start for a new chat

1. Read `AGENTS.md`, `README.md`, this file, G–L roadmap and automation checkpoint from an **exact Git ref**. The checkpoint on `main` may lag a newer, approved live tracker; read both if the task provides a tracker URL.
2. Read `autonomous-repo-workflow` and `github-codex-handoff` skills from the master repository, plus the relevant specialist skill. Verify the current master-skill ref.
3. Check live main SHA, PR heads, CI, prior phase and any human approvals. Do not carry over completed/blocked statuses blindly.
4. Execute only the assigned work unit with explicit permission; stop on missing gates, failing tests or missing tools.
5. Append a durable handoff to the **approved live checkpoint/tracking issue**, using exact PR/SHA/run links. If no writable tracker is authorized, report `CHECKPOINT_NOT_SAVED` and stop before dependent units.

## Phase H work units — today’s priority

| Unit | Work | Permission | Output |
| --- | --- | --- | --- |
| **H.1** | Recovery, drafts, linked files, Save/Save As, Trash, encoding and data safety | Read-only source audit | P0/P1/P2 findings and tests missing |
| **H.2** | Original/V2 UI, cursor, lifecycle, navigation and regression coverage | Read-only source audit | Concrete UI/test findings |
| **H.3** | CI, asset integrity, dependencies, manifest, package and build risks | Read-only source audit | Build/security findings |
| **H.4** | Combine H.1–H.3; fix **at most one confirmed authorized blocker**, or `NO_FIX_REQUIRED` | New draft PR only when separately approved | One focused fix PR or explicit no-fix record |
| **H.CI** | Verify exact-head CI after H.4, only if needed | Read-only CI gate | `PASS`, `FAIL` or `WAITING_CI` |

H.1–H.3 can run independently. **H.4 is dependent on all three readable handoffs.** Do not schedule later phases I–L automatically. Phase I is human phone acceptance of an exact accepted candidate; J requires signing/upgrade decisions; K requires release approval; L requires final closure approval.

For Phase H audit work, **never create a speculative PR** just to fill time. A code fix requires evidence, a small focused patch, relevant tests, and a new draft PR. Never touch `main` directly.

## CI budget policy

The existing `.github/workflows/build-apk.yml` uses an **automatic change-scope check** on PRs:

| Detected PR diff | Result |
| --- | --- |
| Only `README.md`, `TASK_PROGRESS.md` or `docs/**` | **docs**: scope check only; no JDK, Gradle, vendor downloads or APK |
| Only `app/src/test/**` | **tests**: JVM + JavaScript regression; no APK |
| Any `app/src/main/**`, `app/src/debug/**`, `app/build.gradle.kts`, workflow, resources, Gradle files, or unknown paths | **full**: all original gates, including release and isolated APK |
| Manually triggered `workflow_dispatch` | **full**, regardless of changed paths |
| Empty/ambiguous diff or diff-read failure | **full** or fail visibly; never silently select docs-only |

Use this classification only for safe cost savings. **Full** is required for candidate APK delivery, install tests, release integrity, code/manifest changes and suspicious mixed patches. Test-only changes still execute the relevant tests.

The CI job always reports its selected scope. A skipped APK step is `NOT_RUN`, **not** a successful APK build. CI on a PR head does not prove a later squashed `main` merge or a signed production install. The workflow triggers on `pull_request` and manual dispatch only; after an important merge, use an explicit run on `main` if that commit itself must be verified.

If changing this classifier, **run the full CI gate on the workflow PR**. Verify the following cases before accepting future savings: docs only skips APK; test-only runs tests without APK; mixed code+docs runs full; unknown path runs full; manual run runs full.

## Checkpoint and recovery

The committed [automation checkpoint](BLAKE_V2_AUTOMATION_CHECKPOINT.md) is the stable starting reference. For active multi-chat work, choose a **GitHub tracking issue (append-only comments)** or a **dedicated checkpoint branch** and include its exact link/ref in every task. Create/authorize it before scheduling. Do not let separate tasks overwrite a shared `main` file.

Every handoff must include:

```text
Work unit: H.1 / H.2 / H.3 / H.4 / H.CI
Status: PASS | FAIL | BLOCKED | PARTIAL | WAITING_CI | ALREADY_DONE | NO_FIX_REQUIRED
Started from main SHA:
Exact inspected/tested SHA:
Evidence (file:line, PR link, CI run and conclusion):
Changes (or NONE):
Tests: PASS / FAIL / NOT_RUN (per check)
Human approval gate:
Blocked/unfinished:
One next safe action:
Checkpoint URL/ref and save result:
```

Use expected-head/compare-and-swap when updating Git files. When concurrent handoffs exist, append without dropping any work unit; on a conflict re-read and retry safely. If a task starts after its assigned unit already completed on the same SHA, report `ALREADY_DONE` instead of opening a duplicate PR.

**Never mistake a previous task's scheduled time or completion notification for validated work.** `WAITING_CI` stays pending until the exact head succeeds. `BLOCKED` blocks downstream edits.

## Safety gates

Original remains the only normal launcher and external-file route. Protect saved user files, recovery records, SAF permissions, strict encoding and read-only external large-file handling. No automatic merge, promotion of V2 to launcher, publishing, signing-key changes, uninstall, storage clearing, destructive migration, branch deletion or false physical-phone claims.

Human approvals for a code PR merge, launcher promotion, signed release, and project closure are four **separate** decisions.

## Short user report

```text
H.X — STATUS
Checked commit: <SHA>
Result: <one sentence>
PR / CI: <verified link or NONE>
Blocker: <none or one sentence>
Next: <one action>
Phone: NOT_TESTED (unless Boss actually tested the exact build)
```

Keep the technical log in GitHub so Boss can read a **short** mobile chat response. Show 3–5 simple numbered phone steps only when a new APK requires manual checking.

## Reusable one-time task starter

```text
Execute only Blake Editor work unit H.X.
Read AGENTS.md, README.md, docs/BLAKE_V2_CLOSEOUT_ROADMAP.md,
docs/BLAKE_V2_AUTOMATION_WORKFLOW.md and
docs/BLAKE_V2_AUTOMATION_CHECKPOINT.md from the explicit active Git ref.
Also read the approved live checkpoint/tracking issue: <URL>.
Load autonomous-repo-workflow and github-codex-handoff from
sirawitchotika-boop/boss-skills-repo, plus a relevant specialist skill.
Verify current GitHub state and upstream gate.
Permissions: <READ_ONLY or APPROVED_DRAFT_PR_ALLOWED>.
Perform one bounded work unit; select the smallest justified test tier;
record exact commit/PR/CI and append a checkpoint entry.
If blocked or CI pending, stop and report the status.
Never merge, release, switch launcher, clear data or invent phone tests.
```

**Setup status:** This document and the checkpoint are a proposed workflow until their PR merges and the live tracker is authorized. **No ChatGPT Tasks are created by this PR.**
